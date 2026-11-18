package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.PublishedVideo
import com.example.data.VideoComment
import com.example.media.FirebaseStorageVideoUploader
import com.example.media.PulseCastVideoPlayer
import com.example.media.decodeBase64ToBitmap
import com.example.media.formatDuration
import com.example.ui.PulseCastViewModel
import com.example.ui.UiState
import com.example.ui.VideoUploadUiState

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
    val uploadState by viewModel.videoUploadState.collectAsStateWithLifecycle()

    val creatorsById = remember(creatorsState) {
        (creatorsState as? UiState.Success)?.data?.associateBy { it.userId }.orEmpty()
    }

    var activeCategory by remember { mutableStateOf("All") }
    var showPublishDialog by remember { mutableStateOf(false) }

    val selectedVideo = remember(videosState, myVideosState, selectedVideoId) {
        (videosState as? UiState.Success)?.data?.firstOrNull { it.id == selectedVideoId }
            ?: (myVideosState as? UiState.Success)?.data?.firstOrNull { it.id == selectedVideoId }
    }

    if (selectedVideo != null) {
        val authorProfile = creatorsById[selectedVideo.authorId]
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
            authorProfile = authorProfile,
            onAuthorClick = { viewModel.openCreatorProfile(selectedVideo.authorId, selectedVideo.authorName) },
            modifier = modifier
        )
    } else {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showPublishDialog = true },
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
                            onClick = { activeCategory = category },
                            label = { Text(category) },
                            modifier = Modifier.testTag("category_chip_${category.lowercase()}")
                        )
                    }
                }

                when (val state = videosState) {
                    UiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is UiState.Error -> {
                        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    is UiState.Success -> {
                        val filtered = if (activeCategory == "All") {
                            state.data
                        } else {
                            state.data.filter { it.category.equals(activeCategory, ignoreCase = true) }
                        }
                        if (filtered.isEmpty()) {
                            EmptyVideoFeedState(onPublishClick = { showPublishDialog = true })
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp, top = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(filtered, key = { it.id }) { video ->
                                    val authorProfile = creatorsById[video.authorId]
                                    VideoFeedCard(
                                        video = video,
                                        authorProfile = authorProfile,
                                        onAuthorClick = {
                                            viewModel.openCreatorProfile(video.authorId, video.authorName)
                                        },
                                        onClick = { viewModel.selectVideo(video.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
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
private fun VideoFeedCard(
    video: PublishedVideo,
    authorProfile: com.example.data.CreatorProfile? = null,
    onAuthorClick: () -> Unit = {},
    onClick: () -> Unit
) {
    val context = LocalContext.current
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
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = "Likes",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${video.likesCount}", style = MaterialTheme.typography.labelMedium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Comment,
                                contentDescription = "Comments",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${video.commentsCount}", style = MaterialTheme.typography.labelMedium)
                        }
                        IconButton(
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "Watch \"${video.title}\" by ${video.authorName} on PulseCast")
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share video"))
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = "Share video",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
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
    onAuthorClick: (() -> Unit)? = null
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var commentDraft by remember { mutableStateOf("") }

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
                    val playableVideoUri = remember(video.id, video.videoUri, context) {
                        video.videoUri?.takeIf { it.isNotBlank() }
                            ?: FirebaseStorageVideoUploader.resolveLocalVideoUri(context, video.id)
                    }
                    if (!playableVideoUri.isNullOrBlank()) {
                        PulseCastVideoPlayer(
                            videoUri = playableVideoUri,
                            autoPlay = true,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        VideoCoverArt(video = video, modifier = Modifier.fillMaxSize())
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = "Studio preview",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Studio Preview · ${formatDuration(video.durationSec)}",
                                        style = MaterialTheme.typography.labelLarge
                                    )
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
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "Watch \"${video.title}\" on PulseCast!")
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share video"))
                            },
                            modifier = Modifier.testTag("share_video_button")
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = "Share")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Share")
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
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
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
