package com.ampgames.vidsaver.ui.browser.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ampgames.vidsaver.R
import com.ampgames.vidsaver.domain.browser.SearchEngine

const val BROWSER_HOME_TEST_TAG = "browser_home"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BrowserHome(
    shortcuts: List<String>,
    searchEngine: SearchEngine,
    onShortcutClick: (String) -> Unit,
    onSearchEngineSelected: (SearchEngine) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag(BROWSER_HOME_TEST_TAG),
    ) {
        Text(
            text = stringResource(R.string.browser_home_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(R.string.browser_home_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 16.dp),
        ) {
            items(shortcuts, key = { it }) { domain ->
                ShortcutTile(domain = domain, onClick = { onShortcutClick(domain) })
            }
        }

        Text(
            text = stringResource(R.string.browser_search_engine),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
        // FlowRow, not Row: five chips do not fit across a phone, and longer
        // translations make it worse. Wrapping keeps every engine reachable.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            SearchEngine.entries.forEach { engine ->
                FilterChip(
                    selected = engine == searchEngine,
                    onClick = { onSearchEngineSelected(engine) },
                    label = { Text(engine.displayName) },
                    modifier = Modifier.testTag("engine_${engine.id}"),
                )
            }
        }
    }
}

@Composable
private fun ShortcutTile(
    domain: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag("shortcut_$domain"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = domain.substringBefore('.').replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = domain,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
