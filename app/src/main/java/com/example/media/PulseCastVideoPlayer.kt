package com.example.media

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import kotlinx.coroutines.delay

private val PLAYBACK_SPEEDS = listOf(1.0f, 1.5f, 2.0f)

@OptIn(UnstableApi::class)
@Composable
fun PulseCastVideoPlayer(
    videoUri: String,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    externalPlayer: Player? = null,
    renderSurface: Boolean = true
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val ownsPlayer = externalPlayer == null
    val player = remember(context, externalPlayer) {
        externalPlayer ?: ExoPlayer.Builder(context).build()
    }

    var isPlaying by remember(player) { mutableStateOf(player.isPlaying) }
    var playbackState by remember(player) { mutableIntStateOf(player.playbackState) }
    var errorMessage by remember(player) { mutableStateOf<String?>(null) }
    var durationMs by remember(player) { mutableLongStateOf(player.duration.coerceAtLeast(0L)) }
    var currentPosMs by remember(player) { mutableLongStateOf(player.currentPosition.coerceAtLeast(0L)) }
    var isMuted by remember(player) { mutableStateOf(player.volume == 0f) }
    var speedIndex by remember(player) { mutableIntStateOf(0) }
    var controlsVisible by remember { mutableStateOf(true) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPosMs by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(player, videoUri, autoPlay) {
        errorMessage = null
        if (ownsPlayer) {
            player.setMediaItem(MediaItem.fromUri(Uri.parse(videoUri)))
            player.playWhenReady = autoPlay
            player.prepare()
        } else {
            playbackState = player.playbackState
            isPlaying = player.isPlaying
            durationMs = player.duration.coerceAtLeast(0L)
            currentPosMs = player.currentPosition.coerceAtLeast(0L)
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                if (state == Player.STATE_READY) {
                    errorMessage = null
                    durationMs = player.duration.coerceAtLeast(0L)
                } else if (state == Player.STATE_ENDED) {
                    controlsVisible = true
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                errorMessage = error.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.video_player_error_fallback)
                controlsVisible = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    DisposableEffect(lifecycleOwner, player, ownsPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                player.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (ownsPlayer) {
                player.release()
            }
        }
    }

    LaunchedEffect(player, isPlaying, playbackState) {
        while (isPlaying || playbackState == Player.STATE_READY) {
            if (!isScrubbing) {
                currentPosMs = player.currentPosition.coerceAtLeast(0L)
                val latestDuration = player.duration
                if (latestDuration > 0L) {
                    durationMs = latestDuration
                }
            }
            if (!isPlaying) break
            delay(250L)
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures {
                    if (errorMessage == null) {
                        controlsVisible = !controlsVisible
                    }
                }
            }
            .testTag("exo_player_container")
    ) {
        if (renderSurface) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        keepScreenOn = true
                        this.player = player
                    }
                },
                update = { view ->
                    if (view.player !== player) {
                        view.player = player
                    }
                    view.keepScreenOn = isPlaying
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("exo_player_surface")
            )
        }

        if (playbackState == Player.STATE_BUFFERING && errorMessage == null) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
                    .testTag("video_buffering_indicator")
            )
        }

        AnimatedVisibility(
            visible = controlsVisible && errorMessage == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.25f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.78f)
                            )
                        )
                    )
                    .testTag("video_controls_overlay")
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(56.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (playbackState == Player.STATE_ENDED) {
                                player.seekTo(0L)
                                player.play()
                            } else if (isPlaying) {
                                player.pause()
                            } else {
                                player.play()
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("video_play_pause_button")
                    ) {
                        val icon = when {
                            playbackState == Player.STATE_ENDED -> Icons.Filled.Replay
                            isPlaying -> Icons.Filled.Pause
                            else -> Icons.Filled.PlayArrow
                        }
                        val description = when {
                            playbackState == Player.STATE_ENDED -> stringResource(R.string.video_player_replay)
                            isPlaying -> stringResource(R.string.video_player_pause)
                            else -> stringResource(R.string.video_player_play)
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = description,
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    val sliderMax = durationMs.coerceAtLeast(1L).toFloat()
                    val sliderValue = if (isScrubbing) {
                        scrubPosMs.coerceIn(0f, sliderMax)
                    } else {
                        currentPosMs.toFloat().coerceIn(0f, sliderMax)
                    }

                    Slider(
                        value = sliderValue,
                        onValueChange = { next ->
                            isScrubbing = true
                            scrubPosMs = next
                        },
                        onValueChangeFinished = {
                            val targetMs = scrubPosMs.toLong().coerceIn(0L, durationMs.coerceAtLeast(0L))
                            currentPosMs = targetMs
                            player.seekTo(targetMs)
                            isScrubbing = false
                        },
                        valueRange = 0f..sliderMax,
                        enabled = durationMs > 0L,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .testTag("video_seek_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${formatDuration(currentPosMs / 1000L)} / ${formatDuration(durationMs / 1000L)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.testTag("video_time_label")
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = {
                                    speedIndex = (speedIndex + 1) % PLAYBACK_SPEEDS.size
                                    player.setPlaybackSpeed(PLAYBACK_SPEEDS[speedIndex])
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("video_speed_button")
                            ) {
                                Text(
                                    text = "${PLAYBACK_SPEEDS[speedIndex]}x",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White
                                )
                            }

                            IconButton(
                                onClick = {
                                    isMuted = !isMuted
                                    player.volume = if (isMuted) 0f else 1f
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("video_mute_button")
                            ) {
                                Icon(
                                    imageVector = if (isMuted) {
                                        Icons.AutoMirrored.Filled.VolumeOff
                                    } else {
                                        Icons.AutoMirrored.Filled.VolumeUp
                                    },
                                    contentDescription = if (isMuted) {
                                        stringResource(R.string.video_player_unmute)
                                    } else {
                                        stringResource(R.string.video_player_mute)
                                    },
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        if (errorMessage != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.82f))
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(20.dp)
                    .testTag("video_error_overlay"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(34.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        errorMessage = null
                        player.prepare()
                        player.play()
                    },
                    modifier = Modifier.testTag("video_error_retry_button")
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.video_player_retry))
                }
            }
        }
    }
}
