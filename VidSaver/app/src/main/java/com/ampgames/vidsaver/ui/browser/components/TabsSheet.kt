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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ampgames.vidsaver.R
import com.ampgames.vidsaver.ui.browser.BrowserTab

const val TABS_SHEET_TEST_TAG = "tabs_sheet"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsSheet(
    tabs: List<BrowserTab>,
    currentTabId: String,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onNewTab: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        modifier = Modifier.testTag(TABS_SHEET_TEST_TAG),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.tabs_title, tabs.size),
                    style = MaterialTheme.typography.titleLarge,
                )
                TextButton(onClick = onNewTab, modifier = Modifier.testTag("new_tab")) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(stringResource(R.string.tabs_new))
                }
            }

            LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
                items(tabs, key = { it.id }) { tab ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(tab.id) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tab.displayTitle,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                color = if (tab.id == currentTabId) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                            if (tab.url.isNotBlank()) {
                                Text(
                                    text = tab.url,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                        IconButton(onClick = { onClose(tab.id) }) {
                            Icon(Icons.Filled.Close, stringResource(R.string.tabs_close))
                        }
                    }
                }
            }
        }
    }
}
