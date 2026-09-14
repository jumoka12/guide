package com.ampgames.vidsaver.domain.player

import java.util.Locale

/** Playback timestamps. Pure and Locale-explicit so they never shift on a device. */
object DurationFormat {

    /**
     * `04:12`, or `1:02:33` once the media passes an hour — the hour field only
     * appears when it is needed, which is what every player does.
     */
    fun position(millis: Long): String {
        val safe = millis.coerceAtLeast(0L)
        val totalSeconds = safe / 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600

        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    /** `04:12 / 12:30`. */
    fun positionOfDuration(positionMs: Long, durationMs: Long): String =
        "${position(positionMs)} / ${position(durationMs)}"
}
