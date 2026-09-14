package com.ampgames.vidsaver.domain.media

import com.ampgames.vidsaver.core.net.Urls

/** Builds safe, human-readable file names for downloads. */
object FileNames {

    private const val MAX_BASE_LENGTH = 60

    /** Path separators and characters reserved by common file systems. */
    private val ILLEGAL = Regex("""[\\/:*?"<>|]""")
    private val WHITESPACE = Regex("""\s+""")

    /**
     * Sanitises [raw] into something safe for MediaStore and every common file
     * system: no path separators, no control characters, no trailing dots or
     * spaces, and bounded in length.
     */
    fun sanitize(raw: String): String {
        val printable = raw.filter { it.code >= 0x20 && it.code != 0x7F }
        val cleaned = ILLEGAL.replace(printable, " ")
            .replace(WHITESPACE, " ")
            .trim()
            .trim('.', ' ')
        val bounded = if (cleaned.length > MAX_BASE_LENGTH) {
            cleaned.take(MAX_BASE_LENGTH).trimEnd('.', ' ')
        } else {
            cleaned
        }
        return bounded.ifEmpty { "video" }
    }

    /**
     * Name for a download, preferring the page title and falling back to the
     * site host so two downloads from different sites never collide silently.
     */
    fun forCandidate(
        title: String?,
        pageUrl: String,
        extension: String,
        resolution: String? = null,
    ): String {
        val base = title?.takeIf { it.isNotBlank() }
            ?: Urls.host(pageUrl)?.removePrefix("www.")
            ?: "video"
        val withResolution = if (resolution.isNullOrBlank()) base else "$base $resolution"
        return "${sanitize(withResolution)}.$extension"
    }

    /**
     * Appends ` (2)`, ` (3)`… before the extension until the name is not in
     * [taken]. Used when a file of the same name already exists.
     */
    fun deduplicate(fileName: String, taken: Set<String>): String {
        if (fileName !in taken) return fileName
        val base = fileName.substringBeforeLast('.', fileName)
        val ext = fileName.substringAfterLast('.', "")
        val suffix = if (ext.isEmpty()) "" else ".$ext"
        var counter = 2
        while (true) {
            val next = "$base ($counter)$suffix"
            if (next !in taken) return next
            counter++
        }
    }
}
