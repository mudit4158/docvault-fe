package com.docvault.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.docvault.app.ui.components.DocVaultBottomBar
import com.docvault.app.ui.screens.groups.GroupsScreen
import com.docvault.app.ui.screens.me.MeScreen
import com.docvault.app.ui.screens.scan.ScanScreen
import com.docvault.app.ui.screens.vault.VaultScreen

@Composable
fun DocVaultNavHost() {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value
        ?.destination
        ?.route
    val currentDestination = DocVaultDestination.entries
        .firstOrNull { it.route == currentRoute }
        ?: DocVaultDestination.Vault

    Scaffold(
        bottomBar = {
            DocVaultBottomBar(
                currentDestination = currentDestination,
                onDestinationSelected = { destination ->
                    navController.navigate(destination.route) {
                        // Single copy of each tab on the back stack, state preserved across
                        // tab switches (standard bottom-nav pattern).
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = DocVaultDestination.Vault.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(DocVaultDestination.Vault.route) { VaultScreen() }
            composable(DocVaultDestination.Scan.route) { ScanScreen() }
            composable(DocVaultDestination.Groups.route) { GroupsScreen() }
            composable(DocVaultDestination.Me.route) { MeScreen() }
        }
    }
}
