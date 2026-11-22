package com.example.ui.components

import android.graphics.Bitmap
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.data.CreatorProfile
import com.example.data.PublishedVideo
import com.example.ui.theme.PulseCastTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CreatorProfileViewerTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun createSampleAvatarBase64(): String {
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    @Test
    fun userAvatarWithStatus_rendersBase64PhotoAndInvokesOnClick() {
        var avatarClicked = false
        val base64 = createSampleAvatarBase64()

        composeRule.setContent {
            PulseCastTheme {
                UserAvatarWithStatus(
                    displayName = "Rubel Sk",
                    isOnline = true,
                    photoUrl = "https://example.com/photo.jpg",
                    avatarBase64 = base64,
                    onClick = { avatarClicked = true },
                    avatarTestTag = "test_avatar",
                    indicatorTestTag = "test_indicator"
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("test_avatar").assertIsDisplayed()
        composeRule.onNodeWithTag("test_indicator", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Rubel Sk profile photo", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Rubel Sk is online", useUnmergedTree = true)
            .assertIsDisplayed()

        composeRule.onNodeWithTag("test_avatar").performClick()
        assertTrue(avatarClicked)
    }

    @Test
    fun creatorProfileContent_peerDisplaysDetailsVideosAndActionCallbacks() {
        val now = 1_700_000_000_000L
        val creator = CreatorProfile(
            userId = "uid_farid",
            displayName = "Sheikh Farid",
            handle = "sheikh_farid",
            bio = "Acoustic & video producer",
            status = "studio",
            photoUrl = "https://example.com/farid.jpg",
            avatarBase64 = createSampleAvatarBase64(),
            isOnline = true,
            lastActiveAt = com.google.firebase.Timestamp(java.util.Date(now - 5_000L))
        )
        val videos = listOf(
            PublishedVideo(
                id = "vid_farid_1",
                authorId = "uid_farid",
                authorName = "Sheikh Farid",
                title = "Studio Acoustic Check",
                category = "Music",
                visibility = "public",
                likesCount = 24L,
                commentsCount = 7L,
                durationSec = 110L
            )
        )

        var messaged = false
        var audioCalled = false
        var videoCalled = false
        var clickedVideo: PublishedVideo? = null
        var avatarTapped = false

        composeRule.setContent {
            PulseCastTheme {
                CreatorProfileContent(
                    creator = creator,
                    isCurrentUser = false,
                    creatorVideos = videos,
                    nowMillis = now,
                    onClose = {},
                    onAvatarClick = { avatarTapped = true },
                    onMessageClick = { messaged = true },
                    onAudioCallClick = { audioCalled = true },
                    onVideoCallClick = { videoCalled = true },
                    onVideoClick = { clickedVideo = it }
                )
            }
        }

        composeRule.onNodeWithTag("creator_profile_display_name").assertTextContains("Sheikh Farid")
        composeRule.onNodeWithTag("creator_profile_handle").assertTextContains("@sheikh_farid")
        composeRule.onNodeWithTag("creator_profile_bio").assertTextContains("Acoustic & video producer")
        composeRule.onNodeWithText("Online now").assertIsDisplayed()

        composeRule.onNodeWithTag("creator_profile_avatar").performClick()
        assertTrue(avatarTapped)

        composeRule.onNodeWithTag("creator_profile_list")
            .performScrollToNode(hasTestTag("creator_profile_message_button"))
        composeRule.onNodeWithTag("creator_profile_message_button").performClick()
        assertTrue(messaged)

        composeRule.onNodeWithTag("creator_profile_audio_button").performClick()
        assertTrue(audioCalled)

        composeRule.onNodeWithTag("creator_profile_video_button").performClick()
        assertTrue(videoCalled)

        composeRule.onNodeWithTag("creator_profile_list")
            .performScrollToNode(hasTestTag("creator_profile_video_vid_farid_1"))
        composeRule.onNodeWithTag("creator_profile_video_vid_farid_1").assertIsDisplayed().performClick()
        assertEquals("vid_farid_1", clickedVideo?.id)
    }

    @Test
    fun creatorProfileContent_selfShowsEditAndViewPhotoControls() {
        var viewPhotoClicked = false
        var editStudioClicked = false
        val creator = CreatorProfile(
            userId = "uid_me",
            displayName = "My Studio",
            handle = "my_studio",
            bio = "Creator bio",
            status = "online",
            isOnline = false
        )

        composeRule.setContent {
            PulseCastTheme {
                CreatorProfileContent(
                    creator = creator,
                    isCurrentUser = true,
                    creatorVideos = emptyList(),
                    onClose = {},
                    onAvatarClick = { viewPhotoClicked = true },
                    onMessageClick = {},
                    onAudioCallClick = {},
                    onVideoCallClick = {},
                    onVideoClick = {},
                    onEditOwnProfile = { editStudioClicked = true }
                )
            }
        }

        composeRule.onNodeWithText("You").assertIsDisplayed()
        composeRule.onNodeWithTag("creator_profile_list")
            .performScrollToNode(hasTestTag("creator_profile_view_photo_button"))
        composeRule.onNodeWithTag("creator_profile_view_photo_button").performClick()
        assertTrue(viewPhotoClicked)

        composeRule.onNodeWithTag("creator_profile_edit_studio_button").performClick()
        assertTrue(editStudioClicked)

        composeRule.onNodeWithTag("creator_profile_list")
            .performScrollToNode(hasTestTag("creator_profile_empty_videos"))
        composeRule.onNodeWithTag("creator_profile_empty_videos").assertIsDisplayed()
    }

    @Test
    fun avatarPhotoLightboxDialog_showsPhotoAndEditControls() {
        var pickPhotoClicked = false
        var resetPhotoClicked = false
        var dismissed = false

        composeRule.setContent {
            PulseCastTheme {
                AvatarPhotoLightboxDialog(
                    displayName = "Rubel Sk",
                    handle = "rubel",
                    photoUrl = "https://example.com/photo.jpg",
                    avatarBase64 = createSampleAvatarBase64(),
                    isOnline = true,
                    onDismiss = { dismissed = true },
                    canEditPhoto = true,
                    onPickNewPhoto = { pickPhotoClicked = true },
                    onResetToGooglePhoto = { resetPhotoClicked = true }
                )
            }
        }

        composeRule.onNodeWithTag("avatar_photo_lightbox_dialog").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Rubel Sk full profile photo").assertIsDisplayed()
        composeRule.onNodeWithTag("lightbox_change_photo_button").performClick()
        assertTrue(pickPhotoClicked)

        composeRule.onNodeWithTag("lightbox_reset_google_photo_button").performClick()
        assertTrue(resetPhotoClicked)

        composeRule.onNodeWithTag("photo_lightbox_close_button").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun creatorProfileContent_videoCardLikeButton_togglesWithoutOpeningVideo() {
        var videos by androidx.compose.runtime.mutableStateOf(
            listOf(
                PublishedVideo(
                    id = "vid_c_1",
                    authorId = "uid_farid",
                    authorName = "Sheikh Farid",
                    title = "Live Jam Clip",
                    category = "Music",
                    visibility = "public",
                    likesCount = 11L,
                    commentsCount = 2L,
                    durationSec = 85L
                )
            )
        )
        var likedIds by androidx.compose.runtime.mutableStateOf(emptySet<String>())
        var clickedVideoId: String? = null
        val toggleCalls = mutableListOf<String>()

        composeRule.setContent {
            PulseCastTheme {
                CreatorProfileContent(
                    creator = CreatorProfile(
                        userId = "uid_farid",
                        displayName = "Sheikh Farid",
                        handle = "sheikh_farid"
                    ),
                    isCurrentUser = false,
                    creatorVideos = videos,
                    onClose = {},
                    onMessageClick = {},
                    onAudioCallClick = {},
                    onVideoCallClick = {},
                    onVideoClick = { clickedVideoId = it.id },
                    likedVideoIds = likedIds,
                    onToggleLike = { video ->
                        toggleCalls += video.id
                        val currentlyLiked = video.id in likedIds
                        likedIds = if (currentlyLiked) likedIds - video.id else likedIds + video.id
                        videos = videos.map { item ->
                            if (item.id == video.id) {
                                item.copy(likesCount = if (currentlyLiked) item.likesCount - 1L else item.likesCount + 1L)
                            } else {
                                item
                            }
                        }
                    }
                )
            }
        }

        composeRule.onNodeWithTag("creator_profile_list")
            .performScrollToNode(hasTestTag("creator_profile_video_vid_c_1"))
        composeRule.onNodeWithTag("creator_profile_like_button_vid_c_1").assertIsDisplayed()
        composeRule.onNodeWithTag("creator_profile_like_count_vid_c_1", useUnmergedTree = true)
            .assertTextContains("11")

        composeRule.onNodeWithTag("creator_profile_like_button_vid_c_1").performClick()

        assertEquals(listOf("vid_c_1"), toggleCalls)
        assertEquals(null, clickedVideoId)
        composeRule.onNodeWithTag("creator_profile_like_count_vid_c_1", useUnmergedTree = true)
            .assertTextContains("12")
    }
}
