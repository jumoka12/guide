package com.ampgames.vidsaver.ui.browser

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ampgames.vidsaver.R
import com.ampgames.vidsaver.core.net.Urls
import com.ampgames.vidsaver.data.browser.BrowserRepository
import com.ampgames.vidsaver.data.browser.adblock.AdBlocker
import com.ampgames.vidsaver.data.browser.prefs.BrowserPreferences
import com.ampgames.vidsaver.data.browser.sniffer.DomMediaPayload
import com.ampgames.vidsaver.data.browser.sniffer.MediaSniffer
import com.ampgames.vidsaver.data.config.AppConfig
import com.ampgames.vidsaver.data.download.DownloadStarter
import com.ampgames.vidsaver.domain.browser.SearchEngine
import com.ampgames.vidsaver.domain.browser.UnsupportedDomains
import com.ampgames.vidsaver.domain.media.MediaCandidate
import com.ampgames.vidsaver.data.browser.sniffer.VideoSnifferBridge
import com.ampgames.vidsaver.domain.media.extractor.ExtractorRegistry
import com.ampgames.vidsaver.ui.browser.web.VidSaverWebViewClient
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Owns browser state: tabs, navigation, settings and the list of downloadable
 * media found on the current page.
 *
 * It holds no reference to the WebView — imperative actions go out through
 * [commands] so the view layer stays the only thing that touches it.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val appConfig: AppConfig,
    private val preferences: BrowserPreferences,
    private val repository: BrowserRepository,
    private val mediaSniffer: MediaSniffer,
    private val extractorRegistry: ExtractorRegistry,
    private val adBlocker: AdBlocker,
    private val downloadStarter: DownloadStarter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        BrowserUiState(
            shortcuts = appConfig.browser.homeShortcuts,
            tabs = listOf(BrowserTab(id = newTabId())),
        ).let { it.copy(currentTabId = it.tabs.first().id) },
    )
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    private val _commands = Channel<BrowserCommand>(Channel.BUFFERED)
    val commands: Flow<BrowserCommand> = _commands.receiveAsFlow()

    private val _messages = Channel<BrowserMessage>(Channel.BUFFERED)
    val messages: Flow<BrowserMessage> = _messages.receiveAsFlow()

    /**
     * HTML of the current page, captured on load for the extractors. Truncated:
     * extractors parse metadata near the top of a document, and holding a whole
     * multi-megabyte page in memory is not worth it.
     */
    private var currentPageHtml: String = ""

    init {
        observeSettings()
        observeSniffedMedia()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            preferences.settings.collect { settings ->
                _uiState.update {
                    it.copy(
                        searchEngine = settings.searchEngine,
                        desktopMode = settings.desktopMode,
                        adBlockEnabled = settings.adBlockEnabled,
                    )
                }
                _commands.send(BrowserCommand.SetDesktopMode(settings.desktopMode))
            }
        }
    }

    /**
     * Re-runs extraction whenever the sniffer reports something new. Debounced
     * because a page that loads a playlist plus its variants can produce a burst
     * of reports in a few hundred milliseconds.
     */
    private fun observeSniffedMedia() {
        viewModelScope.launch {
            mediaSniffer.media.debounce(EXTRACTION_DEBOUNCE_MS).collect { sniffed ->
                val pageUrl = _uiState.value.currentUrl
                if (pageUrl.isBlank()) {
                    _uiState.update { it.copy(candidates = emptyList()) }
                    return@collect
                }
                val candidates = extractorRegistry.extract(pageUrl, currentPageHtml, sniffed)
                _uiState.update { it.copy(candidates = candidates) }
            }
        }
    }

    // ---------------------------------------------------------------- navigation

    fun onAddressTextChanged(text: String) {
        _uiState.update { it.copy(addressText = text) }
    }

    fun onAddressFocusChanged(focused: Boolean) {
        _uiState.update { it.copy(isAddressFocused = focused) }
    }

    /** Handles the address bar submit: navigate for a URL, search otherwise. */
    fun onAddressSubmitted() {
        val input = _uiState.value.addressText.trim()
        if (input.isEmpty()) return
        val target = if (Urls.looksLikeUrl(input)) {
            Urls.withScheme(input)
        } else {
            _uiState.value.searchEngine.searchUrl(input)
        }
        navigate(target)
    }

    fun onShortcutClicked(domain: String) = navigate(Urls.withScheme(domain))

    fun navigate(url: String) {
        if (UnsupportedDomains.isUnsupported(url)) {
            onUnsupportedDomainBlocked(url)
            return
        }
        _uiState.update { it.copy(isAddressFocused = false) }
        viewModelScope.launch { _commands.send(BrowserCommand.LoadUrl(url)) }
    }

    fun onBackClicked() = send(BrowserCommand.GoBack)

    fun onForwardClicked() = send(BrowserCommand.GoForward)

    fun onReloadClicked() {
        if (_uiState.value.isLoading) send(BrowserCommand.StopLoading) else send(BrowserCommand.Reload)
    }

    fun onHomeClicked() {
        currentPageHtml = ""
        mediaSniffer.onNavigationStarted("")
        _uiState.update { state ->
            state.copy(
                currentUrl = "",
                addressText = "",
                pageTitle = "",
                candidates = emptyList(),
                isLoading = false,
                loadProgress = 0,
                tabs = state.tabs.map {
                    if (it.id == state.currentTabId) it.copy(url = "", title = "") else it
                },
            )
        }
    }

    // ------------------------------------------------------- WebView callbacks

    fun onPageStarted(url: String) {
        currentPageHtml = ""
        _uiState.update { state ->
            state.copy(
                currentUrl = url,
                addressText = if (state.isAddressFocused) state.addressText else url,
                isLoading = true,
                candidates = emptyList(),
                siteAllowlisted = Urls.host(url)?.let { adBlocker.isAllowlisted(it) } ?: false,
                tabs = state.tabs.map {
                    if (it.id == state.currentTabId) it.copy(url = url) else it
                },
            )
        }
        refreshBookmarkState(url)
    }

    fun onPageFinished(url: String, title: String?) {
        _uiState.update { state ->
            state.copy(
                currentUrl = url,
                addressText = if (state.isAddressFocused) state.addressText else url,
                pageTitle = title.orEmpty(),
                isLoading = false,
                loadProgress = 100,
                tabs = state.tabs.map {
                    if (it.id == state.currentTabId) it.copy(url = url, title = title.orEmpty()) else it
                },
            )
        }
        viewModelScope.launch {
            runCatching { repository.recordVisit(url, title.orEmpty()) }
                .onFailure { Timber.w(it, "Could not record visit") }
        }
        refreshBookmarkState(url)
    }

    /** A title can arrive before the page finishes, or change mid-load. */
    fun onTitleChanged(title: String?) {
        val newTitle = title.orEmpty()
        _uiState.update { state ->
            state.copy(
                pageTitle = newTitle,
                tabs = state.tabs.map {
                    if (it.id == state.currentTabId) it.copy(title = newTitle) else it
                },
            )
        }
    }

    fun onProgressChanged(progress: Int) {
        _uiState.update { it.copy(loadProgress = progress, isLoading = progress < 100) }
    }

    fun onNavigationStateChanged(canGoBack: Boolean, canGoForward: Boolean) {
        _uiState.update { it.copy(canGoBack = canGoBack, canGoForward = canGoForward) }
    }

    /** Page HTML captured after load, already truncated by the caller. */
    fun onPageHtmlCaptured(html: String) {
        currentPageHtml = html.take(MAX_HTML_CHARS)
    }

    fun onDomMediaFound(payload: DomMediaPayload) {
        mediaSniffer.onDomMediaFound(payload.media)
    }

    fun onUnsupportedDomainBlocked(url: String) {
        Timber.i("Blocked unsupported domain: %s", Urls.host(url))
        emitMessage(R.string.msg_site_not_supported)
    }

    // ------------------------------------------------------------------ actions

    fun onCandidatesClicked() {
        if (_uiState.value.candidates.isEmpty()) {
            emitMessage(R.string.browser_no_video_yet)
            return
        }
        _uiState.update { it.copy(showCandidatesSheet = true) }
    }

    /** The crown on the home page. Phase 5 swaps this for the paywall. */
    fun onPremiumClicked() = emitMessage(R.string.msg_premium_soon)

    fun onCandidatesSheetDismissed() {
        _uiState.update { it.copy(showCandidatesSheet = false) }
    }

    /** Queues the candidate and wakes the download service. */
    fun onDownloadCandidate(candidate: MediaCandidate) {
        _uiState.update { it.copy(showCandidatesSheet = false) }
        viewModelScope.launch {
            runCatching { downloadStarter.enqueue(candidate) }
                .onSuccess { result ->
                    emitMessage(
                        if (result.alreadyExisted) {
                            R.string.msg_download_already_queued
                        } else {
                            R.string.msg_download_started
                        },
                        candidate.suggestedFileName,
                    )
                }
                .onFailure { error ->
                    Timber.e(error, "Could not queue %s", candidate.url)
                    emitMessage(R.string.msg_download_queue_failed)
                }
        }
    }

    fun onToggleBookmark() {
        val state = _uiState.value
        val url = state.currentUrl
        if (url.isBlank()) return
        viewModelScope.launch {
            val nowBookmarked = repository.toggleBookmark(url, state.pageTitle.ifBlank { url })
            _uiState.update { it.copy(isBookmarked = nowBookmarked) }
            emitMessage(
                if (nowBookmarked) R.string.msg_bookmark_added else R.string.msg_bookmark_removed,
            )
        }
    }

    fun onToggleAdBlock() {
        viewModelScope.launch { preferences.setAdBlockEnabled(!_uiState.value.adBlockEnabled) }
    }

    fun onToggleDesktopMode() {
        viewModelScope.launch { preferences.setDesktopMode(!_uiState.value.desktopMode) }
    }

    fun onSearchEngineSelected(engine: SearchEngine) {
        viewModelScope.launch { preferences.setSearchEngine(engine) }
    }

    /** Per-site ad-block override for the page currently open. */
    fun onToggleSiteAllowlist() {
        val host = Urls.host(_uiState.value.currentUrl) ?: return
        val allowlisted = !_uiState.value.siteAllowlisted
        viewModelScope.launch {
            adBlocker.setAllowlisted(host, allowlisted)
            _uiState.update { it.copy(siteAllowlisted = allowlisted) }
            emitMessage(
                if (allowlisted) R.string.msg_ads_allowed_on else R.string.msg_ads_blocked_on,
                host,
            )
            _commands.send(BrowserCommand.Reload)
        }
    }

    // --------------------------------------------------------------------- tabs

    fun onTabsClicked() = _uiState.update { it.copy(showTabsSheet = true) }

    fun onTabsSheetDismissed() = _uiState.update { it.copy(showTabsSheet = false) }

    fun onNewTab() {
        val tab = BrowserTab(id = newTabId())
        _uiState.update { it.copy(tabs = it.tabs + tab, showTabsSheet = false) }
        selectTab(tab.id)
    }

    fun selectTab(tabId: String) {
        val tab = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
        currentPageHtml = ""
        _uiState.update {
            it.copy(
                currentTabId = tabId,
                currentUrl = tab.url,
                addressText = tab.url,
                pageTitle = tab.title,
                candidates = emptyList(),
                showTabsSheet = false,
            )
        }
        mediaSniffer.onNavigationStarted(tab.url)
        send(BrowserCommand.SwitchTab(tab))
    }

    fun closeTab(tabId: String) {
        val state = _uiState.value
        if (state.tabs.size <= 1) {
            // Never leave zero tabs: reset the last one to the home page.
            onHomeClicked()
            return
        }
        val index = state.tabs.indexOfFirst { it.id == tabId }
        if (index < 0) return
        val remaining = state.tabs.filterNot { it.id == tabId }
        _uiState.update { it.copy(tabs = remaining) }
        if (state.currentTabId == tabId) {
            selectTab(remaining[index.coerceAtMost(remaining.lastIndex)].id)
        }
    }

    // ---------------------------------------------------------------- bookmarks

    fun onBookmarksClicked() = _uiState.update { it.copy(showBookmarksSheet = true) }

    fun onBookmarksSheetDismissed() = _uiState.update { it.copy(showBookmarksSheet = false) }

    fun observeBookmarks() = repository.observeBookmarks()

    fun observeHistory() = repository.observeHistory()

    fun onRemoveBookmark(url: String) {
        viewModelScope.launch { repository.removeBookmark(url) }
    }

    fun onClearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            emitMessage(R.string.msg_history_cleared)
        }
    }

    // -------------------------------------------------------------- WebView glue

    /**
     * Builds the WebView client. Done here rather than in the composable so the
     * ad blocker and sniffer stay private to the ViewModel and the view layer
     * needs no dependencies of its own.
     */
    fun createWebViewClient(): VidSaverWebViewClient = VidSaverWebViewClient(
        adBlocker = adBlocker,
        mediaSniffer = mediaSniffer,
        callbacks = object : VidSaverWebViewClient.Callbacks {
            override fun onPageStarted(url: String) = this@BrowserViewModel.onPageStarted(url)

            override fun onPageFinished(url: String, title: String?) =
                this@BrowserViewModel.onPageFinished(url, title)

            override fun onProgressRelevantStateChanged(canGoBack: Boolean, canGoForward: Boolean) =
                onNavigationStateChanged(canGoBack, canGoForward)

            override fun onUnsupportedDomainBlocked(url: String) =
                this@BrowserViewModel.onUnsupportedDomainBlocked(url)

            override fun isAdBlockEnabled(): Boolean = _uiState.value.adBlockEnabled

            override fun currentPageUrl(): String = _uiState.value.currentUrl
        },
    )

    fun createSnifferBridge(): VideoSnifferBridge =
        VideoSnifferBridge { payload -> onDomMediaFound(payload) }

    /** Records the WebView's own User-Agent so re-fetches match what it sent. */
    fun onUserAgentResolved(userAgent: String) {
        mediaSniffer.defaultUserAgent = userAgent
    }

    // ----------------------------------------------------------------- internals

    private fun refreshBookmarkState(url: String) {
        viewModelScope.launch {
            val bookmarked = runCatching { repository.isBookmarked(url) }.getOrDefault(false)
            _uiState.update { it.copy(isBookmarked = bookmarked) }
        }
    }

    private fun send(command: BrowserCommand) {
        viewModelScope.launch { _commands.send(command) }
    }

    private fun emitMessage(@StringRes resId: Int, vararg args: String) {
        viewModelScope.launch { _messages.send(BrowserMessage(resId, args.toList())) }
    }

    private fun newTabId(): String = UUID.randomUUID().toString()

    companion object {
        const val MAX_HTML_CHARS = 512 * 1024
        private const val EXTRACTION_DEBOUNCE_MS = 350L
    }
}
