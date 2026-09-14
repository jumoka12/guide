package com.ampgames.vidsaver.core.net

/**
 * URL helpers used by the browser, the ad blocker and the extractors.
 *
 * Deliberately hand-rolled instead of `android.net.Uri` so the same code runs in
 * JVM unit tests, and deliberately lenient: it is fed whatever a page requests,
 * including malformed URLs, and must never throw.
 */
object Urls {

    private val SCHEME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

    /**
     * Host of [url], lowercased, without userinfo, port or trailing dot.
     * Returns null when there is no authority component (`data:`, `about:`,
     * `javascript:` and friends).
     */
    fun host(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return null

        val afterScheme = when {
            trimmed.startsWith("//") -> trimmed.substring(2)
            SCHEME_REGEX.containsMatchIn(trimmed) -> {
                val idx = trimmed.indexOf("://")
                if (idx < 0) return null // opaque scheme, e.g. mailto:
                trimmed.substring(idx + 3)
            }
            else -> trimmed
        }

        val authority = afterScheme.takeWhile { it != '/' && it != '?' && it != '#' }
        if (authority.isEmpty()) return null

        val withoutUserInfo = authority.substringAfterLast('@')
        // IPv6 literals keep their brackets; only strip a port after them.
        val hostPart = if (withoutUserInfo.startsWith("[")) {
            val close = withoutUserInfo.indexOf(']')
            if (close < 0) withoutUserInfo else withoutUserInfo.substring(0, close + 1)
        } else {
            withoutUserInfo.substringBefore(':')
        }

        return hostPart.trimEnd('.').lowercase().takeIf { it.isNotEmpty() }
    }

    /** Path of [url] without query or fragment; "/" when absent. */
    fun path(url: String): String {
        val trimmed = url.trim()
        val idx = trimmed.indexOf("://")
        val afterAuthority = if (idx >= 0) {
            val rest = trimmed.substring(idx + 3)
            val slash = rest.indexOf('/')
            if (slash < 0) return "/" else rest.substring(slash)
        } else {
            trimmed
        }
        return afterAuthority.takeWhile { it != '?' && it != '#' }.ifEmpty { "/" }
    }

    /** Lowercased file extension of the URL path, without the dot, or null. */
    fun fileExtension(url: String): String? {
        val name = path(url).substringAfterLast('/')
        if (!name.contains('.')) return null
        return name.substringAfterLast('.').lowercase().takeIf { it.isNotEmpty() }
    }

    /**
     * [host] followed by each of its parent domains: `a.b.example.com` yields
     * `a.b.example.com`, `b.example.com`, `example.com`, `com`. Used for suffix
     * matching against blocklists and allowlists.
     */
    fun hostAndParents(host: String): List<String> {
        if (host.startsWith("[")) return listOf(host) // IPv6 literal has no parents
        val labels = host.split('.')
        if (labels.size <= 1) return listOf(host)
        return (labels.indices).map { i -> labels.subList(i, labels.size).joinToString(".") }
    }

    /** True when [host] equals [domain] or is a subdomain of it. */
    fun hostMatchesDomain(host: String, domain: String): Boolean {
        val h = host.lowercase().trimEnd('.')
        val d = domain.lowercase().trimEnd('.').removePrefix("*.")
        return h == d || h.endsWith(".$d")
    }

    /**
     * True when [input] looks like something to navigate to rather than search
     * for. Anything with a scheme, an obvious TLD, or a host:port form counts.
     */
    fun looksLikeUrl(input: String): Boolean {
        val s = input.trim()
        if (s.isEmpty() || s.contains(' ')) return false
        if (s.startsWith("http://", ignoreCase = true) ||
            s.startsWith("https://", ignoreCase = true) ||
            s.startsWith("about:", ignoreCase = true) ||
            s.startsWith("file://", ignoreCase = true)
        ) {
            return true
        }
        if (s.startsWith("localhost", ignoreCase = true)) return true
        val authority = s.substringBefore('/').substringBefore('?')
        if (!authority.contains('.')) return false
        // Require a plausible TLD: at least two letters after the last dot.
        val tld = authority.substringAfterLast('.').substringBefore(':')
        return tld.length >= 2 && tld.all { it.isLetter() }
    }

    /** Raw query string without the leading `?`; empty when there is none. */
    fun query(url: String): String {
        val afterFragment = url.trim().substringBefore('#')
        val idx = afterFragment.indexOf('?')
        return if (idx < 0) "" else afterFragment.substring(idx + 1)
    }

    /** `key=value` pairs of the query, in order, lowercased keys. */
    fun queryParams(url: String): List<Pair<String, String>> =
        query(url)
            .split('&')
            .filter { it.isNotEmpty() }
            .map { pair ->
                val idx = pair.indexOf('=')
                if (idx < 0) {
                    pair.lowercase() to ""
                } else {
                    pair.substring(0, idx).lowercase() to pair.substring(idx + 1)
                }
            }

    /** True when the query carries any of [names]. */
    fun hasQueryParam(url: String, names: Set<String>): Boolean =
        queryParams(url).any { (key, _) -> key in names }

    /** The URL with [drop] parameters removed, preserving the rest in order. */
    fun withoutQueryParams(url: String, drop: Set<String>): String {
        val trimmed = url.trim()
        if (query(trimmed).isEmpty()) return trimmed.substringBefore('#')

        val base = trimmed.substringBefore('?')
        val kept = queryParams(trimmed)
            .filterNot { (key, _) -> key in drop }
            .joinToString("&") { (key, value) -> if (value.isEmpty()) key else "$key=$value" }

        return if (kept.isEmpty()) base else "$base?$kept"
    }

    /** Scheme + host + path, with the query and fragment dropped entirely. */
    fun withoutQuery(url: String): String = url.trim().substringBefore('?').substringBefore('#')

    /** Adds a scheme when the user typed a bare host. */
    fun withScheme(input: String): String {
        val s = input.trim()
        return if (SCHEME_REGEX.containsMatchIn(s)) s else "https://$s"
    }
}
