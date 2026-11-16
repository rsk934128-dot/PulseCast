package com.example.auth

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.ui.screens.AuthScreen
import com.example.ui.theme.PulseCastTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun authScreen_displaysBrandingAndGoogleSignInButton() {
        composeRule.setContent {
            PulseCastTheme {
                AuthScreen(
                    onAuthSuccess = {},
                    enableAutoSignIn = false
                )
            }
        }

        composeRule.onNodeWithText("PulseCast").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("google_sign_in_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("google_sign_in_button").assertIsEnabled()
        composeRule.onNodeWithText("Sign in with Google").assertIsDisplayed()
        composeRule.onNodeWithText("Protected by Firebase Authentication").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun googleSignInClick_showsLoadingThenInvokesSuccess() {
        var authSucceeded = false
        var triggerSuccess: (() -> Unit)? = null

        composeRule.setContent {
            PulseCastTheme {
                AuthScreen(
                    onAuthSuccess = { authSucceeded = true },
                    enableAutoSignIn = false,
                    onGoogleSignInOverride = { onSuccess, _, _ ->
                        triggerSuccess = onSuccess
                    }
                )
            }
        }

        composeRule.onNodeWithTag("google_sign_in_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("google_sign_in_button").assertIsNotEnabled()
        composeRule.onNodeWithText("Signing in with Google…").assertIsDisplayed()

        composeRule.runOnIdle {
            triggerSuccess?.invoke()
        }
        composeRule.waitForIdle()

        assertTrue(authSucceeded)
        composeRule.onNodeWithTag("google_sign_in_button").assertIsEnabled()
    }

    @Test
    fun googleSignInError_rendersDismissibleErrorBanner() {
        composeRule.setContent {
            PulseCastTheme {
                AuthScreen(
                    onAuthSuccess = {},
                    enableAutoSignIn = false,
                    onGoogleSignInOverride = { _, onError, _ ->
                        onError("No Google account found on this device.")
                    }
                )
            }
        }

        composeRule.onNodeWithTag("google_sign_in_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("auth_error_banner").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("No Google account found on this device.").assertIsDisplayed()

        composeRule.onNodeWithTag("auth_error_dismiss_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("auth_error_banner").assertDoesNotExist()
    }

    @Test
    fun googleSignInCancellation_resetsButtonWithoutErrorBanner() {
        composeRule.setContent {
            PulseCastTheme {
                AuthScreen(
                    onAuthSuccess = {},
                    enableAutoSignIn = false,
                    onGoogleSignInOverride = { _, _, onCancelled ->
                        onCancelled()
                    }
                )
            }
        }

        composeRule.onNodeWithTag("google_sign_in_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("google_sign_in_button").assertIsEnabled()
        composeRule.onNodeWithTag("auth_error_banner").assertDoesNotExist()
    }
}
