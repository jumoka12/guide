package com.ampgames.vidsaver.ui.downloads

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ampgames.vidsaver.R
import com.ampgames.vidsaver.ui.components.PlaceholderScreen
import timber.log.Timber

const val DOWNLOADS_SCREEN_TEST_TAG = "downloads_screen"
const val DOWNLOADS_EMPTY_TEST_TAG = "downloads_empty"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(viewModel, context) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(
                context.getString(message.resId, *message.args.toTypedArray()),
            )
        }
    }

    Scaffold(
        modifier = modifier.testTag(DOWNLOADS_SCREEN_TEST_TAG),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (state.isEmpty) {
                PlaceholderScreen(
                    title = stringResource(R.string.tab_downloads),
                    description = stringResource(R.string.placeholder_downloads),
                    icon = Icons.Outlined.Download,
                    testTag = DOWNLOADS_EMPTY_TEST_TAG,
                )
                return@Box
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item {
                    DownloadsHeader(
                        wifiOnly = state.wifiOnly,
                        showClear = state.finished.isNotEmpty(),
                        onToggleWifiOnly = viewModel::onToggleWifiOnly,
                        onClearFinished = viewModel::onClearFinished,
                    )
                }

                if (state.active.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.downloads_section_active)) }
                    items(state.active, key = { it.id }) { item ->
                        DownloadRow(
                            item = item,
                            onPause = { viewModel.onPause(item.id) },
                            onResume = { viewModel.onResume(item.id) },
                            onCancel = { viewModel.onCancel(item.id) },
                            onRetry = { viewModel.onRetry(item.id) },
                            onOpen = { openInPlayer(context, item) },
                            onDelete = { viewModel.onDelete(item.id) },
                        )
                    }
                }

                if (state.finished.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.downloads_section_finished)) }
                    items(state.finished, key = { it.id }) { item ->
                        DownloadRow(
                            item = item,
                            onPause = { viewModel.onPause(item.id) },
                            onResume = { viewModel.onResume(item.id) },
                            onCancel = { viewModel.onCancel(item.id) },
                            onRetry = { viewModel.onRetry(item.id) },
                            onOpen = { openInPlayer(context, item) },
                            onDelete = { viewModel.onDelete(item.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadsHeader(
    wifiOnly: Boolean,
    showClear: Boolean,
    onToggleWifiOnly: () -> Unit,
    onClearFinished: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        FilterChip(
            selected = wifiOnly,
            onClick = onToggleWifiOnly,
            label = { Text(stringResource(R.string.downloads_wifi_only)) },
            modifier = Modifier.testTag("wifi_only_chip"),
        )
        if (showClear) {
            TextButton(onClick = onClearFinished, modifier = Modifier.testTag("clear_finished")) {
                Text(stringResource(R.string.downloads_clear_finished))
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

/**
 * Phase 3 hands a completed download to whatever the system uses to play video.
 * Phase 4 replaces this with the in-app player.
 */
private fun openInPlayer(context: Context, item: DownloadUiItem) {
    val uri = item.mediaStoreUri ?: return
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(uri), "video/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Timber.w(e, "No app can play %s", uri)
    }
}
