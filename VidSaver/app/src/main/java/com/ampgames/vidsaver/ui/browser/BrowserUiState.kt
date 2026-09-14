package com.ampgames.vidsaver.ui.browser

import androidx.annotation.StringRes

import com.ampgames.vidsaver.domain.browser.SearchEngine
import com.ampgames.vidsaver.domain.media.MediaCandidate

/** One in-memory tab. WebView state itself is kept separately, not in UI state. */
data class BrowserTab(
    val id: String,
    val url: String = "",
    val title: String = "",
) {
    /** A tab with no URL shows the home page. */
    val isHome: Boolean get() = url.isBlank()

    val displayTitle: String get() = title.ifBlank { url.ifBlank { "New tab" } }
}

data class BrowserUiState(
    val tabs: List<BrowserTab> = emptyList(),
    val currentTabId: String = "",
    val addressText: String = "",
    val isAddressFocused: Boolean = false,
    val currentUrl: String = "",
    val pageTitle: String = "",
    val isLoading: Boolean = false,
    val loadProgress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val shortcuts: List<String> = emptyList(),
    val searchEngine: SearchEngine = SearchEngine.DEFAULT,
    val desktopMode: Boolean = false,
    val adBlockEnabled: Boolean = true,
    val siteAllowlisted: Boolean = false,
    val isBookmarked: Boolean = false,
    val candidates: List<MediaCandidate> = emptyList(),
    val showCandidatesSheet: Boolean = false,
    val showTabsSheet: Boolean = false,
    val showBookmarksSheet: Boolean = false,
    /** Bumped to rebuild the WebView after its renderer dies. */
    val webViewGeneration: Int = 0,
) {
    val currentTab: BrowserTab?
        get() = tabs.firstOrNull { it.id == currentTabId }

    val showHome: Boolean get() = currentUrl.isBlank()

    /**
     * What the download button advertises: the videos the page itself named
     * when it named any, otherwise everything that was seen. Matches the
     * headline count in the candidates sheet.
     */
    val candidateCount: Int
        get() = candidates.count { it.isPrimary }.takeIf { it > 0 } ?: candidates.size
}

/** Imperative actions the composable applies to the WebView. */
sealed interface BrowserCommand {
    data class LoadUrl(val url: String) : BrowserCommand
    data object Reload : BrowserCommand
    data object GoBack : BrowserCommand
    data object GoForward : BrowserCommand
    data object StopLoading : BrowserCommand

    /** Save the current tab's WebView state, then show [tab]. */
    data class SwitchTab(val tab: BrowserTab) : BrowserCommand

    /** Render [html] directly, as an origin-less page. Used by the playback self-test. */
    data class LoadHtml(val html: String) : BrowserCommand
    data class SetDesktopMode(val enabled: Boolean) : BrowserCommand
}

/**
 * A one-shot user-visible message, carried as a string resource rather than
 * text so the ViewModel stays free of Context and everything stays localizable.
 */
data class BrowserMessage(
    @StringRes val resId: Int,
    val args: List<String> = emptyList(),
    val id: Long = System.nanoTime(),
)
