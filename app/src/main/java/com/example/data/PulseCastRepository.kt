package com.example.data

import android.content.Context
import android.net.Uri
import com.example.R
import com.example.media.FirebaseStorageVideoUploader
import com.example.media.FirestoreVideoMediaStore
import com.example.media.MAX_FIRESTORE_VIDEO_BYTES
import com.example.media.VideoFileUploader
import com.example.media.VideoTooLargeForFirestoreException
import com.example.media.VideoUploadOutcome
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

sealed interface VideoUploadStage {
    data class UploadingMedia(val progress: Float) : VideoUploadStage
    data object SavingMetadata : VideoUploadStage
}

class PulseCastRepository(
    private val db: FirebaseFirestore,
    private val videoUploader: VideoFileUploader = FirebaseStorageVideoUploader(),
    private val appContext: Context? = (videoUploader as? FirebaseStorageVideoUploader)?.appContext
) {

    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        videoUploader = FirebaseStorageVideoUploader(appContext = context.applicationContext),
        appContext = context.applicationContext
    )

    suspend fun ensureUserAndCreatorInitialized(
        uid: String,
        displayName: String,
        email: String,
        photoUrl: String? = null
    ): Result<Unit> = runCatching {
        val safeName = displayName.trim().ifBlank { "PulseCast Creator" }.take(60)
        val safeEmail = email.trim().ifBlank { "$uid@pulsecast.app" }.take(254)
        val cleanPhotoUrl = photoUrl?.trim()?.takeIf { it.isNotEmpty() }?.take(2000)
        val userRef = db.collection("users").document(uid)
        try {
            val userSnap = userRef.get().await()
            if (!userSnap.exists()) {
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("email", safeEmail)
                    put("displayName", safeName)
                    put("pushCallAlerts", true)
                    put("pushMessageAlerts", true)
                    put("hdVideoDefault", true)
                    put("isOnline", true)
                    if (cleanPhotoUrl != null) put("photoUrl", cleanPhotoUrl)
                    put("lastActiveAt", FieldValue.serverTimestamp())
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                userRef.set(payload).await()
            } else {
                val updates = buildMap<String, Any> {
                    put("isOnline", true)
                    if (cleanPhotoUrl != null) put("photoUrl", cleanPhotoUrl)
                    put("lastActiveAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                userRef.update(updates).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, userRef.path)
            throw e
        }

        val creatorRef = db.collection("creators").document(uid)
        try {
            val creatorSnap = creatorRef.get().await()
            if (!creatorSnap.exists()) {
                val baseHandle = safeName.lowercase()
                    .replace(Regex("[^a-z0-9_]"), "")
                    .ifBlank { "creator" }
                    .take(20)
                val suffix = uid.take(4).lowercase()
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("displayName", safeName)
                    put("handle", "${baseHandle}_$suffix")
                    put("bio", "Audio, video & live studio creator on PulseCast")
                    put("status", "online")
                    put("isDiscoverable", true)
                    put("isOnline", true)
                    if (cleanPhotoUrl != null) put("photoUrl", cleanPhotoUrl)
                    put("lastActiveAt", FieldValue.serverTimestamp())
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                creatorRef.set(payload).await()
            } else {
                val updates = buildMap<String, Any> {
                    put("isOnline", true)
                    if (cleanPhotoUrl != null) put("photoUrl", cleanPhotoUrl)
                    put("lastActiveAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                creatorRef.update(updates).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, creatorRef.path)
            throw e
        }
    }

    fun observeUserAccount(uid: String): Flow<UserAccount?> = flow {
        val docRef = db.collection("users").document(uid)
        emitAll(
            docRef.snapshots().map { snap ->
                snap.toObject(UserAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
        )
    }

    fun observeCreators(): Flow<List<CreatorProfile>> = flow {
        val query = db.collection("creators").whereEqualTo("isDiscoverable", true)
        emitAll(
            query.snapshots().map { snap ->
                snap.toObjects(CreatorProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.displayName.lowercase() }
            }
        )
    }

    fun observeCreatorPresence(userIds: List<String>): Flow<Map<String, CreatorProfile>> {
        val cleanIds = userIds.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(20)
        if (cleanIds.isEmpty()) return flowOf(emptyMap())
        val docFlows = cleanIds.map { peerUid ->
            db.collection("creators").document(peerUid)
                .snapshots()
                .map { snap ->
                    snap.toObject(
                        CreatorProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )?.let { profile -> peerUid to profile }
                }
        }
        return combine(docFlows) { entries ->
            buildMap {
                entries.forEach { entry ->
                    if (entry != null) put(entry.first, entry.second)
                }
            }
        }
    }

    suspend fun updateUserActivity(uid: String, isOnline: Boolean): Result<Unit> = runCatching {
        if (uid.isBlank()) throw IllegalArgumentException("User ID must not be blank.")
        val userRef = db.collection("users").document(uid)
        val creatorRef = db.collection("creators").document(uid)
        val updates = mapOf(
            "isOnline" to isOnline,
            "lastActiveAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            val userSnap = userRef.get().await()
            val creatorSnap = creatorRef.get().await()
            val batch = db.batch()
            var hasUpdates = false
            if (userSnap.exists()) {
                batch.update(userRef, updates)
                hasUpdates = true
            }
            if (creatorSnap.exists()) {
                batch.update(creatorRef, updates)
                hasUpdates = true
            }
            if (hasUpdates) {
                batch.commit().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userRef.path)
            throw e
        }
    }

    suspend fun updateUserPreferences(
        uid: String,
        pushCallAlerts: Boolean,
        pushMessageAlerts: Boolean,
        hdVideoDefault: Boolean
    ): Result<Unit> = runCatching {
        val docRef = db.collection("users").document(uid)
        try {
            docRef.update(
                mapOf(
                    "pushCallAlerts" to pushCallAlerts,
                    "pushMessageAlerts" to pushMessageAlerts,
                    "hdVideoDefault" to hdVideoDefault,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun updateCreatorProfile(
        uid: String,
        displayName: String,
        handle: String,
        bio: String,
        status: String
    ): Result<Unit> = runCatching {
        val creatorRef = db.collection("creators").document(uid)
        val cleanName = displayName.trim().ifBlank { "Creator" }.take(60)
        val cleanHandle = handle.trim().lowercase().replace(Regex("[^a-z0-9_]"), "").ifBlank { "creator" }.take(32)
        val cleanBio = bio.trim().take(240)
        try {
            creatorRef.update(
                mapOf(
                    "displayName" to cleanName,
                    "handle" to cleanHandle,
                    "bio" to cleanBio,
                    "status" to status,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, creatorRef.path)
            throw e
        }
    }

    suspend fun updateProfilePhoto(
        uid: String,
        avatarBase64: String?,
        photoUrl: String? = null
    ): Result<Unit> = runCatching {
        if (uid.isBlank()) throw IllegalArgumentException("User ID must not be blank.")
        val cleanBase64 = avatarBase64?.takeIf { it.isNotBlank() && it.length <= 250000 }
        val cleanPhotoUrl = photoUrl?.trim()?.takeIf { it.isNotEmpty() }?.take(2000)
        val updates = mutableMapOf<String, Any?>(
            "avatarBase64" to cleanBase64,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (cleanPhotoUrl != null) {
            updates["photoUrl"] = cleanPhotoUrl
        }
        val userRef = db.collection("users").document(uid)
        val creatorRef = db.collection("creators").document(uid)
        try {
            val userSnap = userRef.get().await()
            val creatorSnap = creatorRef.get().await()
            val batch = db.batch()
            var hasUpdates = false
            if (creatorSnap.exists()) {
                batch.update(creatorRef, updates)
                hasUpdates = true
            }
            if (userSnap.exists()) {
                batch.update(userRef, updates)
                hasUpdates = true
            }
            if (hasUpdates) {
                batch.commit().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, creatorRef.path)
            throw e
        }
    }

    // --- Video Publishing System ---

    fun observePublicVideos(): Flow<List<PublishedVideo>> = flow {
        val query = db.collection("videos").whereEqualTo("visibility", "public")
        emitAll(
            query.snapshots().map { snap ->
                snap.toObjects(PublishedVideo::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
        )
    }

    fun observeMyVideos(uid: String): Flow<List<PublishedVideo>> = flow {
        val query = db.collection("videos").whereEqualTo("authorId", uid)
        emitAll(
            query.snapshots().map { snap ->
                snap.toObjects(PublishedVideo::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
        )
    }

    suspend fun uploadAndPublishVideo(
        uid: String,
        authorName: String,
        title: String,
        description: String?,
        category: String,
        localVideoUri: Uri?,
        externalVideoUrl: String?,
        mimeType: String?,
        coverKey: String?,
        thumbnailBase64: String?,
        visibility: String,
        durationSec: Long,
        onStageChange: (VideoUploadStage) -> Unit = {},
        onCloudFallbackUsed: () -> Unit = {}
    ): Result<String> = runCatching {
        val docRef = db.collection("videos").document()
        var uploadedStorageUrl: String? = null
        val fallbackExternalUrl = externalVideoUrl?.trim()?.takeIf { it.isNotEmpty() }?.take(2000)
        val safeMime = mimeType?.takeIf { it.startsWith("video/") } ?: "video/mp4"
        val extension = FirebaseStorageVideoUploader.extensionForMime(safeMime)
        var pendingChunkSourceFile: File? = null

        val resolvedVideoUri = if (localVideoUri != null) {
            onStageChange(VideoUploadStage.UploadingMedia(0f))
            when (
                val outcome = videoUploader.uploadVideo(
                    uid = uid,
                    videoId = docRef.id,
                    localUri = localVideoUri,
                    mimeType = mimeType
                ) { progress ->
                    onStageChange(VideoUploadStage.UploadingMedia(progress))
                }
            ) {
                is VideoUploadOutcome.CloudUploaded -> {
                    uploadedStorageUrl = outcome.downloadUrl
                    outcome.downloadUrl
                }
                is VideoUploadOutcome.LocalDeviceOnly -> {
                    val ctx = appContext
                    when {
                        fallbackExternalUrl != null -> fallbackExternalUrl
                        ctx != null -> {
                            pendingChunkSourceFile = prepareCloudSyncFile(
                                context = ctx,
                                videoId = docRef.id,
                                extension = extension,
                                localFilePath = outcome.localFilePath,
                                localVideoUri = localVideoUri
                            )
                            FirestoreVideoMediaStore.buildFirestoreMediaUri(docRef.id, extension)
                        }
                        else -> {
                            onCloudFallbackUsed()
                            null
                        }
                    }
                }
            }
        } else {
            fallbackExternalUrl
        }

        val chunkFile = pendingChunkSourceFile
        if (chunkFile != null && resolvedVideoUri != null) {
            val stagingPayload = buildVideoPayload(
                uid = uid,
                authorName = authorName,
                title = title,
                description = description,
                category = category,
                videoUri = null,
                coverKey = coverKey,
                thumbnailBase64 = thumbnailBase64,
                visibility = "private",
                durationSec = durationSec
            )
            var createdParentDoc = false
            try {
                docRef.set(stagingPayload).await()
                createdParentDoc = true

                uploadChunksToFirestore(
                    docRef = docRef,
                    videoId = docRef.id,
                    uid = uid,
                    mimeType = safeMime,
                    sourceFile = chunkFile
                ) { chunkFraction ->
                    onStageChange(VideoUploadStage.UploadingMedia(0.05f + 0.91f * chunkFraction))
                }

                onStageChange(VideoUploadStage.SavingMetadata)
                docRef.update(
                    mapOf(
                        "videoUri" to resolvedVideoUri,
                        "visibility" to visibility,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                videoUploader.deleteLocalCopy(docRef.id)
                return@runCatching docRef.id
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    if (createdParentDoc) {
                        runCatching { deleteMediaChunksBestEffort(docRef, docRef.id) }
                        runCatching { docRef.delete().await() }
                    }
                    runCatching { videoUploader.deleteLocalCopy(docRef.id) }
                    appContext?.let { runCatching { FirestoreVideoMediaStore.deleteCachedVideo(it, docRef.id) } }
                }
                throw e
            } catch (e: Exception) {
                withContext(NonCancellable) {
                    if (createdParentDoc) {
                        runCatching { deleteMediaChunksBestEffort(docRef, docRef.id) }
                        runCatching { docRef.delete().await() }
                    }
                    runCatching { videoUploader.deleteLocalCopy(docRef.id) }
                    appContext?.let { runCatching { FirestoreVideoMediaStore.deleteCachedVideo(it, docRef.id) } }
                }
                handleFirestoreError(e, OperationType.CREATE, docRef.path)
                throw e
            }
        }

        onStageChange(VideoUploadStage.SavingMetadata)
        val payload = buildVideoPayload(
            uid = uid,
            authorName = authorName,
            title = title,
            description = description,
            category = category,
            videoUri = resolvedVideoUri,
            coverKey = coverKey,
            thumbnailBase64 = thumbnailBase64,
            visibility = visibility,
            durationSec = durationSec
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (uploadedStorageUrl != null) {
                runCatching { videoUploader.deleteByDownloadUrl(uploadedStorageUrl) }
            }
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun publishVideo(
        uid: String,
        authorName: String,
        title: String,
        description: String?,
        category: String,
        videoUri: String?,
        coverKey: String?,
        thumbnailBase64: String?,
        visibility: String,
        durationSec: Long
    ): Result<String> = runCatching {
        val docRef = db.collection("videos").document()
        val payload = buildVideoPayload(
            uid = uid,
            authorName = authorName,
            title = title,
            description = description,
            category = category,
            videoUri = videoUri,
            coverKey = coverKey,
            thumbnailBase64 = thumbnailBase64,
            visibility = visibility,
            durationSec = durationSec
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    private fun buildVideoPayload(
        uid: String,
        authorName: String,
        title: String,
        description: String?,
        category: String,
        videoUri: String?,
        coverKey: String?,
        thumbnailBase64: String?,
        visibility: String,
        durationSec: Long
    ): Map<String, Any> = mapOf(
        "authorId" to uid,
        "authorName" to authorName.trim().ifBlank { "Creator" }.take(60),
        "title" to title.trim().take(120),
        "description" to description?.trim()?.takeIf { it.isNotEmpty() }?.take(1000),
        "category" to category,
        "videoUri" to videoUri?.trim()?.takeIf { it.isNotEmpty() }?.take(2000),
        "coverKey" to coverKey?.trim()?.takeIf { it.isNotEmpty() }?.take(64),
        "thumbnailBase64" to thumbnailBase64?.takeIf { it.isNotEmpty() && it.length <= 250000 },
        "visibility" to visibility,
        "likesCount" to 0L,
        "commentsCount" to 0L,
        "durationSec" to durationSec.coerceIn(0L, 36000L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null }.mapValues { it.value!! }

    suspend fun syncLocalVideoToCloud(
        video: PublishedVideo,
        uid: String,
        onProgress: (Float) -> Unit = {}
    ): Result<String> = runCatching {
        val ctx = appContext ?: throw IllegalStateException("Application context unavailable.")
        val localFile = FirebaseStorageVideoUploader.resolveLocalVideoFile(ctx, video.id)
            ?: throw IllegalStateException("Local video file is no longer present on this device.")
        val ext = FirestoreVideoMediaStore.sanitizeVideoExtension(localFile.extension)
        val mimeType = mimeTypeForExtension(ext)
        val docRef = db.collection("videos").document(video.id)

        when (
            val outcome = videoUploader.uploadVideo(
                uid = uid,
                videoId = video.id,
                localUri = Uri.fromFile(localFile),
                mimeType = mimeType,
                onProgress = onProgress
            )
        ) {
            is VideoUploadOutcome.CloudUploaded -> {
                docRef.update(
                    mapOf(
                        "videoUri" to outcome.downloadUrl,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                videoUploader.deleteLocalCopy(video.id)
                outcome.downloadUrl
            }
            is VideoUploadOutcome.LocalDeviceOnly -> {
                val sizeBytes = localFile.length()
                if (sizeBytes <= 0L) {
                    throw IllegalStateException("Selected local video file is empty.")
                }
                if (sizeBytes > MAX_FIRESTORE_VIDEO_BYTES) {
                    throw VideoTooLargeForFirestoreException(sizeBytes, MAX_FIRESTORE_VIDEO_BYTES)
                }
                val cacheFile = FirestoreVideoMediaStore.cloudVideoTargetFile(ctx, video.id, ext)
                withContext(Dispatchers.IO) {
                    localFile.inputStream().use { input ->
                        FirestoreVideoMediaStore.copyStreamWithMaxLimit(input, cacheFile, MAX_FIRESTORE_VIDEO_BYTES)
                    }
                }
                try {
                    uploadChunksToFirestore(
                        docRef = docRef,
                        videoId = video.id,
                        uid = uid,
                        mimeType = mimeType,
                        sourceFile = cacheFile
                    ) { fraction ->
                        onProgress(0.05f + 0.90f * fraction)
                    }
                    val firestoreUri = FirestoreVideoMediaStore.buildFirestoreMediaUri(video.id, ext)
                    docRef.update(
                        mapOf(
                            "videoUri" to firestoreUri,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                    onProgress(1f)
                    videoUploader.deleteLocalCopy(video.id)
                    firestoreUri
                } catch (e: Exception) {
                    withContext(NonCancellable) {
                        runCatching { deleteMediaChunksBestEffort(docRef, video.id) }
                    }
                    throw e
                }
            }
        }
    }

    suspend fun resolveCloudVideoToCache(video: PublishedVideo): Result<String> = runCatching {
        val ref = FirestoreVideoMediaStore.parseFirestoreMediaUri(video.videoUri)
            ?: throw IllegalArgumentException("Invalid cloud video URI.")
        val ctx = appContext ?: throw IllegalStateException("Application context unavailable.")

        FirestoreVideoMediaStore.resolveCachedVideoUri(ctx, video.id, ref.extension)?.let { cached ->
            return@runCatching cached
        }
        FirebaseStorageVideoUploader.resolveLocalVideoUri(ctx, video.id)?.let { local ->
            return@runCatching local
        }

        val docRef = db.collection("videos").document(video.id)
        val chunksSnap = try {
            docRef.collection("media_chunks")
                .whereEqualTo("videoId", video.id)
                .get()
                .await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "${docRef.path}/media_chunks")
            throw e
        }

        val chunks = chunksSnap.documents.mapNotNull { doc ->
            val idx = doc.getLong("index")?.toInt() ?: return@mapNotNull null
            val b64 = doc.getString("dataBase64")?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            idx to b64
        }
        val expectedTotal = chunksSnap.documents.firstOrNull()?.getLong("totalChunks")?.toInt()
        val targetFile = FirestoreVideoMediaStore.cloudVideoTargetFile(ctx, video.id, ref.extension)
        val tempFile = File(targetFile.parentFile, "${video.id}.${ref.extension}.downloading")

        withContext(Dispatchers.IO) {
            try {
                tempFile.outputStream().use { out ->
                    FirestoreVideoMediaStore.reassembleOrderedBase64Chunks(chunks, expectedTotal, out)
                }
                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
            } catch (e: Exception) {
                runCatching { tempFile.delete() }
                throw e
            }
        }
        Uri.fromFile(targetFile).toString()
    }

    private suspend fun prepareCloudSyncFile(
        context: Context,
        videoId: String,
        extension: String,
        localFilePath: String?,
        localVideoUri: Uri
    ): File = withContext(Dispatchers.IO) {
        val targetFile = FirestoreVideoMediaStore.cloudVideoTargetFile(context, videoId, extension)
        try {
            val localFile = localFilePath?.let(::File)?.takeIf { it.isFile && it.length() > 0L }
            val copiedBytes = if (localFile != null) {
                val length = localFile.length()
                if (length > MAX_FIRESTORE_VIDEO_BYTES) {
                    throw VideoTooLargeForFirestoreException(length, MAX_FIRESTORE_VIDEO_BYTES)
                }
                localFile.inputStream().use { input ->
                    FirestoreVideoMediaStore.copyStreamWithMaxLimit(input, targetFile, MAX_FIRESTORE_VIDEO_BYTES)
                }
            } else {
                context.contentResolver.openInputStream(localVideoUri)?.use { input ->
                    FirestoreVideoMediaStore.copyStreamWithMaxLimit(input, targetFile, MAX_FIRESTORE_VIDEO_BYTES)
                } ?: throw IllegalStateException("Unable to read the selected video file.")
            }
            if (copiedBytes <= 0L) {
                throw IllegalStateException("Selected video file is empty.")
            }
            targetFile
        } catch (e: Exception) {
            runCatching { targetFile.delete() }
            videoUploader.deleteLocalCopy(videoId)
            throw e
        }
    }

    private suspend fun uploadChunksToFirestore(
        docRef: DocumentReference,
        videoId: String,
        uid: String,
        mimeType: String,
        sourceFile: File,
        onFraction: (Float) -> Unit
    ) {
        val chunksCollection = docRef.collection("media_chunks")
        var batch = db.batch()
        var pendingInBatch = 0
        var uploadedCount = 0

        FirestoreVideoMediaStore.streamFileAsBase64Chunks(sourceFile) { index, totalChunks, base64 ->
            val chunkDoc = chunksCollection.document("chunk_$index")
            batch.set(
                chunkDoc,
                mapOf(
                    "videoId" to videoId,
                    "authorId" to uid,
                    "index" to index,
                    "totalChunks" to totalChunks,
                    "mimeType" to mimeType.take(64),
                    "dataBase64" to base64,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            pendingInBatch++
            if (pendingInBatch >= 2 || index == totalChunks - 1) {
                batch.commit().await()
                uploadedCount = index + 1
                onFraction((uploadedCount.toFloat() / totalChunks.toFloat()).coerceIn(0f, 1f))
                batch = db.batch()
                pendingInBatch = 0
            }
        }
    }

    private suspend fun deleteMediaChunksBestEffort(docRef: DocumentReference, videoId: String) {
        val snap = runCatching {
            docRef.collection("media_chunks")
                .whereEqualTo("videoId", videoId)
                .get()
                .await()
        }.getOrNull() ?: return
        if (snap.isEmpty) return
        val docs = snap.documents
        var idx = 0
        while (idx < docs.size) {
            val end = (idx + 200).coerceAtMost(docs.size)
            val batch = db.batch()
            for (i in idx until end) {
                batch.delete(docs[i].reference)
            }
            runCatching { batch.commit().await() }
            idx = end
        }
    }

    private fun mimeTypeForExtension(extension: String): String = when (extension.lowercase()) {
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "3gp" -> "video/3gpp"
        "mkv" -> "video/x-matroska"
        else -> "video/mp4"
    }

    suspend fun deleteVideo(videoId: String): Result<Unit> = runCatching {
        val docRef = db.collection("videos").document(videoId)
        try {
            val videoUri = runCatching { docRef.get().await().getString("videoUri") }.getOrNull()
            deleteMediaChunksBestEffort(docRef, videoId)
            docRef.delete().await()
            videoUploader.deleteLocalCopy(videoId)
            appContext?.let { FirestoreVideoMediaStore.deleteCachedVideo(it, videoId) }
            if (!videoUri.isNullOrBlank()) {
                videoUploader.deleteByDownloadUrl(videoUri)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    fun observeIsVideoLiked(videoId: String, uid: String): Flow<Boolean> = flow {
        val docRef = db.collection("videos").document(videoId).collection("likes").document(uid)
        emitAll(docRef.snapshots().map { it.exists() })
    }

    fun observeLikedVideoIds(videoIds: List<String>, uid: String): Flow<Set<String>> {
        val cleanIds = videoIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (cleanIds.isEmpty() || uid.isBlank()) return flowOf(emptySet())
        val docFlows = cleanIds.map { videoId ->
            db.collection("videos").document(videoId)
                .collection("likes").document(uid)
                .snapshots()
                .map { snap -> if (snap.exists()) videoId else null }
                .catch { emit(null) }
        }
        return combine(docFlows) { results ->
            results.filterNotNull().toSet()
        }
    }

    suspend fun toggleVideoLike(videoId: String, uid: String): Result<Boolean> = runCatching {
        if (videoId.isBlank() || uid.isBlank()) {
            throw IllegalArgumentException("Video ID and user ID must not be blank.")
        }
        val videoRef = db.collection("videos").document(videoId)
        val likeRef = videoRef.collection("likes").document(uid)
        try {
            val likeSnap = likeRef.get().await()
            val currentlyLiked = likeSnap.exists()
            val batch = db.batch()
            if (currentlyLiked) {
                val currentLikes = (videoRef.get().await().getLong("likesCount") ?: 0L).coerceAtLeast(0L)
                batch.delete(likeRef)
                if (currentLikes > 0L) {
                    batch.update(
                        videoRef,
                        mapOf(
                            "likesCount" to FieldValue.increment(-1L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            } else {
                batch.set(
                    likeRef,
                    mapOf(
                        "userId" to uid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.update(
                    videoRef,
                    mapOf(
                        "likesCount" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            !currentlyLiked
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, videoRef.path)
            throw e
        }
    }

    fun observeComments(videoId: String): Flow<List<VideoComment>> = flow {
        val query = db.collection("videos").document(videoId)
            .collection("comments")
            .whereEqualTo("videoId", videoId)
        emitAll(
            query.snapshots().map { snap ->
                snap.toObjects(VideoComment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
        )
    }

    suspend fun addComment(
        videoId: String,
        uid: String,
        authorName: String,
        text: String
    ): Result<String> = runCatching {
        val videoRef = db.collection("videos").document(videoId)
        val commentRef = videoRef.collection("comments").document()
        try {
            val batch = db.batch()
            batch.set(
                commentRef,
                mapOf(
                    "videoId" to videoId,
                    "authorId" to uid,
                    "authorName" to authorName.trim().ifBlank { "Creator" }.take(60),
                    "text" to text.trim().take(500),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                videoRef,
                mapOf(
                    "commentsCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            commentRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, commentRef.path)
            throw e
        }
    }

    // --- Real-Time Messaging Engine ---

    fun observeChats(uid: String): Flow<List<ChatThread>> = flow {
        val query = db.collection("chats").whereArrayContains("participantIds", uid)
        emitAll(
            query.snapshots().map { snap ->
                snap.toObjects(ChatThread::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.lastMessageAt ?: it.createdAt ?: Timestamp(0, 0) }
            }
        )
    }

    suspend fun createChatThread(
        uid: String,
        creatorName: String,
        title: String,
        isGroup: Boolean,
        participantIds: List<String>,
        participantNames: List<String>
    ): Result<String> = runCatching {
        val docRef = db.collection("chats").document()
        val distinctIds = (listOf(uid) + participantIds).distinct().take(20)
        val cleanNames = (listOf(creatorName) + participantNames).distinct().take(20)
        val payload = mapOf(
            "title" to title.trim().ifBlank { "Studio Thread" }.take(80),
            "isGroup" to isGroup,
            "participantIds" to distinctIds,
            "participantNames" to cleanNames,
            "createdBy" to uid,
            "lastMessageText" to "Thread started",
            "lastMessageSenderId" to uid,
            "lastMessageAt" to FieldValue.serverTimestamp(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    fun observeMessages(chatId: String, uid: String): Flow<List<ChatMessage>> = flow {
        val query = db.collection("chats").document(chatId)
            .collection("messages")
            .whereArrayContains("participantIds", uid)
        emitAll(
            query.snapshots().map { snap ->
                snap.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.createdAt ?: Timestamp(Long.MAX_VALUE / 2, 0) }
            }
        )
    }

    suspend fun sendMessage(
        chatId: String,
        uid: String,
        senderName: String,
        participantIds: List<String>,
        messageType: String,
        text: String,
        imageBase64: String? = null,
        voiceDurationSec: Long? = null
    ): Result<String> = runCatching {
        val chatRef = db.collection("chats").document(chatId)
        val msgRef = chatRef.collection("messages").document()
        val safeParticipants = (participantIds + uid).distinct().take(20)
        val safeText = text.trim().take(2000)
        val msgPayload = mapOf(
            "chatId" to chatId,
            "senderId" to uid,
            "senderName" to senderName.trim().ifBlank { "Creator" }.take(60),
            "messageType" to messageType,
            "text" to safeText,
            "imageBase64" to imageBase64?.takeIf { it.isNotEmpty() && it.length <= 250000 },
            "voiceDurationSec" to voiceDurationSec?.coerceIn(0L, 600L),
            "participantIds" to safeParticipants,
            "isRead" to false,
            "createdAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }

        val preview = when (messageType) {
            "image" -> "Photo: ${safeText.ifBlank { "Shared an image" }}".take(500)
            "voice" -> "Voice note (${voiceDurationSec ?: 0}s)".take(500)
            "call_event" -> safeText.ifBlank { "Call session started" }.take(500)
            else -> safeText.take(500)
        }

        try {
            val batch = db.batch()
            batch.set(msgRef, msgPayload)
            batch.update(
                chatRef,
                mapOf(
                    "lastMessageText" to preview,
                    "lastMessageSenderId" to uid,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            msgRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, msgRef.path)
            throw e
        }
    }

    suspend fun markUnreadMessagesRead(
        chatId: String,
        uid: String,
        messages: List<ChatMessage>
    ) {
        val unreadFromOthers = messages.filter { !it.isRead && it.senderId != uid && it.id.isNotBlank() }
        if (unreadFromOthers.isEmpty()) return
        val chatRef = db.collection("chats").document(chatId)
        try {
            val batch = db.batch()
            unreadFromOthers.take(20).forEach { msg ->
                batch.update(chatRef.collection("messages").document(msg.id), mapOf("isRead" to true))
            }
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, chatRef.path)
        }
    }

    // --- Audio & Video Calling Module ---

    fun observeCallSessions(uid: String): Flow<List<CallSession>> = flow {
        val query = db.collection("call_sessions").whereArrayContains("participantIds", uid)
        emitAll(
            query.snapshots().map { snap ->
                snap.toObjects(CallSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
        )
    }

    fun observeCallSession(callId: String): Flow<CallSession?> = flow {
        val docRef = db.collection("call_sessions").document(callId)
        emitAll(
            docRef.snapshots().map { snap ->
                snap.toObject(CallSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
        )
    }

    suspend fun createCallSession(
        uid: String,
        hostName: String,
        callType: String,
        customRoomCode: String? = null,
        invitedParticipantIds: List<String> = emptyList(),
        invitedParticipantNames: List<String> = emptyList()
    ): Result<CallSession> = runCatching {
        val code = (customRoomCode?.trim()?.uppercase()?.replace(Regex("[^A-Z0-9\\-]"), "")
            ?.takeIf { it.length in 4..32 })
            ?: "PULSE-${UUID.randomUUID().toString().take(4).uppercase()}"
        val docRef = db.collection("call_sessions").document(code)
        val cleanHostName = hostName.trim().ifBlank { "Creator" }.take(60)
        val mergedIds = (listOf(uid) + invitedParticipantIds.map { it.trim() }.filter { it.isNotEmpty() })
            .distinct()
            .take(12)
        val mergedNames = (listOf(cleanHostName) + invitedParticipantNames.map { it.trim() }.filter { it.isNotEmpty() })
            .distinct()
            .take(12)
        val payload = mapOf(
            "hostId" to uid,
            "hostName" to cleanHostName,
            "roomCode" to code,
            "callType" to callType,
            "status" to "ringing",
            "participantIds" to mergedIds,
            "participantNames" to mergedNames,
            "durationSec" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            CallSession(
                id = code,
                hostId = uid,
                hostName = cleanHostName,
                roomCode = code,
                callType = callType,
                status = "ringing",
                participantIds = mergedIds,
                participantNames = mergedNames,
                durationSec = 0L
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun joinCallSession(
        roomCode: String,
        uid: String,
        userName: String
    ): Result<CallSession> = runCatching {
        val normalized = roomCode.trim().uppercase().replace(Regex("[^A-Z0-9\\-]"), "")
        require(normalized.length in 4..32) { "Room code must be 4-32 characters." }
        val docRef = db.collection("call_sessions").document(normalized)
        try {
            val snap = docRef.get().await()
            val session = snap.toObject(CallSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: throw IllegalStateException("No active call room found for $normalized")
            require(session.status != "ended") { "This call session has already ended." }
            val updatedIds = (session.participantIds + uid).distinct().take(12)
            val updatedNames = ((session.participantNames ?: emptyList()) + userName.trim().ifBlank { "Guest" }).distinct().take(12)
            docRef.update(
                mapOf(
                    "participantIds" to updatedIds,
                    "participantNames" to updatedNames,
                    "status" to "active",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            session.copy(
                participantIds = updatedIds,
                participantNames = updatedNames,
                status = "active"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun endCallSession(callId: String, durationSec: Long): Result<Unit> = runCatching {
        val docRef = db.collection("call_sessions").document(callId)
        try {
            docRef.update(
                mapOf(
                    "status" to "ended",
                    "durationSec" to durationSec.coerceIn(0L, 86400L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun declineCallSession(callId: String): Result<Unit> = endCallSession(callId, 0L)

    fun observeCallSignals(callId: String, recipientUid: String): Flow<List<CallSignal>> = flow {
        val query = db.collection("call_sessions").document(callId)
            .collection("signals")
            .whereEqualTo("recipientId", recipientUid)
        emitAll(
            query.snapshots().map { snap ->
                snap.toObjects(CallSignal::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.createdAt ?: Timestamp(Long.MAX_VALUE / 2, 0) }
            }
        )
    }

    suspend fun sendCallSignal(
        callId: String,
        senderId: String,
        recipientId: String,
        type: String,
        sdp: String? = null,
        sdpMid: String? = null,
        sdpMLineIndex: Int? = null,
        candidate: String? = null
    ): Result<String> = runCatching {
        val normalizedCallId = callId.trim().uppercase().replace(Regex("[^A-Z0-9\\-]"), "")
        require(normalizedCallId.length in 4..32) { "Invalid callId for signaling." }
        val signalRef = db.collection("call_sessions")
            .document(normalizedCallId)
            .collection("signals")
            .document()
        val payload = buildMap<String, Any> {
            put("callId", normalizedCallId)
            put("senderId", senderId)
            put("recipientId", recipientId)
            put("type", type)
            if (sdp != null) put("sdp", sdp.take(32768))
            if (sdpMid != null) put("sdpMid", sdpMid.take(64))
            if (sdpMLineIndex != null) put("sdpMLineIndex", sdpMLineIndex.coerceIn(0, 32))
            if (candidate != null) put("candidate", candidate.take(2048))
            put("createdAt", FieldValue.serverTimestamp())
        }
        try {
            signalRef.set(payload).await()
            signalRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, signalRef.path)
            throw e
        }
    }

    // --- Admin Panel & Moderation ---

    fun observeIsAdminDoc(uid: String): Flow<Boolean> = flow {
        val docRef = db.collection("admins").document(uid)
        emitAll(docRef.snapshots().map { it.exists() })
    }

    fun observeAdmins(): Flow<List<AdminRecord>> = flow {
        val colRef = db.collection("admins")
        emitAll(
            colRef.snapshots().map { snap ->
                snap.toObjects(AdminRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.displayName.lowercase() }
            }
        )
    }

    fun observeAllUsersForAdmin(): Flow<List<UserAccount>> = flow {
        val colRef = db.collection("users")
        emitAll(
            colRef.snapshots().map { snap ->
                snap.toObjects(UserAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.displayName.lowercase() }
            }
        )
    }

    fun observeAllCreatorsForAdmin(): Flow<List<CreatorProfile>> = flow {
        val colRef = db.collection("creators")
        emitAll(
            colRef.snapshots().map { snap ->
                snap.toObjects(CreatorProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.displayName.lowercase() }
            }
        )
    }

    fun observeAllVideosForAdmin(): Flow<List<PublishedVideo>> = flow {
        val colRef = db.collection("videos")
        emitAll(
            colRef.snapshots().map { snap ->
                snap.toObjects(PublishedVideo::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
        )
    }

    fun observeAllCallSessionsForAdmin(): Flow<List<CallSession>> = flow {
        val colRef = db.collection("call_sessions")
        emitAll(
            colRef.snapshots().map { snap ->
                snap.toObjects(CallSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
        )
    }

    suspend fun adminUpdateVideoVisibility(videoId: String, visibility: String): Result<Unit> = runCatching {
        val docRef = db.collection("videos").document(videoId)
        val safeVisibility = if (visibility == "private") "private" else "public"
        try {
            docRef.update(
                mapOf(
                    "visibility" to safeVisibility,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun adminUpdateCreatorModeration(
        creatorId: String,
        isDiscoverable: Boolean,
        status: String
    ): Result<Unit> = runCatching {
        val docRef = db.collection("creators").document(creatorId)
        val safeStatus = if (status in listOf("online", "in_call", "studio", "away")) status else "online"
        try {
            docRef.update(
                mapOf(
                    "isDiscoverable" to isDiscoverable,
                    "status" to safeStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun adminDeleteCallSession(callId: String): Result<Unit> = runCatching {
        val docRef = db.collection("call_sessions").document(callId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun adminGrantRole(
        targetUid: String,
        displayName: String,
        email: String,
        role: String,
        grantedByUid: String
    ): Result<Unit> = runCatching {
        val cleanUid = targetUid.trim()
        require(cleanUid.isNotEmpty() && cleanUid.length <= 128) { "Valid user UID is required." }
        val safeRole = if (role == "super_admin") "super_admin" else "moderator"
        val docRef = db.collection("admins").document(cleanUid)
        val payload = mapOf(
            "userId" to cleanUid,
            "displayName" to displayName.trim().ifBlank { "Studio Moderator" }.take(60),
            "email" to email.trim().take(254),
            "role" to safeRole,
            "grantedBy" to grantedByUid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun adminRevokeRole(targetUid: String): Result<Unit> = runCatching {
        val docRef = db.collection("admins").document(targetUid)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }
}

