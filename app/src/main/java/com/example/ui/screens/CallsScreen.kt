package com.example.ui.screens

import android.Manifest
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.CallSession
import com.example.media.formatDuration
import com.example.permissions.PermissionManager
import com.example.permissions.PermissionManagerScope
import com.example.permissions.PermissionRequestCard
import com.example.ui.PulseCastViewModel
import com.example.ui.UiState
import kotlinx.coroutines.delay

@Composable
fun CallsScreen(
    viewModel: PulseCastViewModel,
    modifier: Modifier = Modifier
) {
    val activeCall by viewModel.activeCallSession.collectAsStateWithLifecycle()
    val callsState by viewModel.callsState.collectAsStateWithLifecycle()
    var roomCodeInput by remember { mutableStateOf("") }

    if (activeCall != null) {
        ActiveCallStage(
            session = activeCall!!,
            onEndCall = { duration -> viewModel.endActiveCall(duration) },
            modifier = modifier
        )
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Audio & Video Studio",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "Launch a 1-to-1 call or multi-party conference room and invite peers via your room code.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val studioPermissions = listOf(
                            Manifest.permission.CAMERA,
                            Manifest.permission.RECORD_AUDIO
                        )
                        PermissionManager(
                            permissions = studioPermissions,
                            featureTitle = "Audio & Video Calling",
                            rationaleMessage = "Grant camera and microphone access to power 1-to-1 and group calls.",
                            deniedContent = { permScope ->
                                PermissionRequestCard(
                                    scope = permScope,
                                    permissions = studioPermissions,
                                    featureTitle = "Audio & Video Calling",
                                    rationaleMessage = "Grant camera and microphone access to power 1-to-1 and group calls."
                                )
                            }
                        ) { permScope ->
                            PermissionRequestCard(
                                scope = permScope,
                                permissions = studioPermissions,
                                featureTitle = "Audio & Video Calling",
                                rationaleMessage = "Grant camera and microphone access to power 1-to-1 and group calls."
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { viewModel.startCall("video") },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("start_video_call_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Filled.VideoCall, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Video")
                            }
                            FilledTonalButton(
                                onClick = { viewModel.startCall("audio") },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("start_audio_call_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Filled.Call, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Audio")
                            }
                        }

                        FilledTonalButton(
                            onClick = { viewModel.startCall("group") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("start_group_call_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Filled.Groups, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Start Group Conference")
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = roomCodeInput,
                                onValueChange = { roomCodeInput = it.uppercase() },
                                label = { Text("Room Code") },
                                placeholder = { Text("PULSE-8842") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("room_code_input")
                            )
                            Button(
                                onClick = { viewModel.joinCallByCode(roomCodeInput) },
                                modifier = Modifier
                                    .height(54.dp)
                                    .testTag("join_room_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Join")
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Recent Call Sessions",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            when (val state = callsState) {
                UiState.Loading -> {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
                is UiState.Error -> {
                    item {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                    }
                }
                is UiState.Success -> {
                    if (state.data.isEmpty()) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "No call sessions yet. Start an audio or video room above!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(20.dp)
                                )
                            }
                        }
                    } else {
                        items(state.data, key = { it.id }) { session ->
                            CallHistoryRow(
                                session = session,
                                onRejoin = {
                                    if (session.status != "ended") {
                                        viewModel.joinCallByCode(session.roomCode)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CallHistoryRow(
    session: CallSession,
    onRejoin: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (session.callType) {
                        "audio" -> Icons.Filled.Call
                        "group" -> Icons.Filled.Groups
                        else -> Icons.Filled.VideoCall
                    },
                    contentDescription = session.callType,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${session.callType.replaceFirstChar { it.uppercase() }} · ${session.roomCode}",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "Host: ${session.hostName} · ${session.participantIds.size} participant(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (session.status != "ended") {
                FilledTonalButton(onClick = onRejoin) {
                    Text("Join")
                }
            } else {
                Text(
                    text = formatDuration(session.durationSec),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ActiveCallStage(
    session: CallSession,
    onEndCall: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var isCameraEnabled by remember(session.roomCode) { mutableStateOf(session.callType != "audio") }
    val requiredPermissions = remember(isCameraEnabled) {
        if (isCameraEnabled) {
            listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        } else {
            listOf(Manifest.permission.RECORD_AUDIO)
        }
    }

    PermissionManager(
        permissions = requiredPermissions,
        featureTitle = if (isCameraEnabled) "Video Call Studio" else "HD Audio Call",
        rationaleMessage = if (isCameraEnabled) {
            "Camera and microphone access are required for live video calls."
        } else {
            "Microphone access is required for live audio calls."
        },
        autoRequestOnLaunch = true,
        deniedContent = { permScope ->
            ActiveCallStageContent(
                session = session,
                isCameraEnabled = isCameraEnabled,
                onToggleCamera = { isCameraEnabled = !isCameraEnabled },
                permScope = permScope,
                onEndCall = onEndCall,
                modifier = modifier
            )
        }
    ) { permScope ->
        ActiveCallStageContent(
            session = session,
            isCameraEnabled = isCameraEnabled,
            onToggleCamera = { isCameraEnabled = !isCameraEnabled },
            permScope = permScope,
            onEndCall = onEndCall,
            modifier = modifier
        )
    }
}

@Composable
private fun ActiveCallStageContent(
    session: CallSession,
    isCameraEnabled: Boolean,
    onToggleCamera: () -> Unit,
    permScope: PermissionManagerScope,
    onEndCall: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var elapsedSec by remember(session.roomCode) { mutableLongStateOf(0L) }
    var isMuted by remember { mutableStateOf(false) }
    var isFrontCamera by remember { mutableStateOf(true) }
    var isSpeakerOn by remember { mutableStateOf(true) }

    val hasCameraPerm = permScope.hasCamera

    LaunchedEffect(session.roomCode) {
        while (true) {
            delay(1000L)
            elapsedSec += 1L
        }
    }

    BackHandler {
        onEndCall(elapsedSec)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            )
    ) {
        if (isCameraEnabled && hasCameraPerm) {
            CameraXPreview(
                useFrontCamera = isFrontCamera,
                lifecycleOwner = lifecycleOwner,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = session.hostName.take(1).uppercase(),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    text = if (session.callType == "audio" && !isCameraEnabled) {
                        "HD Audio Connected"
                    } else {
                        "Camera Paused"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (isCameraEnabled && !hasCameraPerm) {
                    FilledTonalButton(
                        onClick = {
                            if (permScope.permanentlyDenied) {
                                permScope.openAppSettings()
                            } else {
                                permScope.requestPermissions()
                            }
                        },
                        modifier = Modifier.testTag("active_call_grant_permissions_button")
                    ) {
                        Text(if (permScope.permanentlyDenied) "Open App Settings" else "Enable Camera & Mic")
                    }
                }
            }
        }

        // Top Call Info Overlay
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(
                        text = "Room ${session.roomCode}",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                    Text(
                        text = "${session.status.uppercase()} · ${formatDuration(elapsedSec)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            FilledTonalIconButton(
                onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Join my PulseCast call! Room code: ${session.roomCode}")
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Invite to Call"))
                },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.65f),
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Filled.Share, contentDescription = "Share room code")
            }
        }

        // Bottom Controls Bar
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            color = Color.Black.copy(alpha = 0.78f),
            shape = RoundedCornerShape(24.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = { isMuted = !isMuted },
                    modifier = Modifier.size(50.dp).testTag("toggle_mute_button")
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                        contentDescription = if (isMuted) "Unmute microphone" else "Mute microphone"
                    )
                }

                FilledTonalIconButton(
                    onClick = onToggleCamera,
                    modifier = Modifier.size(50.dp).testTag("toggle_camera_button")
                ) {
                    Icon(
                        imageVector = if (isCameraEnabled) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                        contentDescription = if (isCameraEnabled) "Turn camera off" else "Turn camera on"
                    )
                }

                if (isCameraEnabled && hasCameraPerm) {
                    FilledTonalIconButton(
                        onClick = { isFrontCamera = !isFrontCamera },
                        modifier = Modifier.size(50.dp).testTag("flip_camera_button")
                    ) {
                        Icon(Icons.Filled.Cameraswitch, contentDescription = "Switch front or back camera")
                    }
                } else {
                    FilledTonalIconButton(
                        onClick = { isSpeakerOn = !isSpeakerOn },
                        modifier = Modifier.size(50.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Toggle speakerphone",
                            tint = if (isSpeakerOn) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }
                }

                Button(
                    onClick = { onEndCall(elapsedSec) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = Color.White
                    ),
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp).testTag("end_call_button"),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Filled.CallEnd, contentDescription = "End call")
                }
            }
        }
    }
}

@Composable
private fun CameraXPreview(
    useFrontCamera: Boolean,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val previewView = remember { PreviewView(context) }

    DisposableEffect(useFrontCamera, lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        var providerRef: ProcessCameraProvider? = null
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                providerRef = cameraProvider
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val selector = if (useFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview)
            } catch (_: Exception) {
            }
        }, executor)

        onDispose {
            try {
                providerRef?.unbindAll()
            } catch (_: Exception) {
            }
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}
