package com.example.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.media.FirebaseStorageVideoUploader
import com.example.media.VideoUploadOutcome
import com.example.media.formatFileSize
import com.example.ui.VideoUploadUiState
import com.example.ui.theme.PulseCastTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VideoUploadFormTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun form_requiresTitle_andSubmitsMetadataWithSelectedCategory() {
        var title by mutableStateOf("")
        var description by mutableStateOf("")
        var category by mutableStateOf("Studio")
        var visibility by mutableStateOf("public")
        var streamUrl by mutableStateOf("")
        var submitted = false

        composeRule.setContent {
            PulseCastTheme {
                VideoUploadFormContent(
                    title = title,
                    onTitleChange = { title = it },
                    description = description,
                    onDescriptionChange = { description = it },
                    category = category,
                    onCategoryChange = { category = it },
                    visibility = visibility,
                    onVisibilityChange = { visibility = it },
                    coverKey = "thumb_1",
                    onToggleCoverKey = {},
                    localVideoUri = null,
                    selectedFileName = null,
                    selectedFileSizeBytes = null,
                    durationSec = 0L,
                    externalVideoUrl = streamUrl,
                    onExternalVideoUrlChange = { streamUrl = it },
                    thumbnailBase64 = null,
                    isProcessingMedia = false,
                    uploadState = VideoUploadUiState.Idle,
                    onPickVideo = {},
                    onClearSelectedVideo = {},
                    onPickCover = {},
                    recordPermissionContent = {},
                    onDismiss = {},
                    onSubmit = { submitted = true },
                    showInlinePlayerPreview = false
                )
            }
        }

        composeRule.onNodeWithTag("submit_publish_video").performScrollTo().assertIsNotEnabled()

        composeRule.onNodeWithTag("publish_title_input").performScrollTo().performTextInput("Studio Tour")
        composeRule.onNodeWithTag("publish_desc_input").performScrollTo().performTextInput("Mic & lighting tour")
        composeRule.onNodeWithTag("publish_video_url_input").performScrollTo().performTextInput("https://cdn.example.com/tour.mp4")
        composeRule.onNodeWithTag("upload_category_tech").performScrollTo().performClick()
        composeRule.onNodeWithTag("visibility_private_chip").performScrollTo().performClick()

        composeRule.onNodeWithTag("submit_publish_video").performScrollTo().assertIsEnabled().performClick()

        assertTrue(submitted)
        assertEquals("Studio Tour", title)
        assertEquals("Mic & lighting tour", description)
        assertEquals("Tech", category)
        assertEquals("private", visibility)
        assertEquals("https://cdn.example.com/tour.mp4", streamUrl)
    }

    @Test
    fun form_displaysLocalVideoFileSummaryAndClearsSelection() {
        var localUri by mutableStateOf<Uri?>(Uri.parse("content://media/picker/video/42"))
        var cleared = false

        composeRule.setContent {
            PulseCastTheme {
                VideoUploadFormContent(
                    title = "Backstage Clip",
                    onTitleChange = {},
                    description = "",
                    onDescriptionChange = {},
                    category = "Studio",
                    onCategoryChange = {},
                    visibility = "public",
                    onVisibilityChange = {},
                    coverKey = "thumb_1",
                    onToggleCoverKey = {},
                    localVideoUri = localUri,
                    selectedFileName = "backstage_take.mp4",
                    selectedFileSizeBytes = 5L * 1024L * 1024L,
                    durationSec = 75L,
                    externalVideoUrl = "",
                    onExternalVideoUrlChange = {},
                    thumbnailBase64 = null,
                    isProcessingMedia = false,
                    uploadState = VideoUploadUiState.Idle,
                    onPickVideo = {},
                    onClearSelectedVideo = {
                        cleared = true
                        localUri = null
                    },
                    onPickCover = {},
                    recordPermissionContent = {},
                    onDismiss = {},
                    onSubmit = {},
                    showInlinePlayerPreview = false
                )
            }
        }

        composeRule.onNodeWithTag("selected_video_summary").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("backstage_take.mp4").assertIsDisplayed()
        composeRule.onNodeWithText("01:15 · 5.0 MB").assertIsDisplayed()
        composeRule.onNodeWithTag("submit_publish_video").performScrollTo().assertTextContains("Upload & Publish")

        composeRule.onNodeWithTag("remove_video_button").performScrollTo().performClick()
        assertTrue(cleared)
        composeRule.onNodeWithTag("selected_video_summary").assertDoesNotExist()
    }

    @Test
    fun form_showsStorageUploadProgressAndFirestoreSavingStages() {
        var uploadState by mutableStateOf<VideoUploadUiState>(VideoUploadUiState.Uploading(0.64f))

        composeRule.setContent {
            PulseCastTheme {
                VideoUploadFormContent(
                    title = "Launch Recap",
                    onTitleChange = {},
                    description = "",
                    onDescriptionChange = {},
                    category = "Podcast",
                    onCategoryChange = {},
                    visibility = "public",
                    onVisibilityChange = {},
                    coverKey = "thumb_1",
                    onToggleCoverKey = {},
                    localVideoUri = Uri.parse("content://media/picker/video/99"),
                    selectedFileName = "recap.mp4",
                    selectedFileSizeBytes = 2_048_000L,
                    durationSec = 30L,
                    externalVideoUrl = "",
                    onExternalVideoUrlChange = {},
                    thumbnailBase64 = null,
                    isProcessingMedia = false,
                    uploadState = uploadState,
                    onPickVideo = {},
                    onClearSelectedVideo = {},
                    onPickCover = {},
                    recordPermissionContent = {},
                    onDismiss = {},
                    onSubmit = {},
                    showInlinePlayerPreview = false
                )
            }
        }

        composeRule.onNodeWithTag("upload_status_text")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("64%", substring = true)
        composeRule.onNodeWithTag("upload_progress_indicator").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("submit_publish_video").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag("upload_form_close_button").assertIsNotEnabled()

        composeRule.runOnUiThread {
            uploadState = VideoUploadUiState.SavingMetadata
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("upload_status_text")
            .performScrollTo()
            .assertTextContains("Saving video metadata to Firestore", substring = true)
    }

    @Test
    fun form_displaysErrorBannerAndPreservesInput() {
        composeRule.setContent {
            PulseCastTheme {
                VideoUploadFormContent(
                    title = "Keynote Cut",
                    onTitleChange = {},
                    description = "Draft notes",
                    onDescriptionChange = {},
                    category = "Tech",
                    onCategoryChange = {},
                    visibility = "public",
                    onVisibilityChange = {},
                    coverKey = "thumb_1",
                    onToggleCoverKey = {},
                    localVideoUri = Uri.parse("content://media/picker/video/7"),
                    selectedFileName = "keynote.mp4",
                    selectedFileSizeBytes = 1024L,
                    durationSec = 20L,
                    externalVideoUrl = "",
                    onExternalVideoUrlChange = {},
                    thumbnailBase64 = null,
                    isProcessingMedia = false,
                    uploadState = VideoUploadUiState.Error("Firebase Storage bucket not enabled"),
                    onPickVideo = {},
                    onClearSelectedVideo = {},
                    onPickCover = {},
                    recordPermissionContent = {},
                    onDismiss = {},
                    onSubmit = {},
                    showInlinePlayerPreview = false
                )
            }
        }

        composeRule.onNodeWithTag("upload_error_banner").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Firebase Storage bucket not enabled").assertIsDisplayed()
        composeRule.onNodeWithTag("submit_publish_video").performScrollTo().assertIsEnabled()
    }

    @Test
    fun helpers_formatFileSizeAndIgnoreNonStorageDeleteUrl() = runBlocking {
        assertEquals("512 KB", formatFileSize(512L * 1024L))
        assertEquals("2.5 MB", formatFileSize((2.5 * 1024 * 1024).toLong()))

        val uploader = FirebaseStorageVideoUploader {
            error("Should not initialize FirebaseStorage for non-Storage URLs")
        }
        uploader.deleteByDownloadUrl("https://cdn.example.com/public.mp4")

        val rawNotFound = IllegalStateException("Object does not exist at location.")
        assertTrue(FirebaseStorageVideoUploader.isBucketOrObjectNotFound(rawNotFound))
        val mapped = FirebaseStorageVideoUploader.mapFriendlyStorageException(rawNotFound, "gen-lang-client-0094828632")
        assertTrue(mapped.message?.contains("Firebase Storage is not enabled") == true)
    }

    @Test
    fun localFallback_resolvesAndDeletesSavedVideoCopy() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val videoId = "vid_local_test_42"
        val localDir = File(context.filesDir, FirebaseStorageVideoUploader.LOCAL_VIDEOS_DIR).apply { mkdirs() }
        val targetFile = File(localDir, "$videoId.${FirebaseStorageVideoUploader.extensionForMime("video/quicktime")}")
        targetFile.writeBytes(byteArrayOf(1, 2, 3, 4))

        val resolvedUri = FirebaseStorageVideoUploader.resolveLocalVideoUri(context, videoId)
        assertNotNull(resolvedUri)
        assertTrue(resolvedUri!!.endsWith("$videoId.mov"))

        val outcome = VideoUploadOutcome.LocalDeviceOnly(targetFile.absolutePath)
        assertEquals(targetFile.absolutePath, outcome.localFilePath)

        val uploader = FirebaseStorageVideoUploader(appContext = context) {
            error("Should not initialize FirebaseStorage when cleaning up local copy")
        }
        uploader.deleteLocalCopy(videoId)

        assertFalse(targetFile.exists())
        assertNull(FirebaseStorageVideoUploader.resolveLocalVideoUri(context, videoId))
    }
}
