package com.example.media

import android.net.Uri
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

interface VideoFileUploader {
    suspend fun uploadVideo(
        uid: String,
        videoId: String,
        localUri: Uri,
        mimeType: String?,
        onProgress: (Float) -> Unit
    ): String

    suspend fun deleteByDownloadUrl(downloadUrl: String)
}

class FirebaseStorageVideoUploader(
    storageProvider: () -> FirebaseStorage = { Firebase.storage }
) : VideoFileUploader {

    private val storage: FirebaseStorage by lazy(storageProvider)

    override suspend fun uploadVideo(
        uid: String,
        videoId: String,
        localUri: Uri,
        mimeType: String?,
        onProgress: (Float) -> Unit
    ): String {
        val safeMime = mimeType?.takeIf { it.startsWith("video/") } ?: "video/mp4"
        val extension = when (safeMime.lowercase()) {
            "video/webm" -> "webm"
            "video/quicktime" -> "mov"
            "video/3gpp" -> "3gp"
            "video/x-matroska" -> "mkv"
            else -> "mp4"
        }
        val storageRef = storage.reference
            .child("videos")
            .child(uid)
            .child("$videoId.$extension")

        val metadata = StorageMetadata.Builder()
            .setContentType(safeMime)
            .setCustomMetadata("authorId", uid)
            .setCustomMetadata("videoId", videoId)
            .build()

        onProgress(0f)
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
            onProgress(1f)
            return storageRef.downloadUrl.await().toString()
        } catch (e: CancellationException) {
            uploadTask.cancel()
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase Storage upload failed for videos/$uid/$videoId.$extension", e)
            throw e
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

    private companion object {
        const val TAG = "VideoStorageUploader"
    }
}
