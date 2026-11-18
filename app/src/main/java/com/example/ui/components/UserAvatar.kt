package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.media.decodeBase64ToBitmap
import com.example.ui.theme.EmeraldMint

@Composable
fun UserAvatarWithStatus(
    displayName: String,
    isOnline: Boolean,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 44.dp,
    isGroup: Boolean = false,
    photoUrl: String? = null,
    avatarBase64: String? = null,
    onClick: (() -> Unit)? = null,
    avatarTestTag: String? = null,
    indicatorTestTag: String? = null
) {
    val safeName = displayName.trim().ifBlank { "Creator" }
    val statusDescription = if (isOnline) {
        stringResource(R.string.avatar_status_online, safeName)
    } else {
        stringResource(R.string.avatar_status_offline, safeName)
    }
    val dotSize = (avatarSize * 0.30f).coerceIn(10.dp, 16.dp)
    val dotColor = if (isOnline) {
        EmeraldMint
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.75f)
    }
    val customBitmap = remember(avatarBase64) {
        if (isGroup) null else decodeBase64ToBitmap(avatarBase64)
    }

    Box(
        modifier = modifier
            .size(avatarSize)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .then(
                if (!avatarTestTag.isNullOrBlank()) {
                    Modifier.testTag(avatarTestTag)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (isGroup) {
                Icon(
                    imageVector = Icons.Filled.Group,
                    contentDescription = safeName,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            } else {
                Text(
                    text = safeName.take(1).uppercase(),
                    style = if (avatarSize >= 44.dp) {
                        MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    } else {
                        MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    },
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (customBitmap != null) {
                    Image(
                        bitmap = customBitmap.asImageBitmap(),
                        contentDescription = "$safeName profile photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (!photoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = "$safeName profile photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(dotSize)
                .clip(CircleShape)
                .background(dotColor)
                .border(width = 2.dp, color = MaterialTheme.colorScheme.surface, shape = CircleShape)
                .semantics { contentDescription = statusDescription }
                .then(
                    if (!indicatorTestTag.isNullOrBlank()) {
                        Modifier.testTag(indicatorTestTag)
                    } else {
                        Modifier
                    }
                )
        )
    }
}
