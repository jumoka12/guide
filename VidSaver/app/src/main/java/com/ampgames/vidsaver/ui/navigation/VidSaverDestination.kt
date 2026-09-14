package com.ampgames.vidsaver.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import com.ampgames.vidsaver.R

/** The four top-level tabs. Order here is the order in the bottom bar. */
enum class VidSaverDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    BROWSER(
        route = "browser",
        labelRes = R.string.tab_browser,
        selectedIcon = Icons.Filled.Public,
        unselectedIcon = Icons.Outlined.Public,
    ),
    DOWNLOADS(
        route = "downloads",
        labelRes = R.string.tab_downloads,
        selectedIcon = Icons.Filled.Download,
        unselectedIcon = Icons.Outlined.Download,
    ),
    GALLERY(
        route = "gallery",
        labelRes = R.string.tab_gallery,
        selectedIcon = Icons.Filled.VideoLibrary,
        unselectedIcon = Icons.Outlined.VideoLibrary,
    ),
    SETTINGS(
        route = "settings",
        labelRes = R.string.tab_settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
    );

    companion object {
        val START: VidSaverDestination = BROWSER

        fun fromRoute(route: String?): VidSaverDestination? =
            entries.firstOrNull { it.route == route }
    }
}
