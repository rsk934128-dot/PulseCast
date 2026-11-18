package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.authStateFlow
import com.example.calls.CallActionBus
import com.example.calls.CallActionReceiver
import com.example.calls.CallNotificationAction
import com.example.calls.CallNotificationManager
import com.example.calls.CallRingtoneController
import com.example.calls.IncomingCallOverlay
import com.example.data.PulseCastRepository
import com.example.media.FirebaseStorageVideoUploader
import com.example.ui.PulseCastViewModel
import com.example.ui.UiState
import com.example.ui.components.CreatorProfileDialog
import com.example.ui.screens.ActiveCallStage
import com.example.ui.screens.AdminPanelScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CallsScreen
import com.example.ui.screens.MessagingScreen
import com.example.ui.screens.StudioProfileScreen
import com.example.ui.screens.VideoFeedScreen
import com.example.ui.theme.PulseCastTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        dispatchCallIntent(intent)
        setContent {
            PulseCastTheme {
                AppNavigation()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dispatchCallIntent(intent)
    }

    private fun dispatchCallIntent(intent: Intent?) {
        intent ?: return
        val roomCode = intent.getStringExtra(CallActionReceiver.EXTRA_ROOM_CODE).orEmpty().trim()
        val callAction = intent.getStringExtra(CallActionReceiver.EXTRA_CALL_ACTION) ?: intent.action
        if (roomCode.isNotEmpty() && callAction == CallActionReceiver.ACTION_ANSWER_CALL) {
            CallActionBus.emit(CallNotificationAction.Answer(roomCode))
        }
    }
}

private enum class PulseTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
) {
    FEED("Feed", Icons.Filled.PlayCircle, Icons.Outlined.PlayCircle, "nav_feed"),
    CHATS("Chats", Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Outlined.Chat, "nav_chats"),
    CALLS("Calls", Icons.Filled.Call, Icons.Outlined.Call, "nav_calls"),
    STUDIO("Studio", Icons.Filled.Person, Icons.Outlined.Person, "nav_studio"),
    ADMIN("Admin", Icons.Filled.AdminPanelSettings, Icons.Outlined.AdminPanelSettings, "nav_admin")
}

