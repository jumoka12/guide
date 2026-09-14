package com.ampgames.vidsaver.ui.browser.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ampgames.vidsaver.R
import com.ampgames.vidsaver.data.browser.db.BookmarkEntity
import com.ampgames.vidsaver.data.browser.db.HistoryEntity

const val BOOKMARKS_SHEET_TEST_TAG = "bookmarks_sheet"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksSheet(
    bookmarks: List<BookmarkEntity>,
    history: List<HistoryEntity>,
    onOpen: (String) -> Unit,
    onRemoveBookmark: (String) -> Unit,
    onClearHistory: () -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag(BOOKMARKS_SHEET_TEST_TAG),
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.bookmarks_title)) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.history_title)) },
                )
            }

            if (selectedTab == 0) {
                EntryList(
                    entries = bookmarks.map { Entry(it.url, it.title) },
                    emptyText = stringResource(R.string.bookmarks_empty),
                    onOpen = onOpen,
                    onRemove = onRemoveBookmark,
                )
            } else {
                Column {
                    if (history.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = onClearHistory) {
                                Text(stringResource(R.string.history_clear))
                            }
                        }
                    }
                    EntryList(
                        entries = history.map { Entry(it.url, it.title) },
                        emptyText = stringResource(R.string.history_empty),
                        onOpen = onOpen,
                        onRemove = null,
                    )
                }
            }
        }
    }
}

private data class Entry(val url: String, val title: String)

@Composable
private fun EntryList(
    entries: List<Entry>,
    emptyText: String,
    onOpen: (String) -> Unit,
    onRemove: ((String) -> Unit)?,
) {
    if (entries.isEmpty()) {
        Text(
            text = emptyText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp),
        )
        return
    }

    LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
        items(entries, key = { it.url }) { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(entry.url) }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.title.ifBlank { entry.url },
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                    )
                    Text(
                        text = entry.url,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                if (onRemove != null) {
                    IconButton(onClick = { onRemove(entry.url) }) {
                        Icon(Icons.Filled.Close, stringResource(R.string.bookmarks_remove))
                    }
                }
            }
        }
    }
}
