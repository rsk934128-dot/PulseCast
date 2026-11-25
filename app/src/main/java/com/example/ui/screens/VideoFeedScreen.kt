package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.example.data.PublishedVideo
import com.example.data.VideoComment
import com.example.data.resolveIsUserOnline
import com.example.data.resolveVideoShareUrl
import com.example.media.FirebaseStorageVideoUploader
import com.example.media.FirestoreVideoMediaStore
import com.example.media.PulseCastVideoPlayer
import com.example.media.decodeBase64ToBitmap
import com.example.media.formatDuration
import com.example.ui.PulseCastViewModel
import com.example.ui.UiState
import com.example.ui.VideoPlaybackSourceState
import com.example.ui.VideoUploadUiState
import com.example.ui.adapters.SpacingItemDecoration
import com.example.ui.adapters.VideoFeedRecyclerAdapter
import com.example.ui.adapters.VideoFeedUiItem
import com.example.ui.adapters.configureOptimizedRecyclerView
import com.example.ui.adapters.rememberRecyclerThemePalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val VIDEO_CATEGORIES = listOf("All", "Tech", "Studio", "Podcast", "Music", "Vlog", "Live")

@Composable
fun VideoFeedScreen(
    viewModel: PulseCastViewModel,
    modifier: Modifier = Modifier
) {
    val videosState by viewModel.publicVideosState.collectAsStateWithLifecycle()
    val myVideosState by viewModel.myVideosState.collectAsStateWithLifecycle()
    val creatorsState by viewModel.creatorsState.collectAsStateWithLifecycle()
    val selectedVideoId by viewModel.selectedVideoId.collectAsStateWithLifecycle()
    val comments by viewModel.selectedVideoComments.collectAsStateWithLifecycle()
    val isLiked by viewModel.isSelectedVideoLiked.collectAsStateWithLifecycle()
    val likedVideoIds by viewModel.likedVideoIds.collectAsStateWithLifecycle()
    val likingVideoIds by viewModel.likingVideoIds.collectAsStateWithLifecycle()
    val uploadState by viewModel.videoUploadState.collectAsStateWithLifecycle()
    val playbackSources by viewModel.playbackSources.collectAsStateWithLifecycle()
    val syncingVideoIds by viewModel.syncingVideoIds.collectAsStateWithLifecycle()

    val creatorsById = remember(creatorsState) {
        (creatorsState as? UiState.Success)?.data?.associateBy { it.userId }.orEmpty()
    }

    var activeCategory by remember { mutableStateOf("All") }
    var showPublishDialog by remember { mutableStateOf(false) }

    val selectedVideo = remember(videosState, myVideosState, selectedVideoId) {
        (videosState as? UiState.Success)?.data?.firstOrNull { it.id == selectedVideoId }
            ?: (myVideosState as? UiState.Success)?.data?.firstOrNull { it.id == selectedVideoId }
    }

    val context = LocalContext.current
    LaunchedEffect(selectedVideo?.id, selectedVideo?.videoUri) {
        selectedVideo?.let { video ->
            viewModel.resolvePlaybackForVideo(video, context)
        }
    }

    if (selectedVideo != null) {
        val authorProfile = creatorsById[selectedVideo.authorId]
        val playbackState = playbackSources[viewModel.playbackKey(selectedVideo)]
        VideoDetailScreen(
            video = selectedVideo,
            isOwnedByUser = selectedVideo.authorId == viewModel.currentUserId,
            isLiked = isLiked,
            comments = comments,
            onBack = { viewModel.selectVideo(null) },
            onToggleLike = { viewModel.toggleVideoLike(selectedVideo.id) },
            onSendComment = { text -> viewModel.addComment(selectedVideo.id, text) },
            onDelete = {
                viewModel.deleteVideo(selectedVideo.id) { viewModel.selectVideo(null) }
            },
            modifier = modifier,
            authorProfile = authorProfile,
            onAuthorClick = { viewModel.openCreatorProfile(selectedVideo.authorId, selectedVideo.authorName) },
            playbackState = playbackState,
            onRetryPlayback = { viewModel.resolvePlaybackForVideo(selectedVideo, context, forceReload = true) },
            onSyncLocalToCloud = { viewModel.syncLocalVideoToCloud(selectedVideo) },
            isSyncingToCloud = selectedVideo.id in syncingVideoIds,
            onShareVideo = { viewModel.notifyVideoLinkCopied(selectedVideo) },
            creatorsById = creatorsById,
            onCommentAuthorClick = { uid, name -> viewModel.openCreatorProfile(uid, name) }
        )
    } else {
        VideoFeedContent(
            videosState = videosState,
            creatorsById = creatorsById,
            likedVideoIds = likedVideoIds,
            likingVideoIds = likingVideoIds,
            activeCategory = activeCategory,
            onCategorySelect = { activeCategory = it },
            onPublishClick = { showPublishDialog = true },
            onAuthorClick = { video ->
                viewModel.openCreatorProfile(video.authorId, video.authorName)
            },
            onToggleLike = { video ->
                viewModel.toggleVideoLike(video.id)
            },
            onShareClick = { video ->
                viewModel.notifyVideoLinkCopied(video)
            },
            onVideoClick = { video ->
                viewModel.selectVideo(video.id)
            },
            modifier = modifier
        )
    }

    if (showPublishDialog) {
        val isUploadBusy = uploadState is VideoUploadUiState.Uploading ||
            uploadState is VideoUploadUiState.SavingMetadata
        Dialog(
            onDismissRequest = {
                if (!isUploadBusy) {
                    viewModel.resetVideoUploadState()
                    showPublishDialog = false
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
                        showPublishDialog = false
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
                            showPublishDialog = false
                        }
                    )
                }
            )
        }
    }
}

