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
 * device, the WebView build, and the app's own recent log lines — page console
 * output, failed media requests, sniffer findings.
 *
 * Reads only this process's log (`logcat --pid`), which needs no permission,
 * and shares it through the system sheet so it can go to email or chat. No
 * cookies, no page content, no URLs beyond the ones already logged.
 */
object Diagnostics {

    private const val MAX_LINES = 600

    suspend fun collect(context: Context): String = withContext(Dispatchers.IO) {
        buildString {
            appendLine("VidSaver ${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE})")
            appendLine("${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            val webView = runCatching { WebViewCompat.getCurrentWebViewPackage(context) }.getOrNull()
            appendLine("WebView: ${webView?.packageName ?: "unknown"} ${webView?.versionName ?: ""}")
            appendLine()
            appendLine(recentLog())
        }
    }

    private fun recentLog(): String = runCatching {
        val process = ProcessBuilder(
            "logcat", "-d", "-v", "time", "-t", MAX_LINES.toString(), "--pid=${Process.myPid()}",
        ).redirectErrorStream(true).start()
        process.inputStream.bufferedReader().use { it.readText() }.ifBlank { "(log is empty)" }
    }.getOrElse { error -> "Could not read the log: $error" }

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
