package com.ampgames.vidsaver.ui.gallery

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ampgames.vidsaver.R
import com.ampgames.vidsaver.ui.components.PlaceholderScreen

const val GALLERY_SCREEN_TEST_TAG = "gallery_screen"

@Composable
fun GalleryScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.tab_gallery),
        description = stringResource(R.string.placeholder_gallery),
        icon = Icons.Outlined.VideoLibrary,
        testTag = GALLERY_SCREEN_TEST_TAG,
        modifier = modifier,
    )
}
