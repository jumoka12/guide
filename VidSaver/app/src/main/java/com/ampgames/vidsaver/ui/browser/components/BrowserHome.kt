package com.ampgames.vidsaver.ui.browser.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ampgames.vidsaver.R
import com.ampgames.vidsaver.domain.browser.SearchEngine
import com.ampgames.vidsaver.ui.theme.tintForDomain

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
            .padding(horizontal = 20.dp)
            .testTag(BROWSER_HOME_TEST_TAG),
    ) {
        Text(
            text = stringResource(R.string.browser_home_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            text = stringResource(R.string.browser_home_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 88.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 28.dp),
        ) {
            items(shortcuts, key = { it }) { domain ->
                ShortcutTile(domain = domain, onClick = { onShortcutClick(domain) })
            }
        }

        Text(
            text = stringResource(R.string.browser_search_engine),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
        )
        // FlowRow, not Row: five chips do not fit across a phone, and longer
        // translations make it worse. Wrapping keeps every engine reachable.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
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

/**
 * A tinted monogram and the site name.
 *
 * No card and no domain caption: the card added a box around something that is
 * already a distinct shape, and "Tiktok" over "tiktok.com" said the same thing
 * twice. The monogram carries the recognition instead.
 */
@Composable
private fun ShortcutTile(
    domain: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = domain.substringBefore('.').replaceFirstChar { it.uppercase() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
            .testTag("shortcut_$domain"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            color = tintForDomain(domain),
            shape = CircleShape,
            modifier = Modifier.size(52.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
        }

        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
