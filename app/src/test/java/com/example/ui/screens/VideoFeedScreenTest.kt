package com.example.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.data.CreatorProfile
import com.example.data.PublishedVideo
import com.example.ui.UiState
import com.example.ui.theme.PulseCastTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VideoFeedScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun feedCardLikeButton_displaysCounterUpdatesInRealTimeAndDoesNotOpenVideo() {
        var videosState by mutableStateOf<UiState<List<PublishedVideo>>>(
            UiState.Success(
                listOf(
                    PublishedVideo(
                        id = "vid_1",
                        authorId = "uid_bob",
                        authorName = "Bob Studio",
                        title = "Acoustic Session",
                        category = "Music",
                        visibility = "public",
                        likesCount = 3L,
                        commentsCount = 1L,
                        durationSec = 90L
                    ),
                    PublishedVideo(
                        id = "vid_2",
                        authorId = "uid_carol",
                        authorName = "Carol Live",
                        title = "Podcast Gear Tour",
                        category = "Tech",
                        visibility = "public",
                        likesCount = 10L,
                        commentsCount = 4L,
                        durationSec = 140L
                    )
                )
            )
        )
        var likedIds by mutableStateOf(setOf("vid_2"))
        var openedVideoId: String? = null
        val toggleCalls = mutableListOf<String>()

        composeRule.setContent {
            PulseCastTheme {
                VideoFeedContent(
                    videosState = videosState,
                    creatorsById = mapOf(
                        "uid_bob" to CreatorProfile(userId = "uid_bob", displayName = "Bob Studio", handle = "bob"),
                        "uid_carol" to CreatorProfile(userId = "uid_carol", displayName = "Carol Live", handle = "carol")
                    ),
                    likedVideoIds = likedIds,
                    onToggleLike = { video ->
                        toggleCalls += video.id
                        val currentlyLiked = video.id in likedIds
                        likedIds = if (currentlyLiked) likedIds - video.id else likedIds + video.id
                        val currentList = (videosState as UiState.Success).data
                        videosState = UiState.Success(
                            currentList.map { item ->
                                if (item.id == video.id) {
                                    item.copy(likesCount = if (currentlyLiked) item.likesCount - 1L else item.likesCount + 1L)
                                } else {
                                    item
                                }
                            }
                        )
                    },
                    onVideoClick = { openedVideoId = it.id }
                )
            }
        }

        // Initial state for unliked vid_1 and liked vid_2
        composeRule.onNodeWithTag("feed_like_button_vid_1")
            .assertIsDisplayed()
            .assertContentDescriptionEquals("Like Acoustic Session")
        composeRule.onNodeWithTag("feed_like_count_vid_1", useUnmergedTree = true)
            .assertTextEquals("3")

        composeRule.onNodeWithTag("feed_like_button_vid_2")
            .assertIsDisplayed()
            .assertContentDescriptionEquals("Unlike Podcast Gear Tour")
        composeRule.onNodeWithTag("feed_like_count_vid_2", useUnmergedTree = true)
            .assertTextEquals("10")

        // Tap like on vid_1: increments count and updates state without opening video detail
        composeRule.onNodeWithTag("feed_like_button_vid_1").performClick()
        assertEquals(listOf("vid_1"), toggleCalls)
        assertNull(openedVideoId)
        composeRule.onNodeWithTag("feed_like_button_vid_1")
            .assertContentDescriptionEquals("Unlike Acoustic Session")
        composeRule.onNodeWithTag("feed_like_count_vid_1", useUnmergedTree = true)
            .assertTextEquals("4")

        // Simulate external real-time Firestore snapshot update from another user
        val updatedList = (videosState as UiState.Success).data.map { item ->
            if (item.id == "vid_1") item.copy(likesCount = 5L) else item
        }
        videosState = UiState.Success(updatedList)
        composeRule.onNodeWithTag("feed_like_count_vid_1", useUnmergedTree = true)
            .assertTextEquals("5")

        // Tap unlike on vid_2: decrements count
        composeRule.onNodeWithTag("feed_like_button_vid_2").performClick()
        assertEquals(listOf("vid_1", "vid_2"), toggleCalls)
        assertNull(openedVideoId)
        composeRule.onNodeWithTag("feed_like_button_vid_2")
            .assertContentDescriptionEquals("Like Podcast Gear Tour")
        composeRule.onNodeWithTag("feed_like_count_vid_2", useUnmergedTree = true)
            .assertTextEquals("9")
    }

    @Test
    fun feedCardLikeButton_disablesDuringInFlightUpdate() {
        val video = PublishedVideo(
            id = "vid_busy",
            authorId = "uid_bob",
            authorName = "Bob Studio",
            title = "Live Stream Clip",
            category = "Live",
            visibility = "public",
            likesCount = 7L
        )

        composeRule.setContent {
            PulseCastTheme {
                VideoFeedCard(
                    video = video,
                    isLiked = false,
                    isLikeUpdating = true,
                    onClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("feed_like_button_vid_busy")
            .assertIsDisplayed()
            .assertIsNotEnabled()
        composeRule.onNodeWithTag("feed_like_count_vid_busy", useUnmergedTree = true)
            .assertTextEquals("7")
    }

    @Test
    fun feedCardShareButton_copiesDirectUrlToClipboardAndDoesNotOpenVideo() {
        val directStreamUrl = "https://cdn.pulsecast.example/streams/acoustic.mp4"
        val videosState: UiState<List<PublishedVideo>> = UiState.Success(
            listOf(
                PublishedVideo(
                    id = "vid_1",
                    authorId = "uid_bob",
                    authorName = "Bob Studio",
                    title = "Acoustic Session",
                    category = "Music",
                    videoUri = directStreamUrl,
                    visibility = "public"
                ),
                PublishedVideo(
                    id = "vid_2",
                    authorId = "uid_carol",
                    authorName = "Carol Live",
                    title = "Cloud Synced Clip",
                    category = "Tech",
                    videoUri = "firestore://videos/vid_2?ext=mp4",
                    visibility = "public"
                )
            )
        )
        var openedVideoId: String? = null
        val sharedVideoIds = mutableListOf<String>()

        composeRule.setContent {
            PulseCastTheme {
                VideoFeedContent(
                    videosState = videosState,
                    creatorsById = mapOf(
                        "uid_bob" to CreatorProfile(userId = "uid_bob", displayName = "Bob Studio", handle = "bob"),
                        "uid_carol" to CreatorProfile(userId = "uid_carol", displayName = "Carol Live", handle = "carol")
                    ),
                    likedVideoIds = emptySet(),
                    onShareClick = { sharedVideoIds += it.id },
                    onVideoClick = { openedVideoId = it.id }
                )
            }
        }

        val clipboard = composeRule.activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
            as android.content.ClipboardManager

        composeRule.onNodeWithTag("feed_share_button_vid_1")
            .assertIsDisplayed()
            .assertContentDescriptionEquals("Copy direct link for Acoustic Session")
        composeRule.onNodeWithTag("feed_share_label_vid_1", useUnmergedTree = true)
            .assertTextEquals("Share")

        composeRule.onNodeWithTag("feed_share_button_vid_1").performClick()

        assertEquals(listOf("vid_1"), sharedVideoIds)
        assertNull(openedVideoId)
        assertEquals(
            directStreamUrl,
            clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        )
        composeRule.onNodeWithTag("feed_share_label_vid_1", useUnmergedTree = true)
            .assertTextEquals("Copied")

        composeRule.onNodeWithTag("feed_share_button_vid_2").performClick()

        assertEquals(listOf("vid_1", "vid_2"), sharedVideoIds)
        assertNull(openedVideoId)
        assertEquals(
            "${com.example.data.PULSECAST_SHARE_BASE_URL}/?videoId=vid_2",
            clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        )
    }
}
