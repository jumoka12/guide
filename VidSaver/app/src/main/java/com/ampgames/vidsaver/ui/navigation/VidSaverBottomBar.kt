package com.ampgames.vidsaver.ui.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource

const val BOTTOM_BAR_TEST_TAG = "bottom_bar"

/**
 * The selected tab is a tinted pill with its label; the others are bare icons.
 * One label on the bar names where you are, four labels is a table of contents.
 */
@Composable
fun VidSaverBottomBar(
    current: VidSaverDestination?,
    onSelect: (VidSaverDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    NavigationBar(
        modifier = modifier.testTag(BOTTOM_BAR_TEST_TAG),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        VidSaverDestination.entries.forEach { destination ->
            val selected = destination == current
            val label = stringResource(destination.labelRes)
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) {
                            destination.selectedIcon
                        } else {
                            destination.unselectedIcon
                        },
                        contentDescription = label,
                    )
                },
                label = { Text(label) },
                alwaysShowLabel = false,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = accent,
                    selectedTextColor = accent,
                    indicatorColor = accent.copy(alpha = 0.16f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier.testTag("tab_${destination.route}"),
            )
        }
    }
}