@Composable
internal fun VideoFeedContent(
    videosState: UiState<List<PublishedVideo>>,
    creatorsById: Map<String, com.example.data.CreatorProfile>,
    likedVideoIds: Set<String>,
    likingVideoIds: Set<String> = emptySet(),
    activeCategory: String = "All",
    onCategorySelect: (String) -> Unit = {},
    onPublishClick: () -> Unit = {},
    onAuthorClick: (PublishedVideo) -> Unit = {},
    onToggleLike: (PublishedVideo) -> Unit = {},
    onShareClick: (PublishedVideo) -> Unit = {},
    onVideoClick: (PublishedVideo) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = onPublishClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("publish_video_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Publish video")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VIDEO_CATEGORIES.forEach { category ->
                    FilterChip(
                        selected = activeCategory == category,
                        onClick = { onCategorySelect(category) },
                        label = { Text(category) },
                        modifier = Modifier.testTag("category_chip_${category.lowercase()}")
                    )
                }
            }

            when (videosState) {
                UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.testTag("feed_loading_indicator"))
                    }
                }
                is UiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = videosState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("feed_error_message")
                        )
                    }
                }
                is UiState.Success -> {
                    val filtered = if (activeCategory == "All") {
                        videosState.data
                    } else {
                        videosState.data.filter { it.category.equals(activeCategory, ignoreCase = true) }
                    }
                    if (filtered.isEmpty()) {
                        EmptyVideoFeedState(onPublishClick = onPublishClick)
                    } else {
                        val context = LocalContext.current
                        val coroutineScope = rememberCoroutineScope()
                        val copiedTokens = remember { mutableStateMapOf<String, Long>() }
                        val palette = rememberRecyclerThemePalette()
                        val currentOnAuthorClick by rememberUpdatedState(onAuthorClick)
                        val currentOnToggleLike by rememberUpdatedState(onToggleLike)
                        val currentOnShareClick by rememberUpdatedState(onShareClick)
                        val currentOnVideoClick by rememberUpdatedState(onVideoClick)

                        val feedItems = remember(
                            filtered,
                            creatorsById,
                            likedVideoIds,
                            likingVideoIds,
                            copiedTokens.toMap()
                        ) {
                            filtered.map { video ->
                                val authorProfile = creatorsById[video.authorId]
                                val isAuthorOnline = authorProfile?.let {
                                    resolveIsUserOnline(
                                        isOnlineFlag = it.isOnline,
                                        lastActiveAt = it.lastActiveAt,
                                        updatedAt = it.updatedAt
                                    )
                                } == true
                                VideoFeedUiItem(
                                    video = video,
                                    authorProfile = authorProfile,
                                    isAuthorOnline = isAuthorOnline,
                                    isLiked = video.id in likedVideoIds,
                                    isLikeUpdating = video.id in likingVideoIds,
                                    isCopied = video.id in copiedTokens
                                )
                            }
                        }

                        val handleShare: (PublishedVideo) -> Unit = remember(context) {
                            { video ->
                                copyVideoDirectUrlToClipboard(context, video)
                                val token = System.nanoTime()
                                copiedTokens[video.id] = token
                                coroutineScope.launch {
                                    delay(2000L)
                                    if (copiedTokens[video.id] == token) {
                                        copiedTokens.remove(video.id)
                                    }
                                }
                                currentOnShareClick(video)
                            }
                        }

                        AndroidView(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("feed_video_list"),
                            factory = { ctx ->
                                RecyclerView(ctx).apply {
                                    tag = "feed_video_list"
                                    clipToPadding = false
                                    configureOptimizedRecyclerView(
                                        recyclerView = this,
                                        orientation = RecyclerView.VERTICAL,
                                        viewCacheSize = 6,
                                        prefetchCount = 3
                                    )
                                    val density = ctx.resources.displayMetrics.density
                                    addItemDecoration(
                                        SpacingItemDecoration(
                                            horizontalEdgePx = (16f * density).toInt(),
                                            topEdgePx = (4f * density).toInt(),
                                            bottomEdgePx = (88f * density).toInt(),
                                            itemGapPx = (16f * density).toInt(),
                                            orientation = RecyclerView.VERTICAL
                                        )
                                    )
                                    adapter = VideoFeedRecyclerAdapter(
                                        palette = palette,
                                        onAuthorClick = { currentOnAuthorClick(it) },
                                        onToggleLike = { currentOnToggleLike(it) },
                                        onShareClick = handleShare,
                                        onVideoClick = { currentOnVideoClick(it) }
                                    )
                                }
                            },
                            update = { rv ->
                                val adapter = rv.adapter as VideoFeedRecyclerAdapter
                                adapter.updateEnvironment(
                                    newPalette = palette,
                                    onAuthorClick = { currentOnAuthorClick(it) },
                                    onToggleLike = { currentOnToggleLike(it) },
                                    onShareClick = handleShare,
                                    onVideoClick = { currentOnVideoClick(it) }
                                )
                                adapter.submitList(feedItems)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun VideoFeedCard(
    video: PublishedVideo,
    authorProfile: com.example.data.CreatorProfile? = null,
    isLiked: Boolean = false,
    isLikeUpdating: Boolean = false,
    onAuthorClick: () -> Unit = {},
    onToggleLike: () -> Unit = {},
    onShareClick: () -> Unit = {},
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var copiedToClipboard by remember(video.id) { mutableStateOf(false) }
    LaunchedEffect(copiedToClipboard) {
        if (copiedToClipboard) {
            delay(2000L)
            copiedToClipboard = false
        }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("video_card_${video.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            ) {
                VideoCoverArt(video = video, modifier = Modifier.fillMaxSize())
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                            )
                        )
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = video.category.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = formatDuration(video.durationSec),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(52.dp),
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = CircleShape
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Play ${video.title}",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!video.description.isNullOrBlank()) {
                    Text(
                        text = video.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onAuthorClick)
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                            .testTag("feed_author_${video.id}")
                    ) {
                        val isAuthorOnline = authorProfile?.let {
                            com.example.data.resolveIsUserOnline(
                                isOnlineFlag = it.isOnline,
                                lastActiveAt = it.lastActiveAt,
                                updatedAt = it.updatedAt
                            )
                        } == true
                        com.example.ui.components.UserAvatarWithStatus(
                            displayName = video.authorName,
                            isOnline = isAuthorOnline,
                            avatarSize = 28.dp,
                            photoUrl = authorProfile?.photoUrl,
                            avatarBase64 = authorProfile?.avatarBase64
                        )
                        Text(
                            text = video.authorName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val likeActionDescription = if (isLiked) {
                            stringResource(R.string.feed_unlike_video, video.title)
                        } else {
                            stringResource(R.string.feed_like_video, video.title)
                        }
                        Surface(
                            color = if (isLiked) {
                                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f)
                            } else {
                                Color.Transparent
                            },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(50))
                                .clickable(
                                    enabled = !isLikeUpdating,
                                    role = Role.Button,
                                    onClick = onToggleLike
                                )
                                .semantics { contentDescription = likeActionDescription }
                                .testTag("feed_like_button_${video.id}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = null,
                                    tint = if (isLiked) {
                                        MaterialTheme.colorScheme.tertiary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "${video.likesCount}",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isLiked) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isLiked) {
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.testTag("feed_like_count_${video.id}")
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Comment,
                                contentDescription = "Comments",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${video.commentsCount}", style = MaterialTheme.typography.labelMedium)
                        }
                        val shareActionDescription = stringResource(R.string.feed_share_video_cd, video.title)
                        Surface(
                            color = if (copiedToClipboard) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                            } else {
                                Color.Transparent
                            },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(50))
                                .clickable(role = Role.Button) {
                                    copyVideoDirectUrlToClipboard(context, video)
                                    copiedToClipboard = true
                                    onShareClick()
                                }
                                .semantics { contentDescription = shareActionDescription }
                                .testTag("feed_share_button_${video.id}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = if (copiedToClipboard) Icons.Filled.Check else Icons.Filled.Share,
                                    contentDescription = null,
                                    tint = if (copiedToClipboard) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = stringResource(
                                        if (copiedToClipboard) R.string.feed_share_video_copied
                                        else R.string.feed_share_video
                                    ),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (copiedToClipboard) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (copiedToClipboard) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.testTag("feed_share_label_${video.id}")
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun copyVideoDirectUrlToClipboard(
    context: Context,
    video: PublishedVideo
): String {
    val shareUrl = resolveVideoShareUrl(video)
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(
        ClipData.newPlainText(video.title.ifBlank { "PulseCast Video" }, shareUrl)
    )
    return shareUrl
}

@Composable
fun VideoCoverArt(
    video: PublishedVideo,
    modifier: Modifier = Modifier
) {
    val decodedBitmap = remember(video.thumbnailBase64) {
        decodeBase64ToBitmap(video.thumbnailBase64)
    }
    if (decodedBitmap != null) {
        Image(
            bitmap = decodedBitmap.asImageBitmap(),
            contentDescription = video.title,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        val resId = when (video.coverKey) {
            "thumb_2" -> R.drawable.img_video_thumb_2
            else -> R.drawable.img_video_thumb_1
        }
        Image(
            painter = painterResource(id = resId),
            contentDescription = video.title,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun EmptyVideoFeedState(onPublishClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_video_thumb_1),
            contentDescription = "Empty studio feed",
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(20.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Launch Your First Broadcast",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Publish a clip from your gallery, extract an automatic thumbnail, and gather live likes and comments.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onPublishClick,
            modifier = Modifier.testTag("empty_publish_button")
        ) {
            Icon(Icons.Filled.Videocam, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Publish Video")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoDetailScreen(
    video: PublishedVideo,
    isOwnedByUser: Boolean,
    isLiked: Boolean,
    comments: List<VideoComment>,
    onBack: () -> Unit,
    onToggleLike: () -> Unit,
    onSendComment: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    authorProfile: com.example.data.CreatorProfile? = null,
    onAuthorClick: (() -> Unit)? = null,
    playbackState: VideoPlaybackSourceState? = null,
    onRetryPlayback: (() -> Unit)? = null,
    onSyncLocalToCloud: (() -> Unit)? = null,
    isSyncingToCloud: Boolean = false,
    onShareVideo: () -> Unit = {},
    creatorsById: Map<String, com.example.data.CreatorProfile> = emptyMap(),
    onCommentAuthorClick: ((String, String?) -> Unit)? = null
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var commentDraft by remember { mutableStateOf("") }
    var copiedToClipboard by remember(video.id) { mutableStateOf(false) }
    LaunchedEffect(copiedToClipboard) {
        if (copiedToClipboard) {
            delay(2000L)
            copiedToClipboard = false
        }
    }

    val fallbackResolvedState = remember(video.id, video.videoUri, playbackState, context) {
        playbackState ?: run {
            val rawUri = video.videoUri?.trim().orEmpty()
            when {
                rawUri.isNotBlank() && !FirestoreVideoMediaStore.isFirestoreMediaUri(rawUri) ->
                    VideoPlaybackSourceState.Playable(rawUri)
                else -> {
                    val local = FirebaseStorageVideoUploader.resolveLocalVideoUri(context, video.id)
                    if (local != null) {
                        VideoPlaybackSourceState.Playable(local, isLocalOnlyFallback = rawUri.isBlank())
                    } else if (FirestoreVideoMediaStore.isFirestoreMediaUri(rawUri)) {
                        VideoPlaybackSourceState.Loading
                    } else {
                        VideoPlaybackSourceState.Unavailable(
                            context.getString(R.string.video_detail_unavailable_subtitle)
                        )
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = video.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("video_detail_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to feed")
                    }
                },
                actions = {
                    if (isOwnedByUser) {
                        IconButton(onClick = onDelete, modifier = Modifier.testTag("delete_video_button")) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete video", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                ) {
                    when (val state = fallbackResolvedState) {
                        is VideoPlaybackSourceState.Playable -> {
                            PulseCastVideoPlayer(
                                videoUri = state.uri,
                                autoPlay = true,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        VideoPlaybackSourceState.Idle,
                        VideoPlaybackSourceState.Loading -> {
                            VideoCoverArt(video = video, modifier = Modifier.fillMaxSize())
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.52f))
                                    .testTag("video_cloud_loading_overlay"),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = stringResource(R.string.video_detail_loading_cloud),
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }
                            }
                        }

                        is VideoPlaybackSourceState.Error -> {
                            VideoCoverArt(video = video, modifier = Modifier.fillMaxSize())
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.68f))
                                    .padding(16.dp)
                                    .testTag("video_cloud_error_overlay"),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(30.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                                if (onRetryPlayback != null) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = onRetryPlayback,
                                        modifier = Modifier.testTag("video_cloud_retry_button")
                                    ) {
                                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(stringResource(R.string.video_player_retry))
                                    }
                                }
                            }
                        }

                        is VideoPlaybackSourceState.Unavailable -> {
                            VideoCoverArt(video = video, modifier = Modifier.fillMaxSize())
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.62f))
                                    .padding(16.dp)
                                    .testTag("video_cloud_unavailable_overlay"),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CloudOff,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.video_detail_unavailable_cloud),
                                            style = MaterialTheme.typography.labelLarge,
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            text = stringResource(R.string.video_detail_unavailable_subtitle),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if ((fallbackResolvedState as? VideoPlaybackSourceState.Playable)?.isLocalOnlyFallback == true &&
                isOwnedByUser &&
                onSyncLocalToCloud != null
            ) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .testTag("video_local_only_sync_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.video_detail_local_only_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = onSyncLocalToCloud,
                                enabled = !isSyncingToCloud,
                                modifier = Modifier.testTag("sync_video_to_cloud_button")
                            ) {
                                if (isSyncingToCloud) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.video_detail_syncing_cloud))
                                } else {
                                    Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.video_detail_sync_cloud))
                                }
                            }
                        }
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(video.title, style = MaterialTheme.typography.headlineSmall)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .then(
                                if (onAuthorClick != null) Modifier.clickable(onClick = onAuthorClick)
                                else Modifier
                            )
                            .padding(vertical = 4.dp)
                            .testTag("video_detail_author_row")
                    ) {
                        val isAuthorOnline = authorProfile?.let {
                            com.example.data.resolveIsUserOnline(
                                isOnlineFlag = it.isOnline,
                                lastActiveAt = it.lastActiveAt,
                                updatedAt = it.updatedAt
                            )
                        } == true
                        com.example.ui.components.UserAvatarWithStatus(
                            displayName = video.authorName,
                            isOnline = isAuthorOnline,
                            avatarSize = 36.dp,
                            photoUrl = authorProfile?.photoUrl,
                            avatarBase64 = authorProfile?.avatarBase64
                        )
                        Column {
                            Text(
                                text = video.authorName,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${video.category} · ${formatDuration(video.durationSec)}${if (onAuthorClick != null) " · View Profile" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (!video.description.isNullOrBlank()) {
                        Text(
                            text = video.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onToggleLike,
                            modifier = Modifier.testTag("like_video_button")
                        ) {
                            Icon(
                                imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "Toggle like",
                                tint = if (isLiked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${video.likesCount} Likes")
                        }
                        OutlinedButton(
                            onClick = {
                                copyVideoDirectUrlToClipboard(context, video)
                                copiedToClipboard = true
                                onShareVideo()
                            },
                            modifier = Modifier.testTag("share_video_button")
                        ) {
                            Icon(
                                imageVector = if (copiedToClipboard) Icons.Filled.Check else Icons.Filled.Share,
                                contentDescription = stringResource(R.string.feed_share_video_cd, video.title)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(
                                    if (copiedToClipboard) R.string.feed_share_video_copied
                                    else R.string.feed_share_video
                                )
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = commentDraft,
                        onValueChange = { commentDraft = it },
                        placeholder = { Text("Write a comment...") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("comment_input")
                    )
                    IconButton(
                        onClick = {
                            if (commentDraft.isNotBlank()) {
                                onSendComment(commentDraft)
                                commentDraft = ""
                            }
                        },
                        modifier = Modifier.testTag("send_comment_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Post comment",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Comments (${comments.size})",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            items(comments, key = { it.id }) { comment ->
                val commenter = creatorsById[comment.authorId]
                val isCommenterOnline = commenter?.let {
                    com.example.data.resolveIsUserOnline(
                        isOnlineFlag = it.isOnline,
                        lastActiveAt = it.lastActiveAt,
                        updatedAt = it.updatedAt
                    )
                } == true
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        com.example.ui.components.UserAvatarWithStatus(
                            displayName = comment.authorName,
                            isOnline = isCommenterOnline,
                            avatarSize = 32.dp,
                            photoUrl = commenter?.photoUrl,
                            avatarBase64 = commenter?.avatarBase64,
                            onClick = onCommentAuthorClick?.let { click ->
                                { click(comment.authorId, comment.authorName) }
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = comment.authorName,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = comment.text,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}
