package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.PublishedVideo
import com.example.media.formatDuration

@Composable
internal fun ProfileHeaderCard(
    displayName: String,
    handle: String,
    email: String,
    bio: String,
    status: String,
    photoUrl: String?,
    isEditing: Boolean,
    onToggleEdit: () -> Unit,
    modifier: Modifier = Modifier,
    avatarBase64: String? = null,
    isOnline: Boolean = true,
    onAvatarClick: (() -> Unit)? = null,
    onChangePhotoClick: (() -> Unit)? = null
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("profile_header_card")
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                com.example.ui.components.UserAvatarWithStatus(
                    displayName = displayName,
                    isOnline = isOnline,
                    avatarSize = 68.dp,
                    photoUrl = photoUrl,
                    avatarBase64 = avatarBase64,
                    onClick = onAvatarClick,
                    avatarTestTag = "profile_avatar_image",
                    indicatorTestTag = "profile_online_indicator"
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("profile_display_name")
                    )
                    Text(
                        text = "@$handle",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("profile_handle_label")
                    )
                    if (email.isNotBlank()) {
                        Text(
                            text = email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("profile_email_label")
                        )
                    }
                }
                IconButton(
                    onClick = onToggleEdit,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("edit_profile_toggle_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = if (isEditing) "Hide profile editor" else "Edit profile",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (bio.isNotBlank()) {
                Text(
                    text = bio,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("profile_bio_label")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = status.replace('_', ' ').replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Creator Account",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                if (onChangePhotoClick != null) {
                    TextButton(
                        onClick = onChangePhotoClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("profile_change_photo_button")
                    ) {
                        Text(stringResource(R.string.profile_change_photo))
                    }
                }
            }
        }
    }
}

@Composable
internal fun CreatorProfilesStrip(
    creators: List<com.example.data.CreatorProfile>,
    nowMillis: Long,
    onCreatorClick: (com.example.data.CreatorProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    if (creators.isEmpty()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("studio_creators_strip"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${stringResource(R.string.creator_profile_directory_title)} (${creators.size})",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Tap to view profile",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                items = creators,
                key = { it.userId }
            ) { creator ->
                val isOnline = com.example.data.resolveIsUserOnline(
                    isOnlineFlag = creator.isOnline,
                    lastActiveAt = creator.lastActiveAt,
                    updatedAt = creator.updatedAt,
                    nowMillis = nowMillis
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.68f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .width(124.dp)
                        .clickable { onCreatorClick(creator) }
                        .testTag("studio_creator_card_${creator.userId}")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        com.example.ui.components.UserAvatarWithStatus(
                            displayName = creator.displayName,
                            isOnline = isOnline,
                            avatarSize = 54.dp,
                            photoUrl = creator.photoUrl,
                            avatarBase64 = creator.avatarBase64,
                            indicatorTestTag = "studio_creator_online_${creator.userId}"
                        )
                        Text(
                            text = creator.displayName,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "@${creator.handle}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProfileStatsRow(
    videoCount: Int,
    totalLikes: Long,
    totalComments: Long,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ProfileStatPill(
            icon = Icons.Filled.VideoLibrary,
            label = "Videos",
            value = "$videoCount",
            modifier = Modifier
                .weight(1f)
                .testTag("stat_videos_count")
        )
        ProfileStatPill(
            icon = Icons.Filled.Favorite,
            label = "Likes",
            value = "$totalLikes",
            modifier = Modifier
                .weight(1f)
                .testTag("stat_likes_count")
        )
        ProfileStatPill(
            icon = Icons.AutoMirrored.Filled.Comment,
            label = "Comments",
            value = "$totalComments",
            modifier = Modifier
                .weight(1f)
                .testTag("stat_comments_count")
        )
    }
}

@Composable
private fun ProfileStatPill(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.semantics(mergeDescendants = true) {}
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
internal fun UploadedVideoListItem(
    video: PublishedVideo,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLiked: Boolean = false,
    isLikeUpdating: Boolean = false,
    onLikeClick: () -> Unit = {}
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("profile_video_card_${video.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(104.dp)
                    .height(66.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black)
            ) {
                VideoCoverArt(video = video, modifier = Modifier.fillMaxSize())
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Watch ${video.title}",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Surface(
                    color = Color.Black.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                ) {
                    Text(
                        text = formatDuration(video.durationSec),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = video.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    val isPrivate = video.visibility.equals("private", ignoreCase = true)
                    Surface(
                        color = if (isPrivate) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (isPrivate) {
                                stringResource(R.string.profile_visibility_private)
                            } else {
                                stringResource(R.string.profile_visibility_public)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isPrivate) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
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
                                onClick = onLikeClick
                            )
                            .semantics { contentDescription = likeActionDescription }
                            .testTag("profile_like_button_${video.id}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = null,
                                tint = if (isLiked) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${video.likesCount}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isLiked) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isLiked) {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.testTag("profile_like_count_${video.id}")
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Comment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "${video.commentsCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("profile_delete_video_${video.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Delete ${video.title}",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
internal fun UploadedVideosEmptyState(
    hasFilterApplied: Boolean,
    onUploadClick: () -> Unit,
    onResetFilter: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("profile_empty_videos")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.VideoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp)
            )
            Text(
                text = if (hasFilterApplied) {
                    "No videos match this filter"
                } else {
                    stringResource(R.string.profile_no_videos_title)
                },
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = if (hasFilterApplied) {
                    "Switch back to All to see all uploaded videos."
                } else {
                    stringResource(R.string.profile_no_videos_subtitle)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (hasFilterApplied) {
                OutlinedButton(
                    onClick = onResetFilter,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Show All Videos")
                }
            } else {
                Button(
                    onClick = onUploadClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("profile_empty_upload_button")
                ) {
                    Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.profile_upload_new_video))
                }
            }
        }
    }
}

@Composable
internal fun DeleteVideoConfirmDialog(
    videoTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_delete_video_title)) },
        text = {
            Text(
                text = "\"$videoTitle\" — ${stringResource(R.string.profile_delete_video_message)}",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.testTag("confirm_delete_video_button")
            ) {
                Text(stringResource(R.string.profile_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_delete_video_button")
            ) {
                Text(stringResource(R.string.profile_cancel))
            }
        }
    )
}
