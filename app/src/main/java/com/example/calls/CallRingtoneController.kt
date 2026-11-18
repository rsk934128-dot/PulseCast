package com.example.calls

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CallRingtoneController(private val context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var mediaPlayer: MediaPlayer? = null
    private var fallbackRingtone: Ringtone? = null
    private var incomingTimeoutJob: Job? = null
    private var outgoingRingbackJob: Job? = null
    private var toneGenerator: ToneGenerator? = null

    @Volatile
    var isRinging: Boolean = false
        private set

    @Volatile
    var isPlayingRingback: Boolean = false
        private set

    private var activeRingingCallId: String? = null
    private var activeRingbackCallId: String? = null

    fun startIncomingRingtone(callId: String, maxDurationMs: Long = 45_000L) {
        if (isRinging && activeRingingCallId == callId) return
        stopIncomingRingtone()
        activeRingingCallId = callId
        isRinging = true
        activeControllerRef = this

        val ringtoneUri = resolveRingtoneUri()
        var startedPlayer = false
        if (ringtoneUri != null) {
            startedPlayer = runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .build()
                    )
                    setDataSource(appContext, ringtoneUri)
                    isLooping = true
                    prepare()
                    start()
                    mediaPlayer = this
                }
                true
            }.getOrDefault(false)

            if (!startedPlayer) {
                runCatching {
                    val ringtone = RingtoneManager.getRingtone(appContext, ringtoneUri)
                    if (ringtone != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            ringtone.isLooping = true
                        }
                        ringtone.audioAttributes = AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .build()
                        ringtone.play()
                        fallbackRingtone = ringtone
                    }
                }
            }
        }

        startVibrationPattern()

        incomingTimeoutJob = scope.launch {
            delay(maxDurationMs)
            if (isActive && activeRingingCallId == callId) {
                stopIncomingRingtone()
            }
        }
    }

    fun stopIncomingRingtone() {
        incomingTimeoutJob?.cancel()
        incomingTimeoutJob = null
        activeRingingCallId = null
        isRinging = false

        runCatching {
            mediaPlayer?.let { player ->
                if (player.isPlaying) player.stop()
                player.release()
            }
        }
        mediaPlayer = null

        runCatching { fallbackRingtone?.stop() }
        fallbackRingtone = null

        stopVibration()
    }

    fun startOutgoingRingback(callId: String) {
        if (isPlayingRingback && activeRingbackCallId == callId) return
        stopOutgoingRingback()
        activeRingbackCallId = callId
        isPlayingRingback = true

        outgoingRingbackJob = scope.launch(Dispatchers.Default) {
            val generator = runCatching {
                ToneGenerator(AudioManager.STREAM_VOICE_CALL, 48)
            }.getOrNull()
            toneGenerator = generator
            try {
                while (isActive && isPlayingRingback && activeRingbackCallId == callId) {
                    runCatching {
                        generator?.startTone(ToneGenerator.TONE_SUP_RINGTONE, 1400)
                    }
                    delay(3200L)
                }
            } finally {
                runCatching { generator?.stopTone() }
                runCatching { generator?.release() }
                if (toneGenerator === generator) {
                    toneGenerator = null
                }
            }
        }
    }

    fun stopOutgoingRingback() {
        outgoingRingbackJob?.cancel()
        outgoingRingbackJob = null
        activeRingbackCallId = null
        isPlayingRingback = false
        runCatching { toneGenerator?.stopTone() }
        runCatching { toneGenerator?.release() }
        toneGenerator = null
    }

    fun release() {
        stopIncomingRingtone()
        stopOutgoingRingback()
        if (activeControllerRef === this) {
            activeControllerRef = null
        }
    }

    private fun resolveRingtoneUri(): Uri? = runCatching {
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }.getOrNull()

    private fun startVibrationPattern() {
        runCatching {
            val vibrator = getVibrator() ?: return
            if (!vibrator.hasVibrator()) return
            val pattern = longArrayOf(0L, 750L, 650L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, 0)
            }
        }
    }

    private fun stopVibration() {
        runCatching {
            getVibrator()?.cancel()
        }
    }

    private fun getVibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    companion object {
        @Volatile
        private var activeControllerRef: CallRingtoneController? = null

        fun stopAllGlobal(@Suppress("UNUSED_PARAMETER") context: Context) {
            activeControllerRef?.stopIncomingRingtone()
            activeControllerRef?.stopOutgoingRingback()
        }
    }
}
