package com.ampgames.vidsaver.ui.browser.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ampgames.vidsaver.R

const val BROWSER_TOOLBAR_TEST_TAG = "browser_toolbar"

/**
 * Secondary browser controls. Ad blocking has two levels here: the global
 * toggle, and — when a page is open — a per-site override, which is what the
 * shield button switches.
 */
@Composable
fun BrowserToolbar(
    tabCount: Int,
    adBlockEnabled: Boolean,
    siteAllowlisted: Boolean,
    desktopMode: Boolean,
    hasPageOpen: Boolean,
    onTabs: () -> Unit,
    onBookmarks: () -> Unit,
    onToggleAdBlock: () -> Unit,
    onToggleSiteAllowlist: () -> Unit,
    onToggleDesktopMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(BROWSER_TOOLBAR_TEST_TAG),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onTabs, modifier = Modifier.testTag("open_tabs")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Tab,
                        contentDescription = stringResource(R.string.tabs_open),
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "$tabCount",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
            }

            IconButton(onClick = onBookmarks, modifier = Modifier.testTag("open_bookmarks")) {
                Icon(Icons.Outlined.Bookmarks, stringResource(R.string.bookmarks_open))
            }

            // Per-site override when a page is open, global toggle otherwise.
            IconButton(
                onClick = if (hasPageOpen) onToggleSiteAllowlist else onToggleAdBlock,
                modifier = Modifier.testTag("toggle_adblock"),
            ) {
                val blockingHere = adBlockEnabled && !(hasPageOpen && siteAllowlisted)
                Icon(
                    imageVector = if (blockingHere) Icons.Filled.Shield else Icons.Outlined.Shield,
                    contentDescription = stringResource(
                        if (blockingHere) R.string.adblock_on else R.string.adblock_off,
                    ),
                    tint = if (blockingHere) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            IconButton(
                onClick = onToggleDesktopMode,
                modifier = Modifier.testTag("toggle_desktop"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.DesktopWindows,
                    contentDescription = stringResource(R.string.browser_desktop_mode),
                    tint = if (desktopMode) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
