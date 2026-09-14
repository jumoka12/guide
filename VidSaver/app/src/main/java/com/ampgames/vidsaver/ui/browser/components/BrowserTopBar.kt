package com.ampgames.vidsaver.ui.browser.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ampgames.vidsaver.R
import com.ampgames.vidsaver.core.net.Urls
import com.ampgames.vidsaver.ui.browser.BrowserUiState

const val ADDRESS_BAR_TEST_TAG = "address_bar"
const val BROWSER_MENU_TEST_TAG = "browser_menu"

/**
 * The bar shown over a page: tab counter, host pill, found-videos button and
 * the overflow menu. The home page draws its own top row (see [BrowserHome]),
 * so this returns nothing there.
 *
 * Back and forward are not on the bar: the system back button walks page
 * history, and forward lives in the menu where the few who want it will look.
 */
@Composable
fun BrowserTopBar(
    state: BrowserUiState,
    onAddressChange: (String) -> Unit,
    onAddressSubmit: () -> Unit,
    onAddressFocusChange: (Boolean) -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onHome: () -> Unit,
    onToggleBookmark: () -> Unit,
    onTabs: () -> Unit,
    onBookmarks: () -> Unit,
    onToggleAdBlock: () -> Unit,
    onToggleSiteAllowlist: () -> Unit,
    onToggleDesktopMode: () -> Unit,
    onShowFound: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.showHome) return

    val keyboard = LocalSoftwareKeyboardController.current

    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TabCounter(count = state.tabs.size, onClick = onTabs)

                AddressField(
                    state = state,
                    onAddressChange = onAddressChange,
                    onAddressSubmit = {
                        keyboard?.hide()
                        onAddressSubmit()
                    },
                    onAddressFocusChange = onAddressFocusChange,
                    height = 44.dp,
                    // A page shows its host; the whole URL appears on tap.
                    unfocusedText = { Urls.host(it)?.removePrefix("www.") ?: it },
                    leadingIcon = {
                        IconButton(onClick = onReload, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = if (state.isLoading) Icons.Filled.Close else Icons.Filled.Refresh,
                                contentDescription = stringResource(
                                    if (state.isLoading) R.string.browser_stop else R.string.browser_reload,
                                ),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                    trailingIcon = null,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                )

                IconButton(
                    onClick = onShowFound,
                    modifier = Modifier.testTag("show_found"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FileDownload,
                        contentDescription = stringResource(R.string.browser_show_found),
                        tint = if (state.candidateCount > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }

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
                )
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

/**
 * The address pill, shared by the page bar and the home page.
 *
 * A plain string-valued TextField puts the cursor at the end whenever the URL
 * changes, so a long address scrolls to its tail. Driving the field with a
 * [TextFieldValue] shows [unfocusedText] anchored to the start while idle and
 * the full URL, selected, on focus — one tap replaces it, as in every browser.
 */
@Composable
internal fun AddressField(
    state: BrowserUiState,
    onAddressChange: (String) -> Unit,
    onAddressSubmit: () -> Unit,
    onAddressFocusChange: (Boolean) -> Unit,
    height: Dp,
    unfocusedText: (String) -> String,
    leadingIcon: (@Composable () -> Unit)?,
    trailingIcon: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    var fieldValue by remember { mutableStateOf(TextFieldValue(unfocusedText(state.addressText))) }

    LaunchedEffect(state.addressText, focused) {
        fieldValue = when {
            !focused -> TextFieldValue(unfocusedText(state.addressText), TextRange.Zero)
            // The model changed under an active edit (a tab switch, say).
            fieldValue.text != state.addressText ->
                TextFieldValue(state.addressText, TextRange(0, state.addressText.length))
            else -> fieldValue
        }
    }

    val textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)

    // A BasicTextField in a pill of our own rather than Material's TextField:
    // that one needs 56dp of height for its label slot, and squeezing it to a
    // browser-bar height clips the text.
    BasicTextField(
        value = fieldValue,
        onValueChange = { value ->
            fieldValue = value
            if (value.text != state.addressText) onAddressChange(value.text)
        },
        modifier = modifier
            .height(height)
            .onFocusChanged {
                val gained = it.isFocused && !focused
                focused = it.isFocused
                if (gained) {
                    val full = state.addressText
                    fieldValue = TextFieldValue(full, TextRange(0, full.length))
                }
                onAddressFocusChange(it.isFocused)
            }
            .testTag(ADDRESS_BAR_TEST_TAG),
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Go,
        ),
        keyboardActions = KeyboardActions(onGo = { onAddressSubmit() }),
        decorationBox = { innerField ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(start = if (leadingIcon == null) 16.dp else 8.dp, end = if (trailingIcon == null) 16.dp else 4.dp),
            ) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (fieldValue.text.isEmpty()) {
                        Text(
                            text = stringResource(R.string.browser_address_hint),
                            style = textStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerField()
                }
                if (trailingIcon != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    trailingIcon()
                }
            }
        },
    )
}

