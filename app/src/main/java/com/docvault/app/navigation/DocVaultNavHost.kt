package com.docvault.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.docvault.app.data.DocVaultRepository
import com.docvault.app.ui.components.DocVaultBottomBar
import com.docvault.app.ui.screens.auth.AuthScreen
import com.docvault.app.ui.screens.auth.AuthViewModel
import com.docvault.app.ui.screens.groups.GroupDetailScreen
import com.docvault.app.ui.screens.groups.GroupDetailViewModel
import com.docvault.app.ui.screens.groups.GroupsScreen
import com.docvault.app.ui.screens.groups.GroupsViewModel
import com.docvault.app.ui.screens.me.MeScreen
import com.docvault.app.ui.screens.me.MeViewModel
import com.docvault.app.ui.screens.scan.ScanScreen
import com.docvault.app.ui.screens.vault.VaultScreen

private const val ROUTE_AUTH = "auth"
private const val ROUTE_MAIN = "main"
private const val ROUTE_GROUP_DETAIL = "group/{groupId}"

/**
 * Root navigation.
 *
 * Two graphs: an unauthenticated one holding only the sign-in screen, and the
 * tabbed app. The start destination is chosen from whether a token is already
 * stored, so a returning user lands straight in the vault.
 */
@Composable
fun DocVaultNavHost(repository: DocVaultRepository) {
    val rootNavController = rememberNavController()

    // A rejected token means the session is over — 60-minute expiry makes this
    // routine, not exceptional. Bounce to sign-in from wherever the user is,
    // rather than leaving them on a screen whose every request fails.
    val sessionExpired by repository.sessionExpired.collectAsStateWithLifecycle()
    LaunchedEffect(sessionExpired) {
        if (sessionExpired) {
            repository.acknowledgeSessionExpired()
            rootNavController.navigate(ROUTE_AUTH) {
                popUpTo(rootNavController.graph.id) { inclusive = true }
            }
        }
    }

    NavHost(
        navController = rootNavController,
        startDestination = if (repository.isSignedIn) ROUTE_MAIN else ROUTE_AUTH,
    ) {
        composable(ROUTE_AUTH) {
            AuthScreen(
                viewModel = viewModel(factory = factoryFor { AuthViewModel(repository) }),
                onSignedIn = {
                    rootNavController.navigate(ROUTE_MAIN) {
                        // Drop the sign-in screen so Back does not return to it.
                        popUpTo(ROUTE_AUTH) { inclusive = true }
                    }
                },
            )
        }

        composable(ROUTE_MAIN) {
            MainTabs(
                repository = repository,
                onOpenGroup = { groupId -> rootNavController.navigate("group/$groupId") },
                onSignedOut = {
                    rootNavController.navigate(ROUTE_AUTH) {
                        popUpTo(ROUTE_MAIN) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = ROUTE_GROUP_DETAIL,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId").orEmpty()
            GroupDetailScreen(
                viewModel = viewModel(
                    // Keyed by group id so navigating to a different group gets
                    // its own ViewModel rather than reusing stale state.
                    key = "group_$groupId",
                    factory = factoryFor { GroupDetailViewModel(repository, groupId) },
                ),
                onBack = { rootNavController.popBackStack() },
            )
        }
    }
}

/** The four bottom-nav tabs. */
@Composable
private fun MainTabs(
    repository: DocVaultRepository,
    onOpenGroup: (String) -> Unit,
    onSignedOut: () -> Unit,
) {
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
            composable(DocVaultDestination.Groups.route) {
                GroupsScreen(
                    viewModel = viewModel(factory = factoryFor { GroupsViewModel(repository) }),
                    onOpenGroup = onOpenGroup,
                )
            }
            composable(DocVaultDestination.Me.route) {
                MeScreen(
                    viewModel = viewModel(factory = factoryFor { MeViewModel(repository) }),
                    onSignedOut = onSignedOut,
                )
            }
        }
    }
}

/**
 * Builds a [ViewModelProvider.Factory] from a lambda.
 *
 * The DI graph is wired by hand (see AppContainer), so ViewModels take their
 * dependencies as constructor arguments and need a factory to supply them.
 */
private inline fun <reified VM : ViewModel> factoryFor(
    crossinline create: () -> VM,
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
}
