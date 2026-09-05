package com.kira.companion.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kira.companion.R
import com.kira.companion.ui.chat.ChatScreen
import com.kira.companion.ui.home.HomeScreen
import com.kira.companion.ui.settings.SettingsScreen

private data class BottomTab(val destination: KiraDestination, val labelRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val bottomTabs = listOf(
    BottomTab(KiraDestination.HOME, R.string.nav_home, Icons.Filled.Home),
    BottomTab(KiraDestination.CHAT, R.string.nav_chat, Icons.Filled.Chat),
    BottomTab(KiraDestination.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
)

@Composable
fun KiraApp(pendingRoute: String?, onPendingRouteConsumed: () -> Unit) {
    val navController = rememberNavController()

    androidx.compose.runtime.LaunchedEffect(pendingRoute) {
        pendingRoute?.let { route ->
            navController.navigate(route) { launchSingleTop = true }
            onPendingRouteConsumed()
        }
    }

    Scaffold(
        bottomBar = { KiraBottomBar(navController) },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = KiraDestination.HOME.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(KiraDestination.HOME.route) {
                HomeScreen(
                    onNavigateChat = { navController.navigate(KiraDestination.CHAT.route) },
                    onNavigateSettings = { navController.navigate(KiraDestination.SETTINGS.route) },
                )
            }
            composable(KiraDestination.CHAT.route) { ChatScreen() }
            composable(KiraDestination.SETTINGS.route) { SettingsScreen() }
        }
    }
}

@Composable
private fun KiraBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    NavigationBar {
        bottomTabs.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.destination.route,
                onClick = {
                    navController.navigate(tab.destination.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(stringResource(tab.labelRes)) },
            )
        }
    }
}
