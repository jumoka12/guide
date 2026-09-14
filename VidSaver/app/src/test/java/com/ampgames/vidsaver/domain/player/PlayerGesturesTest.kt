package com.ampgames.vidsaver.domain.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerGesturesTest {

    private val width = 1080f
    private val height = 1920f

    // --------------------------------------------------------------- zones

    @Test
    fun `the left third is brightness and the right third is volume`() {
        assertEquals(PlayerGestures.Zone.LEFT, PlayerGestures.zoneFor(100f, width))
        assertEquals(PlayerGestures.Zone.RIGHT, PlayerGestures.zoneFor(980f, width))
    }

    @Test
    fun `the centre is a dead zone so a mis-aimed drag does nothing`() {
        assertEquals(PlayerGestures.Zone.CENTRE, PlayerGestures.zoneFor(width / 2f, width))
    }

    @Test
    fun `a zero-width view degrades to the dead zone rather than dividing by zero`() {
        assertEquals(PlayerGestures.Zone.CENTRE, PlayerGestures.zoneFor(100f, 0f))
    }

    @Test
    fun `touches outside the view are clamped into it`() {
        assertEquals(PlayerGestures.Zone.LEFT, PlayerGestures.zoneFor(-50f, width))
        assertEquals(PlayerGestures.Zone.RIGHT, PlayerGestures.zoneFor(width + 50f, width))
    }

    // --------------------------------------------------- brightness / volume

    @Test
    fun `dragging up increases the level`() {
        // Negative pixels = upward in screen coordinates.
        val raised = PlayerGestures.adjustLevel(current = 0.5f, dragPixels = -134.4f, viewHeight = height)
        assertEquals(0.6f, raised, 0.001f)
    }

    @Test
    fun `dragging down decreases the level`() {
        val lowered = PlayerGestures.adjustLevel(current = 0.5f, dragPixels = 134.4f, viewHeight = height)
        assertEquals(0.4f, lowered, 0.001f)
    }

    @Test
    fun `the level never leaves zero to one`() {
        assertEquals(1f, PlayerGestures.adjustLevel(0.9f, -10_000f, height), 0.0001f)
        assertEquals(0f, PlayerGestures.adjustLevel(0.1f, 10_000f, height), 0.0001f)
    }

    @Test
    fun `a zero-height view leaves the level alone`() {
        assertEquals(0.5f, PlayerGestures.adjustLevel(0.5f, 500f, 0f), 0.0001f)
    }

    // ------------------------------------------------------------------ seek

    @Test
    fun `a full-width drag covers the whole video`() {
        val target = PlayerGestures.seekTarget(
            currentPositionMs = 0,
            durationMs = 60_000,
            dragPixels = width,
            viewWidth = width,
        )
        assertEquals(60_000L, target)
    }

    @Test
    fun `a half-width drag covers half the video`() {
        val target = PlayerGestures.seekTarget(0, 60_000, width / 2f, width)
        assertEquals(30_000L, target)
    }

    @Test
    fun `dragging backwards seeks backwards`() {
        val target = PlayerGestures.seekTarget(30_000, 60_000, -width / 2f, width)
        assertEquals(0L, target)
    }

    @Test
    fun `seeking past either end parks at the boundary`() {
        assertEquals(60_000L, PlayerGestures.seekTarget(30_000, 60_000, width * 5, width))
        assertEquals(0L, PlayerGestures.seekTarget(30_000, 60_000, -width * 5, width))
    }

    @Test
    fun `an unknown duration leaves the position untouched`() {
        assertEquals(5_000L, PlayerGestures.seekTarget(5_000, 0, width, width))
    }

    // ------------------------------------------------------------ double tap

    @Test
    fun `double tap jumps ten seconds and clamps`() {
        assertEquals(20_000L, PlayerGestures.doubleTapTarget(10_000, 60_000, forward = true))
        assertEquals(0L, PlayerGestures.doubleTapTarget(10_000, 60_000, forward = false))
        assertEquals(60_000L, PlayerGestures.doubleTapTarget(55_000, 60_000, forward = true))
    }

    @Test
    fun `double tap never produces a negative position`() {
        assertEquals(0L, PlayerGestures.doubleTapTarget(2_000, 0, forward = false))
    }

    // -------------------------------------------------------------- overlay

    @Test
    fun `the seek overlay is signed and human readable`() {
        assertEquals("+00:15", PlayerGestures.formatSeekDelta(0, 15_000))
        assertEquals("-01:20", PlayerGestures.formatSeekDelta(100_000, 20_000))
        assertEquals("+00:00", PlayerGestures.formatSeekDelta(5_000, 5_000))
    }
}
