package com.ampgames.vidsaver.domain.player

/**
 * The arithmetic behind the player's touch gestures, kept free of Compose and
 * Android so it can be reasoned about and tested directly.
 *
 * The layout follows what every video app has trained people to expect:
 * - drag horizontally anywhere → scrub
 * - drag vertically on the **left** → screen brightness
 * - drag vertically on the **right** → volume
 * - double-tap left / right → jump back / forward
 */
object PlayerGestures {

    /** Fraction of the width around the centre that belongs to neither side. */
    private const val DEAD_ZONE_FRACTION = 0.15f

    /** A full-height drag covers the whole brightness or volume range. */
    private const val FULL_RANGE_HEIGHT_FRACTION = 0.7f

    const val DOUBLE_TAP_SEEK_MS = 10_000L

    enum class Zone { LEFT, RIGHT, CENTRE }

    /** Which vertical-drag zone [x] falls in, for a view [width] px wide. */
    fun zoneFor(x: Float, width: Float): Zone {
        if (width <= 0f) return Zone.CENTRE
        val fraction = (x / width).coerceIn(0f, 1f)
        val halfDeadZone = DEAD_ZONE_FRACTION / 2f
        return when {
            fraction < 0.5f - halfDeadZone -> Zone.LEFT
            fraction > 0.5f + halfDeadZone -> Zone.RIGHT
            else -> Zone.CENTRE
        }
    }

    /**
     * New 0..1 level after dragging [dragPixels] vertically.
     *
     * Dragging **up** increases, which means a negative delta in screen
     * coordinates, hence the sign flip.
     */
    fun adjustLevel(current: Float, dragPixels: Float, viewHeight: Float): Float {
        if (viewHeight <= 0f) return current.coerceIn(0f, 1f)
        val range = viewHeight * FULL_RANGE_HEIGHT_FRACTION
        return (current - dragPixels / range).coerceIn(0f, 1f)
    }

    /**
     * Where a horizontal scrub lands.
     *
     * The whole width maps to the whole video, so a short clip scrubs finely and
     * a long one coarsely — the same drag always means the same fraction.
     * Clamped to the media, so dragging past either edge parks at the boundary.
     */
    fun seekTarget(
        currentPositionMs: Long,
        durationMs: Long,
        dragPixels: Float,
        viewWidth: Float,
    ): Long {
        if (durationMs <= 0L || viewWidth <= 0f) return currentPositionMs.coerceAtLeast(0L)
        val deltaMs = (dragPixels / viewWidth * durationMs).toLong()
        return (currentPositionMs + deltaMs).coerceIn(0L, durationMs)
    }

    /** Double-tap jump, clamped the same way. */
    fun doubleTapTarget(currentPositionMs: Long, durationMs: Long, forward: Boolean): Long {
        val delta = if (forward) DOUBLE_TAP_SEEK_MS else -DOUBLE_TAP_SEEK_MS
        if (durationMs <= 0L) return (currentPositionMs + delta).coerceAtLeast(0L)
        return (currentPositionMs + delta).coerceIn(0L, durationMs)
    }

    /** Seek offset as a signed, human-readable string: `+00:15`, `-01:20`. */
    fun formatSeekDelta(fromMs: Long, toMs: Long): String {
        val deltaMs = toMs - fromMs
        val sign = if (deltaMs >= 0) "+" else "-"
        return sign + DurationFormat.position(kotlin.math.abs(deltaMs))
    }
}
