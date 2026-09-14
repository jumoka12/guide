package com.ampgames.vidsaver.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ampgames.vidsaver.ui.navigation.VidSaverBottomBar
import com.ampgames.vidsaver.ui.navigation.VidSaverDestination
import com.ampgames.vidsaver.ui.navigation.VidSaverNavHost

@Composable
fun VidSaverApp(
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = VidSaverDestination.fromRoute(backStackEntry?.destination?.route)

    Scaffold(
        bottomBar = {
            VidSaverBottomBar(
                current = current,
                onSelect = { destination -> navController.navigateToTab(destination) },
            )
        },
    ) { innerPadding ->
        VidSaverNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
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
