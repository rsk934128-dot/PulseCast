package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.PulseCastRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class PulseCastRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun publishVideo_authenticatedOwner_persistsAndStreamsToFeed() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = PulseCastRepository(firestore)

        val videoId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.publishVideo(
                uid = uid,
                authorName = "Alice",
                title = "Studio Acoustic Check ${UUID.randomUUID().toString().take(6)}",
                description = "Testing live feed publishing",
                category = "Studio",
                videoUri = null,
                coverKey = "thumb_1",
                thumbnailBase64 = null,
                visibility = "public",
                durationSec = 95L
            ).getOrThrow()
        }
        assertTrue(videoId.isNotBlank())

        val feed = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observePublicVideos().first { list -> list.any { it.id == videoId } }
        }
        assertTrue(feed.any { it.id == videoId })
    }

    @Test
    fun toggleVideoLike_andAddComment_updatesAtomically() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = PulseCastRepository(firestore)
        val videoId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.publishVideo(
                uid = aliceUid,
                authorName = "Alice",
                title = "Live Keynote",
                description = null,
                category = "Tech",
                videoUri = null,
                coverKey = "thumb_2",
                thumbnailBase64 = null,
                visibility = "public",
                durationSec = 180L
            ).getOrThrow()
        }

        val bobUid = signInTestUser(BOB_EMAIL)
        val liked = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.toggleVideoLike(videoId, bobUid).getOrThrow()
        }
        assertTrue(liked)

        val commentId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.addComment(videoId, bobUid, "Bob", "Crystal clear stream!").getOrThrow()
        }
        assertTrue(commentId.isNotBlank())
    }

    @Test
    fun createChatAndSendMessage_crossUserIsolationEnforced() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = PulseCastRepository(firestore)
        val chatId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createChatThread(
                uid = aliceUid,
                creatorName = "Alice",
                title = "Private Sync",
                isGroup = false,
                participantIds = listOf(aliceUid),
                participantNames = listOf("Alice")
            ).getOrThrow()
        }

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.sendMessage(
                chatId = chatId,
                uid = aliceUid,
                senderName = "Alice",
                participantIds = listOf(aliceUid),
                messageType = "text",
                text = "Secret check-in"
            ).getOrThrow()
        }

        val bobUid = signInTestUser(BOB_EMAIL)
        val bobChats = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeChats(bobUid).first()
        }
        assertFalse(bobChats.any { it.id == chatId })
    }

    @Test
    fun callSession_createJoinAndEnd_enforcesTerminalLock() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = PulseCastRepository(firestore)
        val code = "TEST-${UUID.randomUUID().toString().take(4).uppercase()}"

        val created = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createCallSession(aliceUid, "Alice", "video", code).getOrThrow()
        }
        assertEquals(code, created.roomCode)

        val bobUid = signInTestUser(BOB_EMAIL)
        val joined = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.joinCallSession(code, bobUid, "Bob").getOrThrow()
        }
        assertTrue(joined.participantIds.contains(bobUid))

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.endCallSession(code, 42L).getOrThrow()
        }

        val rejoinResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.joinCallSession(code, bobUid, "Bob")
        }
        assertTrue(rejoinResult.isFailure)
    }

    @Test
    fun observePublicVideos_unauthenticated_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = PulseCastRepository(firestore)
        val thrown = try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observePublicVideos().first()
            }
            null
        } catch (e: Exception) {
            e
        }
        val fsEx = checkNotNull(thrown?.unwrapFirestoreException()) {
            "Expected FirebaseFirestoreException for unauthenticated query, got $thrown"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_pulse@test.com"
        const val BOB_EMAIL = "bob_pulse@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
