package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

const val ONLINE_ACTIVITY_WINDOW_MS: Long = 5 * 60 * 1000L

fun resolveIsUserOnline(
    isOnlineFlag: Boolean,
    lastActiveAt: Timestamp?,
    updatedAt: Timestamp? = null,
    nowMillis: Long = System.currentTimeMillis(),
    windowMs: Long = ONLINE_ACTIVITY_WINDOW_MS
): Boolean {
    if (!isOnlineFlag) return false
    val referenceTimestamp = lastActiveAt ?: updatedAt ?: return true
    val ageMs = nowMillis - referenceTimestamp.toDate().time
    return ageMs <= windowMs
}

@Keep
@IgnoreExtraProperties
data class UserAccount(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val pushCallAlerts: Boolean = true,
    val pushMessageAlerts: Boolean = true,
    val hdVideoDefault: Boolean = true,
    @get:PropertyName("isOnline")
    @PropertyName("isOnline")
    val isOnline: Boolean = false,
    val lastActiveAt: Timestamp? = null,
    val photoUrl: String? = null,
    val avatarBase64: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

fun UserAccount.isRecentlyOnline(nowMillis: Long = System.currentTimeMillis()): Boolean =
    resolveIsUserOnline(
        isOnlineFlag = isOnline,
        lastActiveAt = lastActiveAt,
        updatedAt = updatedAt,
        nowMillis = nowMillis
    )

@Keep
@IgnoreExtraProperties
data class CreatorProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val handle: String = "",
    val bio: String? = null,
    val status: String? = "online",
    @get:PropertyName("isDiscoverable")
    @PropertyName("isDiscoverable")
    val isDiscoverable: Boolean = true,
    @get:PropertyName("isOnline")
    @PropertyName("isOnline")
    val isOnline: Boolean = false,
    val lastActiveAt: Timestamp? = null,
    val photoUrl: String? = null,
    val avatarBase64: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

fun CreatorProfile.isRecentlyOnline(nowMillis: Long = System.currentTimeMillis()): Boolean =
    resolveIsUserOnline(
        isOnlineFlag = isOnline,
        lastActiveAt = lastActiveAt,
        updatedAt = updatedAt,
        nowMillis = nowMillis
    )

@IgnoreExtraProperties
data class PublishedVideo(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val title: String = "",
    val description: String? = null,
    val category: String = "Tech",
    val videoUri: String? = null,
    val coverKey: String? = null,
    val thumbnailBase64: String? = null,
    val visibility: String = "public",
    val likesCount: Long = 0L,
    val commentsCount: Long = 0L,
    val durationSec: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class VideoComment(
    @DocumentId val id: String = "",
    val videoId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatThread(
    @DocumentId val id: String = "",
    val title: String = "",
    val isGroup: Boolean = false,
    val participantIds: List<String> = emptyList(),
    val participantNames: List<String>? = null,
    val createdBy: String = "",
    val lastMessageText: String = "",
    val lastMessageSenderId: String = "",
    @ServerTimestamp val lastMessageAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val messageType: String = "text",
    val text: String = "",
    val imageBase64: String? = null,
    val voiceDurationSec: Long? = null,
    val participantIds: List<String> = emptyList(),
    val isRead: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

const val CALL_RING_WINDOW_MS: Long = 3 * 60 * 1000L

@Keep
@IgnoreExtraProperties
data class CallSession(
    @DocumentId val id: String = "",
    val hostId: String = "",
    val hostName: String = "",
    val roomCode: String = "",
    val callType: String = "video",
    val status: String = "ringing",
    val participantIds: List<String> = emptyList(),
    val participantNames: List<String>? = null,
    val durationSec: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

fun CallSession.isFreshRingingForUser(
    currentUserId: String,
    nowMillis: Long = System.currentTimeMillis()
): Boolean {
    if (status != "ringing") return false
    if (hostId.isBlank() || hostId == currentUserId) return false
    if (currentUserId !in participantIds) return false
    val refTimestamp = updatedAt ?: createdAt ?: return true
    return (nowMillis - refTimestamp.toDate().time) <= CALL_RING_WINDOW_MS
}

@Keep
@IgnoreExtraProperties
data class CallSignal(
    @DocumentId val id: String = "",
    val callId: String = "",
    val senderId: String = "",
    val recipientId: String = "",
    val type: String = "",
    val sdp: String? = null,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int? = null,
    val candidate: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AdminRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val role: String = "moderator",
    val grantedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

