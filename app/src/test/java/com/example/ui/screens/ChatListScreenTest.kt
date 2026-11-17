package com.example.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.data.ChatThread
import com.example.data.CreatorProfile
import com.example.ui.UiState
import com.example.ui.theme.PulseCastTheme
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChatListScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun loadingState_showsProgressIndicator() {
        composeRule.setContent {
            PulseCastTheme {
                ChatListScreen(
                    chatsState = UiState.Loading,
                    currentUserId = "uid_alice",
                    onSelectChat = {},
                    onNewChatClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("chat_list_loading").assertIsDisplayed()
    }

    @Test
    fun errorState_showsErrorBanner() {
        composeRule.setContent {
            PulseCastTheme {
                ChatListScreen(
                    chatsState = UiState.Error("Firestore permission denied"),
                    currentUserId = "uid_alice",
                    onSelectChat = {},
                    onNewChatClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("chat_list_error").assertIsDisplayed()
        composeRule.onNodeWithText("Firestore permission denied").assertIsDisplayed()
    }

    @Test
    fun emptyState_displaysCtaAndInvokesNewChat() {
        var clickedNewChat = false
        composeRule.setContent {
            PulseCastTheme {
                ChatListScreen(
                    chatsState = UiState.Success(emptyList()),
                    currentUserId = "uid_alice",
                    onSelectChat = {},
                    onNewChatClick = { clickedNewChat = true }
                )
            }
        }

        composeRule.onNodeWithTag("chat_list_empty").assertIsDisplayed()
        composeRule.onNodeWithTag("empty_new_chat_button").performClick()
        assertTrue(clickedNewChat)
    }

    @Test
    fun conversations_renderRecentMessagesSearchFilterAndSelection() {
        val nowMs = System.currentTimeMillis()
        val threads = listOf(
            ChatThread(
                id = "chat_1",
                title = "Studio Crew",
                isGroup = true,
                participantIds = listOf("uid_alice", "uid_bob", "uid_carol"),
                participantNames = listOf("Alice", "Bob", "Carol"),
                createdBy = "uid_alice",
                lastMessageText = "Sound check at 8pm",
                lastMessageSenderId = "uid_alice",
                lastMessageAt = Timestamp(Date(nowMs - 120_000L))
            ),
            ChatThread(
                id = "chat_2",
                title = "Bob Rivera",
                isGroup = false,
                participantIds = listOf("uid_alice", "uid_bob"),
                participantNames = listOf("Alice", "Bob"),
                createdBy = "uid_bob",
                lastMessageText = "Voice note (14s)",
                lastMessageSenderId = "uid_bob",
                lastMessageAt = Timestamp(Date(nowMs - 7_200_000L))
            )
        )

        var selectedThreadId: String? = null
        composeRule.setContent {
            PulseCastTheme {
                ChatListScreen(
                    chatsState = UiState.Success(threads),
                    currentUserId = "uid_alice",
                    onSelectChat = { selectedThreadId = it.id },
                    onNewChatClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("chat_thread_chat_1").assertIsDisplayed()
        composeRule.onNodeWithText("You: Sound check at 8pm").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_thread_chat_2").assertIsDisplayed()
        composeRule.onNodeWithText("Voice note (14s)").assertIsDisplayed()

        // Filter to Direct only
        composeRule.onNodeWithTag("chat_filter_direct").performClick()
        composeRule.onNodeWithTag("chat_thread_chat_1").assertDoesNotExist()
        composeRule.onNodeWithTag("chat_thread_chat_2").assertIsDisplayed()

        // Reset to All and search by message snippet
        composeRule.onNodeWithTag("chat_filter_all").performClick()
        composeRule.onNodeWithTag("chat_search_input").performTextInput("Sound check")
        composeRule.onNodeWithTag("chat_thread_chat_1").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_thread_chat_2").assertDoesNotExist()

        composeRule.onNodeWithTag("chat_thread_chat_1").performClick()
        assertEquals("chat_1", selectedThreadId)
    }

    @Test
    fun peopleDirectory_showsOtherAccountsEvenWithNoChatsAndStartsDirectChatOrCall() {
        val creators = listOf(
            CreatorProfile(userId = "uid_alice", displayName = "Alice", handle = "alice"),
            CreatorProfile(userId = "uid_bob", displayName = "Bob Rivera", handle = "bob_r", status = "Online"),
            CreatorProfile(userId = "uid_carol", displayName = "Carol Chen", handle = "carol", bio = "Live audio host")
        )

        var chattedWithId: String? = null
        var calledPeer: Pair<String, String>? = null
        composeRule.setContent {
            PulseCastTheme {
                ChatListScreen(
                    chatsState = UiState.Success(emptyList()),
                    creatorsState = UiState.Success(creators),
                    currentUserId = "uid_alice",
                    onSelectChat = {},
                    onNewChatClick = {},
                    onStartDirectChat = { chattedWithId = it.userId },
                    onCallCreator = { creator, type -> calledPeer = creator.userId to type }
                )
            }
        }

        composeRule.onNodeWithTag("people_strip_card_uid_bob").assertIsDisplayed()
        composeRule.onNodeWithTag("quick_chat_uid_bob").performClick()
        assertEquals("uid_bob", chattedWithId)

        composeRule.onNodeWithTag("chat_filter_people").performClick()
        composeRule.onNodeWithTag("creator_directory_row_uid_carol").assertIsDisplayed()
        composeRule.onNodeWithTag("directory_video_uid_carol").performClick()
        assertEquals("uid_carol" to "video", calledPeer)
    }
}
