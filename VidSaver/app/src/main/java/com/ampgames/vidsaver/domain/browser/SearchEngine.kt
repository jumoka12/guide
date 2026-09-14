package com.ampgames.vidsaver.domain.browser

import java.net.URLEncoder

enum class SearchEngine(val id: String, val displayName: String, private val template: String) {
    GOOGLE("google", "Google", "https://www.google.com/search?q="),
    BING("bing", "Bing", "https://www.bing.com/search?q="),
    DUCKDUCKGO("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q="),
    YANDEX("yandex", "Yandex", "https://yandex.com/search/?text="),
    BAIDU("baidu", "Baidu", "https://www.baidu.com/s?wd=");

    fun searchUrl(query: String): String =
        template + URLEncoder.encode(query, Charsets.UTF_8.name())

    companion object {
        val DEFAULT: SearchEngine = GOOGLE

        fun fromId(id: String?): SearchEngine =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
    }
}
