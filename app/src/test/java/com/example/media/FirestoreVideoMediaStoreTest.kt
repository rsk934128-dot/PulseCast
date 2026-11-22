package com.example.media

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import kotlin.random.Random

class FirestoreVideoMediaStoreTest {

    @Test
    fun buildAndParseFirestoreMediaUri_roundTripAndSanitizeExtension() {
        val uri = FirestoreVideoMediaStore.buildFirestoreMediaUri("vid_abc_123", "MOV")
        assertEquals("firestore://videos/vid_abc_123?ext=mov", uri)
        assertTrue(FirestoreVideoMediaStore.isFirestoreMediaUri(uri))

        val parsed = FirestoreVideoMediaStore.parseFirestoreMediaUri(uri)
        assertNotNull(parsed)
        assertEquals("vid_abc_123", parsed!!.videoId)
        assertEquals("mov", parsed.extension)

        val unknownExt = FirestoreVideoMediaStore.parseFirestoreMediaUri("firestore://videos/vid_999?ext=exe")
        assertNotNull(unknownExt)
        assertEquals("mp4", unknownExt!!.extension)

        assertFalse(FirestoreVideoMediaStore.isFirestoreMediaUri("https://example.com/v.mp4"))
        assertNull(FirestoreVideoMediaStore.parseFirestoreMediaUri("https://example.com/v.mp4"))
        assertNull(FirestoreVideoMediaStore.parseFirestoreMediaUri("firestore://videos/"))
    }

    @Test
    fun calculateTotalChunks_handlesExactAndPartialBoundaries() {
        assertEquals(0, FirestoreVideoMediaStore.calculateTotalChunks(0L, 450_000))
        assertEquals(1, FirestoreVideoMediaStore.calculateTotalChunks(1L, 450_000))
        assertEquals(1, FirestoreVideoMediaStore.calculateTotalChunks(450_000L, 450_000))
        assertEquals(2, FirestoreVideoMediaStore.calculateTotalChunks(450_001L, 450_000))
        assertEquals(56, FirestoreVideoMediaStore.calculateTotalChunks(MAX_FIRESTORE_VIDEO_BYTES))
    }

    @Test
    fun streamAndReassembleChunks_outOfOrder_producesByteIdenticalFile() = runBlocking {
        val tempDir = Files.createTempDirectory("pulsecast_chunk_test").toFile()
        try {
            val sourceFile = File(tempDir, "sample.mp4")
            val originalBytes = Random(42).nextBytes(1_050_123) // > 2 chunks at 450 KB
            sourceFile.writeBytes(originalBytes)

            val uploaded = mutableListOf<Triple<Int, Int, String>>()
            FirestoreVideoMediaStore.streamFileAsBase64Chunks(
                file = sourceFile,
                chunkSize = 400_000
            ) { idx, total, b64 ->
                assertEquals(3, total)
                uploaded += Triple(idx, total, b64)
            }

            assertEquals(3, uploaded.size)
            // Simulate Firestore returning documents out of order
            val shuffled = listOf(uploaded[2], uploaded[0], uploaded[1]).map { it.first to it.third }
            val out = ByteArrayOutputStream()
            val written = FirestoreVideoMediaStore.reassembleOrderedBase64Chunks(shuffled, 3, out)

            assertEquals(originalBytes.size.toLong(), written)
            assertArrayEquals(originalBytes, out.toByteArray())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun copyStreamWithMaxLimit_throwsWhenOverMaxBytes() {
        val tempDir = Files.createTempDirectory("pulsecast_limit_test").toFile()
        try {
            val outFile = File(tempDir, "too_large.mp4")
            val payload = ByteArray(1024) { 7 }
            var thrown: VideoTooLargeForFirestoreException? = null
            try {
                FirestoreVideoMediaStore.copyStreamWithMaxLimit(
                    input = ByteArrayInputStream(payload),
                    outputFile = outFile,
                    maxBytes = 512L
                )
            } catch (e: VideoTooLargeForFirestoreException) {
                thrown = e
            }
            assertNotNull(thrown)
            assertTrue(thrown!!.message!!.contains("built-in cloud sync limit"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun reassembleOrderedBase64Chunks_throwsOnMissingChunkIndex() {
        val b64 = FirestoreVideoMediaStore.encodeBytesToBase64(byteArrayOf(1, 2, 3))
        var failed = false
        try {
            FirestoreVideoMediaStore.reassembleOrderedBase64Chunks(
                chunks = listOf(0 to b64, 2 to b64),
                totalChunksExpected = 3,
                output = ByteArrayOutputStream()
            )
        } catch (_: IllegalStateException) {
            failed = true
        }
        assertTrue(failed)
    }
}
