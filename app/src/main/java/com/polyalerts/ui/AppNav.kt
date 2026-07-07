package com.polyalerts.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.polyalerts.ui.theme.Bg
import com.polyalerts.ui.theme.BrandBlue
import com.polyalerts.ui.theme.TextMuted
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val vm: AppViewModel = viewModel()

    val tabs = listOf("browse" to "Markets", "watchlist" to "Alerts")
    val icons = listOf(Icons.Default.List, Icons.Default.Notifications)

    Scaffold(
        bottomBar = {
            val current by nav.currentBackStackEntryAsState()
            val route = current?.destination?.route
            NavigationBar(containerColor = Bg) {
                tabs.forEachIndexed { i, (dest, label) ->
                    NavigationBarItem(
                        selected = route == dest,
                        onClick = {
                            nav.navigate(dest) {
                                // Preserve each tab's state (scroll, list, search) across switches.
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(icons[i], contentDescription = label) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = BrandBlue,
                            selectedTextColor = BrandBlue,
                            indicatorColor = MaterialTheme.colorScheme.surface,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted,
                        ),
                    )
                }
            }
        }
    ) { padding ->
        NavHost(nav, startDestination = "browse", modifier = Modifier.padding(padding)) {
            composable("browse") { BrowseScreen(vm) }
            composable("watchlist") { WatchlistScreen(vm) }
        }
    }
}
