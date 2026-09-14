package com.ampgames.vidsaver.domain.media.hls

import com.ampgames.vidsaver.domain.media.extractor.GenericExtractor

data class HlsVariant(
    val url: String,
    val bandwidth: Long?,
    val width: Int?,
    val height: Int?,
    val codecs: String?,
)

data class HlsSegment(
    val url: String,
    val durationSeconds: Double,
)

data class HlsMediaPlaylist(
    val segments: List<HlsSegment>,
    val initSegmentUrl: String?,
    /**
     * Set when the playlist declares EXT-X-KEY with a method other than NONE.
     * Encrypted playlists are refused rather than decrypted — see
     * [M3u8Parser.parseMedia].
     */
    val encryptionMethod: String?,
    val isLive: Boolean,
) {
    val isEncrypted: Boolean get() = encryptionMethod != null
    val totalDurationSeconds: Double get() = segments.sumOf { it.durationSeconds }
}

/**
 * Minimal HLS playlist parser: enough to pick a variant and enumerate segments,
 * with no attempt at decryption.
 *
 * Encrypted playlists are reported, never unwrapped. Reading an EXT-X-KEY and
 * fetching the key to decrypt the stream would be circumventing the site's
 * access control, which this app does not do.
 */
object M3u8Parser {

    private const val TAG_STREAM_INF = "#EXT-X-STREAM-INF:"
    private const val TAG_INF = "#EXTINF:"
    private const val TAG_KEY = "#EXT-X-KEY:"
    private const val TAG_MAP = "#EXT-X-MAP:"
    private const val TAG_ENDLIST = "#EXT-X-ENDLIST"

    fun isMasterPlaylist(content: String): Boolean = content.lineSequence()
        .any { it.trimStart().startsWith(TAG_STREAM_INF) }

    /**
     * Variants of a master playlist, highest quality first.
     * Returns an empty list when [content] is not a master playlist.
     */
    fun parseMaster(content: String, baseUrl: String): List<HlsVariant> {
        val variants = mutableListOf<HlsVariant>()
        val lines = content.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith(TAG_STREAM_INF)) {
                val attributes = parseAttributes(line.removePrefix(TAG_STREAM_INF))
                val uri = nextUri(lines, i + 1)
                if (uri != null) {
                    val resolution = attributes["RESOLUTION"]?.split('x', 'X')
                    variants += HlsVariant(
                        url = GenericExtractor.absolutize(uri, baseUrl) ?: uri,
                        bandwidth = attributes["BANDWIDTH"]?.toLongOrNull()
                            ?: attributes["AVERAGE-BANDWIDTH"]?.toLongOrNull(),
                        width = resolution?.getOrNull(0)?.toIntOrNull(),
                        height = resolution?.getOrNull(1)?.toIntOrNull(),
                        codecs = attributes["CODECS"],
                    )
                }
            }
            i++
        }
        return variants.sortedWith(
            compareByDescending<HlsVariant> { it.height ?: 0 }
                .thenByDescending { it.bandwidth ?: 0L },
        )
    }

    fun parseMedia(content: String, baseUrl: String): HlsMediaPlaylist {
        val segments = mutableListOf<HlsSegment>()
        var pendingDuration = 0.0
        var encryptionMethod: String? = null
        var initSegmentUrl: String? = null
        var sawEndList = false

        for (rawLine in content.lines()) {
            val line = rawLine.trim()
            when {
                line.isEmpty() -> Unit

                line.startsWith(TAG_INF) -> {
                    pendingDuration = line.removePrefix(TAG_INF)
                        .substringBefore(',')
                        .trim()
                        .toDoubleOrNull() ?: 0.0
                }

                line.startsWith(TAG_KEY) -> {
                    val method = parseAttributes(line.removePrefix(TAG_KEY))["METHOD"]
                    if (method != null && !method.equals("NONE", ignoreCase = true)) {
                        encryptionMethod = method
                    }
                }

                line.startsWith(TAG_MAP) -> {
                    parseAttributes(line.removePrefix(TAG_MAP))["URI"]?.let { uri ->
                        initSegmentUrl = GenericExtractor.absolutize(uri, baseUrl) ?: uri
                    }
                }

                line == TAG_ENDLIST -> sawEndList = true

                line.startsWith("#") -> Unit // any other tag is not needed here

                else -> {
                    segments += HlsSegment(
                        url = GenericExtractor.absolutize(line, baseUrl) ?: line,
                        durationSeconds = pendingDuration,
                    )
                    pendingDuration = 0.0
                }
            }
        }

        return HlsMediaPlaylist(
            segments = segments,
            initSegmentUrl = initSegmentUrl,
            encryptionMethod = encryptionMethod,
            isLive = !sawEndList,
        )
    }

    /** First non-comment, non-blank line at or after [from]. */
    private fun nextUri(lines: List<String>, from: Int): String? {
        for (i in from until lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            return line
        }
        return null
    }

    /**
     * Parses `KEY=VALUE,KEY="quoted,value"` attribute lists, respecting quotes
     * so commas inside a quoted value (CODECS, RESOLUTION lists) do not split it.
     */
    internal fun parseAttributes(raw: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        val current = StringBuilder()
        var inQuotes = false
        val parts = mutableListOf<String>()

        for (c in raw) {
            when {
                c == '"' -> { inQuotes = !inQuotes; current.append(c) }
                c == ',' && !inQuotes -> { parts += current.toString(); current.clear() }
                else -> current.append(c)
            }
        }
        if (current.isNotEmpty()) parts += current.toString()

        for (part in parts) {
            val idx = part.indexOf('=')
            if (idx <= 0) continue
            val key = part.substring(0, idx).trim().uppercase()
            val value = part.substring(idx + 1).trim().removeSurrounding("\"")
            result[key] = value
        }
        return result
    }
}
