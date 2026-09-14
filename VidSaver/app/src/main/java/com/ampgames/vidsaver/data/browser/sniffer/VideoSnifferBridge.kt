package com.ampgames.vidsaver.data.browser.sniffer

import android.webkit.JavascriptInterface
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * The `@JavascriptInterface` the injected sniffer script posts to.
 *
 * Security notes, because a JS bridge is reachable by every page loaded in the
 * WebView, not just our own script:
 * - The only exposed method takes a string and returns nothing. There is no way
 *   to reach app internals, the file system or any other object through it.
 * - The payload is parsed as JSON into a fixed schema; unknown fields are
 *   ignored and malformed input is dropped with a log line, never thrown.
 * - Reported URLs are treated as claims, not facts: [MediaSniffer] re-resolves
 *   them, re-checks them against the unsupported-domain list, and discards
 *   anything that is not recognisably media.
 */
class VideoSnifferBridge(
    private val onMedia: (DomMediaPayload) -> Unit,
) {

    @JavascriptInterface
    fun onMediaFound(payloadJson: String?) {
        if (payloadJson.isNullOrBlank()) return
        if (payloadJson.length > MAX_PAYLOAD_CHARS) {
            Timber.w("Dropping oversized sniffer payload (%d chars)", payloadJson.length)
            return
        }
        val payload = runCatching { json.decodeFromString(DomMediaPayload.serializer(), payloadJson) }
            .getOrElse { error ->
                Timber.w(error, "Malformed sniffer payload")
                return
            }
        if (payload.media.isEmpty() && payload.debug.isEmpty()) return
        onMedia(payload.copy(media = payload.media.take(MAX_REPORTS), debug = payload.debug.take(20)))
    }

    companion object {
        /** Name the interface is registered under; matched by video_sniffer.js. */
        const val INTERFACE_NAME = "VidSaverBridge"

        private const val MAX_PAYLOAD_CHARS = 256 * 1024
        private const val MAX_REPORTS = 50

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
    }
}
