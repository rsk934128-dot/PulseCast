package com.example.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import com.example.data.AdminRecord
import com.example.data.CallSession
import com.example.data.CreatorProfile
import com.example.data.PublishedVideo
import com.example.data.UserAccount
import com.example.ui.UiState
import com.example.ui.theme.PulseCastTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdminPanelScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun scrollAdminListTo(tag: String) {
        composeRule.onNodeWithTag("admin_lazy_list").performScrollToNode(hasTestTag(tag))
    }

    @Test
    fun adminPanel_displaysMetricsAndModeratesVideosCreatorsAndCalls() {
        val videos = listOf(
            PublishedVideo(
                id = "vid_1",
                authorId = "uid_a",
                authorName = "Alice",
                title = "Studio Setup Walkthrough",
                category = "Studio",
                visibility = "public",
                likesCount = 12L
            )
        )
        val creators = listOf(
            CreatorProfile(
                id = "uid_a",
                userId = "uid_a",
                displayName = "Alice Studio",
                handle = "alice_s",
                status = "online",
                isDiscoverable = true
            )
        )
        val calls = listOf(
            CallSession(
                id = "PULSE-9012",
                hostId = "uid_a",
                hostName = "Alice Studio",
                roomCode = "PULSE-9012",
                callType = "video",
                status = "active",
                participantIds = listOf("uid_a")
            )
        )
        var toggledVisibilityId = ""
        var moderatedCreatorHidden: Boolean? = null
        var endedCallId = ""

        composeRule.setContent {
            PulseCastTheme {
                AdminPanelContent(
                    currentUserId = "admin_1",
                    currentUserEmail = "rsk755734@gmail.com",
                    videosState = UiState.Success(videos),
                    creatorsState = UiState.Success(creators),
                    callsState = UiState.Success(calls),
                    usersState = UiState.Success(emptyList()),
                    adminsState = UiState.Success(emptyList()),
                    onToggleVideoVisibility = { toggledVisibilityId = it.id },
                    onDeleteVideo = {},
                    onSetCreatorModeration = { _, discoverable, _ -> moderatedCreatorHidden = discoverable },
                    onForceEndCall = { endedCallId = it.id },
                    onDeleteCall = {},
                    onGrantAdminRole = { _, _, _, _ -> },
                    onRevokeAdminRole = {}
                )
            }
        }

        composeRule.onNodeWithTag("admin_hero_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("admin_metric_videos").assertTextContains("1", substring = true)
        composeRule.onNodeWithTag("admin_metric_creators").assertTextContains("1", substring = true)
        composeRule.onNodeWithTag("admin_metric_calls").assertTextContains("1", substring = true)

        scrollAdminListTo("admin_video_card_vid_1")
        composeRule.onNodeWithTag("admin_toggle_visibility_vid_1").performClick()
        assertEquals("vid_1", toggledVisibilityId)

        // Switch to Creators tab
        scrollAdminListTo("admin_tab_creators")
        composeRule.onNodeWithTag("admin_tab_creators").performClick()
        scrollAdminListTo("admin_creator_card_uid_a")
        composeRule.onNodeWithTag("admin_creator_discoverable_uid_a").performClick()
        assertEquals(false, moderatedCreatorHidden)

        // Switch to Calls tab
        scrollAdminListTo("admin_tab_calls")
        composeRule.onNodeWithTag("admin_tab_calls").performClick()
        scrollAdminListTo("admin_call_card_PULSE-9012")
        composeRule.onNodeWithTag("admin_end_call_PULSE-9012").performClick()
        assertEquals("PULSE-9012", endedCallId)
    }

    @Test
    fun adminPanel_grantAndRevokeRoles_invokesCallbacks() {
        val users = listOf(
            UserAccount(
                id = "uid_bob",
                userId = "uid_bob",
                email = "bob@pulsecast.app",
                displayName = "Bob Creator"
            )
        )
        val admins = listOf(
            AdminRecord(
                id = "uid_charlie",
                userId = "uid_charlie",
                displayName = "Charlie Mod",
                email = "charlie@pulsecast.app",
                role = "moderator",
                grantedBy = "admin_1"
            )
        )
        var grantedUid = ""
        var revokedUid = ""

        composeRule.setContent {
            PulseCastTheme {
                AdminPanelContent(
                    currentUserId = "admin_1",
                    currentUserEmail = "rsk755734@gmail.com",
                    videosState = UiState.Success(emptyList()),
                    creatorsState = UiState.Success(emptyList()),
                    callsState = UiState.Success(emptyList()),
                    usersState = UiState.Success(users),
                    adminsState = UiState.Success(admins),
                    onToggleVideoVisibility = {},
                    onDeleteVideo = {},
                    onSetCreatorModeration = { _, _, _ -> },
                    onForceEndCall = {},
                    onDeleteCall = {},
                    onGrantAdminRole = { uid, _, _, _ -> grantedUid = uid },
                    onRevokeAdminRole = { uid -> revokedUid = uid }
                )
            }
        }

        scrollAdminListTo("admin_tab_access")
        composeRule.onNodeWithTag("admin_tab_access").performClick()

        // Revoke Charlie
        scrollAdminListTo("admin_role_card_uid_charlie")
        composeRule.onNodeWithTag("admin_revoke_role_uid_charlie").performClick()
        assertEquals("uid_charlie", revokedUid)

        // Promote Bob
        scrollAdminListTo("admin_user_card_uid_bob")
        composeRule.onNodeWithTag("admin_promote_user_uid_bob").performClick()
        composeRule.onNodeWithTag("grant_admin_uid_input").performTextReplacement("uid_bob")
        composeRule.onNodeWithTag("confirm_grant_admin_button").performClick()
        assertEquals("uid_bob", grantedUid)
    }
}
