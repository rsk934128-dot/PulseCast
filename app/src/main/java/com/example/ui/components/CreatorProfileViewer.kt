package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.R
import com.example.data.CreatorProfile
import com.example.data.PublishedVideo
import com.example.data.isRecentlyOnline
import com.example.media.decodeBase64ToBitmap
import com.example.media.formatDuration
import com.example.ui.screens.VideoCoverArt
import com.example.ui.theme.EmeraldMint

@Composable
fun CreatorProfileDialog(
    creator: CreatorProfile,
    isCurrentUser: Boolean,
    creatorVideos: List<PublishedVideo>,
    onDismiss: () -> Unit,
    onMessageClick: (CreatorProfile) -> Unit,
    onAudioCallClick: (CreatorProfile) -> Unit,
    onVideoCallClick: (CreatorProfile) -> Unit,
    onVideoClick: (PublishedVideo) -> Unit,
    onEditOwnProfile: () -> Unit = {},
    nowMillis: Long = System.currentTimeMillis()
) {
    var showFullPhotoLightbox by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 16.dp, vertical = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .heightIn(max = 680.dp)
                    .testTag("creator_profile_dialog")
            ) {
                CreatorProfileContent(
                    creator = creator,
                    isCurrentUser = isCurrentUser,
                    creatorVideos = creatorVideos,
                    nowMillis = nowMillis,
                    onClose = onDismiss,
                    onAvatarClick = { showFullPhotoLightbox = true },
                    onMessageClick = { onMessageClick(creator) },
                    onAudioCallClick = { onAudioCallClick(creator) },
                    onVideoCallClick = { onVideoCallClick(creator) },
                    onVideoClick = onVideoClick,
                    onEditOwnProfile = onEditOwnProfile
                )
            }
        }
    }

    if (showFullPhotoLightbox) {
        AvatarPhotoLightboxDialog(
            displayName = creator.displayName,
            handle = creator.handle,
            photoUrl = creator.photoUrl,
            avatarBase64 = creator.avatarBase64,
            isOnline = creator.isRecentlyOnline(nowMillis),
            onDismiss = { showFullPhotoLightbox = false }
        )
    }
}

@Composable
internal fun CreatorProfileContent(
    creator: CreatorProfile,
    isCurrentUser: Boolean,
    creatorVideos: List<PublishedVideo>,
    nowMillis: Long = System.currentTimeMillis(),
    onClose: () -> Unit,
    onAvatarClick: () -> Unit = {},
    onMessageClick: () -> Unit,
    onAudioCallClick: () -> Unit,
    onVideoCallClick: () -> Unit,
    onVideoClick: (PublishedVideo) -> Unit,
    onEditOwnProfile: () -> Unit = {}
) {
    val isOnline = creator.isRecentlyOnline(nowMillis)
    val totalLikes = remember(creatorVideos) { creatorVideos.sumOf { it.likesCount } }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.creator_profile_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("creator_profile_close_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close creator profile"
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("creator_profile_list"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                                )
                            )
                        )
                        .padding(18.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        UserAvatarWithStatus(
                            displayName = creator.displayName,
                            isOnline = isOnline,
                            avatarSize = 92.dp,
                            photoUrl = creator.photoUrl,
                            avatarBase64 = creator.avatarBase64,
                            onClick = onAvatarClick,
                            avatarTestTag = "creator_profile_avatar",
                            indicatorTestTag = "creator_profile_online_indicator"
                        )
                        Text(
                            text = creator.displayName.ifBlank { "PulseCast Creator" },
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("creator_profile_display_name")
                        )
                        Text(
                            text = "@${creator.handle}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("creator_profile_handle")
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = if (isOnline) {
                                    EmeraldMint.copy(alpha = 0.18f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = if (isOnline) "Online now" else "Offline",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isOnline) EmeraldMint else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            if (!creator.status.isNullOrBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(
                                        text = creator.status.replace('_', ' ')
                                            .replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            if (isCurrentUser) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(
                                        text = "You",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        val bioText = creator.bio?.trim().orEmpty()
                        if (bioText.isNotEmpty()) {
                            Text(
                                text = bioText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("creator_profile_bio")
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VideoLibrary,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = "${creatorVideos.size}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Public Videos",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = "$totalLikes",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Total Likes",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item {
                if (isCurrentUser) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onAvatarClick,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("creator_profile_view_photo_button")
                        ) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.profile_view_full_photo))
                        }
                        Button(
                            onClick = onEditOwnProfile,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("creator_profile_edit_studio_button")
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Edit Studio")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onMessageClick,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("creator_profile_message_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.creator_profile_message))
                        }
                        FilledTonalIconButton(
                            onClick = onAudioCallClick,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("creator_profile_audio_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Call,
                                contentDescription = stringResource(R.string.creator_profile_audio_call)
                            )
                        }
                        FilledTonalIconButton(
                            onClick = onVideoCallClick,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("creator_profile_video_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VideoCall,
                                contentDescription = stringResource(R.string.creator_profile_video_call)
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Published Videos (${creatorVideos.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (creatorVideos.isEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("creator_profile_empty_videos")
                    ) {
                        Text(
                            text = stringResource(R.string.creator_profile_no_videos),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(creatorVideos, key = { it.id }) { video ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onVideoClick(video) }
                            .testTag("creator_profile_video_${video.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(88.dp)
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black)
                            ) {
                                VideoCoverArt(video = video, modifier = Modifier.fillMaxSize())
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "Watch ${video.title}",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .align(Alignment.Center)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = video.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${video.category} · ${formatDuration(video.durationSec)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.Favorite,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("${video.likesCount}", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Comment,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("${video.commentsCount}", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AvatarPhotoLightboxDialog(
    displayName: String,
    handle: String,
    photoUrl: String?,
    avatarBase64: String?,
    isOnline: Boolean,
    onDismiss: () -> Unit,
    canEditPhoto: Boolean = false,
    onPickNewPhoto: (() -> Unit)? = null,
    onResetToGooglePhoto: (() -> Unit)? = null
) {
    val safeName = displayName.trim().ifBlank { "PulseCast Creator" }
    val customBitmap = remember(avatarBase64) { decodeBase64ToBitmap(avatarBase64) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .testTag("avatar_photo_lightbox_dialog")
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = safeName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "@$handle · ${if (isOnline) "Online" else "Offline"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("photo_lightbox_close_button")
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close photo")
                    }
                }

                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = safeName.take(1).uppercase(),
                        style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (customBitmap != null) {
                        Image(
                            bitmap = customBitmap.asImageBitmap(),
                            contentDescription = "$safeName full profile photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (!photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = "$safeName full profile photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                if (canEditPhoto && onPickNewPhoto != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onPickNewPhoto,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("lightbox_change_photo_button")
                        ) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.profile_change_photo))
                        }
                        if (!avatarBase64.isNullOrBlank() && onResetToGooglePhoto != null) {
                            TextButton(
                                onClick = onResetToGooglePhoto,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("lightbox_reset_google_photo_button")
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reset")
                            }
                        }
                    }
                }
            }
        }
    }
}
