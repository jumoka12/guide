package com.ampgames.vidsaver.core.format

import java.util.Locale
import kotlin.math.roundToLong

/**
 * Human-readable sizes, speeds and durations.
 *
 * Binary units (1 KB = 1024 B), which is what a download manager should report:
 * it matches what the file system will say the file is.
 *
 * Pure and Locale-explicit so the numbers are unit-testable and never shift with
 * the device locale in a way that breaks a test.
 */
object ByteFormat {

    private val UNITS = listOf("B", "KB", "MB", "GB", "TB")

    /** e.g. `0 B`, `512 B`, `1.5 MB`, `2.0 GB`. */
    fun size(bytes: Long): String {
        if (bytes < 0) return "0 B"
        if (bytes < 1024) return "$bytes B"

        var value = bytes.toDouble()
        var index = 0
        while (value >= 1024 && index < UNITS.lastIndex) {
            value /= 1024
            index++
        }
        return String.format(Locale.US, "%.1f %s", value, UNITS[index])
    }

    /** e.g. `1.2 MB/s`. Returns null when there is no meaningful rate. */
    fun speed(bytesPerSecond: Long): String? =
        if (bytesPerSecond <= 0) null else "${size(bytesPerSecond)}/s"

    /** `12.3 MB of 40.0 MB`, or just the downloaded part when the total is unknown. */
    fun progress(bytesDownloaded: Long, totalBytes: Long?): String =
        if (totalBytes != null && totalBytes > 0) {
            "${size(bytesDownloaded)} / ${size(totalBytes)}"
        } else {
            size(bytesDownloaded)
        }

    /**
     * Time left at the current rate, as a coarse `2m 15s`. Null when the total or
     * the speed is unknown, because a wrong estimate is worse than none.
     */
    fun remaining(bytesDownloaded: Long, totalBytes: Long?, bytesPerSecond: Long): String? {
        if (totalBytes == null || totalBytes <= 0 || bytesPerSecond <= 0) return null
        val remainingBytes = totalBytes - bytesDownloaded
        if (remainingBytes <= 0) return null

        val seconds = (remainingBytes.toDouble() / bytesPerSecond).roundToLong()
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
        }
    }
}
