package com.ampgames.vidsaver.ui.browser.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ampgames.vidsaver.R
import com.ampgames.vidsaver.ui.browser.BrowserUiState

const val ADDRESS_BAR_TEST_TAG = "address_bar"
const val BROWSER_MENU_TEST_TAG = "browser_menu"

/**
 * One compact row: navigation, a search pill, tabs and an overflow menu.
 *
 * Everything that used to live in a second bottom bar (bookmarks, ad blocking,
 * desktop mode) moved into the overflow. Two stacked bars plus the app's own
 * navigation bar spent over 100dp of a phone screen on chrome, which is a lot to
 * charge for controls most people touch once a session.
 */
@Composable
fun BrowserTopBar(
    state: BrowserUiState,
    onAddressChange: (String) -> Unit,
    onAddressSubmit: () -> Unit,
    onAddressFocusChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onHome: () -> Unit,
    onToggleBookmark: () -> Unit,
    onTabs: () -> Unit,
    onBookmarks: () -> Unit,
    onToggleAdBlock: () -> Unit,
    onToggleSiteAllowlist: () -> Unit,
    onToggleDesktopMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Back and forward only matter once there is history to walk.
                if (!state.showHome) {
                    IconButton(onClick = onBack, enabled = state.canGoBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            stringResource(R.string.browser_back),
                        )
                    }
                    IconButton(onClick = onForward, enabled = state.canGoForward) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowForward,
                            stringResource(R.string.browser_forward),
                        )
                    }
                }

                AddressField(
                    state = state,
                    onAddressChange = onAddressChange,
                    onAddressSubmit = {
                        keyboard?.hide()
                        onAddressSubmit()
                    },
                    onAddressFocusChange = onAddressFocusChange,
                    onReload = onReload,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                )

                TabsButton(count = state.tabs.size, onClick = onTabs)

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.testTag(BROWSER_MENU_TEST_TAG),
                    ) {
                        Icon(Icons.Filled.MoreVert, stringResource(R.string.browser_menu))
                    }

                    BrowserMenu(
                        expanded = menuExpanded,
                        state = state,
                        onDismiss = { menuExpanded = false },
                        onHome = onHome,
                        onToggleBookmark = onToggleBookmark,
                        onBookmarks = onBookmarks,
                        onToggleAdBlock = onToggleAdBlock,
                        onToggleSiteAllowlist = onToggleSiteAllowlist,
                        onToggleDesktopMode = onToggleDesktopMode,
                    )
                }
            }

            // A hairline progress bar, not a chunky one — page loads are frequent
            // and this should read as ambient, not as an event.
            if (state.isLoading) {
                LinearProgressIndicator(
                    progress = { state.loadProgress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent,
                )
            }
        }
    }
}

@Composable
private fun AddressField(
    state: BrowserUiState,
    onAddressChange: (String) -> Unit,
    onAddressSubmit: () -> Unit,
    onAddressFocusChange: (Boolean) -> Unit,
    onReload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = state.addressText,
        onValueChange = onAddressChange,
        modifier = modifier
            .height(46.dp)
            .onFocusChanged { onAddressFocusChange(it.isFocused) }
            .testTag(ADDRESS_BAR_TEST_TAG),
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        textStyle = MaterialTheme.typography.bodyMedium,
        placeholder = {
            Text(
                text = stringResource(R.string.browser_address_hint),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
        },
        // A filled pill with no underline: the field is the shape, so the
        // indicator lines are just noise.
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Go,
        ),
        keyboardActions = KeyboardActions(onGo = { onAddressSubmit() }),
        trailingIcon = if (state.showHome) {
            null
        } else {
            {
                IconButton(onClick = onReload, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (state.isLoading) Icons.Filled.Close else Icons.Filled.Refresh,
                        contentDescription = stringResource(
                            if (state.isLoading) R.string.browser_stop else R.string.browser_reload,
                        ),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
    )
}

/** Tab count in a rounded square, the way every mobile browser draws it. */
@Composable
private fun TabsButton(count: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.testTag("open_tabs")) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Outlined.Tab,
                contentDescription = stringResource(R.string.tabs_open),
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = if (count > 9) "9+" else "$count",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun BrowserMenu(
    expanded: Boolean,
    state: BrowserUiState,
    onDismiss: () -> Unit,
    onHome: () -> Unit,
    onToggleBookmark: () -> Unit,
    onBookmarks: () -> Unit,
    onToggleAdBlock: () -> Unit,
    onToggleSiteAllowlist: () -> Unit,
    onToggleDesktopMode: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (!state.showHome) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.browser_home)) },
                leadingIcon = { Icon(Icons.Outlined.Home, null) },
                onClick = { onDismiss(); onHome() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.browser_bookmark)) },
                leadingIcon = {
                    Icon(
                        if (state.isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        null,
                    )
                },
                onClick = { onDismiss(); onToggleBookmark() },
                modifier = Modifier.testTag("menu_bookmark"),
            )
        }

        DropdownMenuItem(
            text = { Text(stringResource(R.string.bookmarks_open)) },
            leadingIcon = { Icon(Icons.Outlined.Bookmarks, null) },
            onClick = { onDismiss(); onBookmarks() },
            modifier = Modifier.testTag("open_bookmarks"),
        )

        // On a page this toggles the per-site override; on the home screen there
        // is no site, so it toggles the global setting.
        val blockingHere = state.adBlockEnabled && !(!state.showHome && state.siteAllowlisted)
        DropdownMenuItem(
            text = { Text(stringResource(if (blockingHere) R.string.adblock_on else R.string.adblock_off)) },
            leadingIcon = { Icon(Icons.Outlined.Shield, null) },
            trailingIcon = { Switch(checked = blockingHere, onCheckedChange = null) },
            onClick = {
                onDismiss()
                if (state.showHome) onToggleAdBlock() else onToggleSiteAllowlist()
            },
            modifier = Modifier.testTag("toggle_adblock"),
        )

        DropdownMenuItem(
            text = { Text(stringResource(R.string.browser_desktop_mode)) },
            leadingIcon = { Icon(Icons.Outlined.DesktopWindows, null) },
            trailingIcon = { Switch(checked = state.desktopMode, onCheckedChange = null) },
            onClick = { onDismiss(); onToggleDesktopMode() },
            modifier = Modifier.testTag("toggle_desktop"),
        )
    }
}
