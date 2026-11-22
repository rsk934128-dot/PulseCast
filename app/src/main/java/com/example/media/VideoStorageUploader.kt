package com.example.media

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

sealed interface VideoUploadOutcome {
    data class CloudUploaded(val downloadUrl: String) : VideoUploadOutcome
    data class LocalDeviceOnly(val localFilePath: String?) : VideoUploadOutcome
}

interface VideoFileUploader {
    suspend fun uploadVideo(
        uid: String,
        videoId: String,
        localUri: Uri,
        mimeType: String?,
        onProgress: (Float) -> Unit
    ): VideoUploadOutcome

    suspend fun deleteByDownloadUrl(downloadUrl: String)

    fun deleteLocalCopy(videoId: String) {}
}

class FirebaseStorageVideoUploader(
    internal val appContext: Context? = null,
    storageProvider: () -> FirebaseStorage = { Firebase.storage }
) : VideoFileUploader {

    private val storage: FirebaseStorage by lazy(storageProvider)

    override suspend fun uploadVideo(
        uid: String,
        videoId: String,
        localUri: Uri,
        mimeType: String?,
        onProgress: (Float) -> Unit
    ): VideoUploadOutcome {
        val safeMime = mimeType?.takeIf { it.startsWith("video/") } ?: "video/mp4"
        val extension = extensionForMime(safeMime)
        val objectFileName = "$videoId.$extension"
        val metadata = StorageMetadata.Builder()
            .setContentType(safeMime)
            .setCustomMetadata("authorId", uid)
            .setCustomMetadata("videoId", videoId)
            .build()

        onProgress(0f)

        val primaryBucket = storage.app.options.storageBucket?.trim().orEmpty()
        val altBucket = resolveAlternateBucketName(storage)

        val primaryExists = if (primaryBucket.isNotEmpty()) probeBucketExists(primaryBucket) else null
        if (primaryExists != false) {
            try {
                val url = uploadToBucket(
                    targetStorage = storage,
                    uid = uid,
                    objectFileName = objectFileName,
                    localUri = localUri,
                    metadata = metadata,
                    onProgress = onProgress
                )
                return VideoUploadOutcome.CloudUploaded(url)
            } catch (e: CancellationException) {
                throw e
            } catch (primaryError: Exception) {
                if (!isBucketOrObjectNotFound(primaryError)) {
                    Log.w(TAG, "Firebase Storage upload failed for videos/$uid/$objectFileName", primaryError)
                    throw mapFriendlyStorageException(primaryError, storage.app.options.projectId)
                }
                if (primaryBucket.isNotEmpty()) {
                    bucketAvailabilityCache[primaryBucket] = false
                }
            }
        }

        if (!altBucket.isNullOrBlank()) {
            val altExists = probeBucketExists(altBucket)
            if (altExists != false) {
                try {
                    val altStorage = Firebase.storage(storage.app, "gs://$altBucket")
                    val url = uploadToBucket(
                        targetStorage = altStorage,
                        uid = uid,
                        objectFileName = objectFileName,
                        localUri = localUri,
                        metadata = metadata,
                        onProgress = onProgress
                    )
                    return VideoUploadOutcome.CloudUploaded(url)
                } catch (e: CancellationException) {
                    throw e
                } catch (altError: Exception) {
                    if (!isBucketOrObjectNotFound(altError)) {
                        Log.w(TAG, "Alternate Storage bucket upload failed for gs://$altBucket", altError)
                        throw mapFriendlyStorageException(altError, storage.app.options.projectId)
                    }
                    bucketAvailabilityCache[altBucket] = false
                }
            }
        }

        // Neither Storage bucket is provisioned (HTTP 404). Persist locally and allow Firestore cloud-chunk sync.
        Log.i(TAG, "Firebase Storage bucket not provisioned; saving local playable clip for $videoId")
        val savedPath = persistLocalVideoCopy(videoId = videoId, extension = extension, localUri = localUri)
        onProgress(0.05f)
        return VideoUploadOutcome.LocalDeviceOnly(savedPath)
    }

    private suspend fun uploadToBucket(
        targetStorage: FirebaseStorage,
        uid: String,
        objectFileName: String,
        localUri: Uri,
        metadata: StorageMetadata,
        onProgress: (Float) -> Unit
    ): String {
        val storageRef = targetStorage.reference
            .child("videos")
            .child(uid)
            .child(objectFileName)

        return try {
            val uploadTask = storageRef.putFile(localUri, metadata)
            uploadTask.addOnProgressListener { snapshot ->
                val total = snapshot.totalByteCount
                if (total > 0L) {
                    val fraction = (snapshot.bytesTransferred.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    onProgress(fraction)
                }
            }
            try {
                uploadTask.await()
            } catch (e: CancellationException) {
                uploadTask.cancel()
                throw e
            }
            onProgress(1f)
            storageRef.downloadUrl.await().toString()
        } catch (e: CancellationException) {
            throw e
        } catch (fileErr: Exception) {
            val resolver = appContext?.contentResolver
            if (resolver != null && isLocalUriReadFailure(fileErr)) {
                Log.w(TAG, "putFile failed to read URI directly; retrying via ContentResolver stream", fileErr)
                resolver.openInputStream(localUri)?.use { stream ->
                    val streamTask = storageRef.putStream(stream, metadata)
                    streamTask.addOnProgressListener { snapshot ->
                        val total = snapshot.totalByteCount
                        if (total > 0L) {
                            val fraction = (snapshot.bytesTransferred.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                            onProgress(fraction)
                        }
                    }
                    try {
                        streamTask.await()
                    } catch (e: CancellationException) {
                        streamTask.cancel()
                        throw e
                    }
                    onProgress(1f)
                    return storageRef.downloadUrl.await().toString()
                }
            }
            throw fileErr
        }
    }

    private suspend fun persistLocalVideoCopy(
        videoId: String,
        extension: String,
        localUri: Uri
    ): String? = withContext(Dispatchers.IO) {
        val ctx = appContext ?: return@withContext null
        try {
            val dir = File(ctx.filesDir, LOCAL_VIDEOS_DIR).apply { mkdirs() }
            val targetFile = File(dir, "$videoId.$extension")
            ctx.contentResolver.openInputStream(localUri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
            } ?: return@withContext null
            targetFile.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "Could not copy local clip for offline preview: ${e.message}")
            null
        }
    }

    override suspend fun deleteByDownloadUrl(downloadUrl: String) {
        if (!downloadUrl.startsWith("https://firebasestorage.googleapis.com/") &&
            !downloadUrl.startsWith("gs://")
        ) {
            return
        }
        try {
            storage.getReferenceFromUrl(downloadUrl).delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Best-effort Storage cleanup failed for $downloadUrl: ${e.message}")
        }
    }

    override fun deleteLocalCopy(videoId: String) {
        val ctx = appContext ?: return
        val dir = File(ctx.filesDir, LOCAL_VIDEOS_DIR)
        if (!dir.exists()) return
        dir.listFiles { file -> file.name.startsWith("$videoId.") }?.forEach { file ->
            runCatching { file.delete() }
        }
    }

    private suspend fun probeBucketExists(bucketName: String): Boolean? {
        bucketAvailabilityCache[bucketName]?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://firebasestorage.googleapis.com/v0/b/$bucketName/o?maxResults=1"
                val request = Request.Builder().url(url).get().build()
                probeClient.newCall(request).execute().use { response ->
                    val exists = response.code != 404
                    bucketAvailabilityCache[bucketName] = exists
                    exists
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
    }

    companion object {
        private const val TAG = "VideoStorageUploader"
        internal const val LOCAL_VIDEOS_DIR = "local_videos"

        private val bucketAvailabilityCache = ConcurrentHashMap<String, Boolean>()
        private val probeClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(4, TimeUnit.SECONDS)
                .build()
        }

        internal fun extensionForMime(safeMime: String): String = when (safeMime.lowercase()) {
            "video/webm" -> "webm"
            "video/quicktime" -> "mov"
            "video/3gpp" -> "3gp"
            "video/x-matroska" -> "mkv"
            else -> "mp4"
        }

        fun resolveLocalVideoFile(context: Context, videoId: String): File? {
            if (videoId.isBlank()) return null
            val dir = File(context.filesDir, LOCAL_VIDEOS_DIR)
            if (!dir.exists()) return null
            return dir.listFiles { file ->
                file.isFile && file.length() > 0L && file.name.startsWith("$videoId.")
            }?.firstOrNull()
        }

        fun resolveLocalVideoUri(context: Context, videoId: String): String? {
            val match = resolveLocalVideoFile(context, videoId) ?: return null
            return Uri.fromFile(match).toString()
        }

        internal fun isBucketOrObjectNotFound(error: Throwable): Boolean {
            if (error is StorageException && error.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND) {
                return true
            }
            val msg = error.message?.lowercase().orEmpty()
            return "object does not exist at location" in msg || "bucket does not exist" in msg
        }

        internal fun isLocalUriReadFailure(error: Throwable): Boolean {
            if (error is SecurityException || error is java.io.FileNotFoundException) return true
            val msg = error.message?.lowercase().orEmpty()
            return "could not open resulting stream" in msg || "permission denial" in msg
        }

        internal fun resolveAlternateBucketName(storage: FirebaseStorage): String? {
            val bucket = storage.app.options.storageBucket?.trim().orEmpty()
            val projectId = storage.app.options.projectId?.trim().orEmpty()
            return when {
                bucket.endsWith(".firebasestorage.app") && projectId.isNotEmpty() ->
                    "$projectId.appspot.com"
                bucket.endsWith(".appspot.com") && projectId.isNotEmpty() ->
                    "$projectId.firebasestorage.app"
                else -> null
            }
        }

        internal fun mapFriendlyStorageException(error: Throwable, projectId: String?): Exception {
            val msg = error.message?.lowercase().orEmpty()
            return when {
                isBucketOrObjectNotFound(error) -> IllegalStateException(
                    "Firebase Storage is not enabled for project ${projectId ?: "this app"} yet (no Storage bucket found). Enable Firebase Console -> Build -> Storage -> Get started and apply storage.rules, OR remove the file and publish with a Stream URL.",
                    error
                )
                error is StorageException &&
                    (error.errorCode == StorageException.ERROR_NOT_AUTHORIZED ||
                        error.errorCode == StorageException.ERROR_NOT_AUTHENTICATED) ||
                    "permission" in msg -> IllegalStateException(
                    "Firebase Storage blocked this upload. Sign in again and ensure storage.rules allows authenticated writes to videos/{userId}/{fileName}.",
                    error
                )
                else -> if (error is Exception) error else IllegalStateException(error.message ?: "Video upload failed", error)
            }
        }
    }
}
