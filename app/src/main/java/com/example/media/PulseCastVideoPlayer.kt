package com.example.media

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
private const val CONTROLS_AUTO_HIDE_MS = 3500L

@OptIn(UnstableApi::class)
@Composable
fun PulseCastVideoPlayer(
    videoUri: String,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    externalPlayer: Player? = null,
    renderSurface: Boolean = true,
    onFullscreenChange: ((Boolean) -> Unit)? = null
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
    var volumeLevel by remember(player) { mutableFloatStateOf(player.volume.coerceIn(0f, 1f)) }
    var lastNonZeroVolume by remember(player) {
        mutableFloatStateOf(player.volume.coerceIn(0f, 1f).takeIf { it > 0f } ?: 1f)
    }
    var speedIndex by remember(player) { mutableIntStateOf(0) }
    var controlsVisible by remember { mutableStateOf(true) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPosMs by remember { mutableFloatStateOf(0f) }
    var isAdjustingVolume by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }

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
            val currentVol = player.volume.coerceIn(0f, 1f)
            volumeLevel = currentVol
            if (currentVol > 0f) lastNonZeroVolume = currentVol
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (!playing) {
                    controlsVisible = true
                }
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

            override fun onVolumeChanged(volume: Float) {
                val clamped = volume.coerceIn(0f, 1f)
                volumeLevel = clamped
                if (clamped > 0f) {
                    lastNonZeroVolume = clamped
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

    LaunchedEffect(controlsVisible, isPlaying, isScrubbing, isAdjustingVolume, errorMessage) {
        if (controlsVisible && isPlaying && !isScrubbing && !isAdjustingVolume && errorMessage == null) {
            delay(CONTROLS_AUTO_HIDE_MS)
            controlsVisible = false
        }
    }

    val togglePlayPause: () -> Unit = {
        controlsVisible = true
        if (playbackState == Player.STATE_ENDED) {
            player.seekTo(0L)
            player.play()
        } else if (isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    val toggleMute: () -> Unit = {
        controlsVisible = true
        if (volumeLevel <= 0.01f) {
            val restored = lastNonZeroVolume.coerceIn(0.2f, 1f)
            volumeLevel = restored
            player.volume = restored
        } else {
            lastNonZeroVolume = volumeLevel
            volumeLevel = 0f
            player.volume = 0f
        }
    }

    val setVolume: (Float) -> Unit = { nextVolume ->
        val clamped = nextVolume.coerceIn(0f, 1f)
        volumeLevel = clamped
        if (clamped > 0f) {
            lastNonZeroVolume = clamped
        }
        player.volume = clamped
    }

    val cycleSpeed: () -> Unit = {
        controlsVisible = true
        speedIndex = (speedIndex + 1) % PLAYBACK_SPEEDS.size
        player.setPlaybackSpeed(PLAYBACK_SPEEDS[speedIndex])
    }

    val setFullscreen: (Boolean) -> Unit = { nextFullscreen ->
        isFullscreen = nextFullscreen
        controlsVisible = true
        onFullscreenChange?.invoke(nextFullscreen)
    }

    val retryPlayback: () -> Unit = {
        errorMessage = null
        player.prepare()
        player.play()
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .testTag("exo_player_container")
    ) {
        if (!isFullscreen) {
            VideoPlayerStage(
                player = player,
                renderSurface = renderSurface,
                isFullscreen = false,
                isPlaying = isPlaying,
                playbackState = playbackState,
                errorMessage = errorMessage,
                durationMs = durationMs,
                currentPosMs = currentPosMs,
                volumeLevel = volumeLevel,
                playbackSpeed = PLAYBACK_SPEEDS[speedIndex],
                controlsVisible = controlsVisible,
                isScrubbing = isScrubbing,
                scrubPosMs = scrubPosMs,
                onToggleControls = {
                    if (errorMessage == null) {
                        controlsVisible = !controlsVisible
                    }
                },
                onTogglePlayPause = togglePlayPause,
                onScrubChange = { next ->
                    isScrubbing = true
                    scrubPosMs = next
                },
                onScrubFinished = {
                    val targetMs = scrubPosMs.toLong().coerceIn(0L, durationMs.coerceAtLeast(0L))
                    currentPosMs = targetMs
                    player.seekTo(targetMs)
                    isScrubbing = false
                },
                onToggleMute = toggleMute,
                onVolumeChange = { next ->
                    isAdjustingVolume = true
                    setVolume(next)
                },
                onVolumeChangeFinished = {
                    isAdjustingVolume = false
                },
                onCycleSpeed = cycleSpeed,
                onToggleFullscreen = { setFullscreen(true) },
                onRetry = retryPlayback,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    if (isFullscreen) {
        Dialog(
            onDismissRequest = { setFullscreen(false) },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            BackHandler { setFullscreen(false) }

            val dialogView = LocalView.current
            val hostActivity = remember(context) { context.findActivity() }
            DisposableEffect(dialogView, hostActivity) {
                val window = (dialogView.parent as? DialogWindowProvider)?.window ?: hostActivity?.window
                val controller = window?.let { WindowCompat.getInsetsController(it, dialogView) }
                runCatching {
                    controller?.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller?.hide(WindowInsetsCompat.Type.systemBars())
                }
                onDispose {
                    runCatching {
                        controller?.show(WindowInsetsCompat.Type.systemBars())
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .testTag("video_fullscreen_dialog")
            ) {
                VideoPlayerStage(
                    player = player,
                    renderSurface = renderSurface,
                    isFullscreen = true,
                    isPlaying = isPlaying,
                    playbackState = playbackState,
                    errorMessage = errorMessage,
                    durationMs = durationMs,
                    currentPosMs = currentPosMs,
                    volumeLevel = volumeLevel,
                    playbackSpeed = PLAYBACK_SPEEDS[speedIndex],
                    controlsVisible = controlsVisible,
                    isScrubbing = isScrubbing,
                    scrubPosMs = scrubPosMs,
                    onToggleControls = {
                        if (errorMessage == null) {
                            controlsVisible = !controlsVisible
                        }
                    },
                    onTogglePlayPause = togglePlayPause,
                    onScrubChange = { next ->
                        isScrubbing = true
                        scrubPosMs = next
                    },
                    onScrubFinished = {
                        val targetMs = scrubPosMs.toLong().coerceIn(0L, durationMs.coerceAtLeast(0L))
                        currentPosMs = targetMs
                        player.seekTo(targetMs)
                        isScrubbing = false
                    },
                    onToggleMute = toggleMute,
                    onVolumeChange = { next ->
                        isAdjustingVolume = true
                        setVolume(next)
                    },
                    onVolumeChangeFinished = {
                        isAdjustingVolume = false
                    },
                    onCycleSpeed = cycleSpeed,
                    onToggleFullscreen = { setFullscreen(false) },
                    onRetry = retryPlayback,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayerStage(
    player: Player,
    renderSurface: Boolean,
    isFullscreen: Boolean,
    isPlaying: Boolean,
    playbackState: Int,
    errorMessage: String?,
    durationMs: Long,
    currentPosMs: Long,
    volumeLevel: Float,
    playbackSpeed: Float,
    controlsVisible: Boolean,
    isScrubbing: Boolean,
    scrubPosMs: Float,
    onToggleControls: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onScrubChange: (Float) -> Unit,
    onScrubFinished: () -> Unit,
    onToggleMute: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onVolumeChangeFinished: () -> Unit,
    onCycleSpeed: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expandCompactVolume by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures {
                    onToggleControls()
                }
            }
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
                onRelease = { view ->
                    if (view.player === player) {
                        view.player = null
                    }
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
            val safeModifier = if (isFullscreen) {
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            } else {
                Modifier.fillMaxSize()
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.28f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.84f)
                            )
                        )
                    )
                    .then(safeModifier)
                    .testTag("video_controls_overlay")
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.62f),
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(56.dp)
                ) {
                    IconButton(
                        onClick = onTogglePlayPause,
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
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    val sliderMax = durationMs.coerceAtLeast(1L).toFloat()
                    val sliderValue = if (isScrubbing) {
                        scrubPosMs.coerceIn(0f, sliderMax)
                    } else {
                        currentPosMs.toFloat().coerceIn(0f, sliderMax)
                    }

                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val showInlineVolumeSlider = maxWidth >= 340.dp

                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (!showInlineVolumeSlider && expandCompactVolume) {
                                Surface(
                                    color = Color.Black.copy(alpha = 0.78f),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .align(Alignment.End)
                                        .padding(bottom = 4.dp, end = 8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.video_player_volume),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        VolumeSlider(
                                            volumeLevel = volumeLevel,
                                            onVolumeChange = onVolumeChange,
                                            onVolumeChangeFinished = onVolumeChangeFinished,
                                            modifier = Modifier.width(104.dp)
                                        )
                                    }
                                }
                            }

                            Slider(
                                value = sliderValue,
                                onValueChange = onScrubChange,
                                onValueChangeFinished = onScrubFinished,
                                valueRange = 0f..sliderMax,
                                enabled = durationMs > 0L,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.32f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(26.dp)
                                    .testTag("video_seek_slider")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = onTogglePlayPause,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("video_bar_play_pause_button")
                                ) {
                                    val barIcon = when {
                                        playbackState == Player.STATE_ENDED -> Icons.Filled.Replay
                                        isPlaying -> Icons.Filled.Pause
                                        else -> Icons.Filled.PlayArrow
                                    }
                                    Icon(
                                        imageVector = barIcon,
                                        contentDescription = stringResource(R.string.video_player_toggle_playback),
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Text(
                                    text = "${formatDuration(currentPosMs / 1000L)} / ${formatDuration(durationMs / 1000L)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    modifier = Modifier.testTag("video_time_label")
                                )

                                Spacer(modifier = Modifier.weight(1f))

                                IconButton(
                                    onClick = {
                                        if (!showInlineVolumeSlider) {
                                            expandCompactVolume = !expandCompactVolume
                                        }
                                        onToggleMute()
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("video_mute_button")
                                ) {
                                    val volumeIcon = when {
                                        volumeLevel <= 0.01f -> Icons.AutoMirrored.Filled.VolumeOff
                                        volumeLevel < 0.5f -> Icons.AutoMirrored.Filled.VolumeDown
                                        else -> Icons.AutoMirrored.Filled.VolumeUp
                                    }
                                    val volumeDescription = if (volumeLevel <= 0.01f) {
                                        stringResource(R.string.video_player_unmute)
                                    } else {
                                        stringResource(R.string.video_player_mute)
                                    }
                                    Icon(
                                        imageVector = volumeIcon,
                                        contentDescription = volumeDescription,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                if (showInlineVolumeSlider) {
                                    VolumeSlider(
                                        volumeLevel = volumeLevel,
                                        onVolumeChange = onVolumeChange,
                                        onVolumeChangeFinished = onVolumeChangeFinished,
                                        modifier = Modifier.width(if (isFullscreen) 100.dp else 64.dp)
                                    )
                                }

                                TextButton(
                                    onClick = onCycleSpeed,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("video_speed_button")
                                ) {
                                    Text(
                                        text = "${playbackSpeed}x",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White
                                    )
                                }

                                IconButton(
                                    onClick = onToggleFullscreen,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("video_fullscreen_button")
                                ) {
                                    Icon(
                                        imageVector = if (isFullscreen) {
                                            Icons.Filled.FullscreenExit
                                        } else {
                                            Icons.Filled.Fullscreen
                                        },
                                        contentDescription = if (isFullscreen) {
                                            stringResource(R.string.video_player_exit_fullscreen)
                                        } else {
                                            stringResource(R.string.video_player_enter_fullscreen)
                                        },
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
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
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onRetry,
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

@Composable
private fun VolumeSlider(
    volumeLevel: Float,
    onVolumeChange: (Float) -> Unit,
    onVolumeChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val volumeLabel = stringResource(R.string.video_player_volume)
    Slider(
        value = volumeLevel.coerceIn(0f, 1f),
        onValueChange = onVolumeChange,
        onValueChangeFinished = onVolumeChangeFinished,
        valueRange = 0f..1f,
        colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = Color.White,
            inactiveTrackColor = Color.White.copy(alpha = 0.32f)
        ),
        modifier = modifier
            .height(24.dp)
            .semantics { contentDescription = volumeLabel }
            .testTag("video_volume_slider")
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
