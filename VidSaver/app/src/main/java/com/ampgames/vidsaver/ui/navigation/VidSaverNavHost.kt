package com.ampgames.vidsaver.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ampgames.vidsaver.ui.browser.BrowserScreen
import com.ampgames.vidsaver.ui.downloads.DownloadsScreen
import com.ampgames.vidsaver.ui.gallery.GalleryScreen
import com.ampgames.vidsaver.ui.player.PlayerScreen
import com.ampgames.vidsaver.ui.player.PlayerViewModel
import com.ampgames.vidsaver.ui.settings.SettingsScreen

/** Full-screen routes that sit outside the bottom navigation. */
object FullScreenRoutes {
    const val PLAYER = "player/{${PlayerViewModel.ARG_VIDEO_ID}}"

    fun player(videoId: Long): String = "player/$videoId"

    /** True when [route] should be shown without the bottom bar. */
    fun isFullScreen(route: String?): Boolean = route == PLAYER
}

@UnstableApi
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

        composable(VidSaverDestination.GALLERY.route) {
            GalleryScreen(
                onOpenVideo = { video -> navController.navigate(FullScreenRoutes.player(video.id)) },
            )
        }

        composable(VidSaverDestination.SETTINGS.route) { SettingsScreen() }

        composable(
            route = FullScreenRoutes.PLAYER,
            arguments = listOf(
                navArgument(PlayerViewModel.ARG_VIDEO_ID) { type = NavType.StringType },
            ),
        ) {
            PlayerScreen(onBack = { navController.popBackStack() })
        }
    }
}
