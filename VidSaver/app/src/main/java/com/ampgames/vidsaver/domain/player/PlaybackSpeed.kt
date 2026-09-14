package com.ampgames.vidsaver.domain.player

/** The speeds the player offers. Cycling wraps, so the button never dead-ends. */
enum class PlaybackSpeed(val value: Float, val label: String) {
    SLOWEST(0.5f, "0.5×"),
    SLOWER(0.75f, "0.75×"),
    NORMAL(1f, "1×"),
    FASTER(1.25f, "1.25×"),
    FAST(1.5f, "1.5×"),
    FASTEST(2f, "2×"),
    ;

    fun next(): PlaybackSpeed = entries[(ordinal + 1) % entries.size]

    companion object {
        val DEFAULT = NORMAL

        /** Nearest supported speed to [value]; used when restoring a saved setting. */
        fun nearest(value: Float): PlaybackSpeed =
            entries.minByOrNull { kotlin.math.abs(it.value - value) } ?: DEFAULT
    }
}
