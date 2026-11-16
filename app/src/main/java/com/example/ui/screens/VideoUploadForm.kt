package com.example.ui.screens

import android.Manifest
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.R
import com.example.media.PulseCastVideoPlayer
import com.example.media.decodeBase64ToBitmap
import com.example.media.encodeUriImageToBase64
import com.example.media.extractVideoMetadata
import com.example.media.formatDuration
import com.example.media.formatFileSize
import com.example.permissions.PermissionManager
import com.example.permissions.PermissionManagerScope
import com.example.permissions.PermissionRequestCard
import com.example.ui.VideoUploadUiState
import kotlinx.coroutines.launch
import java.io.File

internal val UPLOAD_VIDEO_CATEGORIES = listOf("Tech", "Studio", "Podcast", "Music", "Vlog", "Live")

@Composable
fun VideoUploadFormScreen(
    uploadState: VideoUploadUiState,
    onDismiss: () -> Unit,
    onSubmit: (
        title: String,
        description: String?,
        category: String,
        localVideoUri: Uri?,
        externalVideoUrl: String?,
        mimeType: String?,
        coverKey: String?,
        thumbnailBase64: String?,
        visibility: String,
        durationSec: Long
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Studio") }
    var visibility by remember { mutableStateOf("public") }
    var coverKey by remember { mutableStateOf("thumb_1") }
    var localVideoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var selectedFileSizeBytes by remember { mutableStateOf<Long?>(null) }
    var selectedMimeType by remember { mutableStateOf<String?>(null) }
    var externalVideoUrl by remember { mutableStateOf("") }
    var thumbnailBase64 by remember { mutableStateOf<String?>(null) }
    var durationSec by remember { mutableLongStateOf(0L) }
    var isProcessingMedia by remember { mutableStateOf(false) }
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var launchCaptureOnGrant by remember { mutableStateOf(false) }

    val isBusy = uploadState is VideoUploadUiState.Uploading ||
        uploadState is VideoUploadUiState.SavingMetadata

    BackHandler(enabled = !isBusy, onBack = onDismiss)

    fun ingestLocalVideo(uri: Uri) {
        localVideoUri = uri
        isProcessingMedia = true
        scope.launch {
            val meta = extractVideoMetadata(context, uri)
            durationSec = meta.durationSec
            selectedFileName = meta.displayName
            selectedFileSizeBytes = meta.sizeBytes
            selectedMimeType = meta.mimeType
            if (meta.thumbnailBase64 != null) {
                thumbnailBase64 = meta.thumbnailBase64
            }
            isProcessingMedia = false
        }
    }

    val videoRecorderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { saved ->
        val recordedUri = pendingCaptureUri
        if (saved && recordedUri != null) {
            ingestLocalVideo(recordedUri)
        }
    }

    fun launchStudioRecorder() {
        val file = File(context.cacheDir, "studio_clip_${System.currentTimeMillis()}.mp4")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingCaptureUri = uri
        videoRecorderLauncher.launch(uri)
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            ingestLocalVideo(uri)
        }
    }

    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isProcessingMedia = true
            scope.launch {
                thumbnailBase64 = encodeUriImageToBase64(context, uri)
                isProcessingMedia = false
            }
        }
    }

    VideoUploadFormContent(
        title = title,
        onTitleChange = { if (it.length <= 120) title = it },
        description = description,
        onDescriptionChange = { if (it.length <= 1000) description = it },
        category = category,
        onCategoryChange = { category = it },
        visibility = visibility,
        onVisibilityChange = { visibility = it },
        coverKey = coverKey,
        onToggleCoverKey = { coverKey = if (coverKey == "thumb_1") "thumb_2" else "thumb_1" },
        localVideoUri = localVideoUri,
        selectedFileName = selectedFileName,
        selectedFileSizeBytes = selectedFileSizeBytes,
        durationSec = durationSec,
        externalVideoUrl = externalVideoUrl,
        onExternalVideoUrlChange = { externalVideoUrl = it },
        thumbnailBase64 = thumbnailBase64,
        isProcessingMedia = isProcessingMedia,
        uploadState = uploadState,
        onPickVideo = {
            videoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
            )
        },
        onClearSelectedVideo = {
            localVideoUri = null
            selectedFileName = null
            selectedFileSizeBytes = null
            selectedMimeType = null
            durationSec = 0L
        },
        onPickCover = {
            coverPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        },
        recordPermissionContent = {
            val recordPermissions = listOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            )
            PermissionManager(
                permissions = recordPermissions,
                featureTitle = "Video Content Publishing",
                rationaleMessage = "Grant camera and microphone access to record clips directly in PulseCast.",
                onAllPermissionsGranted = {
                    if (launchCaptureOnGrant) {
                        launchCaptureOnGrant = false
                        launchStudioRecorder()
                    }
                },
                onPermissionResult = { results ->
                    if (!results.values.all { it }) {
                        launchCaptureOnGrant = false
                    }
                },
                deniedContent = { permScope ->
                    RecordClipActionSection(
                        permScope = permScope,
                        permissions = recordPermissions,
                        enabled = !isBusy,
                        onRecordClick = {
                            if (permScope.permanentlyDenied) {
                                permScope.openAppSettings()
                            } else {
                                launchCaptureOnGrant = true
                                permScope.requestPermissions()
                            }
                        }
                    )
                }
            ) { permScope ->
                RecordClipActionSection(
                    permScope = permScope,
                    permissions = recordPermissions,
                    enabled = !isBusy,
                    onRecordClick = { launchStudioRecorder() }
                )
            }
        },
        onDismiss = onDismiss,
        onSubmit = {
            onSubmit(
                title,
                description.ifBlank { null },
                category,
                localVideoUri,
                externalVideoUrl.ifBlank { null },
                selectedMimeType,
                coverKey,
                thumbnailBase64,
                visibility,
                durationSec
            )
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VideoUploadFormContent(
    title: String,
    onTitleChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    category: String,
    onCategoryChange: (String) -> Unit,
    visibility: String,
    onVisibilityChange: (String) -> Unit,
    coverKey: String,
    onToggleCoverKey: () -> Unit,
    localVideoUri: Uri?,
    selectedFileName: String?,
    selectedFileSizeBytes: Long?,
    durationSec: Long,
    externalVideoUrl: String,
    onExternalVideoUrlChange: (String) -> Unit,
    thumbnailBase64: String?,
    isProcessingMedia: Boolean,
    uploadState: VideoUploadUiState,
    onPickVideo: () -> Unit,
    onClearSelectedVideo: () -> Unit,
    onPickCover: () -> Unit,
    recordPermissionContent: @Composable () -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    showInlinePlayerPreview: Boolean = true
) {
    val isBusy = uploadState is VideoUploadUiState.Uploading ||
        uploadState is VideoUploadUiState.SavingMetadata
    var previewTypedUrl by remember(externalVideoUrl) { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.upload_form_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = stringResource(R.string.upload_form_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isBusy,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("upload_form_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.upload_form_close)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Video File Card (Firebase Storage target)
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.testTag("upload_video_file_card")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.CloudUpload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Video File (Firebase Storage)", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Pick a clip from your gallery or record a new video to store in the cloud.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onPickVideo,
                                enabled = !isBusy,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("pick_video_button")
                            ) {
                                Icon(Icons.Filled.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (localVideoUri != null) {
                                        stringResource(R.string.upload_change_file)
                                    } else {
                                        stringResource(R.string.upload_select_file)
                                    }
                                )
                            }
                            OutlinedButton(
                                onClick = onPickCover,
                                enabled = !isBusy,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("pick_thumb_button")
                            ) {
                                Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (thumbnailBase64 != null) "Cover Set" else stringResource(R.string.upload_change_cover)
                                )
                            }
                        }

                        recordPermissionContent()

                        if (localVideoUri != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("selected_video_summary")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.VideoFile,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = selectedFileName ?: "Selected video clip",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val sizeLabel = formatFileSize(selectedFileSizeBytes)
                                        val details = listOfNotNull(
                                            formatDuration(durationSec).takeIf { durationSec > 0L },
                                            sizeLabel.takeIf { it.isNotBlank() }
                                        ).joinToString(" · ").ifBlank { "Ready to upload" }
                                        Text(
                                            text = details,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                        )
                                    }
                                    IconButton(
                                        onClick = onClearSelectedVideo,
                                        enabled = !isBusy,
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("remove_video_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.DeleteOutline,
                                            contentDescription = stringResource(R.string.upload_remove_clip),
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }

                            if (showInlinePlayerPreview) {
                                PulseCastVideoPlayer(
                                    videoUri = localVideoUri.toString(),
                                    autoPlay = false,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(14.dp))
                                )
                            }
                        } else {
                            OutlinedTextField(
                                value = externalVideoUrl,
                                onValueChange = {
                                    previewTypedUrl = false
                                    onExternalVideoUrlChange(it)
                                },
                                enabled = !isBusy,
                                label = { Text("Or paste Stream URL (optional)") },
                                placeholder = { Text("https://...mp4") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("publish_video_url_input")
                            )

                            val isRemote = externalVideoUrl.startsWith("https://", ignoreCase = true) ||
                                externalVideoUrl.startsWith("http://", ignoreCase = true)
                            if (isRemote && !previewTypedUrl) {
                                TextButton(
                                    onClick = { previewTypedUrl = true },
                                    modifier = Modifier.testTag("preview_stream_button")
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Preview Video Stream")
                                }
                            }
                            if (isRemote && previewTypedUrl && showInlinePlayerPreview) {
                                PulseCastVideoPlayer(
                                    videoUri = externalVideoUrl.trim(),
                                    autoPlay = false,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(14.dp))
                                )
                            }
                        }

                        if (isProcessingMedia) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Inspecting video & extracting thumbnail…", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                // 2. Metadata Card (Firestore target)
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.testTag("upload_metadata_card")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Video Metadata (Firestore)", style = MaterialTheme.typography.titleSmall)

                        OutlinedTextField(
                            value = title,
                            onValueChange = onTitleChange,
                            enabled = !isBusy,
                            label = { Text("Video Title *") },
                            supportingText = { Text("${title.length}/120") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("publish_title_input")
                        )

                        OutlinedTextField(
                            value = description,
                            onValueChange = onDescriptionChange,
                            enabled = !isBusy,
                            label = { Text("Description") },
                            supportingText = { Text("${description.length}/1000") },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("publish_desc_input")
                        )

                        Text("Category", style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            UPLOAD_VIDEO_CATEGORIES.forEach { cat ->
                                FilterChip(
                                    selected = category == cat,
                                    onClick = { if (!isBusy) onCategoryChange(cat) },
                                    label = { Text(cat) },
                                    modifier = Modifier.testTag("upload_category_${cat.lowercase()}")
                                )
                            }
                        }

                        Text("Visibility & Cover Art", style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = visibility == "public",
                                onClick = { if (!isBusy) onVisibilityChange("public") },
                                label = { Text("Public") },
                                modifier = Modifier.testTag("visibility_public_chip")
                            )
                            FilterChip(
                                selected = visibility == "private",
                                onClick = { if (!isBusy) onVisibilityChange("private") },
                                label = { Text("Private Draft") },
                                modifier = Modifier.testTag("visibility_private_chip")
                            )
                            FilterChip(
                                selected = coverKey == "thumb_2",
                                onClick = { if (!isBusy) onToggleCoverKey() },
                                label = { Text(if (coverKey == "thumb_1") "Studio Art" else "Stage Art") },
                                modifier = Modifier.testTag("cover_art_chip")
                            )
                        }

                        val decodedCover = remember(thumbnailBase64) { decodeBase64ToBitmap(thumbnailBase64) }
                        if (decodedCover != null) {
                            Image(
                                bitmap = decodedCover.asImageBitmap(),
                                contentDescription = "Selected video cover preview",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }

                // 3. Upload Progress / Error Status
                AnimatedVisibility(visible = isBusy) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            when (uploadState) {
                                is VideoUploadUiState.Uploading -> {
                                    val percent = (uploadState.progress * 100).toInt().coerceIn(0, 100)
                                    Text(
                                        text = "${stringResource(R.string.upload_uploading_video)} ($percent%)",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.testTag("upload_status_text")
                                    )
                                    LinearProgressIndicator(
                                        progress = { uploadState.progress.coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .testTag("upload_progress_indicator")
                                    )
                                }
                                VideoUploadUiState.SavingMetadata -> {
                                    Text(
                                        text = stringResource(R.string.upload_saving_metadata),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.testTag("upload_status_text")
                                    )
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .testTag("upload_progress_indicator")
                                    )
                                }
                                else -> Unit
                            }
                        }
                    }
                }

                if (uploadState is VideoUploadUiState.Error) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("upload_error_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = uploadState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                Button(
                    onClick = onSubmit,
                    enabled = title.isNotBlank() && !isProcessingMedia && !isBusy,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("submit_publish_video")
                ) {
                    Icon(Icons.Filled.CloudUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            isBusy -> "Uploading…"
                            localVideoUri != null -> stringResource(R.string.upload_submit_storage)
                            else -> stringResource(R.string.upload_submit_metadata)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun RecordClipActionSection(
    permScope: PermissionManagerScope,
    permissions: List<String>,
    enabled: Boolean,
    onRecordClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onRecordClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("record_video_button")
        ) {
            Icon(Icons.Filled.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.upload_record_clip))
        }

        PermissionRequestCard(
            scope = permScope,
            permissions = permissions,
            featureTitle = "Video Content Publishing",
            rationaleMessage = "Grant camera and microphone access to record clips directly in PulseCast."
        )
    }
}
