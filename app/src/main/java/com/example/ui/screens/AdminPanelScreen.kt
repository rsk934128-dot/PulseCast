package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AdminRecord
import com.example.data.CallSession
import com.example.data.CreatorProfile
import com.example.data.PublishedVideo
import com.example.data.UserAccount
import com.example.ui.PulseCastViewModel
import com.example.ui.UiState

enum class AdminSection(val label: String, val tag: String) {
    VIDEOS("Videos", "admin_tab_videos"),
    CREATORS("Creators", "admin_tab_creators"),
    CALLS("Calls", "admin_tab_calls"),
    ACCESS("Users & Admins", "admin_tab_access")
}

@Composable
fun AdminPanelScreen(
    viewModel: PulseCastViewModel,
    modifier: Modifier = Modifier
) {
    val videosState by viewModel.adminVideosState.collectAsStateWithLifecycle()
    val creatorsState by viewModel.adminCreatorsState.collectAsStateWithLifecycle()
    val callsState by viewModel.adminCallsState.collectAsStateWithLifecycle()
    val usersState by viewModel.adminUsersState.collectAsStateWithLifecycle()
    val adminsState by viewModel.adminsListState.collectAsStateWithLifecycle()

    AdminPanelContent(
        currentUserId = viewModel.currentUserId,
        currentUserEmail = viewModel.currentUserEmail,
        videosState = videosState,
        creatorsState = creatorsState,
        callsState = callsState,
        usersState = usersState,
        adminsState = adminsState,
        onToggleVideoVisibility = viewModel::adminToggleVideoVisibility,
        onDeleteVideo = { viewModel.adminDeleteVideo(it.id) },
        onSetCreatorModeration = viewModel::adminSetCreatorModeration,
        onForceEndCall = viewModel::adminForceEndCall,
        onDeleteCall = { viewModel.adminDeleteCallSession(it.id) },
        onGrantAdminRole = viewModel::adminGrantRole,
        onRevokeAdminRole = viewModel::adminRevokeRole,
        modifier = modifier
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminPanelContent(
    currentUserId: String,
    currentUserEmail: String,
    videosState: UiState<List<PublishedVideo>>,
    creatorsState: UiState<List<CreatorProfile>>,
    callsState: UiState<List<CallSession>>,
    usersState: UiState<List<UserAccount>>,
    adminsState: UiState<List<AdminRecord>>,
    onToggleVideoVisibility: (PublishedVideo) -> Unit,
    onDeleteVideo: (PublishedVideo) -> Unit,
    onSetCreatorModeration: (creatorId: String, isDiscoverable: Boolean, status: String) -> Unit,
    onForceEndCall: (CallSession) -> Unit,
    onDeleteCall: (CallSession) -> Unit,
    onGrantAdminRole: (uid: String, displayName: String, email: String, role: String) -> Unit,
    onRevokeAdminRole: (uid: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSection by rememberSaveable { mutableStateOf(AdminSection.VIDEOS) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var pendingDeleteVideo by remember { mutableStateOf<PublishedVideo?>(null) }
    var grantTargetUser by remember { mutableStateOf<UserAccount?>(null) }
    var showGrantDialog by remember { mutableStateOf(false) }

    val videos = (videosState as? UiState.Success)?.data ?: emptyList()
    val creators = (creatorsState as? UiState.Success)?.data ?: emptyList()
    val calls = (callsState as? UiState.Success)?.data ?: emptyList()
    val users = (usersState as? UiState.Success)?.data ?: emptyList()
    val admins = (adminsState as? UiState.Success)?.data ?: emptyList()

    val q = searchQuery.trim().lowercase()
    val filteredVideos = remember(videos, q) {
        if (q.isEmpty()) videos
        else videos.filter {
            it.title.lowercase().contains(q) ||
                it.authorName.lowercase().contains(q) ||
                it.category.lowercase().contains(q)
        }
    }
    val filteredCreators = remember(creators, q) {
        if (q.isEmpty()) creators
        else creators.filter {
            it.displayName.lowercase().contains(q) ||
                it.handle.lowercase().contains(q) ||
                it.userId.lowercase().contains(q)
        }
    }
    val filteredCalls = remember(calls, q) {
        if (q.isEmpty()) calls
        else calls.filter {
            it.roomCode.lowercase().contains(q) ||
                it.hostName.lowercase().contains(q) ||
                it.status.lowercase().contains(q)
        }
    }
    val filteredUsers = remember(users, q) {
        if (q.isEmpty()) users
        else users.filter {
            it.displayName.lowercase().contains(q) ||
                it.email.lowercase().contains(q) ||
                it.userId.lowercase().contains(q)
        }
    }

    val activeCallCount = remember(calls) { calls.count { !it.status.equals("ended", ignoreCase = true) } }
    val adminUidSet = remember(admins) { admins.map { it.userId }.toSet() }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .testTag("admin_lazy_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                AdminHeroBanner(
                    adminEmail = currentUserEmail,
                    videoCount = videos.size,
                    creatorCount = creators.size,
                    activeCallCount = activeCallCount,
                    adminCount = maxOf(1, admins.size)
                )
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search videos, creators, room codes, or users…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_search_input")
                )
            }

            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AdminSection.entries.forEach { section ->
                        FilterChip(
                            selected = selectedSection == section,
                            onClick = { selectedSection = section },
                            label = { Text(section.label) },
                            modifier = Modifier.testTag(section.tag)
                        )
                    }
                }
            }

            when (selectedSection) {
                AdminSection.VIDEOS -> {
                    when (videosState) {
                        UiState.Loading -> item { AdminLoadingBox() }
                        is UiState.Error -> item { AdminErrorCard(videosState.message) }
                        is UiState.Success -> {
                            if (filteredVideos.isEmpty()) {
                                item { AdminEmptyCard("No videos match your filter.") }
                            } else {
                                items(filteredVideos, key = { it.id }) { video ->
                                    AdminVideoModerationCard(
                                        video = video,
                                        onToggleVisibility = { onToggleVideoVisibility(video) },
                                        onDeleteClick = { pendingDeleteVideo = video }
                                    )
                                }
                            }
                        }
                    }
                }

                AdminSection.CREATORS -> {
                    when (creatorsState) {
                        UiState.Loading -> item { AdminLoadingBox() }
                        is UiState.Error -> item { AdminErrorCard(creatorsState.message) }
                        is UiState.Success -> {
                            if (filteredCreators.isEmpty()) {
                                item { AdminEmptyCard("No creators found.") }
                            } else {
                                items(filteredCreators, key = { it.userId }) { creator ->
                                    AdminCreatorModerationCard(
                                        creator = creator,
                                        onToggleDiscoverable = { visible ->
                                            onSetCreatorModeration(
                                                creator.userId,
                                                visible,
                                                creator.status ?: "online"
                                            )
                                        },
                                        onChangeStatus = { newStatus ->
                                            onSetCreatorModeration(
                                                creator.userId,
                                                creator.isDiscoverable,
                                                newStatus
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                AdminSection.CALLS -> {
                    when (callsState) {
                        UiState.Loading -> item { AdminLoadingBox() }
                        is UiState.Error -> item { AdminErrorCard(callsState.message) }
                        is UiState.Success -> {
                            if (filteredCalls.isEmpty()) {
                                item { AdminEmptyCard("No call sessions recorded.") }
                            } else {
                                items(filteredCalls, key = { it.id }) { session ->
                                    AdminCallSessionCard(
                                        session = session,
                                        onForceEnd = { onForceEndCall(session) },
                                        onDeleteLog = { onDeleteCall(session) }
                                    )
                                }
                            }
                        }
                    }
                }

                AdminSection.ACCESS -> {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Assigned Admins (${admins.size})",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Button(
                                onClick = {
                                    grantTargetUser = null
                                    showGrantDialog = true
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("admin_add_role_button")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Admin")
                            }
                        }
                    }

                    when (adminsState) {
                        UiState.Loading -> item { AdminLoadingBox() }
                        is UiState.Error -> item { AdminErrorCard(adminsState.message) }
                        is UiState.Success -> {
                            if (admins.isEmpty()) {
                                item { AdminEmptyCard("Bootstrapped super-admin active. Grant moderator roles below.") }
                            } else {
                                items(admins, key = { "admin_${it.userId}" }) { record ->
                                    AdminRoleCard(
                                        record = record,
                                        isSelf = record.userId == currentUserId,
                                        onRevoke = { onRevokeAdminRole(record.userId) }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Registered Users (${filteredUsers.size})",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    when (usersState) {
                        UiState.Loading -> item { AdminLoadingBox() }
                        is UiState.Error -> item { AdminErrorCard(usersState.message) }
                        is UiState.Success -> {
                            if (filteredUsers.isEmpty()) {
                                item { AdminEmptyCard("No registered user accounts match.") }
                            } else {
                                items(filteredUsers, key = { "user_${it.userId}" }) { account ->
                                    RegisteredUserAdminCard(
                                        user = account,
                                        isAlreadyAdmin = account.userId in adminUidSet,
                                        onPromoteClick = {
                                            grantTargetUser = account
                                            showGrantDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDeleteVideo?.let { target ->
        DeleteVideoConfirmDialog(
            videoTitle = target.title,
            onConfirm = {
                pendingDeleteVideo = null
                onDeleteVideo(target)
            },
            onDismiss = { pendingDeleteVideo = null }
        )
    }

    if (showGrantDialog) {
        GrantAdminRoleDialog(
            initialUid = grantTargetUser?.userId ?: "",
            initialName = grantTargetUser?.displayName ?: "",
            initialEmail = grantTargetUser?.email ?: "",
            onConfirm = { uid, name, email, role ->
                showGrantDialog = false
                grantTargetUser = null
                onGrantAdminRole(uid, name, email, role)
            },
            onDismiss = {
                showGrantDialog = false
                grantTargetUser = null
            }
        )
    }
}

@Composable
private fun AdminLoadingBox() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun AdminErrorCard(message: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun AdminEmptyCard(message: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(20.dp)
        )
    }
}
