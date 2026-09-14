package com.ampgames.vidsaver.ui.browser.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ampgames.vidsaver.R
import com.ampgames.vidsaver.domain.browser.SearchEngine
import com.ampgames.vidsaver.ui.browser.BrowserUiState
import com.ampgames.vidsaver.ui.theme.BrandGold
import com.ampgames.vidsaver.ui.theme.tintForDomain

const val BROWSER_HOME_TEST_TAG = "browser_home"

/**
 * The start page: the brand mark, one pill to paste a link or search, and a
 * row of site tiles. The top row carries the menu and the premium crown; the
 * page bar with its tab counter only appears once a page is open.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BrowserHome(
    state: BrowserUiState,
    onAddressChange: (String) -> Unit,
    onAddressSubmit: () -> Unit,
    onAddressFocusChange: (Boolean) -> Unit,
    onShortcutClick: (String) -> Unit,
    onSearchEngineSelected: (SearchEngine) -> Unit,
    onPremium: () -> Unit,
    onHome: () -> Unit,
    onForward: () -> Unit,
    onToggleBookmark: () -> Unit,
    onTabs: () -> Unit,
    onBookmarks: () -> Unit,
    onToggleAdBlock: () -> Unit,
    onToggleSiteAllowlist: () -> Unit,
    onToggleDesktopMode: () -> Unit,
    onShareLog: () -> Unit,
    onTestVideo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            // Opaque: the WebView sits underneath, and a page left running
            // there must not show through the start page.
            .background(MaterialTheme.colorScheme.background)
            .testTag(BROWSER_HOME_TEST_TAG),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MenuButton(
                state = state,
                onHome = onHome,
                onForward = onForward,
                onToggleBookmark = onToggleBookmark,
                onTabs = onTabs,
                onBookmarks = onBookmarks,
                onToggleAdBlock = onToggleAdBlock,
                onToggleSiteAllowlist = onToggleSiteAllowlist,
                onToggleDesktopMode = onToggleDesktopMode,
                onShareLog = onShareLog,
                onTestVideo = onTestVideo,
                icon = { Icon(Icons.Filled.Menu, stringResource(R.string.browser_menu)) },
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onPremium, modifier = Modifier.testTag("premium")) {
                Icon(
                    imageVector = Icons.Filled.WorkspacePremium,
                    contentDescription = stringResource(R.string.browser_premium),
                    tint = BrandGold,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Image(
                painter = painterResource(R.drawable.ic_brand_logo),
                contentDescription = null,
                modifier = Modifier.size(150.dp),
            )
            Spacer(modifier = Modifier.height(36.dp))

            HomeAddressField(
                state = state,
                onAddressChange = onAddressChange,
                onAddressSubmit = onAddressSubmit,
                onAddressFocusChange = onAddressFocusChange,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(36.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                state.shortcuts.forEach { domain ->
                    ShortcutTile(domain = domain, onClick = { onShortcutClick(domain) })
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
            Text(
                text = stringResource(R.string.browser_search_engine),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // FlowRow, not Row: five chips do not fit across a phone, and longer
            // translations make it worse. Wrapping keeps every engine reachable.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 24.dp),
            ) {
                SearchEngine.entries.forEach { engine ->
                    FilterChip(
                        selected = engine == state.searchEngine,
                        onClick = { onSearchEngineSelected(engine) },
                        label = { Text(engine.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier.testTag("engine_${engine.id}"),
                    )
                }
            }
        }
    }
}

/**
 * A rounded tile with the site's initial. No caption: the tile is the
 * recognition, and a label under each of seven tiles is a list, not a launcher.
 */
@Composable
private fun ShortcutTile(
    domain: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val initial = domain.substringBefore('.').take(1).uppercase()

    Surface(
        color = tintForDomain(domain),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .size(60.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("shortcut_$domain"),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = initial,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}
