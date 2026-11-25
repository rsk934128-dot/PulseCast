package com.example.ui.screens

import android.view.View
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
        settleRecyclerUi(composeRule)

        waitForViewByTag<View>(composeRule, "chat_thread_chat_1")
        waitUntilTextVisibleInRecycler(composeRule, "chat_list_lazy_column", "You: Sound check at 8pm")
        waitForViewByTag<View>(composeRule, "chat_thread_chat_2")
        waitUntilTextVisibleInRecycler(composeRule, "chat_list_lazy_column", "Voice note (14s)")

        // Filter to Direct only
        composeRule.onNodeWithTag("chat_filter_direct").performClick()
        waitUntilViewAbsentOrHidden(composeRule, "chat_thread_chat_1")
        waitForViewByTag<View>(composeRule, "chat_thread_chat_2")

        // Reset to All and search by message snippet
        composeRule.onNodeWithTag("chat_filter_all").performClick()
        composeRule.onNodeWithTag("chat_search_input").performTextInput("Sound check")
        val chat1Row = waitForViewByTag<View>(composeRule, "chat_thread_chat_1")
        waitUntilViewAbsentOrHidden(composeRule, "chat_thread_chat_2")

        composeRule.runOnUiThread { chat1Row.performClick() }
        settleRecyclerUi(composeRule)
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
        settleRecyclerUi(composeRule)

        waitForViewByTag<View>(composeRule, "people_strip_card_uid_bob")
        val quickChatBtn = waitForViewByTag<View>(composeRule, "quick_chat_uid_bob")
        composeRule.runOnUiThread { quickChatBtn.performClick() }
        settleRecyclerUi(composeRule)
        assertEquals("uid_bob", chattedWithId)

        composeRule.onNodeWithTag("chat_filter_people").performClick()
        settleRecyclerUi(composeRule)
        waitForViewByTag<View>(composeRule, "creator_directory_row_uid_carol")
        val dirVideoBtn = waitForViewByTag<View>(composeRule, "directory_video_uid_carol")
        composeRule.runOnUiThread { dirVideoBtn.performClick() }
        settleRecyclerUi(composeRule)
        assertEquals("uid_carol" to "video", calledPeer)
    }

    @Test
    fun threadListAndPeopleAvatars_displayIsOnlineIndicatorFromRecentActivity() {
        val nowMs = System.currentTimeMillis()
        val creators = listOf(
            CreatorProfile(
                userId = "uid_bob",
                displayName = "Bob Rivera",
                handle = "bob_r",
                isOnline = true,
                lastActiveAt = Timestamp(Date(nowMs - 20_000L))
            ),
            CreatorProfile(
                userId = "uid_carol",
                displayName = "Carol Chen",
                handle = "carol",
                isOnline = true,
                lastActiveAt = Timestamp(Date(nowMs - 15 * 60_000L))
            )
        )
        val threads = listOf(
            ChatThread(
                id = "chat_dm_bob",
                title = "Bob Rivera",
                isGroup = false,
                participantIds = listOf("uid_alice", "uid_bob"),
                lastMessageText = "Ready",
                lastMessageSenderId = "uid_bob",
                lastMessageAt = Timestamp(Date(nowMs - 10_000L))
            ),
            ChatThread(
                id = "chat_dm_carol",
                title = "Carol Chen",
                isGroup = false,
                participantIds = listOf("uid_alice", "uid_carol"),
                lastMessageText = "Catch you later",
                lastMessageSenderId = "uid_carol",
                lastMessageAt = Timestamp(Date(nowMs - 20_000L))
            )
        )

        composeRule.setContent {
            PulseCastTheme {
                ChatListScreen(
                    chatsState = UiState.Success(threads),
                    creatorsState = UiState.Success(creators),
                    currentUserId = "uid_alice",
                    onSelectChat = {},
                    onNewChatClick = {}
                )
            }
        }
        settleRecyclerUi(composeRule)

        val stripDotBob = waitForViewByTag<View>(composeRule, "people_strip_online_indicator_uid_bob")
        assertEquals("Bob Rivera is online", stripDotBob.contentDescription?.toString())
        waitUntilTextVisibleInRecycler(composeRule, "chat_list_lazy_column", "@bob_r · Online")

        val threadDotBob = waitForViewByTag<View>(composeRule, "chat_thread_online_indicator_chat_dm_bob")
        assertEquals("Bob Rivera is online", threadDotBob.contentDescription?.toString())

        scrollRecyclerToViewTag(composeRule, "chat_list_lazy_column", "chat_thread_chat_dm_carol")
        val threadDotCarol = waitForViewByTag<View>(composeRule, "chat_thread_online_indicator_chat_dm_carol")
        assertEquals("Carol Chen is offline", threadDotCarol.contentDescription?.toString())

        composeRule.onNodeWithTag("chat_filter_people").performClick()
        settleRecyclerUi(composeRule)
        val dirDotCarol = waitForViewByTag<View>(composeRule, "directory_online_indicator_uid_carol")
        assertEquals("Carol Chen is offline", dirDotCarol.contentDescription?.toString())
        waitUntilTextVisibleInRecycler(composeRule, "people_directory_list", "@carol · Offline")
    }
}
