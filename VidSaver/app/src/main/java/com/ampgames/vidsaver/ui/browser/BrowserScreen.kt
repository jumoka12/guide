package com.ampgames.vidsaver.ui.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

    // System back walks page history, then returns to the start page. Only
    // once there does it leave the screen.
    BackHandler(enabled = !state.showHome) {
        if (state.canGoBack) viewModel.onBackClicked() else viewModel.onHomeClicked()
    }

    Scaffold(
        modifier = modifier.testTag(BROWSER_SCREEN_TEST_TAG),
        topBar = {
            BrowserTopBar(
                state = state,
                onAddressChange = viewModel::onAddressTextChanged,
                onAddressSubmit = viewModel::onAddressSubmitted,
                onAddressFocusChange = viewModel::onAddressFocusChanged,
                onForward = viewModel::onForwardClicked,
                onReload = viewModel::onReloadClicked,
                onHome = viewModel::onHomeClicked,
                onToggleBookmark = viewModel::onToggleBookmark,
                onTabs = viewModel::onTabsClicked,
                onBookmarks = viewModel::onBookmarksClicked,
                onToggleAdBlock = viewModel::onToggleAdBlock,
                onToggleSiteAllowlist = viewModel::onToggleSiteAllowlist,
                onToggleDesktopMode = viewModel::onToggleDesktopMode,
                onShowFound = viewModel::onCandidatesClicked,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!state.showHome && state.candidateCount > 0) {
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
                generation = state.webViewGeneration,
                modifier = Modifier.fillMaxSize(),
            )

            if (state.showHome) {
                BrowserHome(
                    state = state,
                    onAddressChange = viewModel::onAddressTextChanged,
                    onAddressSubmit = viewModel::onAddressSubmitted,
                    onAddressFocusChange = viewModel::onAddressFocusChanged,
                    onShortcutClick = viewModel::onShortcutClicked,
                    onSearchEngineSelected = viewModel::onSearchEngineSelected,
                    onPremium = viewModel::onPremiumClicked,
                    onHome = viewModel::onHomeClicked,
                    onForward = viewModel::onForwardClicked,
                    onToggleBookmark = viewModel::onToggleBookmark,
                    onTabs = viewModel::onTabsClicked,
                    onBookmarks = viewModel::onBookmarksClicked,
                    onToggleAdBlock = viewModel::onToggleAdBlock,
                    onToggleSiteAllowlist = viewModel::onToggleSiteAllowlist,
                    onToggleDesktopMode = viewModel::onToggleDesktopMode,
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

/**
 * A round red button, the one action a page exists for. The count sits under
 * the glyph rather than in a badge, which used to collide with it.
 */
@Composable
private fun DownloadFab(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier
            .size(64.dp)
            .testTag(DOWNLOAD_FAB_TEST_TAG),
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Download,
                contentDescription = stringResource(R.string.browser_download_available),
                modifier = Modifier.size(28.dp),
            )
            if (count > 1) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
