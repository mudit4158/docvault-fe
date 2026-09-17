package com.docvault.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.docvault.app.ui.components.SecureScreen
import com.docvault.app.ui.screens.auth.AuthScreen
import com.docvault.app.ui.screens.auth.AuthViewModel
import com.docvault.app.ui.screens.auth.ForgotPasswordScreen
import com.docvault.app.ui.screens.auth.ForgotPasswordViewModel
import com.docvault.app.ui.screens.auth.OtpAuthScreen
import com.docvault.app.ui.screens.auth.OtpAuthViewModel
import com.docvault.app.ui.screens.groups.GroupDetailScreen
import com.docvault.app.ui.screens.groups.GroupDetailViewModel
import com.docvault.app.ui.screens.groups.GroupsScreen
import com.docvault.app.ui.screens.groups.GroupsViewModel
import com.docvault.app.ui.screens.me.MeScreen
import com.docvault.app.ui.screens.me.MeViewModel
import com.docvault.app.ui.screens.documents.DocumentDetailScreen
import com.docvault.app.ui.screens.documents.DocumentDetailViewModel
import com.docvault.app.ui.screens.documents.PreviewScreen
import com.docvault.app.ui.screens.documents.PreviewViewModel
import com.docvault.app.ui.screens.documents.TrashScreen
import com.docvault.app.ui.screens.documents.TrashViewModel
import com.docvault.app.ui.screens.scan.ScanScreen
import com.docvault.app.ui.screens.scan.ScanViewModel
import com.docvault.app.ui.screens.scan.data.ScanCacheStore
import com.docvault.app.ui.screens.vault.VaultScreen
import com.docvault.app.ui.screens.vault.VaultViewModel

private const val ROUTE_AUTH = "auth"
private const val ROUTE_MAIN = "main"
private const val ROUTE_GROUP_DETAIL = "group/{groupId}"
private const val ROUTE_DOCUMENT_DETAIL = "document/{documentId}"
private const val ROUTE_DOCUMENT_PREVIEW = "document/{documentId}/preview"
private const val ROUTE_TRASH = "trash"
private const val ROUTE_SCAN = "scan"
private const val ROUTE_OTP_LOGIN = "otp_login"
private const val ROUTE_FORGOT_PASSWORD = "forgot_password"

/**
 * Root navigation.
 *
 * Two graphs: an unauthenticated one holding only the sign-in screen, and the
 * tabbed app. The start destination is chosen from whether a token is already
 * stored, so a returning user lands straight in the vault.
 *
 * Scan is a root-level route, not one of [MainTabs]' inner destinations — its
 * own placeholder KDoc always said it should be "a full-screen modal flow
 * outside the tab bar" (engineering handoff §1), matching how it escapes the
 * bottom bar entirely rather than swapping in inside the tab Scaffold.
 */
