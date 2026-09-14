package com.ampgames.vidsaver.ui.gallery

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ampgames.vidsaver.R
import com.ampgames.vidsaver.data.gallery.toUris
import com.ampgames.vidsaver.domain.gallery.GallerySort
import com.ampgames.vidsaver.domain.gallery.GalleryVideo
import com.ampgames.vidsaver.ui.components.PlaceholderScreen
import timber.log.Timber

const val GALLERY_SCREEN_TEST_TAG = "gallery_screen"
const val GALLERY_EMPTY_TEST_TAG = "gallery_empty"
const val GALLERY_GRID_TEST_TAG = "gallery_grid"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    onOpenVideo: (GalleryVideo) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: GalleryViewModel = hiltViewModel(),
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

    LaunchedEffect(viewModel, context) {
        viewModel.shareRequests.collect { request ->
            shareVideos(context, request.videos)
        }
    }

    Scaffold(
        modifier = modifier.testTag(GALLERY_SCREEN_TEST_TAG),
        topBar = {
            if (state.inSelectionMode) {
                SelectionTopBar(
                    selectedCount = state.selectedCount,
                    allSelected = state.allSelected,
                    canRename = state.selectedCount == 1,
                    onClose = viewModel::onClearSelection,
                    onToggleAll = viewModel::onToggleSelectAll,
                    onShare = viewModel::onShareSelected,
                    onRename = viewModel::onRenameRequested,
                    onDelete = viewModel::onDeleteSelected,
                )
            } else {
                GalleryTopBar(
                    sort = state.sort,
                    onSortSelected = viewModel::onSortSelected,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (state.isEmpty) {
                PlaceholderScreen(
                    title = stringResource(R.string.tab_gallery),
                    description = stringResource(R.string.placeholder_gallery),
                    icon = Icons.Outlined.VideoLibrary,
                    testTag = GALLERY_EMPTY_TEST_TAG,
                )
                return@Box
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(4.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(GALLERY_GRID_TEST_TAG),
            ) {
                items(state.videos, key = { it.id }) { video ->
                    GalleryTile(
                        video = video,
                        selected = state.selection.isSelected(video.id),
                        selectionMode = state.inSelectionMode,
                        onClick = { viewModel.onVideoClicked(video)?.let(onOpenVideo) },
                        onLongClick = { viewModel.onVideoLongPressed(video) },
                    )
                }
            }
        }
    }

    state.renaming?.let { video ->
        RenameDialog(
            initialName = video.baseName,
            onConfirm = viewModel::onRenameConfirmed,
            onDismiss = viewModel::onRenameDismissed,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryTopBar(
    sort: GallerySort,
    onSortSelected: (GallerySort) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text(stringResource(R.string.tab_gallery)) },
        actions = {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.testTag("gallery_sort"),
            ) {
                Icon(Icons.Filled.Sort, stringResource(R.string.gallery_sort))
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                GallerySort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(option.labelRes())) },
                        onClick = {
                            onSortSelected(option)
                            menuExpanded = false
                        },
                        trailingIcon = {
                            if (option == sort) Icon(Icons.Filled.SelectAll, null)
                        },
                    )
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    allSelected: Boolean,
    canRename: Boolean,
    onClose: () -> Unit,
    onToggleAll: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    TopAppBar(
        modifier = Modifier.testTag("gallery_selection_bar"),
        navigationIcon = {
            IconButton(onClick = onClose, modifier = Modifier.testTag("selection_close")) {
                Icon(Icons.Filled.Close, stringResource(R.string.gallery_clear_selection))
            }
        },
        title = { Text(stringResource(R.string.gallery_selected, selectedCount)) },
        actions = {
            IconButton(onClick = onToggleAll, modifier = Modifier.testTag("selection_all")) {
                Icon(
                    Icons.Filled.SelectAll,
                    stringResource(
                        if (allSelected) R.string.gallery_select_none else R.string.gallery_select_all,
                    ),
                )
            }
            if (canRename) {
                IconButton(onClick = onRename, modifier = Modifier.testTag("selection_rename")) {
                    Icon(Icons.Filled.DriveFileRenameOutline, stringResource(R.string.gallery_rename))
                }
            }
            IconButton(onClick = onShare, modifier = Modifier.testTag("selection_share")) {
                Icon(Icons.Filled.Share, stringResource(R.string.gallery_share))
            }
            IconButton(onClick = onDelete, modifier = Modifier.testTag("selection_delete")) {
                Icon(Icons.Filled.Delete, stringResource(R.string.gallery_delete))
            }
        },
    )
}

private fun GallerySort.labelRes(): Int = when (this) {
    GallerySort.NEWEST -> R.string.gallery_sort_newest
    GallerySort.OLDEST -> R.string.gallery_sort_oldest
    GallerySort.NAME -> R.string.gallery_sort_name
    GallerySort.LARGEST -> R.string.gallery_sort_largest
}

/**
 * Shares via content URIs with a read grant, so the receiving app gets access to
 * exactly these files and nothing else.
 */
private fun shareVideos(context: Context, videos: List<GalleryVideo>) {
    if (videos.isEmpty()) return
    val uris = videos.toUris()

    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = videos.first().mimeType ?: "video/*"
            putExtra(Intent.EXTRA_STREAM, uris.first())
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "video/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
    }.apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    val chooser = Intent.createChooser(intent, context.getString(R.string.gallery_share))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    runCatching { ContextCompat.startActivity(context, chooser, null) }
        .onFailure { Timber.w(it, "Could not open the share sheet") }
}
