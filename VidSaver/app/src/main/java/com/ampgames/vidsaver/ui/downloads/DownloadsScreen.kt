package com.ampgames.vidsaver.ui.downloads

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ampgames.vidsaver.R
import com.ampgames.vidsaver.ui.components.PlaceholderScreen

const val DOWNLOADS_SCREEN_TEST_TAG = "downloads_screen"

@Composable
fun DownloadsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.tab_downloads),
        description = stringResource(R.string.placeholder_downloads),
        icon = Icons.Outlined.Download,
        testTag = DOWNLOADS_SCREEN_TEST_TAG,
        modifier = modifier,
    )
}