/** The home page's pill: a link glyph, the hint, and a red search glyph. */
@Composable
internal fun HomeAddressField(
    state: BrowserUiState,
    onAddressChange: (String) -> Unit,
    onAddressSubmit: () -> Unit,
    onAddressFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = {
        keyboard?.hide()
        onAddressSubmit()
    }
    AddressField(
        state = state,
        onAddressChange = onAddressChange,
        onAddressSubmit = submit,
        onAddressFocusChange = onAddressFocusChange,
        height = 56.dp,
        unfocusedText = { it },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Link,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            IconButton(onClick = submit) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = stringResource(R.string.browser_search),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        modifier = modifier,
    )
}

/** The tab count in an outlined rounded square, the way mobile browsers draw it. */
@Composable
private fun TabCounter(count: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.testTag("open_tabs")) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .border(2.dp, MaterialTheme.colorScheme.onSurface, MaterialTheme.shapes.extraSmall),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (count > 9) "9+" else "$count",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** The overflow button and its menu, shared by the page bar and the home page. */
@Composable
internal fun MenuButton(
    state: BrowserUiState,
    onHome: () -> Unit,
    onForward: () -> Unit,
    onToggleBookmark: () -> Unit,
    onTabs: () -> Unit,
    onBookmarks: () -> Unit,
    onToggleAdBlock: () -> Unit,
    onToggleSiteAllowlist: () -> Unit,
    onToggleDesktopMode: () -> Unit,
    icon: @Composable () -> Unit = {
        Icon(Icons.Filled.MoreVert, stringResource(R.string.browser_menu))
    },
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.testTag(BROWSER_MENU_TEST_TAG),
            content = icon,
        )

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val dismiss = { expanded = false }

            if (!state.showHome) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.browser_home)) },
                    leadingIcon = { Icon(Icons.Outlined.Home, null) },
                    onClick = { dismiss(); onHome() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.browser_forward)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ArrowForward, null) },
                    enabled = state.canGoForward,
                    onClick = { dismiss(); onForward() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.browser_bookmark)) },
                    leadingIcon = {
                        Icon(
                            if (state.isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            null,
                        )
                    },
                    onClick = { dismiss(); onToggleBookmark() },
                    modifier = Modifier.testTag("menu_bookmark"),
                )
            } else {
                // The home page has no tab counter in its top row, so tabs are
                // reachable from here.
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tabs_title, state.tabs.size)) },
                    leadingIcon = { Icon(Icons.Outlined.Tab, null) },
                    onClick = { dismiss(); onTabs() },
                    modifier = Modifier.testTag("open_tabs"),
                )
            }

            DropdownMenuItem(
                text = { Text(stringResource(R.string.bookmarks_open)) },
                leadingIcon = { Icon(Icons.Outlined.Bookmarks, null) },
                onClick = { dismiss(); onBookmarks() },
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
                    dismiss()
                    if (state.showHome) onToggleAdBlock() else onToggleSiteAllowlist()
                },
                modifier = Modifier.testTag("toggle_adblock"),
            )

            DropdownMenuItem(
                text = { Text(stringResource(R.string.browser_desktop_mode)) },
                leadingIcon = { Icon(Icons.Outlined.DesktopWindows, null) },
                trailingIcon = { Switch(checked = state.desktopMode, onCheckedChange = null) },
                onClick = { dismiss(); onToggleDesktopMode() },
                modifier = Modifier.testTag("toggle_desktop"),
            )
        }
    }
}
