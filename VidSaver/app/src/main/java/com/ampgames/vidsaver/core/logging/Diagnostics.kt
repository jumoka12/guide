package com.ampgames.vidsaver.core.logging

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import androidx.webkit.WebViewCompat
import com.ampgames.vidsaver.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Collects what a person cannot describe and a screenshot cannot show: the
 * device, the WebView build, a snapshot of the browser's state, and the app's
 * own recent log lines — page console warnings, failed media requests, sniffer
 * findings.
 *
 * Reads only this process's log (`logcat --pid`), which needs no permission,
 * and shares it through the system sheet so it can go to email or chat. No
 * cookies, no page content, no URLs beyond the ones already logged.
 */
object Diagnostics {

    /** Raw lines read from logcat, before filtering. */
    private const val RAW_LINES = 3_000

    /** Lines kept after filtering and collapsing repeats. */
    private const val KEPT_LINES = 400

    /**
     * Only lines worth a developer's time. Everything else the process logs
     * (Compose, OkHttp internals, the system) is noise here.
     */
    private val INTERESTING = Regex(
        "WebConsole|MediaSniffer|VidSaverWebViewClient|BrowserViewModel|BrowserWebView|" +
            "DownloadEngine|DownloadService|HlsDownload|DirectFile|MediaStorePublisher|" +
            "AndroidRuntime|chromium|cr_",
    )

    suspend fun collect(context: Context, stateSnapshot: () -> String): String = withContext(Dispatchers.IO) {
        buildString {
            appendLine("VidSaver ${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE})")
            appendLine("${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            val webView = runCatching { WebViewCompat.getCurrentWebViewPackage(context) }.getOrNull()
            appendLine("WebView: ${webView?.packageName ?: "unknown"} ${webView?.versionName ?: ""}")
            appendLine()
            appendLine("=== State ===")
            appendLine(runCatching(stateSnapshot).getOrElse { "snapshot failed: $it" })
            appendLine()
            appendLine("=== Log (filtered, repeats collapsed) ===")
            appendLine(recentLog())
        }
    }

    private fun recentLog(): String = runCatching {
        val process = ProcessBuilder(
            "logcat", "-d", "-v", "time", "-t", RAW_LINES.toString(), "--pid=${Process.myPid()}",
        ).redirectErrorStream(true).start()
        val lines = process.inputStream.bufferedReader().use { it.readLines() }
        collapse(lines.filter { INTERESTING.containsMatchIn(it) })
            .takeLast(KEPT_LINES)
            .joinToString("\n")
            .ifBlank { "(nothing relevant logged yet)" }
    }.getOrElse { error -> "Could not read the log: $error" }

    /** Adjacent lines that differ only by timestamp become one line and a count. */
    internal fun collapse(lines: List<String>): List<String> {
        val out = ArrayList<String>(lines.size)
        var previous: String? = null
        var repeats = 0
        fun flush() {
            if (repeats > 0) out.add("    (repeated $repeats more times)")
            repeats = 0
        }
        for (line in lines) {
            val body = line.substringAfter(": ", line).substringAfter("): ", line)
            if (body == previous) {
                repeats++
                continue
            }
            flush()
            previous = body
            out.add(line)
        }
        flush()
        return out
    }

    /** A share sheet carrying [text], for the person to send wherever they like. */
    fun shareIntent(text: String): Intent = Intent.createChooser(
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "VidSaver debug log")
            putExtra(Intent.EXTRA_TEXT, text)
        },
        null,
    )
}
