package com.ampgames.vidsaver.ui.browser.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ampgames.vidsaver.R
import com.ampgames.vidsaver.ui.browser.BrowserUiState

const val ADDRESS_BAR_TEST_TAG = "address_bar"

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
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, enabled = state.canGoBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.browser_back))
            }
            IconButton(onClick = onForward, enabled = state.canGoForward) {
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, stringResource(R.string.browser_forward))
            }

            OutlinedTextField(
                value = state.addressText,
                onValueChange = onAddressChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .onFocusChanged { onAddressFocusChange(it.isFocused) }
                    .testTag(ADDRESS_BAR_TEST_TAG),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.browser_address_hint)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(
                    onGo = {
                        keyboard?.hide()
                        onAddressSubmit()
                    },
                ),
                trailingIcon = {
                    IconButton(onClick = onReload) {
                        Icon(
                            imageVector = if (state.isLoading) Icons.Filled.Close else Icons.Filled.Refresh,
                            contentDescription = stringResource(
                                if (state.isLoading) R.string.browser_stop else R.string.browser_reload,
                            ),
                        )
                    }
                },
            )

            if (state.showHome) {
                IconButton(onClick = onHome) {
                    Icon(Icons.Outlined.Home, stringResource(R.string.browser_home))
                }
            } else {
                IconButton(onClick = onToggleBookmark) {
                    Icon(
                        imageVector = if (state.isBookmarked) {
                            Icons.Filled.Bookmark
                        } else {
                            Icons.Outlined.BookmarkBorder
                        },
                        contentDescription = stringResource(R.string.browser_bookmark),
                    )
                }
            }
        }

        if (state.isLoading) {
            LinearProgressIndicator(
                progress = { state.loadProgress / 100f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
