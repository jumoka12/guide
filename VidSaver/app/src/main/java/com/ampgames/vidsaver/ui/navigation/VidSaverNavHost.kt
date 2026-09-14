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
import com.ampgames.vidsaver.ui.paywall.PaywallScreen
import com.ampgames.vidsaver.ui.paywall.PaywallViewModel
import com.ampgames.vidsaver.domain.premium.PaywallSource
import androidx.compose.ui.platform.LocalContext
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import timber.log.Timber

/** Full-screen routes that sit outside the bottom navigation. */
object FullScreenRoutes {
    const val PLAYER = "player/{${PlayerViewModel.ARG_VIDEO_ID}}"
    const val PAYWALL = "paywall/{${PaywallViewModel.ARG_SOURCE}}"

    fun player(videoId: Long): String = "player/$videoId"

    fun paywall(source: PaywallSource): String = "paywall/${source.name}"

    /** True when [route] should be shown without the bottom bar. */
    fun isFullScreen(route: String?): Boolean = route == PLAYER || route == PAYWALL
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

        composable(VidSaverDestination.SETTINGS.route) {
            SettingsScreen(
                onOpenPaywall = { navController.navigate(FullScreenRoutes.paywall(PaywallSource.SETTINGS)) },
            )
        }

        composable(
            route = FullScreenRoutes.PAYWALL,
            arguments = listOf(navArgument(PaywallViewModel.ARG_SOURCE) { type = NavType.StringType }),
        ) {
            val context = LocalContext.current
            PaywallScreen(
                onClose = { navController.popBackStack() },
                onOpenUrl = { url -> openExternal(context, url) },
            )
        }

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

/** Opens [url] in whatever handles it; a missing handler is logged, not fatal. */
private fun openExternal(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Timber.w(e, "No handler for %s", url)
    }
}
