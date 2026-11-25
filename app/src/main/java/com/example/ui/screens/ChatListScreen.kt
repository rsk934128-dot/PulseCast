package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.example.data.ChatThread
import com.example.data.CreatorProfile
import com.example.data.isRecentlyOnline
import com.example.ui.PulseCastViewModel
import com.example.ui.UiState
import com.example.ui.adapters.ChatListRecyclerAdapter
import com.example.ui.adapters.ChatListRowItem
import com.example.ui.adapters.CreatorDirectoryRecyclerAdapter
import com.example.ui.adapters.CreatorDirectoryUiItem
import com.example.ui.adapters.PeopleStripUiItem
import com.example.ui.adapters.SpacingItemDecoration
import com.example.ui.adapters.configureOptimizedRecyclerView
import com.example.ui.adapters.rememberRecyclerThemePalette
import com.google.firebase.Timestamp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale

enum class ChatListFilter(val label: String, val tag: String) {
    ALL("All", "chat_filter_all"),
    DIRECT("Direct", "chat_filter_direct"),
    GROUPS("Groups", "chat_filter_groups"),
    PEOPLE("People", "chat_filter_people")
}

@Composable
fun ChatListScreen(
    viewModel: PulseCastViewModel,
    onSelectChat: (ChatThread) -> Unit,
    onNewChatClick: () -> Unit,
    onStartDirectChat: (CreatorProfile) -> Unit = { viewModel.startOrOpenDirectChat(it) },
    onCallCreator: (CreatorProfile, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    onViewProfile: ((CreatorProfile) -> Unit)? = { viewModel.openCreatorProfile(it) }
) {
    val chatsState by viewModel.chatsState.collectAsStateWithLifecycle()
    val creatorsState by viewModel.creatorsState.collectAsStateWithLifecycle()
    ChatListScreen(
        chatsState = chatsState,
        currentUserId = viewModel.currentUserId,
        onSelectChat = onSelectChat,
        onNewChatClick = onNewChatClick,
        creatorsState = creatorsState,
        onStartDirectChat = onStartDirectChat,
        onCallCreator = onCallCreator,
        modifier = modifier,
        onViewProfile = onViewProfile
    )
}

@Composable
fun ChatListScreen(
    chatsState: UiState<List<ChatThread>>,
    currentUserId: String,
    onSelectChat: (ChatThread) -> Unit,
    onNewChatClick: () -> Unit,
    creatorsState: UiState<List<CreatorProfile>> = UiState.Success(emptyList()),
    onStartDirectChat: (CreatorProfile) -> Unit = {},
    onCallCreator: (CreatorProfile, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    onViewProfile: ((CreatorProfile) -> Unit)? = null
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var activeFilter by rememberSaveable { mutableStateOf(ChatListFilter.ALL) }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            nowMillis = System.currentTimeMillis()
        }
    }
    val allCreators = remember(creatorsState) {
        (creatorsState as? UiState.Success)?.data.orEmpty()
    }
    val creatorsById = remember(allCreators) {
        allCreators.associateBy { it.userId }
    }
    val otherCreators = remember(allCreators, currentUserId) {
        allCreators.filter { it.userId != currentUserId }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewChatClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("new_chat_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.AddComment,
                    contentDescription = "Start new conversation"
                )
            }
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
                    .widthIn(max = 680.dp)
                    .fillMaxSize()
            ) {
                when (chatsState) {
                    UiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.testTag("chat_list_loading"))
                        }
                    }

                    is UiState.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("chat_list_error")
                            ) {
                                Row(
                                    modifier = Modifier.padding(18.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Text(
                                        text = chatsState.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }
                    }

                    is UiState.Success -> {
                        val allThreads = chatsState.data
                        if (allThreads.isEmpty() && otherCreators.isEmpty()) {
                            EmptyConversationsState(onNewChatClick = onNewChatClick)
                        } else {
                            ChatSearchAndFilterBar(
                                searchQuery = searchQuery,
                                onSearchQueryChange = { searchQuery = it },
                                activeFilter = activeFilter,
                                onFilterChange = { activeFilter = it }
                            )

                            val normalizedQuery = searchQuery.trim().lowercase()
                            val filteredCreators = remember(otherCreators, normalizedQuery) {
                                if (normalizedQuery.isEmpty()) otherCreators
                                else otherCreators.filter { creator ->
                                    creator.displayName.lowercase().contains(normalizedQuery) ||
                                        creator.handle.lowercase().contains(normalizedQuery) ||
                                        (creator.bio?.lowercase()?.contains(normalizedQuery) == true)
                                }
                            }

                            val filteredThreads = remember(allThreads, normalizedQuery, activeFilter) {
                                allThreads.filter { thread ->
                                    val matchesFilter = when (activeFilter) {
                                        ChatListFilter.ALL -> true
                                        ChatListFilter.DIRECT -> !thread.isGroup
                                        ChatListFilter.GROUPS -> thread.isGroup
                                        ChatListFilter.PEOPLE -> false
                                    }
                                    val matchesQuery = normalizedQuery.isEmpty() ||
                                        thread.title.lowercase().contains(normalizedQuery) ||
                                        thread.lastMessageText.lowercase().contains(normalizedQuery) ||
                                        (thread.participantNames?.any {
                                            it.lowercase().contains(normalizedQuery)
                                        } == true)
                                    matchesFilter && matchesQuery
                                }
                            }

                            val palette = rememberRecyclerThemePalette()
                            val currentOnSelectChat by rememberUpdatedState(onSelectChat)
                            val currentOnStartDirectChat by rememberUpdatedState(onStartDirectChat)
                            val currentOnCallCreator by rememberUpdatedState(onCallCreator)
                            val currentOnViewProfile by rememberUpdatedState(onViewProfile)

                            if (activeFilter == ChatListFilter.PEOPLE) {
                                if (filteredCreators.isEmpty()) {
                                    FilteredEmptyState(
                                        onClearFilters = {
                                            searchQuery = ""
                                            activeFilter = ChatListFilter.ALL
                                        }
                                    )
                                } else {
                                    val directoryItems = remember(filteredCreators, nowMillis) {
                                        filteredCreators.map { creator ->
                                            CreatorDirectoryUiItem(
                                                creator = creator,
                                                isOnline = creator.isRecentlyOnline(nowMillis)
                                            )
                                        }
                                    }
                                    AndroidView(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .testTag("people_directory_list"),
                                        factory = { ctx ->
                                            RecyclerView(ctx).apply {
                                                tag = "people_directory_list"
                                                clipToPadding = false
                                                configureOptimizedRecyclerView(
                                                    recyclerView = this,
                                                    orientation = RecyclerView.VERTICAL,
                                                    viewCacheSize = 10,
                                                    prefetchCount = 6
                                                )
                                                val density = ctx.resources.displayMetrics.density
                                                addItemDecoration(
                                                    SpacingItemDecoration(
                                                        horizontalEdgePx = (16f * density).toInt(),
                                                        topEdgePx = (4f * density).toInt(),
                                                        bottomEdgePx = (88f * density).toInt(),
                                                        itemGapPx = (10f * density).toInt(),
                                                        orientation = RecyclerView.VERTICAL
                                                    )
                                                )
                                                adapter = CreatorDirectoryRecyclerAdapter(
                                                    palette = palette,
                                                    onViewProfile = { creator ->
                                                        val handler = currentOnViewProfile
                                                        if (handler != null) handler(creator)
                                                        else currentOnStartDirectChat(creator)
                                                    },
                                                    onChat = { currentOnStartDirectChat(it) },
                                                    onAudioCall = { currentOnCallCreator(it, "audio") },
                                                    onVideoCall = { currentOnCallCreator(it, "video") }
                                                )
                                            }
                                        },
                                        update = { rv ->
                                            val adapter = rv.adapter as CreatorDirectoryRecyclerAdapter
                                            adapter.updateEnvironment(
                                                newPalette = palette,
                                                onViewProfile = { creator ->
                                                    val handler = currentOnViewProfile
                                                    if (handler != null) handler(creator)
                                                    else currentOnStartDirectChat(creator)
                                                },
                                                onChat = { currentOnStartDirectChat(it) },
                                                onAudioCall = { currentOnCallCreator(it, "audio") },
                                                onVideoCall = { currentOnCallCreator(it, "video") }
                                            )
                                            adapter.submitList(directoryItems)
                                        }
                                    )
                                }
                            } else if (filteredThreads.isEmpty() && (otherCreators.isEmpty() || activeFilter != ChatListFilter.ALL)) {
                                FilteredEmptyState(
                                    onClearFilters = {
                                        searchQuery = ""
                                        activeFilter = ChatListFilter.ALL
                                    }
                                )
                            } else {
                                val chatRowItems = remember(
                                    activeFilter,
                                    filteredCreators,
                                    filteredThreads,
                                    creatorsById,
                                    currentUserId,
                                    nowMillis
                                ) {
                                    buildList {
                                        if (activeFilter == ChatListFilter.ALL && filteredCreators.isNotEmpty()) {
                                            add(
                                                ChatListRowItem.PeopleStripItem(
                                                    creators = filteredCreators.map { creator ->
                                                        PeopleStripUiItem(
                                                            creator = creator,
                                                            isOnline = creator.isRecentlyOnline(nowMillis)
                                                        )
                                                    }
                                                )
                                            )
                                        }
                                        filteredThreads.forEach { thread ->
                                            val peerProfile = if (thread.isGroup) {
                                                null
                                            } else {
                                                thread.participantIds.firstOrNull { it != currentUserId }
                                                    ?.let { creatorsById[it] }
                                            }
                                            val peerIds = thread.participantIds.filter { it != currentUserId }
                                            val isThreadOnline = if (peerIds.isNotEmpty()) {
                                                peerIds.any { uid -> creatorsById[uid]?.isRecentlyOnline(nowMillis) == true }
                                            } else {
                                                creatorsById[currentUserId]?.isRecentlyOnline(nowMillis) == true
                                            }
                                            val timeLabel = formatChatTimestamp(
                                                timestamp = thread.lastMessageAt ?: thread.createdAt,
                                                nowMillis = nowMillis
                                            )
                                            val previewPrefix = if (
                                                thread.lastMessageSenderId == currentUserId &&
                                                thread.lastMessageText.isNotBlank()
                                            ) {
                                                "You: "
                                            } else {
                                                ""
                                            }
                                            val previewText = "$previewPrefix${thread.lastMessageText.ifBlank { "No messages yet" }}"
                                            add(
                                                ChatListRowItem.ThreadRowItem(
                                                    thread = thread,
                                                    isOnline = isThreadOnline,
                                                    peerPhotoUrl = peerProfile?.photoUrl,
                                                    peerAvatarBase64 = peerProfile?.avatarBase64,
                                                    peerProfile = peerProfile,
                                                    timeLabel = timeLabel,
                                                    previewText = previewText
                                                )
                                            )
                                        }
                                    }
                                }
                                AndroidView(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("chat_list_lazy_column"),
                                    factory = { ctx ->
                                        RecyclerView(ctx).apply {
                                            tag = "chat_list_lazy_column"
                                            clipToPadding = false
                                            configureOptimizedRecyclerView(
                                                recyclerView = this,
                                                orientation = RecyclerView.VERTICAL,
                                                viewCacheSize = 10,
                                                prefetchCount = 6
                                            )
                                            val density = ctx.resources.displayMetrics.density
                                            addItemDecoration(
                                                SpacingItemDecoration(
                                                    horizontalEdgePx = (16f * density).toInt(),
                                                    topEdgePx = (4f * density).toInt(),
                                                    bottomEdgePx = (88f * density).toInt(),
                                                    itemGapPx = (10f * density).toInt(),
                                                    orientation = RecyclerView.VERTICAL
                                                )
                                            )
                                            adapter = ChatListRecyclerAdapter(
                                                palette = palette,
                                                onSelectChat = { currentOnSelectChat(it) },
                                                onViewProfile = { creator ->
                                                    val handler = currentOnViewProfile
                                                    if (handler != null) handler(creator)
                                                    else currentOnStartDirectChat(creator)
                                                },
                                                onChatWithCreator = { currentOnStartDirectChat(it) },
                                                onCallCreator = { creator, type -> currentOnCallCreator(creator, type) }
                                            )
                                        }
                                    },
                                    update = { rv ->
                                        val adapter = rv.adapter as ChatListRecyclerAdapter
                                        adapter.updateEnvironment(
                                            newPalette = palette,
                                            onSelectChat = { currentOnSelectChat(it) },
                                            onViewProfile = { creator ->
                                                val handler = currentOnViewProfile
                                                if (handler != null) handler(creator)
                                                else currentOnStartDirectChat(creator)
                                            },
                                            onChatWithCreator = { currentOnStartDirectChat(it) },
                                            onCallCreator = { creator, type -> currentOnCallCreator(creator, type) }
                                        )
                                        adapter.submitList(chatRowItems)
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

@Composable
private fun ChatSearchAndFilterBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    activeFilter: ChatListFilter,
    onFilterChange: (ChatListFilter) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text(stringResource(R.string.chat_list_search_placeholder)) },
            leadingIcon = {
                Icon(imageVector = Icons.Filled.Search, contentDescription = null)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.chat_list_clear_search)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("chat_search_input")
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChatListFilter.entries.forEach { filter ->
                FilterChip(
                    selected = activeFilter == filter,
                    onClick = { onFilterChange(filter) },
                    label = { Text(filter.label) },
                    modifier = Modifier.testTag(filter.tag)
                )
            }
        }
    }
}


@Composable
private fun EmptyConversationsState(onNewChatClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("chat_list_empty"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Chat,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.chat_list_empty_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.chat_list_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onNewChatClick,
            modifier = Modifier.testTag("empty_new_chat_button")
        ) {
            Text(stringResource(R.string.chat_list_new_conversation))
        }
    }
}

@Composable
private fun FilteredEmptyState(onClearFilters: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("chat_list_filtered_empty"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.chat_list_no_matches),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = onClearFilters) {
            Text(stringResource(R.string.chat_list_clear_filters))
        }
    }
}


internal fun formatChatTimestamp(
    timestamp: Timestamp?,
    nowMillis: Long = System.currentTimeMillis()
): String {
    val ts = timestamp ?: return ""
    val diffSec = ((nowMillis - ts.toDate().time) / 1000L).coerceAtLeast(0L)
    return when {
        diffSec < 60L -> "Just now"
        diffSec < 3600L -> "${diffSec / 60L}m"
        diffSec < 86400L -> "${diffSec / 3600L}h"
        diffSec < 172800L -> "Yesterday"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(ts.toDate())
    }
}
