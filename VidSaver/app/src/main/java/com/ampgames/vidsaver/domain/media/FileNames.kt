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

    /** `#tag` tokens, which social captions carry by the dozen. */
    private val HASHTAG = Regex("""#[\p{L}\p{N}_]+""")

    /**
     * Trailing site branding: " | Facebook", " - TikTok", " on Instagram".
     * Only the site the page is on is stripped, so a title that genuinely
     * ends in " - Part 2" is left alone.
     */
    private fun siteSuffix(siteName: String) = Regex(
        """(?:\s*[|\-–—•:]\s*|\s+on\s+)${Regex.escape(siteName)}\s*$""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Turns a page or `<video>` title into something a person would call the
     * video: hashtags gone, the site's own name gone from the end, whitespace
     * collapsed. Returns null when nothing meaningful is left, so callers fall
     * back to the host rather than saving "#fyp #viral.mp4".
     */
    fun cleanTitle(raw: String?, pageUrl: String): String? {
        if (raw.isNullOrBlank()) return null
        var title = HASHTAG.replace(raw, " ")
        siteName(pageUrl)?.let { site ->
            title = siteSuffix(site).replace(title, "")
            title = sitePrefix(site).replace(title, "")
        }
        title = title.replace(WHITESPACE, " ").trim().trim('-', '|', '•', ':', ' ')
        // "Make Your Day", "For You": the site's slogan or a feed's name is not
        // the name of a clip. Better to fall through to the post id or a stamp.
        if (title.lowercase() in GENERIC_TITLES) return null
        return title.takeIf { it.isNotBlank() }
    }

    /** Leading site branding: "TikTok - Make Your Day", "Facebook | Watch". */
    private fun sitePrefix(siteName: String) = Regex(
        """^\s*${Regex.escape(siteName)}\s*[|\-–—•:]\s*""",
        RegexOption.IGNORE_CASE,
    )

    private val GENERIC_TITLES = setOf(
        "make your day", "for you", "following", "explore", "discover", "watch", "reels", "reel",
        "home", "feed", "videos", "video", "log in or sign up", "log in", "sign up",
        "facebook", "instagram", "tiktok", "x", "twitter", "pinterest", "vimeo", "dailymotion",
    )

    /** "facebook" for m.facebook.com, "tiktok" for www.tiktok.com. */
    private fun siteName(pageUrl: String): String? {
        val host = Urls.host(pageUrl) ?: return null
        val labels = host.split('.').filter { it.isNotBlank() }
        if (labels.size < 2) return labels.firstOrNull()
        // The label before the public suffix; good enough for the sites we
        // target without shipping a suffix list.
        val idx = if (labels.size >= 3 && labels[labels.size - 2].length <= 3) labels.size - 3 else labels.size - 2
        return labels.getOrNull(idx)
    }

    /** The post id in a social URL: `/video/123`, `/reel/AbC`, `?v=123`, `/status/123`. */
    private val POST_ID = Regex(
        """(?:/(?:video|videos|reel|reels|p|status|watch|shorts)/([A-Za-z0-9_-]{4,}))|(?:[?&]v=([A-Za-z0-9_-]{4,}))""",
    )

    /**
     * `tiktok_1789411373000`, `facebook_4194402202515`: the site and the post
     * id, which is what a person can match back to the page later. Null when
     * the URL carries no recognisable id.
     */
    fun siteIdName(pageUrl: String): String? {
        val site = siteName(pageUrl) ?: return null
        val match = POST_ID.find(pageUrl.substringBefore('#')) ?: return null
        val id = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() } ?: return null
        return "${site}_$id"
    }

    /**
     * Name for a download: the cleaned page title, else the site and post id,
     * else the site host — so two downloads from different sites never collide
     * silently.
     */
    fun forCandidate(
        title: String?,
        pageUrl: String,
        extension: String,
        resolution: String? = null,
        detectedAt: Long? = null,
    ): String {
        val base = cleanTitle(title, pageUrl)
            ?: siteIdName(pageUrl)
            ?: stampedName(pageUrl, detectedAt)
            ?: Urls.host(pageUrl)?.removePrefix("www.")
            ?: "video"
        val withResolution = if (resolution.isNullOrBlank()) base else "$base $resolution"
        return "${sanitize(withResolution)}.$extension"
    }

    /**
     * `tiktok_20260914_213045`: for a feed page whose address carries no post
     * id, the site and the moment the clip was seen — unique, and sortable.
     */
    fun stampedName(pageUrl: String, detectedAt: Long?): String? {
        if (detectedAt == null || detectedAt <= 0L) return null
        val site = siteName(pageUrl) ?: return null
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date(detectedAt))
        return "${site}_$stamp"
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
