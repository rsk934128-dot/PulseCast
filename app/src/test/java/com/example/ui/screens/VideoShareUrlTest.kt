package com.example.ui.screens

import com.example.data.PULSECAST_SHARE_BASE_URL
import com.example.data.PublishedVideo
import com.example.data.resolveVideoShareUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoShareUrlTest {

    @Test
    fun resolveVideoShareUrl_returnsDirectHttpsUriVerbatim() {
        val video = PublishedVideo(
            id = "vid_https",
            title = "Live Jam",
            videoUri = "  https://firebasestorage.googleapis.com/v0/b/pulsecast/o/clip.mp4?alt=media  "
        )

        assertEquals(
            "https://firebasestorage.googleapis.com/v0/b/pulsecast/o/clip.mp4?alt=media",
            resolveVideoShareUrl(video)
        )
    }

    @Test
    fun resolveVideoShareUrl_returnsDirectHttpUriVerbatim() {
        val video = PublishedVideo(
            id = "vid_http",
            title = "Audio Stream",
            videoUri = "http://cdn.pulsecast.example/audio.mp4"
        )

        assertEquals(
            "http://cdn.pulsecast.example/audio.mp4",
            resolveVideoShareUrl(video)
        )
    }

    @Test
    fun resolveVideoShareUrl_fallsBackToWebShareLinkForFirestoreUri() {
        val video = PublishedVideo(
            id = "vid_cloud_42",
            title = "Studio Preview",
            videoUri = "firestore://videos/vid_cloud_42?ext=mp4"
        )

        assertEquals(
            "$PULSECAST_SHARE_BASE_URL/?videoId=vid_cloud_42",
            resolveVideoShareUrl(video)
        )
    }

    @Test
    fun resolveVideoShareUrl_fallsBackToWebShareLinkWhenUriMissingAndEncodesId() {
        val video = PublishedVideo(
            id = "vid with space&1",
            title = "Draft",
            videoUri = null
        )

        assertEquals(
            "$PULSECAST_SHARE_BASE_URL/?videoId=vid+with+space%261",
            resolveVideoShareUrl(video)
        )
    }

    @Test
    fun resolveVideoShareUrl_returnsBaseUrlWhenVideoIdIsBlank() {
        val video = PublishedVideo(
            id = "",
            title = "Untitled",
            videoUri = ""
        )

        assertEquals(PULSECAST_SHARE_BASE_URL, resolveVideoShareUrl(video))
    }
}
