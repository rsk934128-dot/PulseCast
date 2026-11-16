package com.example.ui

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CallSession
import com.example.data.ChatMessage
import com.example.data.ChatThread
import com.example.data.CreatorProfile
import com.example.data.PublishedVideo
import com.example.data.PulseCastRepository
import com.example.data.UserAccount
import com.example.data.VideoComment
import com.example.data.VideoUploadStage
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface VideoUploadUiState {
    data object Idle : VideoUploadUiState
    data class Uploading(val progress: Float) : VideoUploadUiState
    data object SavingMetadata : VideoUploadUiState
    data object Success : VideoUploadUiState
    data class Error(val message: String) : VideoUploadUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class PulseCastViewModel(
    private val repository: PulseCastRepository,
    val currentUserId: String,
    val currentUserName: String,
    val currentUserEmail: String
) : ViewModel() {

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    private val _selectedVideoId = MutableStateFlow<String?>(null)
    val selectedVideoId: StateFlow<String?> = _selectedVideoId.asStateFlow()

    private val _selectedChatId = MutableStateFlow<String?>(null)
    val selectedChatId: StateFlow<String?> = _selectedChatId.asStateFlow()

    private val _activeCallId = MutableStateFlow<String?>(null)
    val activeCallId: StateFlow<String?> = _activeCallId.asStateFlow()

    private val _videoUploadState = MutableStateFlow<VideoUploadUiState>(VideoUploadUiState.Idle)
    val videoUploadState: StateFlow<VideoUploadUiState> = _videoUploadState.asStateFlow()

    val publicVideosState: StateFlow<UiState<List<PublishedVideo>>> =
        repository.observePublicVideos()
            .map<List<PublishedVideo>, UiState<List<PublishedVideo>>> { UiState.Success(it) }
            .catch { error -> emit(UiState.Error(unwrapError(error, "Failed to load video feed"))) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val myVideosState: StateFlow<UiState<List<PublishedVideo>>> =
        repository.observeMyVideos(currentUserId)
            .map<List<PublishedVideo>, UiState<List<PublishedVideo>>> { UiState.Success(it) }
            .catch { error -> emit(UiState.Error(unwrapError(error, "Failed to load your videos"))) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val creatorsState: StateFlow<UiState<List<CreatorProfile>>> =
        repository.observeCreators()
            .map<List<CreatorProfile>, UiState<List<CreatorProfile>>> { UiState.Success(it) }
            .catch { error -> emit(UiState.Error(unwrapError(error, "Failed to load creators"))) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val chatsState: StateFlow<UiState<List<ChatThread>>> =
        repository.observeChats(currentUserId)
            .map<List<ChatThread>, UiState<List<ChatThread>>> { UiState.Success(it) }
            .catch { error -> emit(UiState.Error(unwrapError(error, "Failed to load chats"))) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val callsState: StateFlow<UiState<List<CallSession>>> =
        repository.observeCallSessions(currentUserId)
            .map<List<CallSession>, UiState<List<CallSession>>> { UiState.Success(it) }
            .catch { error -> emit(UiState.Error(unwrapError(error, "Failed to load call logs"))) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val userAccountState: StateFlow<UserAccount?> =
        repository.observeUserAccount(currentUserId)
            .catch { error ->
                unwrapError(error, "Error observing account")
                emit(null)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val selectedVideoComments: StateFlow<List<VideoComment>> =
        _selectedVideoId.flatMapLatest { videoId ->
            if (videoId.isNullOrBlank()) flowOf(emptyList())
            else repository.observeComments(videoId).catch { emit(emptyList()) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val isSelectedVideoLiked: StateFlow<Boolean> =
        _selectedVideoId.flatMapLatest { videoId ->
            if (videoId.isNullOrBlank()) flowOf(false)
            else repository.observeIsVideoLiked(videoId, currentUserId).catch { emit(false) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    val selectedChatMessages: StateFlow<List<ChatMessage>> =
        _selectedChatId.flatMapLatest { chatId ->
            if (chatId.isNullOrBlank()) flowOf(emptyList())
            else repository.observeMessages(chatId, currentUserId).catch { emit(emptyList()) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val activeCallSession: StateFlow<CallSession?> =
        _activeCallId.flatMapLatest { callId ->
            if (callId.isNullOrBlank()) flowOf(null)
            else repository.observeCallSession(callId).catch { emit(null) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    init {
        viewModelScope.launch {
            repository.ensureUserAndCreatorInitialized(
                uid = currentUserId,
                displayName = currentUserName,
                email = currentUserEmail
            ).onFailure {
                Log.w(TAG, "Profile bootstrap failed: ${it.message}")
            }
        }
    }

    fun selectVideo(videoId: String?) {
        _selectedVideoId.value = videoId
    }

    fun selectChat(chatId: String?) {
        _selectedChatId.value = chatId
    }

    fun resetVideoUploadState() {
        _videoUploadState.value = VideoUploadUiState.Idle
    }

    fun uploadAndPublishVideo(
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
        onComplete: () -> Unit = {}
    ) {
        if (title.isBlank()) {
            _videoUploadState.value = VideoUploadUiState.Error("Please enter a title before publishing.")
            return
        }
        val cleanUrl = externalVideoUrl?.trim()?.takeIf { it.isNotEmpty() }
        if (localVideoUri == null && cleanUrl != null &&
            !cleanUrl.startsWith("https://", ignoreCase = true) &&
            !cleanUrl.startsWith("http://", ignoreCase = true)
        ) {
            _videoUploadState.value = VideoUploadUiState.Error(
                "Enter a valid http:// or https:// stream URL, or select a video file."
            )
            return
        }
        if (_videoUploadState.value is VideoUploadUiState.Uploading ||
            _videoUploadState.value is VideoUploadUiState.SavingMetadata
        ) {
            return
        }

        viewModelScope.launch {
            repository.uploadAndPublishVideo(
                uid = currentUserId,
                authorName = currentUserName,
                title = title,
                description = description,
                category = category,
                localVideoUri = localVideoUri,
                externalVideoUrl = cleanUrl,
                mimeType = mimeType,
                coverKey = coverKey,
                thumbnailBase64 = thumbnailBase64,
                visibility = visibility,
                durationSec = durationSec,
                onStageChange = { stage ->
                    _videoUploadState.value = when (stage) {
                        is VideoUploadStage.UploadingMedia -> VideoUploadUiState.Uploading(stage.progress)
                        VideoUploadStage.SavingMetadata -> VideoUploadUiState.SavingMetadata
                    }
                }
            ).onSuccess {
                _videoUploadState.value = VideoUploadUiState.Success
                _snackbarEvents.tryEmit(
                    if (localVideoUri != null) "Video uploaded to Storage & published"
                    else "Video metadata published to PulseCast"
                )
                onComplete()
            }.onFailure { err ->
                val message = err.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: "Failed to upload video. Check your connection and Firebase Storage setup."
                _videoUploadState.value = VideoUploadUiState.Error(message)
            }
        }
    }

    fun publishVideo(
        title: String,
        description: String?,
        category: String,
        videoUri: String?,
        coverKey: String?,
        thumbnailBase64: String?,
        visibility: String,
        durationSec: Long,
        onComplete: () -> Unit = {}
    ) {
        if (title.isBlank()) {
            _snackbarEvents.tryEmit("Please enter a video title.")
            return
        }
        viewModelScope.launch {
            repository.publishVideo(
                uid = currentUserId,
                authorName = currentUserName,
                title = title,
                description = description,
                category = category,
                videoUri = videoUri,
                coverKey = coverKey,
                thumbnailBase64 = thumbnailBase64,
                visibility = visibility,
                durationSec = durationSec
            ).onSuccess {
                _snackbarEvents.tryEmit("Video published to PulseCast")
                onComplete()
            }.onFailure {
                _snackbarEvents.tryEmit(it.localizedMessage ?: "Failed to publish video")
            }
        }
    }

    fun toggleVideoLike(videoId: String) {
        viewModelScope.launch {
            repository.toggleVideoLike(videoId, currentUserId)
                .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "Could not update like") }
        }
    }

    fun addComment(videoId: String, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.addComment(videoId, currentUserId, currentUserName, text)
                .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "Failed to post comment") }
        }
    }

    fun deleteVideo(videoId: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteVideo(videoId)
                .onSuccess {
                    _snackbarEvents.tryEmit("Video removed")
                    onDeleted()
                }
                .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "Delete failed") }
        }
    }

    fun createChatThread(
        title: String,
        isGroup: Boolean,
        participantIds: List<String>,
        participantNames: List<String>
    ) {
        viewModelScope.launch {
            repository.createChatThread(
                uid = currentUserId,
                creatorName = currentUserName,
                title = title,
                isGroup = isGroup,
                participantIds = participantIds,
                participantNames = participantNames
            ).onSuccess { chatId ->
                _selectedChatId.value = chatId
            }.onFailure {
                _snackbarEvents.tryEmit(it.localizedMessage ?: "Failed to create conversation")
            }
        }
    }

    fun sendMessage(
        chat: ChatThread,
        messageType: String,
        text: String,
        imageBase64: String? = null,
        voiceDurationSec: Long? = null
    ) {
        viewModelScope.launch {
            repository.sendMessage(
                chatId = chat.id,
                uid = currentUserId,
                senderName = currentUserName,
                participantIds = chat.participantIds,
                messageType = messageType,
                text = text,
                imageBase64 = imageBase64,
                voiceDurationSec = voiceDurationSec
            ).onFailure {
                _snackbarEvents.tryEmit(it.localizedMessage ?: "Failed to send message")
            }
        }
    }

    fun markMessagesRead(chatId: String, messages: List<ChatMessage>) {
        viewModelScope.launch {
            repository.markUnreadMessagesRead(chatId, currentUserId, messages)
        }
    }

    fun startCall(callType: String, linkedChat: ChatThread? = null) {
        viewModelScope.launch {
            repository.createCallSession(
                uid = currentUserId,
                hostName = currentUserName,
                callType = callType
            ).onSuccess { session ->
                _activeCallId.value = session.roomCode
                if (linkedChat != null) {
                    repository.sendMessage(
                        chatId = linkedChat.id,
                        uid = currentUserId,
                        senderName = currentUserName,
                        participantIds = linkedChat.participantIds,
                        messageType = "call_event",
                        text = "Started ${callType.uppercase()} call · Room ${session.roomCode}"
                    )
                }
            }.onFailure {
                _snackbarEvents.tryEmit(it.localizedMessage ?: "Unable to start call")
            }
        }
    }

    fun joinCallByCode(roomCode: String) {
        if (roomCode.isBlank()) {
            _snackbarEvents.tryEmit("Enter a room code to join")
            return
        }
        viewModelScope.launch {
            repository.joinCallSession(roomCode, currentUserId, currentUserName)
                .onSuccess { session ->
                    _activeCallId.value = session.roomCode
                }
                .onFailure {
                    _snackbarEvents.tryEmit(it.localizedMessage ?: "Could not join call room")
                }
        }
    }

    fun endActiveCall(durationSec: Long) {
        val callId = _activeCallId.value ?: return
        _activeCallId.value = null
        viewModelScope.launch {
            repository.endCallSession(callId, durationSec)
                .onSuccess { _snackbarEvents.tryEmit("Call ended (${durationSec}s)") }
                .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "Failed to close call") }
        }
    }

    fun updateProfile(displayName: String, handle: String, bio: String, status: String) {
        viewModelScope.launch {
            repository.updateCreatorProfile(currentUserId, displayName, handle, bio, status)
                .onSuccess { _snackbarEvents.tryEmit("Creator profile saved") }
                .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "Update failed") }
        }
    }

    fun updatePreferences(pushCalls: Boolean, pushMessages: Boolean, hdVideo: Boolean) {
        viewModelScope.launch {
            repository.updateUserPreferences(currentUserId, pushCalls, pushMessages, hdVideo)
                .onSuccess { _snackbarEvents.tryEmit("Preferences updated") }
                .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "Failed to save preferences") }
        }
    }

    private fun unwrapError(error: Throwable, fallback: String): String {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, fallback, rootError)
        return rootError.message ?: fallback
    }

    private companion object {
        const val TAG = "PulseCastVM"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
