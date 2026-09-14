package com.ampgames.vidsaver.ui.browser

import androidx.lifecycle.ViewModel
import com.ampgames.vidsaver.data.config.AppConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BrowserUiState(
    val shortcuts: List<String> = emptyList(),
    val searchEngine: String = "google",
    val blockedDomains: List<String> = emptyList(),
)

/**
 * Phase 1 scope: proves the config pipeline end to end (asset -> Hilt -> UI).
 * The WebView, tabs, ad-blocking and video detection arrive in Phase 2.
 */
@HiltViewModel
class BrowserViewModel @Inject constructor(
    appConfig: AppConfig,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        BrowserUiState(
            shortcuts = appConfig.browser.homeShortcuts,
            searchEngine = appConfig.browser.defaultSearchEngine,
            blockedDomains = appConfig.browser.blockedDomains,
        ),
    )
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()
}
