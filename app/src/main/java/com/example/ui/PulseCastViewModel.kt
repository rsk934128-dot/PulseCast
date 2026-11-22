package com.example.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calls.CallNotificationAction
import com.example.data.AdminRecord
import com.example.data.CallSession
import com.example.data.CallSignal
import com.example.data.ChatMessage
import com.example.data.ChatThread
import com.example.data.CreatorProfile
import com.example.data.PublishedVideo
import com.example.data.PulseCastRepository
import com.example.data.UserAccount
import com.example.data.VideoComment
import com.example.data.VideoUploadStage
import com.example.data.isFreshRingingForUser
import com.example.media.FirebaseStorageVideoUploader
import com.example.media.FirestoreVideoMediaStore
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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

sealed interface VideoPlaybackSourceState {
    data object Idle : VideoPlaybackSourceState
    data object Loading : VideoPlaybackSourceState
    data class Playable(val uri: String, val isLocalOnlyFallback: Boolean = false) : VideoPlaybackSourceState
    data class Unavailable(val reason: String) : VideoPlaybackSourceState
    data class Error(val message: String) : VideoPlaybackSourceState
}

@OptIn(ExperimentalCoroutinesApi::class)
class PulseCastViewModel(
    private val repository: PulseCastRepository,
    val currentUserId: String,
    val currentUserName: String,
    val currentUserEmail: String,
    val currentUserEmailVerified: Boolean = false,
    val currentUserPhotoUrl: String? = null
) : ViewModel() {

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    private val _selectedVideoId = MutableStateFlow<String?>(null)
    val selectedVideoId: StateFlow<String?> = _selectedVideoId.asStateFlow()

    private val _selectedChatId = MutableStateFlow<String?>(null)
    val selectedChatId: StateFlow<String?> = _selectedChatId.asStateFlow()

    private val _activeCallId = MutableStateFlow<String?>(null)
    val activeCallId: StateFlow<String?> = _activeCallId.asStateFlow()

    private val _inspectingCreatorTarget = MutableStateFlow<Pair<String, String?>?>(null)

    private val _dismissedIncomingCallIds = MutableStateFlow<Set<String>>(emptySet())

    private val _likingVideoIds = MutableStateFlow<Set<String>>(emptySet())
    val likingVideoIds: StateFlow<Set<String>> = _likingVideoIds.asStateFlow()

    private val _videoUploadState = MutableStateFlow<VideoUploadUiState>(VideoUploadUiState.Idle)
    val videoUploadState: StateFlow<VideoUploadUiState> = _videoUploadState.asStateFlow()

    private val _playbackSources = MutableStateFlow<Map<String, VideoPlaybackSourceState>>(emptyMap())
    val playbackSources: StateFlow<Map<String, VideoPlaybackSourceState>> = _playbackSources.asStateFlow()

    private val _syncingVideoIds = MutableStateFlow<Set<String>>(emptySet())
    val syncingVideoIds: StateFlow<Set<String>> = _syncingVideoIds.asStateFlow()

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

    val inspectingCreator: StateFlow<CreatorProfile?> =
        combine(
            _inspectingCreatorTarget,
            creatorsState,
            userAccountState
        ) { target, creatorsUiState, ownAccount ->
            val (targetUid, fallbackName) = target ?: return@combine null
            val creators = (creatorsUiState as? UiState.Success)?.data.orEmpty()
            creators.firstOrNull { it.userId == targetUid }
                ?: if (targetUid == currentUserId) {
                    CreatorProfile(
                        userId = currentUserId,
                        displayName = ownAccount?.displayName?.ifBlank { currentUserName } ?: currentUserName,
                        handle = currentUserName.lowercase().replace(Regex("[^a-z0-9_]"), "").ifBlank { "creator" },
                        bio = "Audio, video & live studio creator on PulseCast",
                        isDiscoverable = true,
                        isOnline = ownAccount?.isOnline ?: true,
                        lastActiveAt = ownAccount?.lastActiveAt,
                        photoUrl = ownAccount?.photoUrl ?: currentUserPhotoUrl,
                        avatarBase64 = ownAccount?.avatarBase64
                    )
                } else {
                    CreatorProfile(
                        userId = targetUid,
                        displayName = fallbackName?.takeIf { it.isNotBlank() } ?: "PulseCast Creator",
                        handle = fallbackName?.lowercase()?.replace(Regex("[^a-z0-9_]"), "")?.ifBlank { "creator" } ?: "creator",
                        isDiscoverable = true
                    )
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val selectedVideoComments: StateFlow<List<VideoComment>> =
        _selectedVideoId.flatMapLatest { videoId ->
            if (videoId.isNullOrBlank()) flowOf(emptyList())
            else repository.observeComments(videoId).catch { emit(emptyList()) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val likedVideoIds: StateFlow<Set<String>> =
        combine(publicVideosState, myVideosState) { publicState, myState ->
            val publicIds = (publicState as? UiState.Success)?.data?.map { it.id }.orEmpty()
            val myIds = (myState as? UiState.Success)?.data?.map { it.id }.orEmpty()
            (publicIds + myIds).distinct()
        }
            .distinctUntilChanged()
            .flatMapLatest { videoIds ->
                if (videoIds.isEmpty()) flowOf(emptySet())
                else repository.observeLikedVideoIds(videoIds, currentUserId).catch { emit(emptySet()) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptySet())

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

    val selectedChatParticipants: StateFlow<Map<String, CreatorProfile>> =
        combine(_selectedChatId, chatsState) { chatId, state ->
            if (chatId.isNullOrBlank()) emptyList()
            else (state as? UiState.Success)?.data
                ?.firstOrNull { it.id == chatId }
                ?.participantIds
                .orEmpty()
        }
            .distinctUntilChanged()
            .flatMapLatest { participantIds ->
                if (participantIds.isEmpty()) flowOf(emptyMap())
                else repository.observeCreatorPresence(participantIds).catch { emit(emptyMap()) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyMap())

    val activeCallSession: StateFlow<CallSession?> =
        _activeCallId.flatMapLatest { callId ->
            if (callId.isNullOrBlank()) flowOf(null)
            else repository.observeCallSession(callId).catch { emit(null) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val activeCallSignals: StateFlow<List<CallSignal>> =
        _activeCallId.flatMapLatest { callId ->
            if (callId.isNullOrBlank()) flowOf(emptyList())
            else repository.observeCallSignals(callId, currentUserId).catch { emit(emptyList()) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val callParticipantProfiles: StateFlow<Map<String, CreatorProfile>> =
        combine(activeCallSession, incomingCallSession, callsState) { active, incoming, state ->
            val fromActiveAndIncoming = buildList {
                if (active != null) {
                    add(active.hostId)
                    addAll(active.participantIds)
                }
                if (incoming != null) {
                    add(incoming.hostId)
                    addAll(incoming.participantIds)
                }
            }
            val fromRecentCalls = (state as? UiState.Success)?.data
                .orEmpty()
                .take(10)
                .flatMap { listOf(it.hostId) + it.participantIds }
            (fromActiveAndIncoming + fromRecentCalls)
                .map { it.trim() }
                .filter { it.isNotBlank() && it != currentUserId }
                .distinct()
                .take(20)
        }
            .distinctUntilChanged()
            .flatMapLatest { peerIds ->
                if (peerIds.isEmpty()) flowOf(emptyMap())
                else repository.observeCreatorPresence(peerIds).catch { emit(emptyMap()) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyMap())

    val incomingCallSession: StateFlow<CallSession?> =
        combine(callsState, _activeCallId, _dismissedIncomingCallIds) { state, activeId, dismissed ->
            if (!activeId.isNullOrBlank()) return@combine null
            val sessions = (state as? UiState.Success)?.data.orEmpty()
            val nowMs = System.currentTimeMillis()
            sessions.firstOrNull { session ->
                session.isFreshRingingForUser(currentUserId, nowMs) &&
                    session.id !in dismissed &&
                    session.roomCode !in dismissed
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val isBootstrappedSuperAdmin: Boolean =
        currentUserEmail.equals(BOOTSTRAPPED_ADMIN_EMAIL, ignoreCase = true) && currentUserEmailVerified

    val isAdmin: StateFlow<Boolean> =
        repository.observeIsAdminDoc(currentUserId)
            .map { hasDoc -> hasDoc || isBootstrappedSuperAdmin }
            .catch { emit(isBootstrappedSuperAdmin) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), isBootstrappedSuperAdmin)

    val adminVideosState: StateFlow<UiState<List<PublishedVideo>>> =
        isAdmin.flatMapLatest { admin ->
            if (!admin) flowOf(UiState.Success(emptyList()))
            else repository.observeAllVideosForAdmin()
                .map<List<PublishedVideo>, UiState<List<PublishedVideo>>> { UiState.Success(it) }
                .catch { error -> emit(UiState.Error(unwrapError(error, "Failed to load all videos"))) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val adminCreatorsState: StateFlow<UiState<List<CreatorProfile>>> =
        isAdmin.flatMapLatest { admin ->
            if (!admin) flowOf(UiState.Success(emptyList()))
            else repository.observeAllCreatorsForAdmin()
                .map<List<CreatorProfile>, UiState<List<CreatorProfile>>> { UiState.Success(it) }
                .catch { error -> emit(UiState.Error(unwrapError(error, "Failed to load all creators"))) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val adminUsersState: StateFlow<UiState<List<UserAccount>>> =
        isAdmin.flatMapLatest { admin ->
            if (!admin) flowOf(UiState.Success(emptyList()))
            else repository.observeAllUsersForAdmin()
                .map<List<UserAccount>, UiState<List<UserAccount>>> { UiState.Success(it) }
                .catch { error -> emit(UiState.Error(unwrapError(error, "Failed to load users"))) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val adminCallsState: StateFlow<UiState<List<CallSession>>> =
        isAdmin.flatMapLatest { admin ->
            if (!admin) flowOf(UiState.Success(emptyList()))
            else repository.observeAllCallSessionsForAdmin()
                .map<List<CallSession>, UiState<List<CallSession>>> { UiState.Success(it) }
                .catch { error -> emit(UiState.Error(unwrapError(error, "Failed to load call sessions"))) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val adminsListState: StateFlow<UiState<List<AdminRecord>>> =
        isAdmin.flatMapLatest { admin ->
            if (!admin) flowOf(UiState.Success(emptyList()))
            else repository.observeAdmins()
                .map<List<AdminRecord>, UiState<List<AdminRecord>>> { UiState.Success(it) }
                .catch { error -> emit(UiState.Error(unwrapError(error, "Failed to load admins"))) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    @Volatile
    private var lastActivitySyncMs: Long = 0L

    init {
        viewModelScope.launch {
            repository.ensureUserAndCreatorInitialized(
                uid = currentUserId,
                displayName = currentUserName,
                email = currentUserEmail,
                photoUrl = currentUserPhotoUrl
            ).onSuccess {
                lastActivitySyncMs = System.currentTimeMillis()
            }.onFailure {
                Log.w(TAG, "Profile bootstrap failed: ${it.message}")
            }
        }
        viewModelScope.launch {
            activeCallSession.collect { session ->
                val currentCallId = _activeCallId.value
                if (currentCallId != null &&
                    session != null &&
                    session.roomCode == currentCallId &&
                    session.status == "ended"
                ) {
                    _activeCallId.value = null
                    _snackbarEvents.tryEmit(
                        if (session.durationSec == 0L) "Call declined or ended"
                        else "Call ended (${session.durationSec}s)"
                    )
                }
            }
        }
    }

    fun setUserOnline(isOnline: Boolean) {
        lastActivitySyncMs = if (isOnline) System.currentTimeMillis() else 0L
        viewModelScope.launch {
            repository.updateUserActivity(currentUserId, isOnline)
        }
    }

    fun recordRecentActivity(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastActivitySyncMs < ACTIVITY_HEARTBEAT_THROTTLE_MS) return
        lastActivitySyncMs = now
        viewModelScope.launch {
            repository.updateUserActivity(currentUserId, isOnline = true)
        }
    }

    suspend fun markOfflineNow() {
        lastActivitySyncMs = 0L
        withTimeoutOrNull(1500L) {
            repository.updateUserActivity(currentUserId, isOnline = false)
        }
    }

    fun selectVideo(videoId: String?) {
        _selectedVideoId.value = videoId
    }

    fun selectChat(chatId: String?) {
        _selectedChatId.value = chatId
        if (!chatId.isNullOrBlank()) {
            recordRecentActivity()
        }
    }

    fun openCreatorProfile(creatorId: String, fallbackName: String? = null) {
        if (creatorId.isBlank()) return
        _inspectingCreatorTarget.value = creatorId to fallbackName
    }

    fun openCreatorProfile(creator: CreatorProfile) {
        if (creator.userId.isBlank()) return
        _inspectingCreatorTarget.value = creator.userId to creator.displayName
    }

    fun closeCreatorProfile() {
        _inspectingCreatorTarget.value = null
    }

    fun resetVideoUploadState() {
        _videoUploadState.value = VideoUploadUiState.Idle
    }

    fun playbackKey(video: PublishedVideo): String = "${video.id}::${video.videoUri.orEmpty()}"

    fun resolvePlaybackForVideo(
        video: PublishedVideo,
        context: Context,
        forceReload: Boolean = false
    ) {
        val key = playbackKey(video)
        val current = _playbackSources.value[key]
        if (!forceReload && (current is VideoPlaybackSourceState.Loading || current is VideoPlaybackSourceState.Playable)) {
            return
        }

        val rawUri = video.videoUri?.trim().orEmpty()
        when {
            rawUri.startsWith("http://", ignoreCase = true) ||
                rawUri.startsWith("https://", ignoreCase = true) -> {
                _playbackSources.value = _playbackSources.value + (key to VideoPlaybackSourceState.Playable(rawUri))
            }

            FirestoreVideoMediaStore.isFirestoreMediaUri(rawUri) -> {
                val ref = FirestoreVideoMediaStore.parseFirestoreMediaUri(rawUri)
                val cached = FirestoreVideoMediaStore.resolveCachedVideoUri(context, video.id, ref?.extension)
                    ?: FirebaseStorageVideoUploader.resolveLocalVideoUri(context, video.id)
                if (cached != null) {
                    _playbackSources.value = _playbackSources.value + (key to VideoPlaybackSourceState.Playable(cached))
                    return
                }
                _playbackSources.value = _playbackSources.value + (key to VideoPlaybackSourceState.Loading)
                viewModelScope.launch {
                    val result = repository.resolveCloudVideoToCache(video)
                    val nextState = result.fold(
                        onSuccess = { uri -> VideoPlaybackSourceState.Playable(uri) },
                        onFailure = { err ->
                            VideoPlaybackSourceState.Error(
                                err.localizedMessage?.takeIf { it.isNotBlank() }
                                    ?: "Unable to load video stream."
                            )
                        }
                    )
                    _playbackSources.value = _playbackSources.value + (key to nextState)
                }
            }

            rawUri.isBlank() -> {
                val localUri = FirebaseStorageVideoUploader.resolveLocalVideoUri(context, video.id)
                val nextState = if (localUri != null) {
                    VideoPlaybackSourceState.Playable(localUri, isLocalOnlyFallback = true)
                } else {
                    VideoPlaybackSourceState.Unavailable(
                        "Video was uploaded to the creator's device only and has not synced to the cloud."
                    )
                }
                _playbackSources.value = _playbackSources.value + (key to nextState)
            }

            else -> {
                _playbackSources.value = _playbackSources.value + (key to VideoPlaybackSourceState.Playable(rawUri))
            }
        }
    }

    fun syncLocalVideoToCloud(video: PublishedVideo) {
        if (video.id.isBlank() || video.id in _syncingVideoIds.value) return
        _syncingVideoIds.value = _syncingVideoIds.value + video.id
        viewModelScope.launch {
            try {
                repository.syncLocalVideoToCloud(video, currentUserId)
                    .onSuccess {
                        _playbackSources.value = _playbackSources.value.filterKeys { !it.startsWith("${video.id}::") }
                        _snackbarEvents.tryEmit("Video synced to cloud for all users")
                    }
                    .onFailure { err ->
                        _snackbarEvents.tryEmit(
                            err.localizedMessage?.takeIf { it.isNotBlank() }
                                ?: "Failed to sync video to cloud"
                        )
                    }
            } finally {
                _syncingVideoIds.value = _syncingVideoIds.value - video.id
            }
        }
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
            var usedLocalFallback = false
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
                },
                onCloudFallbackUsed = { usedLocalFallback = true }
            ).onSuccess {
                _videoUploadState.value = VideoUploadUiState.Success
                _snackbarEvents.tryEmit(
                    when {
                        usedLocalFallback -> "Published to Feed (saved locally; enable Firebase Storage for cloud streaming)"
                        localVideoUri != null -> "Video uploaded & published for all users"
                        else -> "Video metadata published to PulseCast"
                    }
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
        if (videoId.isBlank() || videoId in _likingVideoIds.value) return
        _likingVideoIds.value = _likingVideoIds.value + videoId
        viewModelScope.launch {
            try {
                repository.toggleVideoLike(videoId, currentUserId)
                    .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "Could not update like") }
            } finally {
                _likingVideoIds.value = _likingVideoIds.value - videoId
            }
        }
    }

    fun notifyVideoLinkCopied(video: PublishedVideo) {
        val cleanTitle = video.title.trim()
        val message = if (cleanTitle.isNotEmpty()) {
            "Video link copied for \"$cleanTitle\""
        } else {
            "Video link copied to clipboard"
        }
        _snackbarEvents.tryEmit(message)
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

    fun startOrOpenDirectChat(peer: CreatorProfile) {
        if (peer.userId == currentUserId) return
        val existingChats = (chatsState.value as? UiState.Success)?.data.orEmpty()
        val existingDirect = existingChats.firstOrNull { thread ->
            !thread.isGroup &&
                thread.participantIds.size == 2 &&
                thread.participantIds.contains(currentUserId) &&
                thread.participantIds.contains(peer.userId)
        }
        if (existingDirect != null) {
            _selectedChatId.value = existingDirect.id
            return
        }
        createChatThread(
            title = peer.displayName.ifBlank { "@${peer.handle}" },
            isGroup = false,
            participantIds = listOf(peer.userId),
            participantNames = listOf(peer.displayName)
        )
    }

    fun createChatThread(
        title: String,
        isGroup: Boolean,
        participantIds: List<String>,
        participantNames: List<String>
    ) {
        recordRecentActivity()
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
        recordRecentActivity(force = true)
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
        recordRecentActivity()
        viewModelScope.launch {
            repository.markUnreadMessagesRead(chatId, currentUserId, messages)
        }
    }

    fun startCall(
        callType: String,
        linkedChat: ChatThread? = null,
        targetCreator: CreatorProfile? = null
    ) {
        viewModelScope.launch {
            val invitedIds = when {
                linkedChat != null -> linkedChat.participantIds
                targetCreator != null -> listOf(targetCreator.userId)
                else -> emptyList()
            }
            val invitedNames = when {
                linkedChat != null -> linkedChat.participantNames.orEmpty()
                targetCreator != null -> listOf(targetCreator.displayName)
                else -> emptyList()
            }
            repository.createCallSession(
                uid = currentUserId,
                hostName = currentUserName,
                callType = callType,
                invitedParticipantIds = invitedIds,
                invitedParticipantNames = invitedNames
            ).onSuccess { session ->
                _activeCallId.value = session.roomCode
                val chatToNotify = linkedChat ?: if (targetCreator != null) {
                    (chatsState.value as? UiState.Success)?.data?.firstOrNull { thread ->
                        !thread.isGroup &&
                            thread.participantIds.size == 2 &&
                            thread.participantIds.contains(currentUserId) &&
                            thread.participantIds.contains(targetCreator.userId)
                    }
                } else {
                    null
                }
                if (chatToNotify != null) {
                    repository.sendMessage(
                        chatId = chatToNotify.id,
                        uid = currentUserId,
                        senderName = currentUserName,
                        participantIds = chatToNotify.participantIds,
                        messageType = "call_event",
                        text = "Started ${callType.uppercase()} call · Room ${session.roomCode}"
                    )
                } else if (targetCreator != null) {
                    val createdChatId = repository.createChatThread(
                        uid = currentUserId,
                        creatorName = currentUserName,
                        title = targetCreator.displayName.ifBlank { "@${targetCreator.handle}" },
                        isGroup = false,
                        participantIds = listOf(targetCreator.userId),
                        participantNames = listOf(targetCreator.displayName)
                    ).getOrNull()
                    if (!createdChatId.isNullOrBlank()) {
                        repository.sendMessage(
                            chatId = createdChatId,
                            uid = currentUserId,
                            senderName = currentUserName,
                            participantIds = listOf(currentUserId, targetCreator.userId),
                            messageType = "call_event",
                            text = "Started ${callType.uppercase()} call · Room ${session.roomCode}"
                        )
                    }
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

    fun acceptIncomingCall(session: CallSession) {
        val code = session.roomCode.ifBlank { session.id }
        if (code.isBlank()) return
        _dismissedIncomingCallIds.value = _dismissedIncomingCallIds.value + session.id + code
        recordRecentActivity(force = true)
        _activeCallId.value = code
        viewModelScope.launch {
            repository.joinCallSession(code, currentUserId, currentUserName)
                .onSuccess { joined ->
                    _activeCallId.value = joined.roomCode
                }
                .onFailure {
                    if (_activeCallId.value == code) {
                        _activeCallId.value = null
                    }
                    _snackbarEvents.tryEmit(it.localizedMessage ?: "Call is no longer active")
                }
        }
    }

    fun declineIncomingCall(session: CallSession) {
        val code = session.roomCode.ifBlank { session.id }
        _dismissedIncomingCallIds.value = _dismissedIncomingCallIds.value + session.id + code
        if (code.isBlank()) return
        viewModelScope.launch {
            repository.declineCallSession(code)
                .onFailure {
                    Log.w(TAG, "Decline call failed: ${it.message}")
                }
        }
    }

    fun handleCallNotificationAction(action: CallNotificationAction) {
        val matching = (callsState.value as? UiState.Success)?.data
            ?.firstOrNull { it.roomCode == action.roomCode || it.id == action.roomCode }
        when (action) {
            is CallNotificationAction.Answer -> {
                if (matching != null && matching.status != "ended") {
                    acceptIncomingCall(matching)
                } else {
                    joinCallByCode(action.roomCode)
                }
            }

            is CallNotificationAction.Decline -> {
                if (matching != null) {
                    declineIncomingCall(matching)
                } else {
                    _dismissedIncomingCallIds.value = _dismissedIncomingCallIds.value + action.roomCode
                    viewModelScope.launch { repository.declineCallSession(action.roomCode) }
                }
            }

            is CallNotificationAction.Hangup -> {
                if (_activeCallId.value == action.roomCode || _activeCallId.value != null) {
                    endActiveCall(0L)
                }
            }
        }
    }

    suspend fun sendCallSignal(
        callId: String,
        recipientId: String,
        type: String,
        sdp: String?,
        sdpMid: String?,
        sdpMLineIndex: Int?,
        candidate: String?
    ) {
        repository.sendCallSignal(
            callId = callId,
            senderId = currentUserId,
            recipientId = recipientId,
            type = type,
            sdp = sdp,
            sdpMid = sdpMid,
            sdpMLineIndex = sdpMLineIndex,
            candidate = candidate
        ).onFailure {
            Log.w(TAG, "Signal send failed ($type -> $recipientId): ${it.message}")
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

    fun updateProfilePhoto(avatarBase64: String?) {
        viewModelScope.launch {
            repository.updateProfilePhoto(
                uid = currentUserId,
                avatarBase64 = avatarBase64,
                photoUrl = currentUserPhotoUrl
            ).onSuccess {
                _snackbarEvents.tryEmit(
                    if (avatarBase64.isNullOrBlank()) "Reverted to Google account photo"
                    else "Profile photo updated"
                )
            }.onFailure {
                _snackbarEvents.tryEmit(it.localizedMessage ?: "Failed to update profile photo")
            }
        }
    }

    fun updatePreferences(pushCalls: Boolean, pushMessages: Boolean, hdVideo: Boolean) {
        viewModelScope.launch {
            repository.updateUserPreferences(currentUserId, pushCalls, pushMessages, hdVideo)
                .onSuccess { _snackbarEvents.tryEmit("Preferences updated") }
                .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "Failed to save preferences") }
        }
    }

    // --- Admin Actions ---

    fun adminToggleVideoVisibility(video: PublishedVideo) {
        val nextVisibility = if (video.visibility == "public") "private" else "public"
        viewModelScope.launch {
            repository.adminUpdateVideoVisibility(video.id, nextVisibility)
                .onSuccess { _snackbarEvents.tryEmit("Video moved to $nextVisibility") }
                .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "Failed to update visibility") }
        }
    }

    fun adminDeleteVideo(videoId: String) {
        viewModelScope.launch {
            repository.deleteVideo(videoId)
                .onSuccess { _snackbarEvents.tryEmit("Video deleted by admin") }
                .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "Admin delete failed") }
        }
    }

    fun adminSetCreatorModeration(creatorId: String, isDiscoverable: Boolean, status: String) {
        viewModelScope.launch {
            repository.adminUpdateCreatorModeration(creatorId, isDiscoverable, status)
                .onSuccess { _snackbarEvents.tryEmit("Creator moderation updated") }
                .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "Failed to update creator") }
        }
    }

    fun adminForceEndCall(callSession: CallSession) {
        viewModelScope.launch {
            repository.endCallSession(callSession.id, callSession.durationSec)
                .onSuccess { _snackbarEvents.tryEmit("Room ${callSession.roomCode} ended") }
                .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "Failed to end room") }
        }
    }

    fun adminDeleteCallSession(callId: String) {
        viewModelScope.launch {
            repository.adminDeleteCallSession(callId)
                .onSuccess { _snackbarEvents.tryEmit("Call log removed") }
                .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "Failed to remove call log") }
        }
    }

    fun adminGrantRole(targetUid: String, displayName: String, email: String, role: String) {
        viewModelScope.launch {
            repository.adminGrantRole(targetUid, displayName, email, role, currentUserId)
                .onSuccess { _snackbarEvents.tryEmit("Granted $role access") }
                .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "Failed to grant admin role") }
        }
    }

    fun adminRevokeRole(targetUid: String) {
        if (targetUid == currentUserId) {
            _snackbarEvents.tryEmit("You cannot revoke your own active admin role.")
            return
        }
        viewModelScope.launch {
            repository.adminRevokeRole(targetUid)
                .onSuccess { _snackbarEvents.tryEmit("Admin role revoked") }
                .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "Failed to revoke admin role") }
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
        const val ACTIVITY_HEARTBEAT_THROTTLE_MS = 30_000L
        const val BOOTSTRAPPED_ADMIN_EMAIL = "rsk755734@gmail.com"
    }
}
