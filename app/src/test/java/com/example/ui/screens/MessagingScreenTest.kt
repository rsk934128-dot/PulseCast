package com.example.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.data.ChatMessage
import com.example.data.ChatThread
import com.example.data.CreatorProfile
import com.example.data.UserAccount
import com.example.data.isRecentlyOnline
import com.example.data.resolveIsUserOnline
import com.example.ui.theme.PulseCastTheme
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MessagingScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun presenceHelper_respectsFlagAndRecentActivityWindow() {
        val nowMs = 1_700_000_000_000L
        val thirtySecondsAgo = Timestamp(Date(nowMs - 30_000L))
        val tenMinutesAgo = Timestamp(Date(nowMs - 10 * 60_000L))

        assertTrue(resolveIsUserOnline(isOnlineFlag = true, lastActiveAt = thirtySecondsAgo, nowMillis = nowMs))
        assertFalse(resolveIsUserOnline(isOnlineFlag = true, lastActiveAt = tenMinutesAgo, nowMillis = nowMs))
        assertFalse(resolveIsUserOnline(isOnlineFlag = false, lastActiveAt = thirtySecondsAgo, nowMillis = nowMs))
        assertTrue(resolveIsUserOnline(isOnlineFlag = true, lastActiveAt = null, updatedAt = thirtySecondsAgo, nowMillis = nowMs))

        val activeUser = UserAccount(
            userId = "u1",
            displayName = "Alice",
            isOnline = true,
            lastActiveAt = thirtySecondsAgo
        )
        val staleCreator = CreatorProfile(
            userId = "u2",
            displayName = "Bob",
            handle = "bob",
            isOnline = true,
            lastActiveAt = tenMinutesAgo
        )
        assertTrue(activeUser.isRecentlyOnline(nowMs))
        assertFalse(staleCreator.isRecentlyOnline(nowMs))
    }

    @Test
    fun chatThread_rendersOnlineAndOfflineAvatarIndicatorsBasedOnRecentActivity() {
        val nowMs = 1_700_000_000_000L
        val recentTs = Timestamp(Date(nowMs - 45_000L))
        val staleTs = Timestamp(Date(nowMs - 12 * 60_000L))

        val groupChat = ChatThread(
            id = "chat_group_1",
            title = "Studio Crew",
            isGroup = true,
            participantIds = listOf("uid_alice", "uid_bob", "uid_carol"),
            participantNames = listOf("Alice", "Bob Rivera", "Carol Chen")
        )
        val messages = listOf(
            ChatMessage(
                id = "msg_bob",
                chatId = "chat_group_1",
                senderId = "uid_bob",
                senderName = "Bob Rivera",
                messageType = "text",
                text = "Mic check ready",
                participantIds = listOf("uid_alice", "uid_bob", "uid_carol")
            ),
            ChatMessage(
                id = "msg_carol",
                chatId = "chat_group_1",
                senderId = "uid_carol",
                senderName = "Carol Chen",
                messageType = "text",
                text = "Joining soon",
                participantIds = listOf("uid_alice", "uid_bob", "uid_carol")
            ),
            ChatMessage(
                id = "msg_alice",
                chatId = "chat_group_1",
                senderId = "uid_alice",
                senderName = "Alice",
                messageType = "text",
                text = "Let's go live",
                participantIds = listOf("uid_alice", "uid_bob", "uid_carol")
            )
        )
        val participants = mapOf(
            "uid_bob" to CreatorProfile(
                userId = "uid_bob",
                displayName = "Bob Rivera",
                handle = "bob_r",
                isOnline = true,
                lastActiveAt = recentTs
            ),
            "uid_carol" to CreatorProfile(
                userId = "uid_carol",
                displayName = "Carol Chen",
                handle = "carol_c",
                isOnline = true,
                lastActiveAt = staleTs // stale > 5m => offline
            )
        )

        var sentMessage = ""
        composeRule.setContent {
            PulseCastTheme {
                ChatConversationContent(
                    chat = groupChat,
                    currentUserId = "uid_alice",
                    messages = messages,
                    participantProfiles = participants,
                    nowMillis = nowMs,
                    onBack = {},
                    onSendText = { sentMessage = it },
                    onPickImage = {},
                    onToggleVoiceRecord = {},
                    onPlayVoice = {},
                    onStartCall = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("1 online · 3 participant(s)").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_header_online_indicator", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertContentDescriptionEquals("Studio Crew is online")

        composeRule.onNodeWithTag("message_avatar_online_indicator_msg_bob", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertContentDescriptionEquals("Bob Rivera is online")

        composeRule.onNodeWithTag("message_avatar_online_indicator_msg_carol", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertContentDescriptionEquals("Carol Chen is offline")

        composeRule.onNodeWithTag("message_avatar_online_indicator_msg_alice", useUnmergedTree = true)
            .assertDoesNotExist()

        composeRule.onNodeWithTag("message_input").performTextInput("Rolling camera")
        composeRule.onNodeWithTag("send_message_button").performClick()
        assertEquals("Rolling camera", sentMessage)
    }

    @Test
    fun directChat_showsOfflineHeaderWhenPeerMarkedOffline() {
        val nowMs = 1_700_000_000_000L
        val directChat = ChatThread(
            id = "chat_dm_1",
            title = "Bob Rivera",
            isGroup = false,
            participantIds = listOf("uid_alice", "uid_bob"),
            participantNames = listOf("Alice", "Bob Rivera")
        )
        val participants = mapOf(
            "uid_bob" to CreatorProfile(
                userId = "uid_bob",
                displayName = "Bob Rivera",
                handle = "bob_r",
                isOnline = false,
                lastActiveAt = Timestamp(Date(nowMs - 15_000L))
            )
        )

        composeRule.setContent {
            PulseCastTheme {
                ChatConversationContent(
                    chat = directChat,
                    currentUserId = "uid_alice",
                    messages = emptyList(),
                    participantProfiles = participants,
                    nowMillis = nowMs,
                    onBack = {},
                    onSendText = {},
                    onPickImage = {},
                    onToggleVoiceRecord = {},
                    onPlayVoice = {},
                    onStartCall = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Offline · Encrypted sync").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_header_online_indicator", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertContentDescriptionEquals("Bob Rivera is offline")
    }
}
