package com.example.media

import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.media3.common.AudioAttributes
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.unit.dp
import com.example.ui.theme.PulseCastTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp")
class PulseCastVideoPlayerTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun exoPlayer_initializesPreparesMediaItemAndReleasesCleanly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val player = ExoPlayer.Builder(context).build()
        try {
            player.setMediaItem(MediaItem.fromUri("https://example.com/studio_demo.mp4"))
            player.prepare()
            assertNotNull(player.currentMediaItem)
            assertEquals("https://example.com/studio_demo.mp4", player.currentMediaItem?.localConfiguration?.uri.toString())
        } finally {
            player.release()
        }
    }

    @Test
    fun controls_togglePlayPauseMuteAndPlaybackSpeed() {
        val fakePlayer = FakeMedia3Player().apply {
            stateValue = Player.STATE_READY
            durationValue = 90_000L
            currentPositionValue = 15_000L
        }

        composeRule.setContent {
            PulseCastTheme {
                PulseCastVideoPlayer(
                    videoUri = "https://example.com/clip.mp4",
                    autoPlay = false,
                    externalPlayer = fakePlayer,
                    renderSurface = false,
                    modifier = Modifier.fillMaxWidth().height(240.dp)
                )
            }
        }

        composeRule.onNodeWithTag("video_controls_overlay").assertIsDisplayed()
        composeRule.onNodeWithTag("video_time_label").assertTextEquals("00:15 / 01:30")

        // Play -> Pause
        composeRule.onNodeWithTag("video_play_pause_button").performClick()
        assertTrue(fakePlayer.isPlayingValue)
        composeRule.onNodeWithContentDescription("Pause video").assertIsDisplayed()

        composeRule.onNodeWithTag("video_play_pause_button").performClick()
        assertFalse(fakePlayer.isPlayingValue)
        composeRule.onNodeWithContentDescription("Play video").assertIsDisplayed()

        // Mute toggle
        composeRule.onNodeWithTag("video_mute_button").performClick()
        assertEquals(0f, fakePlayer.volumeValue, 0.01f)
        composeRule.onNodeWithContentDescription("Unmute audio").assertIsDisplayed()

        // Unmute restores previous volume
        composeRule.onNodeWithTag("video_mute_button").performClick()
        assertTrue(fakePlayer.volumeValue > 0f)
        composeRule.onNodeWithContentDescription("Mute audio").assertIsDisplayed()

        // Bottom bar play/pause button
        composeRule.onNodeWithTag("video_bar_play_pause_button").performClick()
        assertTrue(fakePlayer.isPlayingValue)
        composeRule.onNodeWithTag("video_bar_play_pause_button").performClick()
        assertFalse(fakePlayer.isPlayingValue)

        // Volume slider visible in standard layout
        composeRule.onNodeWithTag("video_volume_slider").assertIsDisplayed()

        // Speed cycle 1.0x -> 1.5x -> 2.0x
        composeRule.onNodeWithText("1.0x").assertIsDisplayed()
        composeRule.onNodeWithTag("video_speed_button").performClick()
        assertEquals(1.5f, fakePlayer.speedValue, 0.01f)
        composeRule.onNodeWithText("1.5x").assertIsDisplayed()
        composeRule.onNodeWithTag("video_speed_button").performClick()
        assertEquals(2.0f, fakePlayer.speedValue, 0.01f)
        composeRule.onNodeWithText("2.0x").assertIsDisplayed()
    }

    @Test
    fun compactWidth_togglesCompactVolumePopupOnMute() {
        val fakePlayer = FakeMedia3Player().apply {
            stateValue = Player.STATE_READY
            durationValue = 90_000L
            currentPositionValue = 10_000L
        }

        composeRule.setContent {
            PulseCastTheme {
                PulseCastVideoPlayer(
                    videoUri = "https://example.com/compact.mp4",
                    autoPlay = false,
                    externalPlayer = fakePlayer,
                    renderSurface = false,
                    modifier = Modifier.width(300.dp).height(240.dp)
                )
            }
        }

        // Compact width (< 340.dp) hides inline volume slider until mute toggle expands popup
        composeRule.onNodeWithTag("video_volume_slider").assertDoesNotExist()
        composeRule.onNodeWithTag("video_mute_button").performClick()
        assertEquals(0f, fakePlayer.volumeValue, 0.01f)
        composeRule.onNodeWithTag("video_volume_slider").assertIsDisplayed()

        // Second tap unmutes and collapses the compact volume popup
        composeRule.onNodeWithTag("video_mute_button").performClick()
        assertTrue(fakePlayer.volumeValue > 0f)
        composeRule.onNodeWithTag("video_volume_slider").assertDoesNotExist()
    }

    @Test
    fun fullscreenToggle_opensAndExitsFullscreenPreservingPlaybackState() {
        val fakePlayer = FakeMedia3Player().apply {
            stateValue = Player.STATE_READY
            durationValue = 120_000L
            currentPositionValue = 42_000L
            volumeValue = 0.7f
        }
        var fullscreenState = false

        composeRule.setContent {
            PulseCastTheme {
                PulseCastVideoPlayer(
                    videoUri = "https://example.com/studio.mp4",
                    autoPlay = false,
                    externalPlayer = fakePlayer,
                    renderSurface = false,
                    onFullscreenChange = { fullscreenState = it },
                    modifier = Modifier.fillMaxWidth().height(240.dp)
                )
            }
        }

        composeRule.onNodeWithContentDescription("Enter fullscreen").assertIsDisplayed()
        composeRule.onNodeWithTag("video_fullscreen_button").performClick()
        composeRule.waitForIdle()

        assertTrue(fullscreenState)
        composeRule.onNodeWithTag("video_fullscreen_dialog").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Exit fullscreen").assertIsDisplayed()
        composeRule.onNodeWithTag("video_time_label").assertTextEquals("00:42 / 02:00")
        assertEquals(0.7f, fakePlayer.volumeValue, 0.01f)

        composeRule.onNodeWithTag("video_fullscreen_button").performClick()
        composeRule.waitForIdle()

        assertFalse(fullscreenState)
        composeRule.onNodeWithTag("video_fullscreen_dialog").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Enter fullscreen").assertIsDisplayed()
    }

    @Test
    fun errorState_displaysRetryAndClearsOnSuccessfulPrepare() {
        val fakePlayer = FakeMedia3Player().apply {
            stateValue = Player.STATE_READY
            durationValue = 30_000L
        }

        composeRule.setContent {
            PulseCastTheme {
                PulseCastVideoPlayer(
                    videoUri = "https://example.com/broken.mp4",
                    autoPlay = false,
                    externalPlayer = fakePlayer,
                    renderSurface = false,
                    modifier = Modifier.fillMaxWidth().height(240.dp)
                )
            }
        }

        composeRule.runOnIdle {
            fakePlayer.dispatchError(
                PlaybackException(
                    "Network timeout",
                    null,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                )
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("video_error_overlay").assertIsDisplayed()
        composeRule.onNodeWithText("Network timeout").assertIsDisplayed()

        composeRule.onNodeWithTag("video_error_retry_button").performClick()
        assertTrue(fakePlayer.prepareCalls > 0)
        assertTrue(fakePlayer.isPlayingValue)
        composeRule.onNodeWithTag("video_error_overlay").assertDoesNotExist()
    }

    @Test
    fun endedState_showsReplayAndSeeksToStart() {
        val fakePlayer = FakeMedia3Player().apply {
            stateValue = Player.STATE_ENDED
            durationValue = 45_000L
            currentPositionValue = 45_000L
        }

        composeRule.setContent {
            PulseCastTheme {
                PulseCastVideoPlayer(
                    videoUri = "https://example.com/finished.mp4",
                    autoPlay = false,
                    externalPlayer = fakePlayer,
                    renderSurface = false,
                    modifier = Modifier.fillMaxWidth().height(240.dp)
                )
            }
        }

        composeRule.onNodeWithContentDescription("Replay video").assertIsDisplayed()
        composeRule.onNodeWithTag("video_play_pause_button").performClick()
        assertEquals(0L, fakePlayer.lastSeekPosition)
        assertTrue(fakePlayer.isPlayingValue)
    }
}

private class FakeMedia3Player : Player {
    private val listeners = mutableSetOf<Player.Listener>()
    var stateValue: Int = Player.STATE_IDLE
    var isPlayingValue: Boolean = false
    var durationValue: Long = 0L
    var currentPositionValue: Long = 0L
    var volumeValue: Float = 1f
    var speedValue: Float = 1f
    var prepareCalls: Int = 0
    var lastSeekPosition: Long? = null

    fun dispatchError(error: PlaybackException) {
        listeners.forEach { it.onPlayerError(error) }
    }

    override fun addListener(listener: Player.Listener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        listeners.remove(listener)
    }

    override fun prepare() {
        prepareCalls += 1
        stateValue = Player.STATE_READY
        listeners.forEach { it.onPlaybackStateChanged(stateValue) }
    }

    override fun play() {
        isPlayingValue = true
        stateValue = Player.STATE_READY
        listeners.forEach {
            it.onPlaybackStateChanged(stateValue)
            it.onIsPlayingChanged(true)
        }
    }

    override fun pause() {
        isPlayingValue = false
        listeners.forEach { it.onIsPlayingChanged(false) }
    }

    override fun seekTo(positionMs: Long) {
        lastSeekPosition = positionMs
        currentPositionValue = positionMs
    }

    override fun setPlaybackSpeed(speed: Float) {
        speedValue = speed
    }

    override fun setVolume(volume: Float) {
        volumeValue = volume
    }

    override fun getVolume(): Float = volumeValue
    override fun isPlaying(): Boolean = isPlayingValue
    override fun getPlaybackState(): Int = stateValue
    override fun getDuration(): Long = durationValue
    override fun getCurrentPosition(): Long = currentPositionValue
    override fun getApplicationLooper(): Looper = Looper.getMainLooper()

    // Unused Player interface members for this test fake
    override fun setMediaItems(mediaItems: MutableList<MediaItem>) {}
    override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {}
    override fun setMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long) {}
    override fun setMediaItem(mediaItem: MediaItem) {}
    override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) {}
    override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) {}
    override fun addMediaItem(mediaItem: MediaItem) {}
    override fun addMediaItem(index: Int, mediaItem: MediaItem) {}
    override fun addMediaItems(mediaItems: MutableList<MediaItem>) {}
    override fun addMediaItems(index: Int, mediaItems: MutableList<MediaItem>) {}
    override fun moveMediaItem(currentIndex: Int, newIndex: Int) {}
    override fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) {}
    override fun replaceMediaItem(index: Int, mediaItem: MediaItem) {}
    override fun replaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: MutableList<MediaItem>) {}
    override fun removeMediaItem(index: Int) {}
    override fun removeMediaItems(fromIndex: Int, toIndex: Int) {}
    override fun clearMediaItems() {}
    override fun isCommandAvailable(command: Int): Boolean = true
    override fun canAdvertiseSession(): Boolean = false
    override fun getAvailableCommands(): Player.Commands = Player.Commands.EMPTY
    override fun getPlaybackSuppressionReason(): Int = Player.PLAYBACK_SUPPRESSION_REASON_NONE
    override fun getPlayerError(): PlaybackException? = null
    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (playWhenReady) play() else pause()
    }
    override fun getPlayWhenReady(): Boolean = isPlayingValue
    override fun setRepeatMode(repeatMode: Int) {}
    override fun getRepeatMode(): Int = Player.REPEAT_MODE_OFF
    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {}
    override fun getShuffleModeEnabled(): Boolean = false
    override fun isLoading(): Boolean = false
    override fun seekToDefaultPosition() {}
    override fun seekToDefaultPosition(mediaItemIndex: Int) {}
    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {}
    override fun getSeekBackIncrement(): Long = 5000L
    override fun seekBack() {}
    override fun getSeekForwardIncrement(): Long = 5000L
    override fun seekForward() {}
    override fun hasPreviousMediaItem(): Boolean = false
    override fun seekToPreviousMediaItem() {}
    override fun getMaxSeekToPreviousPosition(): Long = 3000L
    override fun seekToPrevious() {}
    override fun hasNextMediaItem(): Boolean = false
    override fun seekToNextMediaItem() {}
    override fun seekToNext() {}
    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {}
    override fun getPlaybackParameters(): PlaybackParameters = PlaybackParameters(speedValue)
    override fun stop() {}
    override fun release() {}
    override fun getCurrentTracks(): Tracks = Tracks.EMPTY
    override fun getTrackSelectionParameters(): TrackSelectionParameters = TrackSelectionParameters.DEFAULT
    override fun setTrackSelectionParameters(parameters: TrackSelectionParameters) {}
    override fun getMediaMetadata(): MediaMetadata = MediaMetadata.EMPTY
    override fun getPlaylistMetadata(): MediaMetadata = MediaMetadata.EMPTY
    override fun setPlaylistMetadata(mediaMetadata: MediaMetadata) {}
    override fun getCurrentManifest(): Any? = null
    override fun getCurrentTimeline(): Timeline = Timeline.EMPTY
    override fun getCurrentPeriodIndex(): Int = 0
    override fun getCurrentMediaItemIndex(): Int = 0
    @Deprecated("Deprecated in Player")
    override fun getCurrentWindowIndex(): Int = 0
    override fun getNextMediaItemIndex(): Int = -1
    @Deprecated("Deprecated in Player")
    override fun getNextWindowIndex(): Int = -1
    override fun getPreviousMediaItemIndex(): Int = -1
    @Deprecated("Deprecated in Player")
    override fun getPreviousWindowIndex(): Int = -1
    override fun getCurrentMediaItem(): MediaItem? = null
    override fun getMediaItemCount(): Int = 0
    override fun getMediaItemAt(index: Int): MediaItem = MediaItem.EMPTY
    override fun getBufferedPosition(): Long = currentPositionValue
    override fun getBufferedPercentage(): Int = 0
    override fun getTotalBufferedDuration(): Long = 0L
    override fun isCurrentMediaItemDynamic(): Boolean = false
    @Deprecated("Deprecated in Player")
    override fun isCurrentWindowDynamic(): Boolean = false
    override fun isCurrentMediaItemLive(): Boolean = false
    @Deprecated("Deprecated in Player")
    override fun isCurrentWindowLive(): Boolean = false
    override fun getCurrentLiveOffset(): Long = 0L
    override fun isCurrentMediaItemSeekable(): Boolean = true
    @Deprecated("Deprecated in Player")
    override fun isCurrentWindowSeekable(): Boolean = true
    override fun mute() {
        volumeValue = 0f
    }
    override fun unmute() {
        volumeValue = 1f
    }
    override fun isPlayingAd(): Boolean = false
    override fun getCurrentAdGroupIndex(): Int = -1
    override fun getCurrentAdIndexInAdGroup(): Int = -1
    override fun getContentDuration(): Long = durationValue
    override fun getContentPosition(): Long = currentPositionValue
    override fun getContentBufferedPosition(): Long = currentPositionValue
    override fun getAudioAttributes(): AudioAttributes = AudioAttributes.DEFAULT
    override fun setAudioAttributes(audioAttributes: AudioAttributes, handleAudioFocus: Boolean) {}
    override fun clearVideoSurface() {}
    override fun clearVideoSurface(surface: android.view.Surface?) {}
    override fun setVideoSurface(surface: android.view.Surface?) {}
    override fun setVideoSurfaceHolder(surfaceHolder: android.view.SurfaceHolder?) {}
    override fun clearVideoSurfaceHolder(surfaceHolder: android.view.SurfaceHolder?) {}
    override fun setVideoSurfaceView(surfaceView: android.view.SurfaceView?) {}
    override fun clearVideoSurfaceView(surfaceView: android.view.SurfaceView?) {}
    override fun setVideoTextureView(textureView: android.view.TextureView?) {}
    override fun clearVideoTextureView(textureView: android.view.TextureView?) {}
    override fun getVideoSize(): VideoSize = VideoSize.UNKNOWN
    override fun getSurfaceSize(): androidx.media3.common.util.Size = androidx.media3.common.util.Size.UNKNOWN
    override fun getCurrentCues(): CueGroup = CueGroup.EMPTY_TIME_ZERO
    override fun getDeviceInfo(): DeviceInfo = DeviceInfo.UNKNOWN
    override fun getDeviceVolume(): Int = 0
    override fun isDeviceMuted(): Boolean = volumeValue == 0f
    @Deprecated("Deprecated in Player")
    override fun setDeviceVolume(volume: Int) {}
    override fun setDeviceVolume(volume: Int, flags: Int) {}
    @Deprecated("Deprecated in Player")
    override fun increaseDeviceVolume() {}
    override fun increaseDeviceVolume(flags: Int) {}
    @Deprecated("Deprecated in Player")
    override fun decreaseDeviceVolume() {}
    override fun decreaseDeviceVolume(flags: Int) {}
    @Deprecated("Deprecated in Player")
    override fun setDeviceMuted(muted: Boolean) {}
    override fun setDeviceMuted(muted: Boolean, flags: Int) {}
}
