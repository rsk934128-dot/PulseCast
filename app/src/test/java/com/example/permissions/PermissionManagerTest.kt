package com.example.permissions

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.ui.theme.PulseCastTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PermissionManagerTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun deniedCameraAndMic_rendersPermissionRequestCardInsteadOfContent() {
        val app = ApplicationProvider.getApplicationContext<Context>().applicationContext as Application
        shadowOf(app).denyPermissions(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

        composeRule.setContent {
            PulseCastTheme {
                PermissionManager(
                    featureTitle = "Video Call Studio",
                    rationaleMessage = "Camera and mic needed."
                ) {
                    Text("Studio Active", modifier = Modifier.testTag("studio_active_content"))
                }
            }
        }

        composeRule.onNodeWithTag("permission_manager_card").assertIsDisplayed()
        composeRule.onNodeWithTag("permission_grant_button").assertIsDisplayed()
        composeRule.onNodeWithText("Allow Camera & Microphone").assertIsDisplayed()
        composeRule.onNodeWithText("Camera").assertIsDisplayed()
        composeRule.onNodeWithText("Mic").assertIsDisplayed()
        composeRule.onNodeWithTag("studio_active_content").assertDoesNotExist()
    }

    @Test
    fun partialMicOnlyGrant_updatesChipsAndKeepsCameraGateActive() {
        val app = ApplicationProvider.getApplicationContext<Context>().applicationContext as Application
        shadowOf(app).denyPermissions(Manifest.permission.CAMERA)
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)

        composeRule.setContent {
            PulseCastTheme {
                PermissionManager(
                    featureTitle = "Video Publishing",
                    rationaleMessage = "Camera and mic needed."
                ) {
                    Text("Recorder Ready", modifier = Modifier.testTag("recorder_ready_content"))
                }
            }
        }

        composeRule.onNodeWithTag("permission_manager_card").assertIsDisplayed()
        composeRule.onNodeWithText("Mic OK").assertIsDisplayed()
        composeRule.onNodeWithText("Camera").assertIsDisplayed()
        composeRule.onNodeWithTag("recorder_ready_content").assertDoesNotExist()
    }

    @Test
    fun grantedCameraAndMic_rendersContentAndInvokesCallback() {
        val app = ApplicationProvider.getApplicationContext<Context>().applicationContext as Application
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        var grantedCallbackInvoked = false

        composeRule.setContent {
            PulseCastTheme {
                PermissionManager(
                    featureTitle = "Video Call Studio",
                    rationaleMessage = "Camera and mic needed.",
                    onAllPermissionsGranted = { grantedCallbackInvoked = true }
                ) { scope ->
                    Text(
                        text = if (scope.hasCamera && scope.hasMicrophone) "Studio Active" else "Missing",
                        modifier = Modifier.testTag("studio_active_content")
                    )
                }
            }
        }

        composeRule.onNodeWithTag("studio_active_content").assertIsDisplayed()
        composeRule.onNodeWithText("Studio Active").assertIsDisplayed()
        composeRule.onNodeWithTag("permission_grant_button").assertDoesNotExist()
        assertTrue(grantedCallbackInvoked)
    }

    @Test
    fun microphoneOnlyRequest_usesMicrophoneCopyWhenDenied() {
        val app = ApplicationProvider.getApplicationContext<Context>().applicationContext as Application
        shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO)

        composeRule.setContent {
            PulseCastTheme {
                PermissionManager(
                    permissions = listOf(Manifest.permission.RECORD_AUDIO),
                    featureTitle = "HD Audio Call",
                    rationaleMessage = "Microphone access required for voice."
                ) {
                    Text("Audio Connected", modifier = Modifier.testTag("audio_connected_content"))
                }
            }
        }

        composeRule.onNodeWithTag("permission_manager_card").assertIsDisplayed()
        composeRule.onNodeWithText("Allow Microphone").assertIsDisplayed()
        composeRule.onNodeWithText("Mic").assertIsDisplayed()
        composeRule.onNodeWithText("Camera").assertDoesNotExist()
        composeRule.onNodeWithTag("audio_connected_content").assertDoesNotExist()
    }
}