@Composable
fun DocVaultNavHost(repository: DocVaultRepository, scanCacheStore: ScanCacheStore) {
    // Applied once, for this composable's entire lifetime — not per-route.
    // The root NavHost below has several sibling routes (main, group detail,
    // document detail, trash, scan); a SecureScreen() call inside any one of
    // them clears the flag the moment you navigate away from it, which is
    // exactly backwards — you'd be LEAST protected while looking at a
    // document's details. Scan's own SecureScreen() call was fine in
    // isolation before this existed but would now double-clear the flag on
    // its way out, so it's been removed in favour of this one.
    SecureScreen()

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
                onOtpLogin = { rootNavController.navigate(ROUTE_OTP_LOGIN) },
                onForgotPassword = { rootNavController.navigate(ROUTE_FORGOT_PASSWORD) },
            )
        }

        composable(ROUTE_OTP_LOGIN) {
            OtpAuthScreen(
                viewModel = viewModel(factory = factoryFor { OtpAuthViewModel(repository) }),
                onSignedIn = {
                    rootNavController.navigate(ROUTE_MAIN) {
                        // Drop both the OTP screen and the sign-in screen underneath it.
                        popUpTo(ROUTE_AUTH) { inclusive = true }
                    }
                },
                onBack = { rootNavController.popBackStack() },
            )
        }

        composable(ROUTE_FORGOT_PASSWORD) {
            ForgotPasswordScreen(
                viewModel = viewModel(factory = factoryFor { ForgotPasswordViewModel(repository) }),
                onDone = {
                    // Back to sign-in, not straight into the app — resetting the
                    // credential isn't a session; the user still signs in normally.
                    rootNavController.popBackStack(ROUTE_AUTH, inclusive = false)
                },
                onBack = { rootNavController.popBackStack() },
            )
        }

        composable(ROUTE_MAIN) {
            MainTabs(
                repository = repository,
                onOpenGroup = { groupId -> rootNavController.navigate("group/$groupId") },
                onOpenDocument = { documentId -> rootNavController.navigate("document/$documentId") },
                onOpenTrash = { rootNavController.navigate(ROUTE_TRASH) },
                onOpenScan = { rootNavController.navigate(ROUTE_SCAN) },
                onSignedOut = {
                    rootNavController.navigate(ROUTE_AUTH) {
                        popUpTo(ROUTE_MAIN) { inclusive = true }
                    }
                },
            )
        }

        composable(ROUTE_SCAN) {
            val resolver = LocalContext.current.contentResolver
            // No explicit key: each visit to this route is a fresh push (never
            // saveState/restoreState'd like the bottom tabs are), so this is
            // already a brand-new backstack entry with its own ViewModelStore
            // — a fresh scan session every time, never a reused one.
            ScanScreen(
                viewModel = viewModel(factory = factoryFor { ScanViewModel(repository, resolver, scanCacheStore) }),
                onFinished = { rootNavController.popBackStack() },
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
                onOpenDocument = { documentId -> rootNavController.navigate("document/$documentId") },
            )
        }

        composable(
            route = ROUTE_DOCUMENT_DETAIL,
            arguments = listOf(navArgument("documentId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val documentId = backStackEntry.arguments?.getString("documentId").orEmpty()
            val resolver = LocalContext.current.contentResolver
            DocumentDetailScreen(
                viewModel = viewModel(
                    key = "document_$documentId",
                    factory = factoryFor { DocumentDetailViewModel(repository, resolver, documentId) },
                ),
                onBack = { rootNavController.popBackStack() },
                onOpenPreview = { id -> rootNavController.navigate("document/$id/preview") },
            )
        }

        composable(
            route = ROUTE_DOCUMENT_PREVIEW,
            arguments = listOf(navArgument("documentId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val documentId = backStackEntry.arguments?.getString("documentId").orEmpty()
            val cacheDir = LocalContext.current.cacheDir
            PreviewScreen(
                viewModel = viewModel(
                    key = "preview_$documentId",
                    factory = factoryFor { PreviewViewModel(repository, cacheDir, documentId) },
                ),
                onBack = { rootNavController.popBackStack() },
            )
        }

        composable(ROUTE_TRASH) {
            TrashScreen(
                viewModel = viewModel(factory = factoryFor { TrashViewModel(repository) }),
                onBack = { rootNavController.popBackStack() },
            )
        }
    }
}

/** The four bottom-nav tabs. Scan is not one of this NavHost's destinations — see [onOpenScan]. */
@Composable
private fun MainTabs(
    repository: DocVaultRepository,
    onOpenGroup: (String) -> Unit,
    onOpenDocument: (String) -> Unit,
    onOpenTrash: () -> Unit,
    onOpenScan: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val resolver = LocalContext.current.contentResolver
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
                    if (destination == DocVaultDestination.Scan) {
                        // Scan is a full-screen modal outside the tab shell, not
                        // an inner destination — escape to the root nav host
                        // instead of navigating this Scaffold's own NavHost.
                        onOpenScan()
                    } else {
                        navController.navigate(destination.route) {
                            // Single copy of each tab on the back stack, state preserved across
                            // tab switches (standard bottom-nav pattern).
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
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
            composable(DocVaultDestination.Vault.route) {
                VaultScreen(
                    viewModel = viewModel(factory = factoryFor { VaultViewModel(repository, resolver) }),
                    onOpenDocument = onOpenDocument,
                    onOpenTrash = onOpenTrash,
                    onSignOut = { repository.signOut(); onSignedOut() },
                )
            }
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
