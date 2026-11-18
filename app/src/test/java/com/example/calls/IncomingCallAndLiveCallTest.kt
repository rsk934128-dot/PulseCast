package com.example.calls

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.CALL_RING_WINDOW_MS
import com.example.data.CallSession
import com.example.data.isFreshRingingForUser
import com.example.ui.screens.ActiveCallStage
import com.example.ui.theme.PulseCastTheme
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class IncomingCallAndLiveCallTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun isFreshRingingForUser_filtersHostEndedNonParticipantAndStaleCalls() {
        val nowMs = 1_750_000_000_000L
        val freshTimestamp = Timestamp(Date(nowMs - 5_000L))
        val staleTimestamp = Timestamp(Date(nowMs - CALL_RING_WINDOW_MS - 1_000L))

        val incoming = CallSession(
            id = "PULSE-1122",
            hostId = "uid_alice",
            hostName = "Alice",
            roomCode = "PULSE-1122",
            callType = "audio",
            status = "ringing",
            participantIds = listOf("uid_alice", "uid_bob"),
            createdAt = freshTimestamp,
            updatedAt = freshTimestamp
        )

        assertTrue(incoming.isFreshRingingForUser("uid_bob", nowMs))
        assertFalse(incoming.isFreshRingingForUser("uid_alice", nowMs))
        assertFalse(incoming.isFreshRingingForUser("uid_charol", nowMs))
        assertFalse(incoming.copy(status = "active").isFreshRingingForUser("uid_bob", nowMs))
        assertFalse(incoming.copy(status = "ended").isFreshRingingForUser("uid_bob", nowMs))
        assertFalse(
            incoming.copy(createdAt = staleTimestamp, updatedAt = staleTimestamp)
                .isFreshRingingForUser("uid_bob", nowMs)
        )
    }

    @Test
    fun incomingCallOverlay_displaysCallerInfoAndTriggersAnswerAndDecline() {
        val session = CallSession(
            id = "PULSE-7788",
            hostId = "uid_alice",
            hostName = "Alice Vance",
            roomCode = "PULSE-7788",
            callType = "video",
            status = "ringing",
            participantIds = listOf("uid_alice", "uid_bob")
        )
        var answered = false
        var declined = false

        composeRule.setContent {
            PulseCastTheme {
                IncomingCallOverlay(
                    session = session,
                    onAnswer = { answered = true },
                    onDecline = { declined = true }
                )
            }
        }

        composeRule.onNodeWithTag("incoming_call_overlay").assertIsDisplayed()
        composeRule.onNodeWithText("Alice Vance").assertIsDisplayed()
        composeRule.onNodeWithText("Incoming Video Call").assertIsDisplayed()
        composeRule.onNodeWithText("Ringing… · Room PULSE-7788").assertIsDisplayed()

        composeRule.onNodeWithTag("incoming_call_accept_button").performClick()
        assertTrue(answered)

        composeRule.onNodeWithTag("incoming_call_decline_button").performClick()
        assertTrue(declined)
    }

    @Test
    fun callNotificationManager_andRingtoneController_createChannelsPostAndStopCleanly() {
        val app = ApplicationProvider.getApplicationContext<Context>().applicationContext as Application
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        val notificationManager = CallNotificationManager(app)
        val systemNm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNm = shadowOf(systemNm)

        val session = CallSession(
            id = "PULSE-5510",
            hostId = "uid_alice",
            hostName = "Alice Vance",
            roomCode = "PULSE-5510",
            callType = "audio",
            status = "ringing",
            participantIds = listOf("uid_alice", "uid_bob")
        )

        notificationManager.showIncomingCallNotification(session)
        assertNotNull(systemNm.getNotificationChannel(CallNotificationManager.INCOMING_CHANNEL_ID))
        assertNotNull(systemNm.getNotificationChannel(CallNotificationManager.ONGOING_CHANNEL_ID))
        assertEquals(1, shadowNm.allNotifications.size)

        notificationManager.dismissIncomingCallNotification()
        assertEquals(0, shadowNm.allNotifications.size)

        val ringtoneController = CallRingtoneController(app)
        ringtoneController.startIncomingRingtone(session.roomCode)
        assertTrue(ringtoneController.isRinging)

        ringtoneController.stopIncomingRingtone()
        assertFalse(ringtoneController.isRinging)
        ringtoneController.release()
    }

    @Test
    fun activeCallStage_showsControlsAndEndsCallCleanly() {
        val app = ApplicationProvider.getApplicationContext<Context>().applicationContext as Application
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)

        val session = CallSession(
            id = "PULSE-9901",
            hostId = "uid_alice",
            hostName = "Alice Vance",
            roomCode = "PULSE-9901",
            callType = "audio",
            status = "active",
            participantIds = listOf("uid_alice", "uid_bob"),
            participantNames = listOf("Alice Vance", "Bob Rivera")
        )
        var endedWithDuration: Long? = null

        composeRule.setContent {
            PulseCastTheme {
                ActiveCallStage(
                    session = session,
                    currentUserId = "uid_bob",
                    onEndCall = { endedWithDuration = it }
                )
            }
        }

        composeRule.onNodeWithTag("active_call_stage").assertIsDisplayed()
        composeRule.onNodeWithTag("toggle_mute_button").assertIsDisplayed()
        composeRule.onNodeWithTag("toggle_speaker_button").assertIsDisplayed()
        composeRule.onNodeWithTag("toggle_camera_button").assertIsDisplayed()
        composeRule.onNodeWithTag("end_call_button").performClick()

        assertNotNull(endedWithDuration)
    }
}
