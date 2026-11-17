package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.ChatThread
import com.example.data.CreatorProfile
import com.example.ui.PulseCastViewModel
import com.example.ui.UiState
import com.google.firebase.Timestamp
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
    modifier: Modifier = Modifier
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
        modifier = modifier
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
    modifier: Modifier = Modifier
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var activeFilter by rememberSaveable { mutableStateOf(ChatListFilter.ALL) }
    val otherCreators = remember(creatorsState, currentUserId) {
        (creatorsState as? UiState.Success)?.data
            ?.filter { it.userId != currentUserId }
            .orEmpty()
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

                            if (activeFilter == ChatListFilter.PEOPLE) {
                                if (filteredCreators.isEmpty()) {
                                    FilteredEmptyState(
                                        onClearFilters = {
                                            searchQuery = ""
                                            activeFilter = ChatListFilter.ALL
                                        }
                                    )
                                } else {
                                    LazyColumn(
                                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .testTag("people_directory_list")
                                    ) {
                                        items(filteredCreators, key = { it.userId }) { creator ->
                                            CreatorDirectoryRow(
                                                creator = creator,
                                                onChat = { onStartDirectChat(creator) },
                                                onAudioCall = { onCallCreator(creator, "audio") },
                                                onVideoCall = { onCallCreator(creator, "video") }
                                            )
                                        }
                                    }
                                }
                            } else if (filteredThreads.isEmpty() && (otherCreators.isEmpty() || activeFilter != ChatListFilter.ALL)) {
                                FilteredEmptyState(
                                    onClearFilters = {
                                        searchQuery = ""
                                        activeFilter = ChatListFilter.ALL
                                    }
                                )
                            } else {
                                LazyColumn(
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("chat_list_lazy_column")
                                ) {
                                    if (activeFilter == ChatListFilter.ALL && filteredCreators.isNotEmpty()) {
                                        item(key = "people_strip") {
                                            PeopleStripSection(
                                                creators = filteredCreators,
                                                onChat = onStartDirectChat,
                                                onCall = onCallCreator
                                            )
                                        }
                                    }
                                    items(filteredThreads, key = { it.id }) { thread ->
                                        ChatConversationRow(
                                            thread = thread,
                                            currentUserId = currentUserId,
                                            onClick = { onSelectChat(thread) }
                                        )
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
private fun ChatConversationRow(
    thread: ChatThread,
    currentUserId: String,
    onClick: () -> Unit
) {
    val timeLabel = remember(thread.lastMessageAt, thread.createdAt) {
        formatChatTimestamp(thread.lastMessageAt ?: thread.createdAt)
    }
    val previewPrefix = if (thread.lastMessageSenderId == currentUserId && thread.lastMessageText.isNotBlank()) {
        "You: "
    } else {
        ""
    }
    val previewText = "$previewPrefix${thread.lastMessageText.ifBlank { "No messages yet" }}"
    val previewIcon = remember(thread.lastMessageText) {
        when {
            thread.lastMessageText.startsWith("Photo:") -> Icons.Filled.Image
            thread.lastMessageText.startsWith("Voice note") -> Icons.Filled.Mic
            thread.lastMessageText.startsWith("Started ") -> Icons.Filled.VideoCall
            else -> null
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("chat_thread_${thread.id}"),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.68f),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (thread.isGroup) {
                    Icon(
                        imageVector = Icons.Filled.Group,
                        contentDescription = thread.title,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    Text(
                        text = thread.title.take(1).uppercase().ifBlank { "C" },
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = thread.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (timeLabel.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = timeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (previewIcon != null) {
                        Icon(
                            imageVector = previewIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = previewText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = if (thread.isGroup) "Group · ${thread.participantIds.size}" else "Direct · ${thread.participantIds.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    val peersLabel = thread.participantNames
                        ?.filter { it.isNotBlank() }
                        ?.joinToString(", ")
                    if (!peersLabel.isNullOrBlank()) {
                        Text(
                            text = peersLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun PeopleStripSection(
    creators: List<CreatorProfile>,
    onChat: (CreatorProfile) -> Unit,
    onCall: (CreatorProfile, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .testTag("people_strip_section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "People on PulseCast (${creators.size})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Tap to chat or call",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            items(creators, key = { it.userId }) { creator ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .width(224.dp)
                        .clickable { onChat(creator) }
                        .testTag("people_strip_card_${creator.userId}")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = creator.displayName.take(1).uppercase().ifBlank { "P" },
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = creator.displayName,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "@${creator.handle} · ${creator.status}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { onChat(creator) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("quick_chat_${creator.userId}")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Message", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Chat", style = MaterialTheme.typography.labelSmall)
                            }
                            FilledTonalIconButton(
                                onClick = { onCall(creator, "audio") },
                                modifier = Modifier.size(48.dp).testTag("quick_audio_${creator.userId}")
                            ) {
                                Icon(Icons.Filled.Call, contentDescription = "Audio call ${creator.displayName}", modifier = Modifier.size(18.dp))
                            }
                            FilledTonalIconButton(
                                onClick = { onCall(creator, "video") },
                                modifier = Modifier.size(48.dp).testTag("quick_video_${creator.userId}")
                            ) {
                                Icon(Icons.Filled.VideoCall, contentDescription = "Video call ${creator.displayName}", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreatorDirectoryRow(
    creator: CreatorProfile,
    onChat: () -> Unit,
    onAudioCall: () -> Unit,
    onVideoCall: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.68f),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onChat)
            .testTag("creator_directory_row_${creator.userId}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = creator.displayName.take(1).uppercase().ifBlank { "U" },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = creator.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "@${creator.handle} · ${creator.status}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val bioText = creator.bio?.trim().orEmpty()
                if (bioText.isNotEmpty()) {
                    Text(
                        text = bioText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            FilledTonalIconButton(
                onClick = onChat,
                modifier = Modifier.size(48.dp).testTag("directory_chat_${creator.userId}")
            ) {
                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Message ${creator.displayName}", modifier = Modifier.size(18.dp))
            }
            FilledTonalIconButton(
                onClick = onAudioCall,
                modifier = Modifier.size(48.dp).testTag("directory_audio_${creator.userId}")
            ) {
                Icon(Icons.Filled.Call, contentDescription = "Audio call ${creator.displayName}", modifier = Modifier.size(18.dp))
            }
            FilledTonalIconButton(
                onClick = onVideoCall,
                modifier = Modifier.size(48.dp).testTag("directory_video_${creator.userId}")
            ) {
                Icon(Icons.Filled.VideoCall, contentDescription = "Video call ${creator.displayName}", modifier = Modifier.size(18.dp))
            }
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
