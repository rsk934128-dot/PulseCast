package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ChatMessage
import com.example.data.ChatThread
import com.example.data.CreatorProfile
import com.example.data.UserAccount
import com.example.data.isRecentlyOnline
import com.example.media.VoiceNoteRecorder
import com.example.media.decodeBase64ToBitmap
import com.example.media.encodeUriImageToBase64
import com.example.media.formatDuration
import com.example.permissions.openPulseCastAppSettings
import com.example.ui.PulseCastViewModel
import com.example.ui.UiState
import com.example.ui.components.UserAvatarWithStatus
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MessagingScreen(
    viewModel: PulseCastViewModel,
    onNavigateToCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val chatsState by viewModel.chatsState.collectAsStateWithLifecycle()
    val creatorsState by viewModel.creatorsState.collectAsStateWithLifecycle()
    val selectedChatId by viewModel.selectedChatId.collectAsStateWithLifecycle()
    val messages by viewModel.selectedChatMessages.collectAsStateWithLifecycle()
    val selectedParticipants by viewModel.selectedChatParticipants.collectAsStateWithLifecycle()
    val currentUserAccount by viewModel.userAccountState.collectAsStateWithLifecycle()

    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            nowMillis = System.currentTimeMillis()
        }
    }

    val participantProfiles = remember(creatorsState, selectedParticipants) {
        buildMap {
            (creatorsState as? UiState.Success)?.data?.forEach { profile ->
                if (profile.userId.isNotBlank()) put(profile.userId, profile)
            }
            putAll(selectedParticipants)
        }
    }

    var showNewChatDialog by remember { mutableStateOf(false) }

    val selectedChat = remember(chatsState, selectedChatId) {
        (chatsState as? UiState.Success)?.data?.firstOrNull { it.id == selectedChatId }
    }

    if (selectedChat != null) {
        ChatConversationScreen(
            chat = selectedChat,
            currentUserId = viewModel.currentUserId,
            messages = messages,
            participantProfiles = participantProfiles,
            currentUserAccount = currentUserAccount,
            nowMillis = nowMillis,
            onBack = { viewModel.selectChat(null) },
            onMessagesSeen = { msgs -> viewModel.markMessagesRead(selectedChat.id, msgs) },
            onSendText = { text -> viewModel.sendMessage(selectedChat, "text", text) },
            onSendImage = { base64, caption ->
                viewModel.sendMessage(selectedChat, "image", caption, imageBase64 = base64)
            },
            onSendVoice = { durationSec ->
                viewModel.sendMessage(selectedChat, "voice", "Voice note", voiceDurationSec = durationSec)
            },
            onStartCall = { callType ->
                viewModel.startCall(callType, linkedChat = selectedChat)
                onNavigateToCall()
            },
            onViewCreatorProfile = { uid, name -> viewModel.openCreatorProfile(uid, name) },
            modifier = modifier
        )
    } else {
        ChatListScreen(
            viewModel = viewModel,
            onSelectChat = { thread -> viewModel.selectChat(thread.id) },
            onNewChatClick = { showNewChatDialog = true },
            onStartDirectChat = { creator -> viewModel.startOrOpenDirectChat(creator) },
            onCallCreator = { creator, callType ->
                viewModel.startCall(callType, targetCreator = creator)
                onNavigateToCall()
            },
            onViewProfile = { creator -> viewModel.openCreatorProfile(creator) },
            modifier = modifier
        )
    }

    if (showNewChatDialog) {
        val creators = (creatorsState as? UiState.Success)?.data
            ?.filter { it.userId != viewModel.currentUserId }
            ?: emptyList()
        NewChatDialog(
            creators = creators,
            nowMillis = nowMillis,
            onDismiss = { showNewChatDialog = false },
            onCreate = { title, selectedCreators ->
                val isGroup = selectedCreators.size > 1 || title.isNotBlank()
                val finalTitle = title.ifBlank {
                    selectedCreators.firstOrNull()?.displayName ?: "Studio Notes"
                }
                viewModel.createChatThread(
                    title = finalTitle,
                    isGroup = isGroup,
                    participantIds = selectedCreators.map { it.userId },
                    participantNames = selectedCreators.map { it.displayName }
                )
                showNewChatDialog = false
            }
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun ChatConversationScreen(
    chat: ChatThread,
    currentUserId: String,
    messages: List<ChatMessage>,
    participantProfiles: Map<String, CreatorProfile>,
    currentUserAccount: UserAccount?,
    nowMillis: Long,
    onBack: () -> Unit,
    onMessagesSeen: (List<ChatMessage>) -> Unit,
    onSendText: (String) -> Unit,
    onSendImage: (String, String) -> Unit,
    onSendVoice: (Long) -> Unit,
    onStartCall: (String) -> Unit,
    onViewCreatorProfile: ((String, String?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isRecordingVoice by remember { mutableStateOf(false) }
    val voiceRecorder = remember(context) { VoiceNoteRecorder(context) }

    DisposableEffect(Unit) {
        onDispose { voiceRecorder.release() }
    }

    LaunchedEffect(messages) {
        onMessagesSeen(messages)
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val base64 = encodeUriImageToBase64(context, uri)
                if (base64 != null) {
                    onSendImage(base64, "Shared photo")
                }
            }
        }
    }

    var requestedVoicePermission by remember { mutableStateOf(false) }
    val micPermissionState = rememberPermissionState(
        permission = Manifest.permission.RECORD_AUDIO
    ) { granted ->
        requestedVoicePermission = true
        if (granted) {
            isRecordingVoice = voiceRecorder.startRecording()
        }
    }

    ChatConversationContent(
        chat = chat,
        currentUserId = currentUserId,
        messages = messages,
        participantProfiles = participantProfiles,
        currentUserAccount = currentUserAccount,
        nowMillis = nowMillis,
        isRecordingVoice = isRecordingVoice,
        onBack = onBack,
        onSendText = onSendText,
        onPickImage = {
            imagePicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        },
        onToggleVoiceRecord = {
            if (isRecordingVoice) {
                val sec = voiceRecorder.stopRecording()
                isRecordingVoice = false
                onSendVoice(sec)
            } else if (micPermissionState.status.isGranted) {
                isRecordingVoice = voiceRecorder.startRecording()
            } else if (requestedVoicePermission && !micPermissionState.status.shouldShowRationale) {
                openPulseCastAppSettings(context)
            } else {
                micPermissionState.launchPermissionRequest()
            }
        },
        onPlayVoice = { voiceRecorder.playLastOrStop {} },
        onStartCall = onStartCall,
        onViewCreatorProfile = onViewCreatorProfile,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatConversationContent(
    chat: ChatThread,
    currentUserId: String,
    messages: List<ChatMessage>,
    participantProfiles: Map<String, CreatorProfile>,
    currentUserAccount: UserAccount? = null,
    nowMillis: Long = System.currentTimeMillis(),
    isRecordingVoice: Boolean = false,
    onBack: () -> Unit,
    onSendText: (String) -> Unit,
    onPickImage: () -> Unit,
    onToggleVoiceRecord: () -> Unit,
    onPlayVoice: () -> Unit,
    onStartCall: (String) -> Unit,
    modifier: Modifier = Modifier,
    onViewCreatorProfile: ((String, String?) -> Unit)? = null
) {
    var messageDraft by remember { mutableStateOf("") }
    val peerIds = remember(chat.participantIds, currentUserId) {
        chat.participantIds.filter { it != currentUserId }
    }
    val selfIsOnline = remember(currentUserAccount, participantProfiles, currentUserId, nowMillis) {
        (currentUserAccount?.isRecentlyOnline(nowMillis) == true) ||
            (participantProfiles[currentUserId]?.isRecentlyOnline(nowMillis) == true)
    }
    val onlinePeerCount = remember(peerIds, participantProfiles, nowMillis) {
        peerIds.count { uid -> participantProfiles[uid]?.isRecentlyOnline(nowMillis) == true }
    }
    val headerIsOnline = if (peerIds.isNotEmpty()) onlinePeerCount > 0 else selfIsOnline
    val headerSubtitle = remember(chat.isGroup, peerIds, onlinePeerCount, headerIsOnline, chat.participantIds.size) {
        when {
            !chat.isGroup && peerIds.isNotEmpty() ->
                if (headerIsOnline) "Online · Encrypted sync" else "Offline · Encrypted sync"
            chat.isGroup ->
                "$onlinePeerCount online · ${chat.participantIds.size} participant(s)"
            else ->
                if (headerIsOnline) "Online · Studio notes" else "Offline · Studio notes"
        }
    }

    val headerPeerProfile = remember(chat.isGroup, peerIds, participantProfiles) {
        if (chat.isGroup) null else peerIds.firstOrNull()?.let { participantProfiles[it] }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    val headerTargetId = if (!chat.isGroup) peerIds.firstOrNull() else null
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = if (headerTargetId != null && onViewCreatorProfile != null) {
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onViewCreatorProfile(headerTargetId, chat.title) }
                                .padding(vertical = 2.dp, horizontal = 2.dp)
                                .testTag("chat_header_profile_button")
                        } else {
                            Modifier
                        }
                    ) {
                        UserAvatarWithStatus(
                            displayName = chat.title,
                            isOnline = headerIsOnline,
                            avatarSize = 38.dp,
                            isGroup = chat.isGroup,
                            photoUrl = headerPeerProfile?.photoUrl,
                            avatarBase64 = headerPeerProfile?.avatarBase64,
                            indicatorTestTag = "chat_header_online_indicator"
                        )
                        Column {
                            Text(chat.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = headerSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (headerIsOnline) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("chat_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to chats")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onStartCall("audio") },
                        modifier = Modifier.testTag("chat_audio_call_button")
                    ) {
                        Icon(Icons.Filled.Call, contentDescription = "Start audio call", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(
                        onClick = { onStartCall("video") },
                        modifier = Modifier.testTag("chat_video_call_button")
                    ) {
                        Icon(Icons.Filled.VideoCall, contentDescription = "Start video call", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    val senderProfile = participantProfiles[msg.senderId]
                    val senderIsOnline = if (msg.senderId == currentUserId) {
                        selfIsOnline
                    } else {
                        senderProfile?.isRecentlyOnline(nowMillis) == true
                    }
                    MessageBubble(
                        message = msg,
                        isMine = msg.senderId == currentUserId,
                        senderIsOnline = senderIsOnline,
                        senderPhotoUrl = senderProfile?.photoUrl,
                        senderAvatarBase64 = senderProfile?.avatarBase64,
                        onAvatarClick = if (msg.senderId != currentUserId && onViewCreatorProfile != null) {
                            { onViewCreatorProfile(msg.senderId, msg.senderName) }
                        } else {
                            null
                        },
                        onPlayVoice = onPlayVoice
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = onPickImage,
                        modifier = Modifier.testTag("chat_pick_image_button")
                    ) {
                        Icon(Icons.Filled.Image, contentDescription = "Send photo")
                    }

                    IconButton(
                        onClick = onToggleVoiceRecord,
                        modifier = Modifier.testTag("chat_voice_button")
                    ) {
                        Icon(
                            imageVector = if (isRecordingVoice) Icons.Filled.Stop else Icons.Filled.Mic,
                            contentDescription = if (isRecordingVoice) "Stop voice note" else "Record voice note",
                            tint = if (isRecordingVoice) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    OutlinedTextField(
                        value = messageDraft,
                        onValueChange = { messageDraft = it },
                        placeholder = {
                            Text(if (isRecordingVoice) "Recording voice note..." else "Message...")
                        },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("message_input")
                    )

                    IconButton(
                        onClick = {
                            if (messageDraft.isNotBlank()) {
                                onSendText(messageDraft)
                                messageDraft = ""
                            }
                        },
                        modifier = Modifier.testTag("send_message_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send message",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    isMine: Boolean,
    senderIsOnline: Boolean,
    senderPhotoUrl: String? = null,
    senderAvatarBase64: String? = null,
    onAvatarClick: (() -> Unit)? = null,
    onPlayVoice: () -> Unit
) {
    if (message.messageType == "call_event") {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(50)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.VideoCall, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(message.text, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        return
    }

    val bubbleColor = if (isMine) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isMine) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isMine) {
            UserAvatarWithStatus(
                displayName = message.senderName,
                isOnline = senderIsOnline,
                avatarSize = 32.dp,
                photoUrl = senderPhotoUrl,
                avatarBase64 = senderAvatarBase64,
                onClick = onAvatarClick,
                indicatorTestTag = "message_avatar_online_indicator_${message.id}"
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isMine) 18.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 18.dp
            ),
            modifier = Modifier.widthIn(max = 276.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (!isMine) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                if (message.messageType == "image") {
                    val bmp = remember(message.imageBase64) { decodeBase64ToBitmap(message.imageBase64) }
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Shared photo",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
                if (message.messageType == "voice") {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onPlayVoice)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play voice note", tint = contentColor)
                        Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = contentColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatDuration(message.voiceDurationSec ?: 1L),
                            style = MaterialTheme.typography.labelMedium,
                            color = contentColor
                        )
                    }
                } else if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor
                    )
                }
                if (isMine) {
                    Row(
                        modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DoneAll,
                            contentDescription = if (message.isRead) "Read" else "Sent",
                            tint = if (message.isRead) MaterialTheme.colorScheme.primary else contentColor.copy(alpha = 0.6f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NewChatDialog(
    creators: List<CreatorProfile>,
    nowMillis: Long,
    onDismiss: () -> Unit,
    onCreate: (title: String, selectedCreators: List<CreatorProfile>) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateListOf<String>() }
    val filteredCreators = remember(creators, search) {
        val q = search.trim().lowercase()
        if (q.isEmpty()) creators
        else creators.filter {
            it.displayName.lowercase().contains(q) || it.handle.lowercase().contains(q)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Conversation") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Room / Thread Title") },
                    placeholder = { Text("e.g., Production Crew") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_chat_title_input")
                )
                if (creators.isNotEmpty()) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        label = { Text("Find Account / Creator") },
                        placeholder = { Text("Search name or @handle") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("new_chat_people_search")
                    )
                    Text("Accounts (${filteredCreators.size})", style = MaterialTheme.typography.labelLarge)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(filteredCreators, key = { it.userId }) { creator ->
                            val checked = creator.userId in selectedIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (checked) selectedIds.remove(creator.userId)
                                        else selectedIds.add(creator.userId)
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        if (it) selectedIds.add(creator.userId)
                                        else selectedIds.remove(creator.userId)
                                    }
                                )
                                UserAvatarWithStatus(
                                    displayName = creator.displayName,
                                    isOnline = creator.isRecentlyOnline(nowMillis),
                                    avatarSize = 30.dp,
                                    photoUrl = creator.photoUrl,
                                    avatarBase64 = creator.avatarBase64,
                                    indicatorTestTag = "new_chat_avatar_online_${creator.userId}"
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("${creator.displayName} (@${creator.handle})", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                } else {
                    Text(
                        text = "No other accounts signed in yet — create a Studio Notes room or wait for another account to sign in.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val chosen = creators.filter { it.userId in selectedIds }
                    onCreate(title, chosen)
                },
                modifier = Modifier.testTag("create_chat_confirm_button")
            ) {
                Text("Start Chat")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
