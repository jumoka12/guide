package com.ampgames.vidsaver.ui.player

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.ui.PlayerView
import com.ampgames.vidsaver.domain.gallery.GalleryVideo
import com.ampgames.vidsaver.domain.player.PlayerGestures
import com.ampgames.vidsaver.ui.util.findActivity
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

const val PLAYER_SCREEN_TEST_TAG = "player_screen"

/**
 * Full-screen playback.
 *
 * The [PlayerView] renders the video surface but carries none of the chrome —
 * its own controller is switched off and [PlayerControls] draws on top, so the
 * overlay is ordinary Compose and matches the rest of the app.
 */
@UnstableApi
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val controller by rememberMediaController()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val volume = remember(context) { SystemVolume(context) }
    val brightness = remember(activity) { WindowBrightness(activity) }

    // Where an in-flight scrub would land; committed on finger-up so the decoder
    // is not asked to seek on every pointer event.
    val pendingSeek = remember { PendingSeek() }

    // In Picture-in-Picture the window is a thumbnail: controls and gestures
    // over it are unusable and only obscure the video.
    //
    // Entering or leaving PiP is a configuration change, and the Activity
    // declares it in `configChanges`, so LocalConfiguration is the signal —
    // no listener to register, and nothing beyond the platform API.
    val configuration = LocalConfiguration.current
    val inPictureInPicture = remember(configuration) {
        activity?.isInPictureInPictureMode == true
    }

    // Load the playlist into the player, and keep it pointed at the right item.
    LaunchedEffect(controller, state.playlist.items) {
        val player = controller ?: return@LaunchedEffect
        val items = state.playlist.items
        if (items.isEmpty()) return@LaunchedEffect

        if (player.mediaItemCount != items.size) {
            player.setMediaItems(items.map { it.toMediaItem() }, state.playlist.currentIndex, 0L)
            player.prepare()
            player.playWhenReady = true
        }
    }

    LaunchedEffect(controller, state.playlist.currentIndex) {
        val player = controller ?: return@LaunchedEffect
        if (player.currentMediaItemIndex != state.playlist.currentIndex &&
            state.playlist.currentIndex < player.mediaItemCount
        ) {
            player.seekTo(state.playlist.currentIndex, 0L)
        }
    }

    LaunchedEffect(controller, state.speed) {
        controller?.setPlaybackSpeed(state.speed.value)
    }

    PlayerStateListener(controller, viewModel)

    // Poll the position: Player has no position callback, and a 500 ms tick is
    // enough for a scrubber without waking the CPU needlessly.
    LaunchedEffect(controller, state.isPlaying) {
        val player = controller ?: return@LaunchedEffect
        while (true) {
            viewModel.onPositionChanged(
                positionMs = player.currentPosition,
                durationMs = player.duration.takeIf { it > 0 } ?: 0L,
                bufferedMs = player.bufferedPosition,
            )
            delay(POSITION_POLL_MS)
        }
    }

    // Auto-hide the controls while playing.
    LaunchedEffect(state.controlsVisible, state.isPlaying) {
        if (state.controlsVisible && state.isPlaying) {
            delay(CONTROLS_TIMEOUT_MS)
            viewModel.onControlsVisibilityChanged(false)
        }
    }

    // Keep the screen on only while a video is actually playing.
    LaunchedEffect(state.isPlaying, activity) {
        val window = activity?.window ?: return@LaunchedEffect
        if (state.isPlaying) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Hand the display brightness back to the system on the way out.
    DisposableEffect(brightness) {
        onDispose { brightness.release() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag(PLAYER_SCREEN_TEST_TAG),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { view -> view.player = controller },
            onRelease = { view -> view.player = null },
            modifier = Modifier.fillMaxSize(),
        )

        if (!inPictureInPicture) {
            PlayerGestureBox(
                onTap = viewModel::onToggleControls,
                onDoubleTap = { forward ->
                    val player = controller ?: return@PlayerGestureBox
                    val from = player.currentPosition
                    val target = PlayerGestures.doubleTapTarget(from, state.durationMs, forward)
                    player.seekTo(target)
                    viewModel.onGestureOverlay(PlayerGestures.formatSeekDelta(from, target))
                },
                onSeekDrag = { dragPixels, viewWidth ->
                    if (pendingSeek.originMs < 0) pendingSeek.originMs = state.positionMs
                    val target = PlayerGestures.seekTarget(
                        pendingSeek.originMs,
                        state.durationMs,
                        dragPixels,
                        viewWidth,
                    )
                    pendingSeek.targetMs = target
                    viewModel.onGestureOverlay(
                        PlayerGestures.formatSeekDelta(pendingSeek.originMs, target),
                    )
                },
                onSeekDragEnd = {
                    pendingSeek.targetMs.takeIf { it >= 0 }?.let { controller?.seekTo(it) }
                    pendingSeek.reset()
                    viewModel.onGestureOverlay(null)
                },
                onVerticalDrag = { zone, dragPixels, viewHeight ->
                    when (zone) {
                        PlayerGestures.Zone.LEFT -> {
                            val level = PlayerGestures.adjustLevel(
                                brightness.level(),
                                dragPixels,
                                viewHeight,
                            )
                            brightness.set(level)
                            viewModel.onGestureOverlay("☀ ${(level * 100).roundToInt()}%")
                        }

                        PlayerGestures.Zone.RIGHT -> {
                            val level = PlayerGestures.adjustLevel(
                                volume.level(),
                                dragPixels,
                                viewHeight,
                            )
                            volume.set(level)
                            viewModel.onGestureOverlay("♪ ${(level * 100).roundToInt()}%")
                        }

                        PlayerGestures.Zone.CENTRE -> Unit
                    }
                },
                onVerticalDragEnd = { viewModel.onGestureOverlay(null) },
            )

            PlayerControls(
                state = state,
                onBack = onBack,
                onPlayPause = {
                    val player = controller ?: return@PlayerControls
                    if (player.isPlaying) player.pause() else player.play()
                },
                onNext = viewModel::onNext,
                onPrevious = viewModel::onPrevious,
                onSeekTo = { fraction ->
                    val player = controller ?: return@PlayerControls
                    if (state.durationMs > 0) {
                        player.seekTo((state.durationMs * fraction).toLong())
                    }
                },
                onCycleSpeed = viewModel::onCycleSpeed,
                onToggleBackgroundAudio = viewModel::onToggleBackgroundAudio,
                onEnterPip = { activity?.enterPipMode() },
            )

            state.gestureOverlay?.let { text ->
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag("gesture_overlay"),
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

/** Mirrors the player's own state back into the ViewModel. */
@UnstableApi
@Composable
private fun PlayerStateListener(controller: MediaController?, viewModel: PlayerViewModel) {
    DisposableEffect(controller) {
        val player = controller
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                viewModel.onPlaybackStateChanged(isPlaying)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // The player advanced on its own; keep the playlist in step.
                player?.let { viewModel.onMediaItemIndexChanged(it.currentMediaItemIndex) }
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }
}

private fun GalleryVideo.toMediaItem(): MediaItem = MediaItem.Builder()
    .setUri(uri)
    .setMediaId(id.toString())
    .setMediaMetadata(MediaMetadata.Builder().setTitle(displayName).build())
    .build()

/** Scrub-in-progress accumulator; deliberately not Compose state. */
private class PendingSeek {
    var originMs: Long = -1L
    var targetMs: Long = -1L

    fun reset() {
        originMs = -1L
        targetMs = -1L
    }
}

private const val POSITION_POLL_MS = 500L
private const val CONTROLS_TIMEOUT_MS = 3_000L
