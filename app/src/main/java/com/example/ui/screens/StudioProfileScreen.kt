package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.auth.signOutUser
import com.example.data.CreatorProfile
import com.example.data.PublishedVideo
import com.example.data.isRecentlyOnline
import com.example.media.encodeUriImageToBase64
import com.example.ui.PulseCastViewModel
import com.example.ui.UiState
import com.example.ui.VideoUploadUiState
import com.example.ui.components.AvatarPhotoLightboxDialog
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

private enum class ProfileVideoFilter(val label: String, val tag: String) {
    ALL("All", "profile_filter_all"),
    PUBLIC("Public", "profile_filter_public"),
    PRIVATE("Private Drafts", "profile_filter_private")
}

@Composable
fun StudioProfileScreen(
    viewModel: PulseCastViewModel,
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val userAccount by viewModel.userAccountState.collectAsStateWithLifecycle()
    val creatorsState by viewModel.creatorsState.collectAsStateWithLifecycle()
    val myVideosState by viewModel.myVideosState.collectAsStateWithLifecycle()
    val uploadState by viewModel.videoUploadState.collectAsStateWithLifecycle()
    val comments by viewModel.selectedVideoComments.collectAsStateWithLifecycle()
    val isLiked by viewModel.isSelectedVideoLiked.collectAsStateWithLifecycle()

    val authPhotoUrl = remember(viewModel.currentUserId) {
        runCatching { Firebase.auth.currentUser?.photoUrl?.toString() }.getOrNull()
    }

    val allCreators = remember(creatorsState) {
        (creatorsState as? UiState.Success)?.data.orEmpty()
    }
    val myCreator = remember(allCreators, viewModel.currentUserId) {
        allCreators.firstOrNull { it.userId == viewModel.currentUserId }
    }
    val otherCreators = remember(allCreators, viewModel.currentUserId) {
        allCreators.filter { it.userId != viewModel.currentUserId }
    }
    val effectivePhotoUrl = myCreator?.photoUrl?.takeIf { it.isNotBlank() }
        ?: userAccount?.photoUrl?.takeIf { it.isNotBlank() }
        ?: authPhotoUrl
    val effectiveAvatarBase64 = myCreator?.avatarBase64?.takeIf { it.isNotBlank() }
        ?: userAccount?.avatarBase64?.takeIf { it.isNotBlank() }

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val base64 = encodeUriImageToBase64(context, uri, maxDimension = 360, quality = 75)
                if (base64 != null) {
                    viewModel.updateProfilePhoto(base64)
                }
            }
        }
    }

    var viewingVideoId by rememberSaveable { mutableStateOf<String?>(null) }
    var showUploadForm by remember { mutableStateOf(false) }

    val selectedVideo = remember(myVideosState, viewingVideoId) {
        (myVideosState as? UiState.Success)?.data?.firstOrNull { it.id == viewingVideoId }
    }

    LaunchedEffect(viewingVideoId) {
        if (viewingVideoId != null) {
            viewModel.selectVideo(viewingVideoId)
        }
    }

    if (selectedVideo != null) {
        BackHandler {
            viewingVideoId = null
            viewModel.selectVideo(null)
        }
        VideoDetailScreen(
            video = selectedVideo,
            isOwnedByUser = selectedVideo.authorId == viewModel.currentUserId,
            isLiked = isLiked,
            comments = comments,
            onBack = {
                viewingVideoId = null
                viewModel.selectVideo(null)
            },
            onToggleLike = { viewModel.toggleVideoLike(selectedVideo.id) },
            onSendComment = { text -> viewModel.addComment(selectedVideo.id, text) },
            onDelete = {
                viewModel.deleteVideo(selectedVideo.id) {
                    viewingVideoId = null
                    viewModel.selectVideo(null)
                }
            },
            modifier = modifier
        )
    } else {
        StudioProfileContent(
            initialDisplayName = myCreator?.displayName ?: viewModel.currentUserName,
            initialHandle = myCreator?.handle ?: "creator",
            email = userAccount?.email?.ifBlank { viewModel.currentUserEmail } ?: viewModel.currentUserEmail,
            initialBio = myCreator?.bio.orEmpty(),
            initialStatus = myCreator?.status ?: "online",
            photoUrl = effectivePhotoUrl,
            pushCalls = userAccount?.pushCallAlerts ?: true,
            pushMessages = userAccount?.pushMessageAlerts ?: true,
            hdVideo = userAccount?.hdVideoDefault ?: true,
            myVideosState = myVideosState,
            onSaveProfile = { name, handle, bio, status ->
                viewModel.updateProfile(name, handle, bio, status)
            },
            onUpdatePreferences = { calls, messages, hd ->
                viewModel.updatePreferences(calls, messages, hd)
            },
            onUploadVideoClick = { showUploadForm = true },
            onVideoClick = { video ->
                viewingVideoId = video.id
                viewModel.selectVideo(video.id)
            },
            onDeleteVideo = { video ->
                viewModel.deleteVideo(video.id) {}
            },
            onSignOut = {
                signOutUser(
                    credentialManager = credentialManager,
                    scope = scope,
                    onBeforeSignOut = { viewModel.markOfflineNow() },
                    onSignOutComplete = onSignedOut
                )
            },
            modifier = modifier,
            avatarBase64 = effectiveAvatarBase64,
            isOnline = myCreator?.isRecentlyOnline() ?: (userAccount?.isRecentlyOnline() ?: true),
            discoverableCreators = otherCreators,
            onPickNewPhoto = {
                avatarPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onResetGooglePhoto = { viewModel.updateProfilePhoto(null) },
            onViewCreatorProfile = { creator -> viewModel.openCreatorProfile(creator) }
        )
    }

    if (showUploadForm) {
        val isUploadBusy = uploadState is VideoUploadUiState.Uploading ||
            uploadState is VideoUploadUiState.SavingMetadata
        Dialog(
            onDismissRequest = {
                if (!isUploadBusy) {
                    viewModel.resetVideoUploadState()
                    showUploadForm = false
                }
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = !isUploadBusy,
                dismissOnClickOutside = false
            )
        ) {
            VideoUploadFormScreen(
                uploadState = uploadState,
                onDismiss = {
                    if (!isUploadBusy) {
                        viewModel.resetVideoUploadState()
                        showUploadForm = false
                    }
                },
                onSubmit = { title, desc, category, localUri, externalUrl, mimeType, coverKey, thumbBase64, visibility, durationSec ->
                    viewModel.uploadAndPublishVideo(
                        title = title,
                        description = desc,
                        category = category,
                        localVideoUri = localUri,
                        externalVideoUrl = externalUrl,
                        mimeType = mimeType,
                        coverKey = coverKey,
                        thumbnailBase64 = thumbBase64,
                        visibility = visibility,
                        durationSec = durationSec,
                        onComplete = {
                            viewModel.resetVideoUploadState()
                            showUploadForm = false
                        }
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioProfileContent(
    initialDisplayName: String,
    initialHandle: String,
    email: String,
    initialBio: String,
    initialStatus: String,
    photoUrl: String?,
    pushCalls: Boolean,
    pushMessages: Boolean,
    hdVideo: Boolean,
    myVideosState: UiState<List<PublishedVideo>>,
    onSaveProfile: (displayName: String, handle: String, bio: String, status: String) -> Unit,
    onUpdatePreferences: (pushCalls: Boolean, pushMessages: Boolean, hdVideo: Boolean) -> Unit,
    onUploadVideoClick: () -> Unit,
    onVideoClick: (PublishedVideo) -> Unit,
    onDeleteVideo: (PublishedVideo) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    avatarBase64: String? = null,
    isOnline: Boolean = true,
    discoverableCreators: List<CreatorProfile> = emptyList(),
    onPickNewPhoto: () -> Unit = {},
    onResetGooglePhoto: () -> Unit = {},
    onViewCreatorProfile: (CreatorProfile) -> Unit = {}
) {
    var displayName by remember(initialDisplayName) { mutableStateOf(initialDisplayName) }
    var handle by remember(initialHandle) { mutableStateOf(initialHandle) }
    var bio by remember(initialBio) { mutableStateOf(initialBio) }
    var status by remember(initialStatus) { mutableStateOf(initialStatus) }

    var pushCallsState by remember(pushCalls) { mutableStateOf(pushCalls) }
    var pushMessagesState by remember(pushMessages) { mutableStateOf(pushMessages) }
    var hdVideoState by remember(hdVideo) { mutableStateOf(hdVideo) }

    var isEditingProfile by rememberSaveable { mutableStateOf(false) }
    var showPhotoLightbox by rememberSaveable { mutableStateOf(false) }
    var videoFilter by rememberSaveable { mutableStateOf(ProfileVideoFilter.ALL) }
    var pendingDeleteVideo by remember { mutableStateOf<PublishedVideo?>(null) }

    val allVideos = (myVideosState as? UiState.Success)?.data ?: emptyList()
    val filteredVideos = remember(allVideos, videoFilter) {
        when (videoFilter) {
            ProfileVideoFilter.ALL -> allVideos
            ProfileVideoFilter.PUBLIC -> allVideos.filter { it.visibility.equals("public", ignoreCase = true) }
            ProfileVideoFilter.PRIVATE -> allVideos.filter { it.visibility.equals("private", ignoreCase = true) }
        }
    }
    val totalLikes = remember(allVideos) { allVideos.sumOf { it.likesCount } }
    val totalComments = remember(allVideos) { allVideos.sumOf { it.commentsCount } }
    val nowMillis = remember(discoverableCreators) { System.currentTimeMillis() }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .testTag("profile_lazy_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ProfileHeaderCard(
                    displayName = displayName,
                    handle = handle,
                    email = email,
                    bio = bio,
                    status = status,
                    photoUrl = photoUrl,
                    isEditing = isEditingProfile,
                    onToggleEdit = { isEditingProfile = !isEditingProfile },
                    avatarBase64 = avatarBase64,
                    isOnline = isOnline,
                    onAvatarClick = { showPhotoLightbox = true },
                    onChangePhotoClick = onPickNewPhoto
                )
            }

            item {
                ProfileStatsRow(
                    videoCount = allVideos.size,
                    totalLikes = totalLikes,
                    totalComments = totalComments
                )
            }

            if (discoverableCreators.isNotEmpty()) {
                item {
                    CreatorProfilesStrip(
                        creators = discoverableCreators,
                        nowMillis = nowMillis,
                        onCreatorClick = onViewCreatorProfile
                    )
                }
            }

            item {
                AnimatedVisibility(visible = isEditingProfile) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.testTag("profile_editor_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("Edit Creator Profile", style = MaterialTheme.typography.titleMedium)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onPickNewPhoto,
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("editor_change_photo_button")
                                ) {
                                    Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.profile_change_photo))
                                }
                                if (!avatarBase64.isNullOrBlank()) {
                                    OutlinedButton(
                                        onClick = onResetGooglePhoto,
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("editor_reset_google_photo_button")
                                    ) {
                                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(stringResource(R.string.profile_reset_google_photo))
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = displayName,
                                onValueChange = { displayName = it },
                                label = { Text("Display Name") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("profile_name_input")
                            )
                            OutlinedTextField(
                                value = handle,
                                onValueChange = { handle = it },
                                label = { Text("Public Handle") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("profile_handle_input")
                            )
                            OutlinedTextField(
                                value = bio,
                                onValueChange = { bio = it },
                                label = { Text("Bio") },
                                maxLines = 3,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("profile_bio_input")
                            )

                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("online", "studio", "in_call", "away").forEach { st ->
                                    FilterChip(
                                        selected = status == st,
                                        onClick = { status = st },
                                        label = { Text(st.replace('_', ' ')) }
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    onSaveProfile(displayName, handle, bio, status)
                                    isEditingProfile = false
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("save_profile_button")
                            ) {
                                Icon(Icons.Filled.Save, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Save Creator Profile")
                            }
                        }
                    }
                }
            }

            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.testTag("profile_uploaded_videos_section")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${stringResource(R.string.profile_uploaded_videos_title)} (${allVideos.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        TextButton(
                            onClick = onUploadVideoClick,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("profile_upload_video_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.profile_upload_new_video))
                        }
                    }

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProfileVideoFilter.entries.forEach { option ->
                            FilterChip(
                                selected = videoFilter == option,
                                onClick = { videoFilter = option },
                                label = { Text(option.label) },
                                modifier = Modifier.testTag(option.tag)
                            )
                        }
                    }
                }
            }

            when (myVideosState) {
                UiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp)
                                .testTag("profile_videos_loading"),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                is UiState.Error -> {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_videos_error")
                        ) {
                            Text(
                                text = myVideosState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
                is UiState.Success -> {
                    if (filteredVideos.isEmpty()) {
                        item {
                            UploadedVideosEmptyState(
                                hasFilterApplied = videoFilter != ProfileVideoFilter.ALL && allVideos.isNotEmpty(),
                                onUploadClick = onUploadVideoClick,
                                onResetFilter = { videoFilter = ProfileVideoFilter.ALL }
                            )
                        }
                    } else {
                        items(filteredVideos, key = { it.id }) { video ->
                            UploadedVideoListItem(
                                video = video,
                                onClick = { onVideoClick(video) },
                                onDeleteClick = { pendingDeleteVideo = video }
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Notifications & Stream Quality", style = MaterialTheme.typography.titleMedium)
                        PreferenceToggleRow("Incoming Call Alerts", pushCallsState) {
                            pushCallsState = it
                            onUpdatePreferences(pushCallsState, pushMessagesState, hdVideoState)
                        }
                        PreferenceToggleRow("Real-Time Message Alerts", pushMessagesState) {
                            pushMessagesState = it
                            onUpdatePreferences(pushCallsState, pushMessagesState, hdVideoState)
                        }
                        PreferenceToggleRow("HD Video Capture Default", hdVideoState) {
                            hdVideoState = it
                            onUpdatePreferences(pushCallsState, pushMessagesState, hdVideoState)
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("sign_out_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.sign_out))
                }
            }
        }
    }

    if (showPhotoLightbox) {
        AvatarPhotoLightboxDialog(
            displayName = displayName,
            handle = handle,
            photoUrl = photoUrl,
            avatarBase64 = avatarBase64,
            isOnline = isOnline,
            onDismiss = { showPhotoLightbox = false },
            canEditPhoto = true,
            onPickNewPhoto = {
                showPhotoLightbox = false
                onPickNewPhoto()
            },
            onResetToGooglePhoto = {
                showPhotoLightbox = false
                onResetGooglePhoto()
            }
        )
    }

    pendingDeleteVideo?.let { target ->
        DeleteVideoConfirmDialog(
            videoTitle = target.title,
            onConfirm = {
                pendingDeleteVideo = null
                onDeleteVideo(target)
            },
            onDismiss = { pendingDeleteVideo = null }
        )
    }
}

@Composable
private fun PreferenceToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
