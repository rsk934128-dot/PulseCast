package com.example.calls

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
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
import kotlin.math.PI
import kotlin.math.sin

class CallRingtoneController(private val context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var mediaPlayer: MediaPlayer? = null
    private var fallbackRingtone: Ringtone? = null
    private var incomingSynthJob: Job? = null
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

        val audioManager = runCatching {
            appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        }.getOrNull()
        val ringerMode = runCatching { audioManager?.ringerMode }.getOrNull() ?: AudioManager.RINGER_MODE_NORMAL
        val ringVolume = runCatching { audioManager?.getStreamVolume(AudioManager.STREAM_RING) ?: 1 }.getOrDefault(1)

        val ringtoneUri = resolveRingtoneUri()
        var startedNativeRingtone = false
        if (ringtoneUri != null && ringerMode != AudioManager.RINGER_MODE_SILENT) {
            startedNativeRingtone = runCatching {
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

            if (!startedNativeRingtone) {
                startedNativeRingtone = runCatching {
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
                        true
                    } else {
                        false
                    }
                }.getOrDefault(false)
            }
        }

        // Synthesize an audible dual-tone ringtone fallback if system ringtone failed/missing
        // or if the device/emulator ring stream is muted while ringer mode is normal.
        if (ringerMode != AudioManager.RINGER_MODE_SILENT && (!startedNativeRingtone || ringVolume <= 0)) {
            startSynthesizedIncomingRing(callId, useMediaUsage = ringVolume <= 0 || !startedNativeRingtone)
        }

        if (ringerMode != AudioManager.RINGER_MODE_SILENT) {
            startVibrationPattern()
        }

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
        incomingSynthJob?.cancel()
        incomingSynthJob = null
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
            val audioManager = runCatching {
                appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            }.getOrNull()
            val voiceVol = runCatching { audioManager?.getStreamVolume(AudioManager.STREAM_VOICE_CALL) ?: 1 }.getOrDefault(1)

            val generator = runCatching {
                ToneGenerator(
                    if (voiceVol > 0) AudioManager.STREAM_VOICE_CALL else AudioManager.STREAM_MUSIC,
                    85
                )
            }.getOrNull()
            toneGenerator = generator

            try {
                while (isActive && isPlayingRingback && activeRingbackCallId == callId) {
                    val playedTone = if (generator != null) {
                        runCatching {
                            generator.startTone(ToneGenerator.TONE_SUP_RINGTONE, 1200)
                        }.getOrDefault(false)
                    } else {
                        false
                    }
                    if (!playedTone) {
                        playDualToneBurst(
                            freqA = 440.0,
                            freqB = 480.0,
                            durationMs = 1100,
                            amplitude = 0.25,
                            usage = AudioAttributes.USAGE_MEDIA
                        )
                    } else {
                        delay(1200L)
                    }
                    delay(1900L)
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

    private fun startSynthesizedIncomingRing(callId: String, useMediaUsage: Boolean) {
        val usage = if (useMediaUsage) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_NOTIFICATION_RINGTONE
        incomingSynthJob = scope.launch(Dispatchers.Default) {
            while (isActive && isRinging && activeRingingCallId == callId) {
                playDualToneBurst(
                    freqA = 523.25,
                    freqB = 659.25,
                    durationMs = 360,
                    amplitude = 0.35,
                    usage = usage
                )
                delay(120L)
                if (!isActive || !isRinging || activeRingingCallId != callId) break
                playDualToneBurst(
                    freqA = 659.25,
                    freqB = 783.99,
                    durationMs = 460,
                    amplitude = 0.35,
                    usage = usage
                )
                delay(1200L)
            }
        }
    }

    private suspend fun playDualToneBurst(
        freqA: Double,
        freqB: Double,
        durationMs: Int,
        amplitude: Double,
        usage: Int
    ) {
        val sampleRate = 22050
        val totalSamples = (sampleRate * durationMs) / 1000
        if (totalSamples <= 0) return

        val pcm = ShortArray(totalSamples)
        val fadeSamples = (sampleRate * 25) / 1000
        for (i in 0 until totalSamples) {
            val env = when {
                i < fadeSamples -> i.toDouble() / fadeSamples.coerceAtLeast(1)
                i > totalSamples - fadeSamples -> (totalSamples - i).toDouble() / fadeSamples.coerceAtLeast(1)
                else -> 1.0
            }
            val t = i.toDouble() / sampleRate
            val sample = (sin(2.0 * PI * freqA * t) + sin(2.0 * PI * freqB * t)) * 0.5 * amplitude * env
            pcm[i] = (sample * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        val track = runCatching {
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(totalSamples * 2)
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(usage)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        }.getOrNull()

        if (track == null) {
            delay(durationMs.toLong())
            return
        }

        try {
            runCatching {
                track.write(pcm, 0, pcm.size)
                track.play()
            }
            delay(durationMs.toLong())
        } finally {
            runCatching { track.stop() }
            runCatching { track.release() }
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
