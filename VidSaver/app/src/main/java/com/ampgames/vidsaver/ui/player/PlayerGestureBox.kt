package com.ampgames.vidsaver.ui.player

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import com.ampgames.vidsaver.domain.player.PlayerGestures

const val PLAYER_GESTURE_TEST_TAG = "player_gestures"

/**
 * Touch handling over the video surface.
 *
 * The arithmetic lives in [PlayerGestures]; this only turns pointer events into
 * calls. Horizontal and vertical drags are detected separately so a scrub never
 * accidentally changes the volume half way through.
 */
@Composable
fun PlayerGestureBox(
    onTap: () -> Unit,
    onDoubleTap: (forward: Boolean) -> Unit,
    onSeekDrag: (dragPixels: Float, viewWidth: Float) -> Unit,
    onSeekDragEnd: () -> Unit,
    onVerticalDrag: (zone: PlayerGestures.Zone, dragPixels: Float, viewHeight: Float) -> Unit,
    onVerticalDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Accumulated across a gesture so the seek preview is relative to where the
    // finger went down, not to the last pointer event.
    val dragState = remember { DragState() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(PLAYER_GESTURE_TEST_TAG)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { offset ->
                        onDoubleTap(offset.x > size.width / 2f)
                    },
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragState.horizontal = 0f },
                    onDragEnd = {
                        dragState.horizontal = 0f
                        onSeekDragEnd()
                    },
                    onDragCancel = {
                        dragState.horizontal = 0f
                        onSeekDragEnd()
                    },
                    onHorizontalDrag = { _, amount ->
                        dragState.horizontal += amount
                        onSeekDrag(dragState.horizontal, size.width.toFloat())
                    },
                )
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        dragState.zone = PlayerGestures.zoneFor(offset.x, size.width.toFloat())
                    },
                    onDragEnd = {
                        dragState.zone = PlayerGestures.Zone.CENTRE
                        onVerticalDragEnd()
                    },
                    onDragCancel = {
                        dragState.zone = PlayerGestures.Zone.CENTRE
                        onVerticalDragEnd()
                    },
                    onVerticalDrag = { _, amount ->
                        onVerticalDrag(dragState.zone, amount, size.height.toFloat())
                    },
                )
            },
    )
}

/** Mutable gesture accumulator; not state, so it never triggers recomposition. */
private class DragState {
    var horizontal: Float = 0f
    var zone: PlayerGestures.Zone = PlayerGestures.Zone.CENTRE
}
