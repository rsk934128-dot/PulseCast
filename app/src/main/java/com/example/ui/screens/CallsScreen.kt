package com.example.ui.screens

import android.Manifest
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.calls.RtcConnectionState
import com.example.calls.WebRtcCallClient
import com.example.data.CallSession
import com.example.data.CallSignal
import com.example.media.formatDuration
import com.example.permissions.PermissionManager
import com.example.permissions.PermissionManagerScope
import com.example.permissions.PermissionRequestCard
import com.example.ui.PulseCastViewModel
import com.example.ui.UiState
import com.example.ui.components.UserAvatarWithStatus
import com.example.ui.theme.EmeraldMint
import kotlinx.coroutines.delay
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
fun CallsScreen(
    viewModel: PulseCastViewModel,
    modifier: Modifier = Modifier,
    renderActiveCallStage: Boolean = true
) {
    val activeCall by viewModel.activeCallSession.collectAsStateWithLifecycle()
    val activeCallSignals by viewModel.activeCallSignals.collectAsStateWithLifecycle()
    val callsState by viewModel.callsState.collectAsStateWithLifecycle()
    val creatorsState by viewModel.creatorsState.collectAsStateWithLifecycle()
    val callParticipantProfiles by viewModel.callParticipantProfiles.collectAsStateWithLifecycle()
    val creatorsList = remember(creatorsState) {
        (creatorsState as? UiState.Success)?.data.orEmpty()
    }
    val creatorsById = remember(creatorsList, callParticipantProfiles) {
        buildMap {
            creatorsList.forEach { if (it.userId.isNotBlank()) put(it.userId, it) }
            putAll(callParticipantProfiles)
        }
    }
    val otherCreators = remember(creatorsList, viewModel.currentUserId) {
        creatorsList.filter { it.userId != viewModel.currentUserId }
    }
    var roomCodeInput by remember { mutableStateOf("") }
    var selectedTargetId by remember { mutableStateOf<String?>(null) }
    val selectedTargetCreator = remember(otherCreators, selectedTargetId) {
        otherCreators.firstOrNull { it.userId == selectedTargetId }
    }

    if (renderActiveCallStage && activeCall != null) {
        val currentSession = activeCall!!
        val peerCreator = remember(currentSession, creatorsById, viewModel.currentUserId) {
            val peerId = currentSession.participantIds.firstOrNull { it != viewModel.currentUserId }
                ?: currentSession.hostId
            creatorsById[peerId]
        }
        ActiveCallStage(
            session = currentSession,
            currentUserId = viewModel.currentUserId,
            signals = activeCallSignals,
            onSendSignal = { recipientId, type, sdp, sdpMid, sdpMLineIndex, candidate ->
                viewModel.sendCallSignal(
                    callId = currentSession.roomCode,
                    recipientId = recipientId,
                    type = type,
                    sdp = sdp,
                    sdpMid = sdpMid,
                    sdpMLineIndex = sdpMLineIndex,
                    candidate = candidate
                )
            },
            onEndCall = { duration -> viewModel.endActiveCall(duration) },
            modifier = modifier,
            peerPhotoUrl = peerCreator?.photoUrl,
            peerAvatarBase64 = peerCreator?.avatarBase64,
            peerFallbackName = peerCreator?.displayName
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

                        if (otherCreators.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = if (selectedTargetCreator != null) {
                                        "Ringing: ${selectedTargetCreator.displayName}"
                                    } else {
                                        "Select a person to ring (optional):"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selectedTargetCreator != null) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    item(key = "open_room") {
                                        androidx.compose.material3.FilterChip(
                                            selected = selectedTargetCreator == null,
                                            onClick = { selectedTargetId = null },
                                            label = { Text("Open Room") }
                                        )
                                    }
                                    items(otherCreators, key = { it.userId }) { creator ->
                                        val isSelected = selectedTargetId == creator.userId
                                        androidx.compose.material3.FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                selectedTargetId = if (isSelected) null else creator.userId
                                            },
                                            leadingIcon = {
                                                UserAvatarWithStatus(
                                                    displayName = creator.displayName,
                                                    isOnline = com.example.data.resolveIsUserOnline(
                                                        isOnlineFlag = creator.isOnline,
                                                        lastActiveAt = creator.lastActiveAt,
                                                        updatedAt = creator.updatedAt
                                                    ),
                                                    avatarSize = 22.dp,
                                                    photoUrl = creator.photoUrl,
                                                    avatarBase64 = creator.avatarBase64
                                                )
                                            },
                                            label = { Text(creator.displayName, maxLines = 1) }
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { viewModel.startCall("video", targetCreator = selectedTargetCreator) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("start_video_call_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Filled.VideoCall, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (selectedTargetCreator != null) "Call Video" else "Video")
                            }
                            FilledTonalButton(
                                onClick = { viewModel.startCall("audio", targetCreator = selectedTargetCreator) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("start_audio_call_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Filled.Call, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (selectedTargetCreator != null) "Call Audio" else "Audio")
                            }
                        }

                        FilledTonalButton(
                            onClick = { viewModel.startCall("group", targetCreator = selectedTargetCreator) },
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

            if (otherCreators.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Call People Directly (${otherCreators.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(otherCreators, key = { it.userId }) { creator ->
                                val isOnline = com.example.data.resolveIsUserOnline(
                                    isOnlineFlag = creator.isOnline,
                                    lastActiveAt = creator.lastActiveAt,
                                    updatedAt = creator.updatedAt
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .width(222.dp)
                                        .testTag("calls_creator_card_${creator.userId}")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable { viewModel.openCreatorProfile(creator) }
                                                .padding(2.dp)
                                                .testTag("calls_creator_profile_${creator.userId}")
                                        ) {
                                            UserAvatarWithStatus(
                                                displayName = creator.displayName,
                                                isOnline = isOnline,
                                                avatarSize = 40.dp,
                                                photoUrl = creator.photoUrl,
                                                avatarBase64 = creator.avatarBase64
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = creator.displayName,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    maxLines = 1
                                                )
                                                Text(
                                                    text = "@${creator.handle}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            FilledTonalButton(
                                                onClick = { viewModel.startCall("audio", targetCreator = creator) },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                            ) {
                                                Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Audio", style = MaterialTheme.typography.labelSmall)
                                            }
                                            Button(
                                                onClick = { viewModel.startCall("video", targetCreator = creator) },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                            ) {
                                                Icon(Icons.Filled.VideoCall, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Video", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Live & Recent Call Sessions",
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
                            val peerId = session.participantIds.firstOrNull { it != viewModel.currentUserId }
                                ?: session.hostId
                            val peerCreator = creatorsById[peerId] ?: creatorsById[session.hostId]
                            CallHistoryRow(
                                session = session,
                                currentUserId = viewModel.currentUserId,
                                peerCreator = peerCreator,
                                onAnswer = { viewModel.acceptIncomingCall(session) },
                                onDecline = { viewModel.declineIncomingCall(session) },
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
    currentUserId: String,
    peerCreator: com.example.data.CreatorProfile? = null,
    onAnswer: () -> Unit = {},
    onDecline: () -> Unit = {},
    onRejoin: () -> Unit
) {
    val isIncomingRinging = session.status == "ringing" &&
        session.hostId.isNotBlank() &&
        session.hostId != currentUserId
    val displayPeerName = peerCreator?.displayName?.takeIf { it.isNotBlank() }
        ?: if (session.hostId != currentUserId) {
            session.hostName.ifBlank { "PulseCast Caller" }
        } else {
            session.participantNames
                ?.firstOrNull { it.isNotBlank() && !it.equals(session.hostName, ignoreCase = true) }
                ?: session.hostName.ifBlank { "PulseCast Peer" }
        }
    val isPeerOnline = peerCreator?.let {
        com.example.data.resolveIsUserOnline(
            isOnlineFlag = it.isOnline,
            lastActiveAt = it.lastActiveAt,
            updatedAt = it.updatedAt
        )
    } ?: (session.status != "ended")

    Surface(
        color = if (isIncomingRinging) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        },
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatarWithStatus(
                displayName = displayPeerName,
                isOnline = isPeerOnline,
                avatarSize = 44.dp,
                isGroup = session.callType == "group",
                photoUrl = peerCreator?.photoUrl,
                avatarBase64 = peerCreator?.avatarBase64
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$displayPeerName · ${session.callType.replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "${session.roomCode} · ${session.status.uppercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (session.status != "ended") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isIncomingRinging) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(
                        onClick = onDecline,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Decline")
                    }
                    Button(
                        onClick = onAnswer,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldMint,
                            contentColor = Color(0xFF002114)
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("Answer")
                    }
                }
            } else if (session.status != "ended") {
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
internal fun ActiveCallStage(
    session: CallSession,
    currentUserId: String = "",
    signals: List<CallSignal> = emptyList(),
    onSendSignal: suspend (
        recipientId: String,
        type: String,
        sdp: String?,
        sdpMid: String?,
        sdpMLineIndex: Int?,
        candidate: String?
    ) -> Unit = { _, _, _, _, _, _ -> },
    onEndCall: (Long) -> Unit,
    modifier: Modifier = Modifier,
    peerPhotoUrl: String? = null,
    peerAvatarBase64: String? = null,
    peerFallbackName: String? = null
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
                currentUserId = currentUserId,
                signals = signals,
                isCameraEnabled = isCameraEnabled,
                onToggleCamera = { isCameraEnabled = !isCameraEnabled },
                onSendSignal = onSendSignal,
                permScope = permScope,
                onEndCall = onEndCall,
                modifier = modifier,
                peerPhotoUrl = peerPhotoUrl,
                peerAvatarBase64 = peerAvatarBase64,
                peerFallbackName = peerFallbackName
            )
        }
    ) { permScope ->
        ActiveCallStageContent(
            session = session,
            currentUserId = currentUserId,
            signals = signals,
            isCameraEnabled = isCameraEnabled,
            onToggleCamera = { isCameraEnabled = !isCameraEnabled },
            onSendSignal = onSendSignal,
            permScope = permScope,
            onEndCall = onEndCall,
            modifier = modifier,
            peerPhotoUrl = peerPhotoUrl,
            peerAvatarBase64 = peerAvatarBase64,
            peerFallbackName = peerFallbackName
        )
    }
}

@Composable
private fun ActiveCallStageContent(
    session: CallSession,
    currentUserId: String,
    signals: List<CallSignal>,
    isCameraEnabled: Boolean,
    onToggleCamera: () -> Unit,
    onSendSignal: suspend (
        recipientId: String,
        type: String,
        sdp: String?,
        sdpMid: String?,
        sdpMLineIndex: Int?,
        candidate: String?
    ) -> Unit,
    permScope: PermissionManagerScope,
    onEndCall: (Long) -> Unit,
    modifier: Modifier = Modifier,
    peerPhotoUrl: String? = null,
    peerAvatarBase64: String? = null,
    peerFallbackName: String? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var elapsedSec by remember(session.roomCode) { mutableLongStateOf(0L) }
    var isMuted by remember(session.roomCode) { mutableStateOf(false) }
    var isFrontCamera by remember(session.roomCode) { mutableStateOf(true) }
    var isSpeakerOn by remember(session.roomCode) { mutableStateOf(session.callType != "audio") }

    val hasCameraPerm = permScope.hasCamera
    val hasMicPerm = permScope.hasMicrophone

    val rtcClient = remember(session.roomCode, currentUserId) {
        WebRtcCallClient(
            context = context,
            roomCode = session.roomCode,
            selfUid = currentUserId,
            initialVideoEnabled = session.callType != "audio",
            onSendSignal = onSendSignal
        )
    }

    DisposableEffect(rtcClient) {
        onDispose { rtcClient.dispose() }
    }

    LaunchedEffect(rtcClient, hasMicPerm, hasCameraPerm, isCameraEnabled) {
        if (hasMicPerm && currentUserId.isNotBlank()) {
            rtcClient.startIfPermitted()
            rtcClient.setCameraEnabled(isCameraEnabled)
        }
    }

    LaunchedEffect(rtcClient, session.participantIds, session.status, hasMicPerm) {
        if (hasMicPerm && currentUserId.isNotBlank()) {
            rtcClient.syncParticipants(session.participantIds, session.status)
        }
    }

    val rtcState by rtcClient.connectionState.collectAsStateWithLifecycle()
    val localVideoTrack by rtcClient.localVideoTrack.collectAsStateWithLifecycle()
    val remoteVideoTrack by rtcClient.remoteVideoTrack.collectAsStateWithLifecycle()
    val isMediaReady by rtcClient.isMediaReady.collectAsStateWithLifecycle()

    LaunchedEffect(rtcClient, signals, isMediaReady) {
        if (isMediaReady) {
            rtcClient.handleIncomingSignals(signals)
        }
    }

    LaunchedEffect(session.roomCode, session.status) {
        if (session.status == "active") {
            while (true) {
                delay(1000L)
                elapsedSec += 1L
            }
        }
    }

    BackHandler {
        onEndCall(elapsedSec)
    }

    val peerDisplayName = remember(session, currentUserId, peerFallbackName) {
        peerFallbackName?.takeIf { it.isNotBlank() }
            ?: if (session.hostId != currentUserId && session.hostName.isNotBlank()) {
                session.hostName
            } else {
                session.participantNames
                    ?.firstOrNull { name -> name.isNotBlank() && !name.equals(session.hostName, ignoreCase = true) }
                    ?: session.hostName.ifBlank { "PulseCast Peer" }
            }
    }

    val statusText = when {
        session.status == "ringing" ->
            stringResource(R.string.call_status_ringing_peer)
        rtcState == RtcConnectionState.CONNECTED ->
            "${stringResource(R.string.call_status_connected_live)} · ${formatDuration(elapsedSec)}"
        rtcState == RtcConnectionState.CONNECTING ->
            "${stringResource(R.string.call_status_connecting)} · ${formatDuration(elapsedSec)}"
        rtcState == RtcConnectionState.RECONNECTING ->
            "${stringResource(R.string.call_status_reconnecting)} · ${formatDuration(elapsedSec)}"
        else ->
            "${session.status.uppercase()} · ${formatDuration(elapsedSec)}"
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
            .testTag("active_call_stage")
    ) {
        when {
            remoteVideoTrack != null && rtcClient.eglBase != null -> {
                WebRtcVideoSurface(
                    videoTrack = remoteVideoTrack!!,
                    eglContext = rtcClient.eglBase!!.eglBaseContext,
                    mirror = false,
                    isOverlay = false,
                    modifier = Modifier.fillMaxSize()
                )
                if (isCameraEnabled && hasCameraPerm && localVideoTrack != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        tonalElevation = 6.dp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 84.dp, end = 16.dp)
                            .size(width = 112.dp, height = 164.dp)
                    ) {
                        WebRtcVideoSurface(
                            videoTrack = localVideoTrack!!,
                            eglContext = rtcClient.eglBase!!.eglBaseContext,
                            mirror = isFrontCamera,
                            isOverlay = true,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            isCameraEnabled && hasCameraPerm && localVideoTrack != null && rtcClient.eglBase != null -> {
                WebRtcVideoSurface(
                    videoTrack = localVideoTrack!!,
                    eglContext = rtcClient.eglBase!!.eglBaseContext,
                    mirror = isFrontCamera,
                    isOverlay = false,
                    modifier = Modifier.fillMaxSize()
                )
            }

            isCameraEnabled && hasCameraPerm && !isMediaReady -> {
                CameraXPreview(
                    useFrontCamera = isFrontCamera,
                    lifecycleOwner = lifecycleOwner,
                    modifier = Modifier.fillMaxSize()
                )
            }

            else -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    UserAvatarWithStatus(
                        displayName = peerDisplayName,
                        isOnline = session.status != "ended",
                        avatarSize = 104.dp,
                        isGroup = session.callType == "group",
                        photoUrl = peerPhotoUrl,
                        avatarBase64 = peerAvatarBase64,
                        indicatorTestTag = "active_call_peer_avatar_indicator"
                    )
                    Text(
                        text = peerDisplayName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = when {
                            session.status == "ringing" -> "Calling $peerDisplayName…"
                            session.callType == "audio" || !isCameraEnabled -> "Live Voice Call · Microphone ${if (isMuted) "Muted" else "Active"}"
                            else -> "Camera Paused"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (session.status == "active" && !isMuted) {
                            EmeraldMint
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    if ((isCameraEnabled && !hasCameraPerm) || !hasMicPerm) {
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
                            Text(
                                if (permScope.permanentlyDenied) "Open App Settings"
                                else if (isCameraEnabled) "Enable Camera & Mic"
                                else "Allow Microphone to Talk"
                            )
                        }
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
                color = Color.Black.copy(alpha = 0.68f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(
                        text = "Room ${session.roomCode}",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (rtcState == RtcConnectionState.CONNECTED || session.status == "active") {
                            EmeraldMint
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.testTag("active_call_status_label")
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
                    containerColor = Color.Black.copy(alpha = 0.68f),
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
            color = Color.Black.copy(alpha = 0.80f),
            shape = RoundedCornerShape(24.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = {
                        val nextMuted = !isMuted
                        isMuted = nextMuted
                        rtcClient.setMuted(nextMuted)
                    },
                    modifier = Modifier.size(48.dp).testTag("toggle_mute_button")
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                        contentDescription = if (isMuted) "Unmute microphone" else "Mute microphone",
                        tint = if (isMuted) MaterialTheme.colorScheme.error else Color.White
                    )
                }

                FilledTonalIconButton(
                    onClick = {
                        val nextEnabled = !isCameraEnabled
                        rtcClient.setCameraEnabled(nextEnabled)
                        onToggleCamera()
                    },
                    modifier = Modifier.size(48.dp).testTag("toggle_camera_button")
                ) {
                    Icon(
                        imageVector = if (isCameraEnabled) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                        contentDescription = if (isCameraEnabled) "Turn camera off" else "Turn camera on"
                    )
                }

                FilledTonalIconButton(
                    onClick = {
                        val nextSpeaker = !isSpeakerOn
                        isSpeakerOn = nextSpeaker
                        rtcClient.setSpeakerphone(nextSpeaker)
                    },
                    modifier = Modifier.size(48.dp).testTag("toggle_speaker_button")
                ) {
                    Icon(
                        imageVector = if (isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = if (isSpeakerOn) "Switch to earpiece" else "Turn speakerphone on",
                        tint = if (isSpeakerOn) EmeraldMint else Color.White
                    )
                }

                if (isCameraEnabled && hasCameraPerm) {
                    FilledTonalIconButton(
                        onClick = {
                            isFrontCamera = !isFrontCamera
                            rtcClient.switchCamera()
                        },
                        modifier = Modifier.size(48.dp).testTag("flip_camera_button")
                    ) {
                        Icon(Icons.Filled.Cameraswitch, contentDescription = "Switch front or back camera")
                    }
                }

                Button(
                    onClick = { onEndCall(elapsedSec) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
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
private fun WebRtcVideoSurface(
    videoTrack: VideoTrack,
    eglContext: EglBase.Context,
    mirror: Boolean,
    isOverlay: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val renderer = remember(eglContext, isOverlay) {
        SurfaceViewRenderer(context).apply {
            init(eglContext, null)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            setEnableHardwareScaler(true)
            setZOrderMediaOverlay(isOverlay)
        }
    }

    DisposableEffect(renderer, videoTrack, mirror) {
        renderer.setMirror(mirror)
        videoTrack.addSink(renderer)
        onDispose {
            runCatching { videoTrack.removeSink(renderer) }
        }
    }

    DisposableEffect(renderer) {
        onDispose {
            runCatching { renderer.release() }
        }
    }

    AndroidView(
        factory = { renderer },
        modifier = modifier
    )
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
