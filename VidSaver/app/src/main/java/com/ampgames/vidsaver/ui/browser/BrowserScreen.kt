package com.ampgames.vidsaver.ui.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ampgames.vidsaver.R
import com.ampgames.vidsaver.ui.browser.components.BookmarksSheet
import com.ampgames.vidsaver.ui.browser.components.BrowserHome
import com.ampgames.vidsaver.ui.browser.components.BrowserTopBar
import com.ampgames.vidsaver.ui.browser.components.CandidatesSheet
import com.ampgames.vidsaver.ui.browser.components.TabsSheet
import com.ampgames.vidsaver.ui.browser.web.BrowserWebView
import com.ampgames.vidsaver.ui.permissions.rememberDownloadPermissions

const val BROWSER_SCREEN_TEST_TAG = "browser_screen"
const val DOWNLOAD_FAB_TEST_TAG = "download_fab"

@Composable
fun BrowserScreen(
    modifier: Modifier = Modifier,
    viewModel: BrowserViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val bookmarks by viewModel.observeBookmarks().collectAsStateWithLifecycle(emptyList())
    val history by viewModel.observeHistory().collectAsStateWithLifecycle(emptyList())
    val snackbarHostState = remember { SnackbarHostState() }

    // Built once and kept: recreating the client would detach it from the WebView.
    val webViewClient = remember(viewModel) { viewModel.createWebViewClient() }
    // Asked for at the moment the user saves a video, not on a cold start.
    val downloadPermissions = rememberDownloadPermissions()
    val snifferBridge = remember(viewModel) { viewModel.createSnifferBridge() }

    val context = LocalContext.current
    LaunchedEffect(viewModel, context) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(
                context.getString(message.resId, *message.args.toTypedArray()),
            )
        }
    }

    Scaffold(
        modifier = modifier.testTag(BROWSER_SCREEN_TEST_TAG),
        topBar = {
            BrowserTopBar(
                state = state,
                onAddressChange = viewModel::onAddressTextChanged,
                onAddressSubmit = viewModel::onAddressSubmitted,
                onAddressFocusChange = viewModel::onAddressFocusChanged,
                onBack = viewModel::onBackClicked,
                onForward = viewModel::onForwardClicked,
                onReload = viewModel::onReloadClicked,
                onHome = viewModel::onHomeClicked,
                onToggleBookmark = viewModel::onToggleBookmark,
                onTabs = viewModel::onTabsClicked,
                onBookmarks = viewModel::onBookmarksClicked,
                onToggleAdBlock = viewModel::onToggleAdBlock,
                onToggleSiteAllowlist = viewModel::onToggleSiteAllowlist,
                onToggleDesktopMode = viewModel::onToggleDesktopMode,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (state.candidateCount > 0) {
                DownloadFab(
                    count = state.candidateCount,
                    onClick = viewModel::onCandidatesClicked,
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // The WebView stays in the tree even on the home page so its state
            // and the current page survive a trip to the shortcuts grid.
            BrowserWebView(
                commands = viewModel.commands,
                client = webViewClient,
                bridge = snifferBridge,
                desktopMode = state.desktopMode,
                onProgressChanged = viewModel::onProgressChanged,
                onTitleChanged = viewModel::onTitleChanged,
                onNavigationStateChanged = viewModel::onNavigationStateChanged,
                onUserAgentResolved = viewModel::onUserAgentResolved,
                onPageHtmlCaptured = viewModel::onPageHtmlCaptured,
                modifier = Modifier.fillMaxSize(),
            )

            if (state.showHome) {
                BrowserHome(
                    shortcuts = state.shortcuts,
                    searchEngine = state.searchEngine,
                    onShortcutClick = viewModel::onShortcutClicked,
                    onSearchEngineSelected = viewModel::onSearchEngineSelected,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (state.showCandidatesSheet) {
        CandidatesSheet(
            candidates = state.candidates,
            onDownload = { candidate ->
                // Fire-and-forget: a denied notification permission only costs
                // the progress notification, so the download starts either way.
                downloadPermissions.request()
                viewModel.onDownloadCandidate(candidate)
            },
            onDismiss = viewModel::onCandidatesSheetDismissed,
        )
    }

    if (state.showTabsSheet) {
        TabsSheet(
            tabs = state.tabs,
            currentTabId = state.currentTabId,
            onSelect = viewModel::selectTab,
            onClose = viewModel::closeTab,
            onNewTab = viewModel::onNewTab,
            onDismiss = viewModel::onTabsSheetDismissed,
        )
    }

    if (state.showBookmarksSheet) {
        BookmarksSheet(
            bookmarks = bookmarks,
            history = history,
            onOpen = { url ->
                viewModel.onBookmarksSheetDismissed()
                viewModel.navigate(url)
            },
            onRemoveBookmark = viewModel::onRemoveBookmark,
            onClearHistory = viewModel::onClearHistory,
            onDismiss = viewModel::onBookmarksSheetDismissed,
        )
    }
}

@Composable
private fun DownloadFab(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Solid brand colour: this is the one action the screen exists for, and a
    // tonal container reads as "disabled" next to a busy web page. The count
    // lives in the label rather than a badge, which collided with the icon.
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier.testTag(DOWNLOAD_FAB_TEST_TAG),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        icon = { Icon(Icons.Filled.Download, contentDescription = null) },
        text = {
            Text(
                if (count == 1) {
                    stringResource(R.string.browser_download_available)
                } else {
                    stringResource(R.string.browser_download_count, count)
                },
            )
        },
    )
}