@Composable
fun AppNavigation() {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    var authRefreshTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, auth) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && auth.currentUser != null) {
                authRefreshTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val user = remember(currentUser, authRefreshTick) { currentUser ?: auth.currentUser }

    if (user == null) {
        AuthScreen(onAuthSuccess = { authRefreshTick++ })
    } else {
        key(user.uid) {
            PulseCastMainShell(
                currentUserId = user.uid,
                displayName = user.displayName ?: "PulseCast Creator",
                email = user.email ?: "",
                isEmailVerified = user.isEmailVerified,
                photoUrl = user.photoUrl?.toString()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PulseCastMainShell(
    currentUserId: String,
    displayName: String,
    email: String,
    isEmailVerified: Boolean,
    photoUrl: String?
) {
    val viewModel: PulseCastViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                PulseCastViewModel(
                    repository = PulseCastRepository(
                        db = db,
                        videoUploader = FirebaseStorageVideoUploader(appContext = app.applicationContext)
                    ),
                    currentUserId = currentUserId,
                    currentUserName = displayName,
                    currentUserEmail = email,
                    currentUserEmailVerified = isEmailVerified,
                    currentUserPhotoUrl = photoUrl
                )
            }
        }
    )

    val context = LocalContext.current
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val incomingCall by viewModel.incomingCallSession.collectAsStateWithLifecycle()
    val activeCall by viewModel.activeCallSession.collectAsStateWithLifecycle()
    val activeCallSignals by viewModel.activeCallSignals.collectAsStateWithLifecycle()
    val userAccount by viewModel.userAccountState.collectAsStateWithLifecycle()
    val creatorsState by viewModel.creatorsState.collectAsStateWithLifecycle()
    val publicVideosState by viewModel.publicVideosState.collectAsStateWithLifecycle()
    val inspectingCreator by viewModel.inspectingCreator.collectAsStateWithLifecycle()
    val pendingNotificationAction by CallActionBus.pendingAction.collectAsStateWithLifecycle()

    val creatorsById = remember(creatorsState) {
        (creatorsState as? UiState.Success)?.data?.associateBy { it.userId }.orEmpty()
    }

    val visibleTabs = remember(isAdmin) {
        if (isAdmin) PulseTab.entries else PulseTab.entries.filter { it != PulseTab.ADMIN }
    }

    var currentTab by rememberSaveable { mutableStateOf(PulseTab.FEED) }
    val snackbarHostState = remember { SnackbarHostState() }

    val callNotificationManager = remember(context) { CallNotificationManager(context) }
    val callRingtoneController = remember(context) { CallRingtoneController(context) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        callNotificationManager.ensureChannels()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    DisposableEffect(callRingtoneController, callNotificationManager) {
        onDispose {
            callRingtoneController.release()
            callNotificationManager.dismissIncomingCallNotification()
            callNotificationManager.dismissOngoingCallNotification()
        }
    }

    LaunchedEffect(pendingNotificationAction) {
        val action = pendingNotificationAction ?: return@LaunchedEffect
        viewModel.handleCallNotificationAction(action)
        if (action is CallNotificationAction.Answer) {
            currentTab = PulseTab.CALLS
        }
        CallActionBus.clear(action)
    }

    LaunchedEffect(incomingCall?.id, incomingCall?.status, userAccount?.pushCallAlerts) {
        val call = incomingCall
        if (call != null && userAccount?.pushCallAlerts != false) {
            callNotificationManager.showIncomingCallNotification(call)
            callRingtoneController.startIncomingRingtone(call.roomCode.ifBlank { call.id })
        } else {
            callRingtoneController.stopIncomingRingtone()
            callNotificationManager.dismissIncomingCallNotification()
        }
    }

    LaunchedEffect(activeCall?.roomCode, activeCall?.status, activeCall?.hostId) {
        val call = activeCall
        if (call != null && call.status == "ringing" && call.hostId == currentUserId) {
            callRingtoneController.startOutgoingRingback(call.roomCode)
        } else {
            callRingtoneController.stopOutgoingRingback()
        }
        if (call != null && call.status == "active") {
            callNotificationManager.showOngoingCallNotification(call)
        } else {
            callNotificationManager.dismissOngoingCallNotification()
        }
    }

    LaunchedEffect(isAdmin) {
        if (!isAdmin && currentTab == PulseTab.ADMIN) {
            currentTab = PulseTab.FEED
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.snackbarEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val shellLifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(shellLifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.setUserOnline(true)
                Lifecycle.Event.ON_STOP -> viewModel.setUserOnline(false)
                else -> Unit
            }
        }
        shellLifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            shellLifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(shellLifecycleOwner, viewModel) {
        shellLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(60_000L)
                viewModel.recordRecentActivity(force = true)
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = when (currentTab) {
                                    PulseTab.FEED -> "PulseCast Feed"
                                    PulseTab.CHATS -> "Messages"
                                    PulseTab.CALLS -> "Audio & Video Calls"
                                    PulseTab.STUDIO -> "Creator Studio"
                                    PulseTab.ADMIN -> "Admin Console"
                                },
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                    )
                },
                bottomBar = {
                    if (!useRail && activeCall == null) {
                        NavigationBar {
                            visibleTabs.forEach { tab ->
                                val selected = currentTab == tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag(tab.tag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useRail && activeCall == null) {
                        NavigationRail {
                            visibleTabs.forEach { tab ->
                                val selected = currentTab == tab
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag(tab.tag)
                                )
                            }
                        }
                    }

                    when (currentTab) {
                        PulseTab.FEED -> VideoFeedScreen(viewModel = viewModel, modifier = Modifier.weight(1f))
                        PulseTab.CHATS -> MessagingScreen(
                            viewModel = viewModel,
                            onNavigateToCall = { currentTab = PulseTab.CALLS },
                            modifier = Modifier.weight(1f)
                        )
                        PulseTab.CALLS -> CallsScreen(
                            viewModel = viewModel,
                            modifier = Modifier.weight(1f),
                            renderActiveCallStage = false
                        )
                        PulseTab.STUDIO -> StudioProfileScreen(
                            viewModel = viewModel,
                            onSignedOut = {},
                            modifier = Modifier.weight(1f)
                        )
                        PulseTab.ADMIN -> if (isAdmin) {
                            AdminPanelScreen(viewModel = viewModel, modifier = Modifier.weight(1f))
                        } else {
                            VideoFeedScreen(viewModel = viewModel, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            if (activeCall != null) {
                val liveSession = activeCall!!
                val peerCreator = remember(liveSession, creatorsById, currentUserId) {
                    val peerId = liveSession.participantIds.firstOrNull { it != currentUserId }
                        ?: liveSession.hostId
                    creatorsById[peerId]
                }
                ActiveCallStage(
                    session = liveSession,
                    currentUserId = currentUserId,
                    signals = activeCallSignals,
                    onSendSignal = { recipientId, type, sdp, sdpMid, sdpMLineIndex, candidate ->
                        viewModel.sendCallSignal(
                            callId = liveSession.roomCode,
                            recipientId = recipientId,
                            type = type,
                            sdp = sdp,
                            sdpMid = sdpMid,
                            sdpMLineIndex = sdpMLineIndex,
                            candidate = candidate
                        )
                    },
                    onEndCall = { duration -> viewModel.endActiveCall(duration) },
                    modifier = Modifier.fillMaxSize(),
                    peerPhotoUrl = peerCreator?.photoUrl,
                    peerAvatarBase64 = peerCreator?.avatarBase64
                )
            } else if (incomingCall != null) {
                val ringingCall = incomingCall!!
                val callerCreator = creatorsById[ringingCall.hostId]
                IncomingCallOverlay(
                    session = ringingCall,
                    onAnswer = {
                        viewModel.acceptIncomingCall(ringingCall)
                        currentTab = PulseTab.CALLS
                    },
                    onDecline = {
                        viewModel.declineIncomingCall(ringingCall)
                    },
                    modifier = Modifier.fillMaxSize(),
                    callerPhotoUrl = callerCreator?.photoUrl,
                    callerAvatarBase64 = callerCreator?.avatarBase64
                )
            } else if (inspectingCreator != null) {
                val targetCreator = inspectingCreator!!
                val creatorVideos = remember(publicVideosState, targetCreator.userId) {
                    (publicVideosState as? UiState.Success)?.data
                        ?.filter { it.authorId == targetCreator.userId }
                        .orEmpty()
                }
                CreatorProfileDialog(
                    creator = targetCreator,
                    isCurrentUser = targetCreator.userId == currentUserId,
                    creatorVideos = creatorVideos,
                    onDismiss = { viewModel.closeCreatorProfile() },
                    onMessageClick = { peer ->
                        viewModel.closeCreatorProfile()
                        viewModel.startOrOpenDirectChat(peer)
                        currentTab = PulseTab.CHATS
                    },
                    onAudioCallClick = { peer ->
                        viewModel.closeCreatorProfile()
                        viewModel.startCall("audio", targetCreator = peer)
                        currentTab = PulseTab.CALLS
                    },
                    onVideoCallClick = { peer ->
                        viewModel.closeCreatorProfile()
                        viewModel.startCall("video", targetCreator = peer)
                        currentTab = PulseTab.CALLS
                    },
                    onVideoClick = { video ->
                        viewModel.closeCreatorProfile()
                        viewModel.selectVideo(video.id)
                        currentTab = PulseTab.FEED
                    },
                    onEditOwnProfile = {
                        viewModel.closeCreatorProfile()
                        currentTab = PulseTab.STUDIO
                    }
                )
            }
        }
    }
}
