package com.ampgames.vidsaver.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.ampgames.vidsaver.ui.browser.BrowserScreen
import com.ampgames.vidsaver.ui.downloads.DownloadsScreen
import com.ampgames.vidsaver.ui.gallery.GalleryScreen
import com.ampgames.vidsaver.ui.settings.SettingsScreen

@Composable
fun VidSaverNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = VidSaverDestination.START.route,
        modifier = modifier,
    ) {
        composable(VidSaverDestination.BROWSER.route) { BrowserScreen() }
        composable(VidSaverDestination.DOWNLOADS.route) { DownloadsScreen() }
        composable(VidSaverDestination.GALLERY.route) { GalleryScreen() }
        composable(VidSaverDestination.SETTINGS.route) { SettingsScreen() }
    }
}
