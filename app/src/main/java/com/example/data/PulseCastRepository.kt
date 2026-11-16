package com.example.data

import android.content.Context
import android.net.Uri
import com.example.R
import com.example.media.FirebaseStorageVideoUploader
import com.example.media.VideoFileUploader
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

sealed interface VideoUploadStage {
    data class UploadingMedia(val progress: Float) : VideoUploadStage
    data object SavingMetadata : VideoUploadStage
}

class PulseCastRepository(
    private val db: FirebaseFirestore,
    private val videoUploader: VideoFileUploader = FirebaseStorageVideoUploader()
) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    suspend fun ensureUserAndCreatorInitialized(
        uid: String,
        displayName: String,
        email: String
    ): Result<Unit> = runCatching {
        val safeName = displayName.trim().ifBlank { "PulseCast Creator" }.take(60)
        val safeEmail = email.trim().ifBlank { "$uid@pulsecast.app" }.take(254)
        val userRef = db.collection("users").document(uid)
        try {
            val userSnap = userRef.get().await()
            if (!userSnap.exists()) {
                userRef.set(
                    mapOf(
                        "userId" to uid,
                        "email" to safeEmail,
                        "displayName" to safeName,
                        "pushCallAlerts" to true,
                        "pushMessageAlerts" to true,
                        "hdVideoDefault" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
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
                creatorRef.set(
                    mapOf(
                        "userId" to uid,
                        "displayName" to safeName,
                        "handle" to "${baseHandle}_$suffix",
                        "bio" to "Audio, video & live studio creator on PulseCast",
                        "status" to "online",
                        "isDiscoverable" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
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
        onStageChange: (VideoUploadStage) -> Unit = {}
    ): Result<String> = runCatching {
        val docRef = db.collection("videos").document()
        var uploadedStorageUrl: String? = null
        val resolvedVideoUri = if (localVideoUri != null) {
            onStageChange(VideoUploadStage.UploadingMedia(0f))
            val downloadUrl = videoUploader.uploadVideo(
                uid = uid,
                videoId = docRef.id,
                localUri = localVideoUri,
                mimeType = mimeType
            ) { progress ->
                onStageChange(VideoUploadStage.UploadingMedia(progress))
            }
            uploadedStorageUrl = downloadUrl
            downloadUrl
        } else {
            externalVideoUrl?.trim()?.takeIf { it.isNotEmpty() }?.take(2000)
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

    suspend fun deleteVideo(videoId: String): Result<Unit> = runCatching {
        val docRef = db.collection("videos").document(videoId)
        try {
            val videoUri = runCatching { docRef.get().await().getString("videoUri") }.getOrNull()
            docRef.delete().await()
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

    suspend fun toggleVideoLike(videoId: String, uid: String): Result<Boolean> = runCatching {
        val videoRef = db.collection("videos").document(videoId)
        val likeRef = videoRef.collection("likes").document(uid)
        try {
            val currentlyLiked = likeRef.get().await().exists()
            val batch = db.batch()
            if (currentlyLiked) {
                batch.delete(likeRef)
                batch.update(
                    videoRef,
                    mapOf(
                        "likesCount" to FieldValue.increment(-1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
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
        customRoomCode: String? = null
    ): Result<CallSession> = runCatching {
        val code = (customRoomCode?.trim()?.uppercase()?.replace(Regex("[^A-Z0-9\\-]"), "")
            ?.takeIf { it.length in 4..32 })
            ?: "PULSE-${UUID.randomUUID().toString().take(4).uppercase()}"
        val docRef = db.collection("call_sessions").document(code)
        val cleanHostName = hostName.trim().ifBlank { "Creator" }.take(60)
        val payload = mapOf(
            "hostId" to uid,
            "hostName" to cleanHostName,
            "roomCode" to code,
            "callType" to callType,
            "status" to "ringing",
            "participantIds" to listOf(uid),
            "participantNames" to listOf(cleanHostName),
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
                participantIds = listOf(uid),
                participantNames = listOf(cleanHostName),
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
}
