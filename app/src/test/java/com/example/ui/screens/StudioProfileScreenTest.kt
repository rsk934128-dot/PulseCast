package com.example.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import com.example.data.PublishedVideo
import com.example.ui.UiState
import com.example.ui.theme.PulseCastTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StudioProfileScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun scrollListToTag(tag: String) {
        composeRule.onNodeWithTag("profile_lazy_list").performScrollToNode(hasTestTag(tag))
    }

    @Test
    fun profile_displaysUserInfoStatsAndUploadedVideos_withFiltersAndClick() {
        val uploadedVideos = listOf(
            PublishedVideo(
                id = "vid_pub_1",
                authorId = "uid_alice",
                authorName = "Alice Studio",
                title = "Live Mixing Breakdown",
                category = "Studio",
                visibility = "public",
                likesCount = 14L,
                commentsCount = 3L,
                durationSec = 95L
            ),
            PublishedVideo(
                id = "vid_priv_2",
                authorId = "uid_alice",
                authorName = "Alice Studio",
                title = "Unlisted Soundcheck",
                category = "Tech",
                visibility = "private",
                likesCount = 2L,
                commentsCount = 1L,
                durationSec = 40L
            )
        )
        var clickedVideo: PublishedVideo? = null

        composeRule.setContent {
            PulseCastTheme {
                StudioProfileContent(
                    initialDisplayName = "Alice Studio",
                    initialHandle = "alice_cast",
                    email = "alice@pulsecast.app",
                    initialBio = "Podcast & audio engineer",
                    initialStatus = "studio",
                    photoUrl = null,
                    pushCalls = true,
                    pushMessages = true,
                    hdVideo = true,
                    myVideosState = UiState.Success(uploadedVideos),
                    onSaveProfile = { _, _, _, _ -> },
                    onUpdatePreferences = { _, _, _ -> },
                    onUploadVideoClick = {},
                    onVideoClick = { clickedVideo = it },
                    onDeleteVideo = {},
                    onSignOut = {}
                )
            }
        }

        composeRule.onNodeWithTag("profile_display_name").assertTextContains("Alice Studio")
        composeRule.onNodeWithTag("profile_handle_label").assertTextContains("@alice_cast")
        composeRule.onNodeWithTag("profile_email_label").assertTextContains("alice@pulsecast.app")
        composeRule.onNodeWithTag("profile_bio_label").assertTextContains("Podcast & audio engineer")

        composeRule.onNodeWithTag("stat_videos_count").assertTextContains("2", substring = true)
        composeRule.onNodeWithTag("stat_likes_count").assertTextContains("16", substring = true)
        composeRule.onNodeWithTag("stat_comments_count").assertTextContains("4", substring = true)

        scrollListToTag("profile_video_card_vid_pub_1")
        composeRule.onNodeWithTag("profile_video_card_vid_pub_1").assertIsDisplayed()
        scrollListToTag("profile_video_card_vid_priv_2")
        composeRule.onNodeWithTag("profile_video_card_vid_priv_2").assertIsDisplayed()

        // Filter to Private Drafts only
        scrollListToTag("profile_uploaded_videos_section")
        composeRule.onNodeWithTag("profile_filter_private").performClick()
        composeRule.onNodeWithTag("profile_video_card_vid_pub_1").assertDoesNotExist()
        scrollListToTag("profile_video_card_vid_priv_2")
        composeRule.onNodeWithTag("profile_video_card_vid_priv_2").assertIsDisplayed()

        // Click video card opens video detail callback
        composeRule.onNodeWithTag("profile_video_card_vid_priv_2").performClick()
        assertEquals("vid_priv_2", clickedVideo?.id)
    }

    @Test
    fun profile_editAndDeleteVideoConfirmation_invokesCallbacks() {
        val video = PublishedVideo(
            id = "vid_del_1",
            authorId = "uid_alice",
            authorName = "Alice",
            title = "Studio Setup Clip",
            category = "Vlog",
            visibility = "public",
            durationSec = 60L
        )
        var savedName = ""
        var deletedId: String? = null

        composeRule.setContent {
            PulseCastTheme {
                StudioProfileContent(
                    initialDisplayName = "Alice",
                    initialHandle = "alice_h",
                    email = "alice@pulsecast.app",
                    initialBio = "",
                    initialStatus = "online",
                    photoUrl = null,
                    pushCalls = true,
                    pushMessages = true,
                    hdVideo = true,
                    myVideosState = UiState.Success(listOf(video)),
                    onSaveProfile = { name, _, _, _ -> savedName = name },
                    onUpdatePreferences = { _, _, _ -> },
                    onUploadVideoClick = {},
                    onVideoClick = {},
                    onDeleteVideo = { deletedId = it.id },
                    onSignOut = {}
                )
            }
        }

        // Expand profile editor and save updated name
        composeRule.onNodeWithTag("edit_profile_toggle_button").performClick()
        scrollListToTag("profile_editor_card")
        composeRule.onNodeWithTag("profile_name_input").performTextReplacement("Alice Pro")
        composeRule.onNodeWithTag("save_profile_button").performClick()
        assertEquals("Alice Pro", savedName)

        // Delete video with confirmation dialog
        scrollListToTag("profile_video_card_vid_del_1")
        composeRule.onNodeWithTag("profile_delete_video_vid_del_1").performClick()
        composeRule.onNodeWithTag("confirm_delete_video_button").assertIsDisplayed().performClick()
        assertEquals("vid_del_1", deletedId)
    }

    @Test
    fun profile_showsEmptyStateAndUploadAction() {
        var uploadClicked = false
        composeRule.setContent {
            PulseCastTheme {
                StudioProfileContent(
                    initialDisplayName = "Creator",
                    initialHandle = "creator",
                    email = "creator@pulsecast.app",
                    initialBio = "",
                    initialStatus = "online",
                    photoUrl = null,
                    pushCalls = true,
                    pushMessages = true,
                    hdVideo = true,
                    myVideosState = UiState.Success(emptyList()),
                    onSaveProfile = { _, _, _, _ -> },
                    onUpdatePreferences = { _, _, _ -> },
                    onUploadVideoClick = { uploadClicked = true },
                    onVideoClick = {},
                    onDeleteVideo = {},
                    onSignOut = {}
                )
            }
        }

        scrollListToTag("profile_empty_videos")
        composeRule.onNodeWithTag("profile_empty_videos").assertIsDisplayed()
        composeRule.onNodeWithTag("profile_empty_upload_button").performClick()
        assertTrue(uploadClicked)
    }

    @Test
    fun profile_errorState_displaysErrorMessage() {
        composeRule.setContent {
            PulseCastTheme {
                StudioProfileContent(
                    initialDisplayName = "Creator",
                    initialHandle = "creator",
                    email = "creator@pulsecast.app",
                    initialBio = "",
                    initialStatus = "online",
                    photoUrl = null,
                    pushCalls = true,
                    pushMessages = true,
                    hdVideo = true,
                    myVideosState = UiState.Error("Unable to load uploaded videos"),
                    onSaveProfile = { _, _, _, _ -> },
                    onUpdatePreferences = { _, _, _ -> },
                    onUploadVideoClick = {},
                    onVideoClick = {},
                    onDeleteVideo = {},
                    onSignOut = {}
                )
            }
        }

        scrollListToTag("profile_videos_error")
        composeRule.onNodeWithTag("profile_videos_error").assertIsDisplayed()
        composeRule.onNodeWithText("Unable to load uploaded videos").assertIsDisplayed()
    }

    @Test
    fun profile_creatorProfilesStripAndPhotoActions_invokeCallbacksAndOpenLightbox() {
        val otherCreators = listOf(
            com.example.data.CreatorProfile(
                userId = "uid_khoku",
                displayName = "Khoku Moni",
                handle = "khoku",
                photoUrl = "https://example.com/khoku.jpg",
                isOnline = true,
                lastActiveAt = com.google.firebase.Timestamp.now()
            )
        )
        var viewedCreatorId: String? = null
        var pickPhotoCount = 0

        composeRule.setContent {
            PulseCastTheme {
                StudioProfileContent(
                    initialDisplayName = "Rubel Sk",
                    initialHandle = "rubel",
                    email = "rubel@pulsecast.app",
                    initialBio = "Live creator",
                    initialStatus = "online",
                    photoUrl = "https://example.com/rubel.jpg",
                    pushCalls = true,
                    pushMessages = true,
                    hdVideo = true,
                    myVideosState = UiState.Success(emptyList()),
                    onSaveProfile = { _, _, _, _ -> },
                    onUpdatePreferences = { _, _, _ -> },
                    onUploadVideoClick = {},
                    onVideoClick = {},
                    onDeleteVideo = {},
                    onSignOut = {},
                    discoverableCreators = otherCreators,
                    onPickNewPhoto = { pickPhotoCount++ },
                    onViewCreatorProfile = { viewedCreatorId = it.userId }
                )
            }
        }

        // Tapping profile change photo invokes picker callback
        composeRule.onNodeWithTag("profile_change_photo_button").assertIsDisplayed().performClick()
        assertEquals(1, pickPhotoCount)

        // Tapping own avatar opens lightbox dialog
        composeRule.onNodeWithTag("profile_avatar_image").performClick()
        composeRule.onNodeWithTag("avatar_photo_lightbox_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("photo_lightbox_close_button").performClick()

        // Tapping creator card in Studio Creator Profiles strip opens creator profile
        scrollListToTag("studio_creators_strip")
        composeRule.onNodeWithTag("studio_creator_card_uid_khoku").assertIsDisplayed().performClick()
        assertEquals("uid_khoku", viewedCreatorId)
    }
}
