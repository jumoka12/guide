package com.ampgames.vidsaver.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ampgames.vidsaver.ui.navigation.FullScreenRoutes
import com.ampgames.vidsaver.ui.navigation.VidSaverBottomBar
import com.ampgames.vidsaver.ui.navigation.VidSaverDestination
import com.ampgames.vidsaver.ui.navigation.VidSaverNavHost

@UnstableApi
@Composable
fun VidSaverApp(
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val current = VidSaverDestination.fromRoute(route)
    // The player takes the whole screen; a nav bar over video is just chrome in
    // the way.
    val showBottomBar = !FullScreenRoutes.isFullScreen(route)

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                VidSaverBottomBar(
                    current = current,
                    onSelect = { destination -> navController.navigateToTab(destination) },
                )
            }
        },
    ) { innerPadding ->
        VidSaverNavHost(
            navController = navController,
            modifier = if (showBottomBar) Modifier.padding(innerPadding) else Modifier,
        )
    }
}

/**
 * Tab switching keeps a single instance per tab and restores its state, so
 * leaving the Browser tab and coming back does not reload the page.
 */
private fun NavHostController.navigateToTab(destination: VidSaverDestination) {
    if (currentDestination?.route == destination.route) return
    navigate(destination.route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
