package com.example.calls

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.CallSession

class CallNotificationManager(private val context: Context) {
    private val appContext = context.applicationContext
    private val notificationManager = NotificationManagerCompat.from(appContext)

    fun ensureChannels() {
        val ringtoneUri = runCatching {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }.getOrNull()

        val incomingChannel = NotificationChannelCompat.Builder(
            INCOMING_CHANNEL_ID,
            NotificationManagerCompat.IMPORTANCE_HIGH
        )
            .setName(appContext.getString(R.string.call_channel_incoming_name))
            .setDescription(appContext.getString(R.string.call_channel_incoming_desc))
            .setVibrationEnabled(true)
            .setShowBadge(true)
            .apply {
                if (ringtoneUri != null) {
                    setSound(
                        ringtoneUri,
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setLegacyStreamType(AudioManager.STREAM_RING)
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .build()
                    )
                }
            }
            .build()

        val ongoingChannel = NotificationChannelCompat.Builder(
            ONGOING_CHANNEL_ID,
            NotificationManagerCompat.IMPORTANCE_LOW
        )
            .setName(appContext.getString(R.string.call_channel_ongoing_name))
            .setDescription(appContext.getString(R.string.call_channel_ongoing_desc))
            .setSound(null, null)
            .setVibrationEnabled(false)
            .build()

        runCatching {
            notificationManager.createNotificationChannel(incomingChannel)
            notificationManager.createNotificationChannel(ongoingChannel)
        }
    }

    fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun showIncomingCallNotification(session: CallSession) {
        ensureChannels()
        if (!hasNotificationPermission()) return

        val callerName = session.hostName.ifBlank { "PulseCast Caller" }
        val caller = Person.Builder()
            .setName(callerName)
            .setImportant(true)
            .build()

        val callSubtitle = when (session.callType) {
            "audio" -> appContext.getString(R.string.incoming_call_audio_label)
            "group" -> appContext.getString(R.string.incoming_call_group_label)
            else -> appContext.getString(R.string.incoming_call_video_label)
        }

        val fullScreenIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(CallActionReceiver.EXTRA_ROOM_CODE, session.roomCode)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            appContext,
            REQUEST_CONTENT,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val answerIntent = Intent(appContext, MainActivity::class.java).apply {
            action = CallActionReceiver.ACTION_ANSWER_CALL
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(CallActionReceiver.EXTRA_ROOM_CODE, session.roomCode)
            putExtra(CallActionReceiver.EXTRA_CALL_ACTION, CallActionReceiver.ACTION_ANSWER_CALL)
        }
        val answerPendingIntent = PendingIntent.getActivity(
            appContext,
            REQUEST_ANSWER,
            answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val declineIntent = Intent(appContext, CallActionReceiver::class.java).apply {
            action = CallActionReceiver.ACTION_DECLINE_CALL
            putExtra(CallActionReceiver.EXTRA_ROOM_CODE, session.roomCode)
        }
        val declinePendingIntent = PendingIntent.getBroadcast(
            appContext,
            REQUEST_DECLINE,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, INCOMING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_call_notification)
            .setContentTitle(callerName)
            .setContentText("$callSubtitle · Room ${session.roomCode}")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setColorized(true)
            .setContentIntent(contentPendingIntent)
            .setFullScreenIntent(contentPendingIntent, true)
            .addPerson(caller)
            .setStyle(
                NotificationCompat.CallStyle.forIncomingCall(
                    caller,
                    declinePendingIntent,
                    answerPendingIntent
                )
            )
            .build()

        runCatching {
            notificationManager.notify(INCOMING_NOTIFICATION_ID, notification)
        }
    }

    fun showOngoingCallNotification(session: CallSession, peerDisplayName: String? = null) {
        ensureChannels()
        if (!hasNotificationPermission()) return

        val label = peerDisplayName?.takeIf { it.isNotBlank() }
            ?: session.participantNames?.firstOrNull { it.isNotBlank() && it != session.hostName }
            ?: session.hostName.ifBlank { "PulseCast Call" }
        val peer = Person.Builder()
            .setName(label)
            .setImportant(true)
            .build()

        val contentIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(CallActionReceiver.EXTRA_ROOM_CODE, session.roomCode)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            appContext,
            REQUEST_ONGOING_CONTENT,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val hangupIntent = Intent(appContext, CallActionReceiver::class.java).apply {
            action = CallActionReceiver.ACTION_HANGUP_CALL
            putExtra(CallActionReceiver.EXTRA_ROOM_CODE, session.roomCode)
        }
        val hangupPendingIntent = PendingIntent.getBroadcast(
            appContext,
            REQUEST_HANGUP,
            hangupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, ONGOING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_call_notification)
            .setContentTitle(label)
            .setContentText("Live ${session.callType} call · Room ${session.roomCode}")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPendingIntent)
            .addPerson(peer)
            .setStyle(
                NotificationCompat.CallStyle.forOngoingCall(peer, hangupPendingIntent)
            )
            .build()

        runCatching {
            notificationManager.notify(ONGOING_NOTIFICATION_ID, notification)
        }
    }

    fun dismissIncomingCallNotification() {
        runCatching { notificationManager.cancel(INCOMING_NOTIFICATION_ID) }
    }

    fun dismissOngoingCallNotification() {
        runCatching { notificationManager.cancel(ONGOING_NOTIFICATION_ID) }
    }

    companion object {
        const val INCOMING_CHANNEL_ID = "pulsecast_incoming_calls"
        const val ONGOING_CHANNEL_ID = "pulsecast_ongoing_calls"
        const val INCOMING_NOTIFICATION_ID = 4101
        const val ONGOING_NOTIFICATION_ID = 4102
        private const val REQUEST_CONTENT = 1001
        private const val REQUEST_ANSWER = 1002
        private const val REQUEST_DECLINE = 1003
        private const val REQUEST_ONGOING_CONTENT = 1004
        private const val REQUEST_HANGUP = 1005
    }
}
