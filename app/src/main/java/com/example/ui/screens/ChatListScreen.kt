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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoCall
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.ChatThread
import com.example.ui.PulseCastViewModel
import com.example.ui.UiState
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

enum class ChatListFilter(val label: String, val tag: String) {
    ALL("All", "chat_filter_all"),
    DIRECT("Direct", "chat_filter_direct"),
    GROUPS("Groups", "chat_filter_groups")
}

@Composable
fun ChatListScreen(
    viewModel: PulseCastViewModel,
    onSelectChat: (ChatThread) -> Unit,
    onNewChatClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val chatsState by viewModel.chatsState.collectAsStateWithLifecycle()
    ChatListScreen(
        chatsState = chatsState,
        currentUserId = viewModel.currentUserId,
        onSelectChat = onSelectChat,
        onNewChatClick = onNewChatClick,
        modifier = modifier
    )
}

@Composable
fun ChatListScreen(
    chatsState: UiState<List<ChatThread>>,
    currentUserId: String,
    onSelectChat: (ChatThread) -> Unit,
    onNewChatClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var activeFilter by rememberSaveable { mutableStateOf(ChatListFilter.ALL) }

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
                        if (allThreads.isEmpty()) {
                            EmptyConversationsState(onNewChatClick = onNewChatClick)
                        } else {
                            ChatSearchAndFilterBar(
                                searchQuery = searchQuery,
                                onSearchQueryChange = { searchQuery = it },
                                activeFilter = activeFilter,
                                onFilterChange = { activeFilter = it }
                            )

                            val filteredThreads = remember(allThreads, searchQuery, activeFilter) {
                                val normalizedQuery = searchQuery.trim().lowercase()
                                allThreads.filter { thread ->
                                    val matchesFilter = when (activeFilter) {
                                        ChatListFilter.ALL -> true
                                        ChatListFilter.DIRECT -> !thread.isGroup
                                        ChatListFilter.GROUPS -> thread.isGroup
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

                            if (filteredThreads.isEmpty()) {
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
