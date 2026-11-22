package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale

data class ExtractedVideoMeta(
    val durationSec: Long,
    val thumbnailBase64: String?,
    val displayName: String? = null,
    val sizeBytes: Long? = null,
    val mimeType: String? = null
)

suspend fun encodeUriImageToBase64(
    context: Context,
    uri: Uri,
    maxDimension: Int = 560,
    quality: Int = 72
): String? = withContext(Dispatchers.IO) {
    try {
        val rawBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: return@withContext null

        bitmapToCompactBase64(rawBitmap, maxDimension, quality)
    } catch (_: Exception) {
        null
    }
}

suspend fun extractVideoMetadata(
    context: Context,
    uri: Uri
): ExtractedVideoMeta = withContext(Dispatchers.IO) {
    var displayName: String? = uri.lastPathSegment
    var sizeBytes: Long? = null
    val mimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull()

    runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) {
                    cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { displayName = it }
                }
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                    sizeBytes = cursor.getLong(sizeIdx).takeIf { it > 0L }
                }
            }
        }
    }

    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val frame = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: retriever.frameAtTime
        val thumbBase64 = frame?.let { bitmapToCompactBase64(it, 520, 70) }
        ExtractedVideoMeta(
            durationSec = (durationMs / 1000L).coerceAtLeast(1L),
            thumbnailBase64 = thumbBase64,
            displayName = displayName,
            sizeBytes = sizeBytes,
            mimeType = mimeType
        )
    } catch (_: Exception) {
        ExtractedVideoMeta(
            durationSec = 15L,
            thumbnailBase64 = null,
            displayName = displayName,
            sizeBytes = sizeBytes,
            mimeType = mimeType
        )
    } finally {
        try {
            retriever.release()
        } catch (_: Exception) {
        }
    }
}

fun decodeBase64ToBitmap(base64: String?): Bitmap? {
    if (base64.isNullOrBlank()) return null
    return try {
        val cleaned = base64.trim().let { raw ->
            val commaIdx = raw.indexOf(',')
            if (raw.startsWith("data:", ignoreCase = true) && commaIdx > 0) {
                raw.substring(commaIdx + 1)
            } else {
                raw
            }
        }
        val bytes = Base64.decode(cleaned, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (_: Exception) {
        null
    }
}

private fun bitmapToCompactBase64(
    bitmap: Bitmap,
    maxDimension: Int,
    quality: Int
): String? {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 0 || height <= 0) return null
    val scale = minOf(maxDimension.toFloat() / width, maxDimension.toFloat() / height, 1f)
    val scaled = if (scale < 1f) {
        Bitmap.createScaledBitmap(
            bitmap,
            (width * scale).toInt().coerceAtLeast(1),
            (height * scale).toInt().coerceAtLeast(1),
            true
        )
    } else {
        bitmap
    }
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
    val bytes = out.toByteArray()
    if (bytes.size > 175_000) {
        out.reset()
        scaled.compress(Bitmap.CompressFormat.JPEG, 50, out)
    }
    val finalBytes = out.toByteArray()
    return if (finalBytes.size <= 185_000) {
        Base64.encodeToString(finalBytes, Base64.NO_WRAP)
    } else {
        null
    }
}

fun formatDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    val mins = safe / 60L
    val secs = safe % 60L
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}

fun formatFileSize(bytes: Long?): String {
    if (bytes == null || bytes <= 0L) return ""
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.0f KB", kb.coerceAtLeast(1.0))
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.1f MB", mb)
}

class VoiceNoteRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var outputFile: File? = null
    private var startMillis: Long = 0L

    fun startRecording(): Boolean {
        return try {
            stopRecording()
            val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioEncodingBitRate(64000)
            mr.setAudioSamplingRate(22050)
            mr.setOutputFile(file.absolutePath)
            mr.prepare()
            mr.start()
            recorder = mr
            outputFile = file
            startMillis = System.currentTimeMillis()
            true
        } catch (_: Exception) {
            recorder = null
            false
        }
    }

    fun stopRecording(): Long {
        val elapsedSec = ((System.currentTimeMillis() - startMillis) / 1000L).coerceAtLeast(1L)
        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {
            try {
                recorder?.release()
            } catch (_: Exception) {
            }
        }
        recorder = null
        return elapsedSec
    }

    fun playLastOrStop(onComplete: () -> Unit) {
        val file = outputFile
        if (file == null || !file.exists()) {
            onComplete()
            return
        }
        try {
            player?.release()
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    it.release()
                    player = null
                    onComplete()
                }
                prepare()
                start()
            }
        } catch (_: Exception) {
            onComplete()
        }
    }

    fun release() {
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        try {
            player?.release()
        } catch (_: Exception) {
        }
        recorder = null
        player = null
    }
}
