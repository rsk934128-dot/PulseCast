package com.example.media

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

const val FIRESTORE_VIDEO_SCHEME = "firestore://videos/"
const val RAW_CHUNK_SIZE_BYTES = 450_000
const val MAX_FIRESTORE_VIDEO_BYTES = 24L * 1024L * 1024L
const val CLOUD_VIDEOS_CACHE_DIR = "cloud_videos"

data class FirestoreMediaRef(
    val videoId: String,
    val extension: String
)

class VideoTooLargeForFirestoreException(
    val sizeBytes: Long,
    val maxBytes: Long
) : IllegalStateException(
    "Video file (${formatFileSize(sizeBytes)}) exceeds the ${formatFileSize(maxBytes)} built-in cloud sync limit while Firebase Storage is not enabled. Select a smaller clip (≤ ${formatFileSize(maxBytes)}), paste a https:// stream URL, or enable Firebase Storage in Firebase Console."
)

object FirestoreVideoMediaStore {

    fun sanitizeVideoExtension(extension: String?): String {
        val clean = extension?.trim()?.lowercase().orEmpty()
        return when (clean) {
            "mp4", "webm", "mov", "3gp", "mkv" -> clean
            else -> "mp4"
        }
    }

    fun buildFirestoreMediaUri(videoId: String, extension: String = "mp4"): String {
        val cleanId = videoId.trim()
        val ext = sanitizeVideoExtension(extension)
        return "${FIRESTORE_VIDEO_SCHEME}$cleanId?ext=$ext"
    }

    fun isFirestoreMediaUri(uri: String?): Boolean {
        return uri?.trim()?.startsWith(FIRESTORE_VIDEO_SCHEME) == true
    }

    fun parseFirestoreMediaUri(uri: String?): FirestoreMediaRef? {
        val trimmed = uri?.trim() ?: return null
        if (!trimmed.startsWith(FIRESTORE_VIDEO_SCHEME)) return null
        val remainder = trimmed.removePrefix(FIRESTORE_VIDEO_SCHEME)
        val qIdx = remainder.indexOf('?')
        val rawId = (if (qIdx >= 0) remainder.substring(0, qIdx) else remainder).trim()
        if (rawId.isBlank()) return null
        var ext = "mp4"
        if (qIdx >= 0 && qIdx + 1 < remainder.length) {
            val query = remainder.substring(qIdx + 1)
            query.split('&').forEach { pair ->
                val eqIdx = pair.indexOf('=')
                if (eqIdx > 0) {
                    val key = pair.substring(0, eqIdx).trim()
                    val value = pair.substring(eqIdx + 1).trim()
                    if (key == "ext" && value.isNotEmpty()) {
                        ext = sanitizeVideoExtension(value)
                    }
                }
            }
        }
        return FirestoreMediaRef(videoId = rawId, extension = ext)
    }

    fun calculateTotalChunks(
        byteCount: Long,
        chunkSize: Int = RAW_CHUNK_SIZE_BYTES
    ): Int {
        require(chunkSize > 0) { "chunkSize must be positive" }
        if (byteCount <= 0L) return 0
        return ((byteCount + chunkSize - 1L) / chunkSize.toLong()).toInt()
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun encodeBytesToBase64(bytes: ByteArray, length: Int = bytes.size): String {
        return Base64.Default.encode(bytes, 0, length)
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun decodeBase64ToBytes(dataBase64: String): ByteArray {
        return Base64.Default.decode(dataBase64)
    }

    fun copyStreamWithMaxLimit(
        input: InputStream,
        outputFile: File,
        maxBytes: Long = MAX_FIRESTORE_VIDEO_BYTES
    ): Long {
        outputFile.parentFile?.mkdirs()
        val buffer = ByteArray(64 * 1024)
        var totalRead = 0L
        outputFile.outputStream().use { out ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                totalRead += read
                if (totalRead > maxBytes) {
                    throw VideoTooLargeForFirestoreException(totalRead, maxBytes)
                }
                out.write(buffer, 0, read)
            }
            out.flush()
        }
        return totalRead
    }

    suspend fun streamFileAsBase64Chunks(
        file: File,
        chunkSize: Int = RAW_CHUNK_SIZE_BYTES,
        onChunk: suspend (index: Int, totalChunks: Int, dataBase64: String) -> Unit
    ) {
        val fileLength = file.length()
        if (fileLength <= 0L) {
            throw IllegalStateException("Selected video file is empty.")
        }
        val totalChunks = calculateTotalChunks(fileLength, chunkSize)
        val buffer = ByteArray(chunkSize)
        var chunkIndex = 0
        file.inputStream().use { input ->
            while (true) {
                var filled = 0
                while (filled < chunkSize) {
                    val read = input.read(buffer, filled, chunkSize - filled)
                    if (read <= 0) break
                    filled += read
                }
                if (filled <= 0) break
                val encoded = encodeBytesToBase64(buffer, filled)
                onChunk(chunkIndex, totalChunks, encoded)
                chunkIndex++
            }
        }
    }

    fun reassembleOrderedBase64Chunks(
        chunks: List<Pair<Int, String>>,
        totalChunksExpected: Int?,
        output: OutputStream
    ): Long {
        if (chunks.isEmpty()) {
            throw IllegalStateException("Video media data was not found in cloud storage.")
        }
        val sorted = chunks.sortedBy { it.first }
        val expectedCount = totalChunksExpected ?: sorted.size
        if (sorted.size != expectedCount) {
            throw IllegalStateException("Video stream is incomplete (${sorted.size}/$expectedCount parts).")
        }
        var totalWritten = 0L
        sorted.forEachIndexed { expectedIdx, (actualIdx, base64) ->
            if (actualIdx != expectedIdx) {
                throw IllegalStateException("Video stream part $expectedIdx is missing.")
            }
            val bytes = decodeBase64ToBytes(base64)
            output.write(bytes)
            totalWritten += bytes.size
        }
        output.flush()
        return totalWritten
    }

    fun cloudVideoCacheDir(context: Context): File {
        return File(context.cacheDir, CLOUD_VIDEOS_CACHE_DIR).apply { mkdirs() }
    }

    fun cloudVideoTargetFile(context: Context, videoId: String, extension: String): File {
        val safeExt = sanitizeVideoExtension(extension)
        return File(cloudVideoCacheDir(context), "$videoId.$safeExt")
    }

    fun resolveCachedVideoFile(context: Context, videoId: String, extension: String? = null): File? {
        if (videoId.isBlank()) return null
        val dir = File(context.cacheDir, CLOUD_VIDEOS_CACHE_DIR)
        if (!dir.exists()) return null
        if (!extension.isNullOrBlank()) {
            val exact = File(dir, "$videoId.${sanitizeVideoExtension(extension)}")
            if (exact.isFile && exact.length() > 0L) return exact
        }
        return dir.listFiles { file ->
            file.isFile && file.length() > 0L && file.name.startsWith("$videoId.") && !file.name.endsWith(".downloading")
        }?.firstOrNull()
    }

    fun resolveCachedVideoUri(context: Context, videoId: String, extension: String? = null): String? {
        val file = resolveCachedVideoFile(context, videoId, extension) ?: return null
        return Uri.fromFile(file).toString()
    }

    fun deleteCachedVideo(context: Context, videoId: String) {
        if (videoId.isBlank()) return
        val dir = File(context.cacheDir, CLOUD_VIDEOS_CACHE_DIR)
        if (!dir.exists()) return
        dir.listFiles { file -> file.name.startsWith("$videoId.") }?.forEach { file ->
            runCatching { file.delete() }
        }
    }
}
