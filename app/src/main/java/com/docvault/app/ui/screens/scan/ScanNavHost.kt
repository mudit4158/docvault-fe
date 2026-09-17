package com.docvault.app.ui.screens.scan

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

private const val ROUTE_EDIT = "edit"
private const val ROUTE_REVIEW = "review"

/**
 * The part of the Scan flow that runs once at least one page exists:
 * per-page edit, then review/reorder/format/save. Capture itself happens
 * before this is shown — ML Kit's own scanner activity (see
 * [com.docvault.app.ui.screens.scan.data.rememberDocumentScanner]) is the
 * "capture" screen.
 *
 * Only two routes: which page is "current" lives in [ScanViewModel] itself,
 * so paging Next/Back within the edit screen never touches the back stack —
 * only the edit-to-review transition does.
 */
@Composable
fun ScanNavHost(viewModel: ScanViewModel, onCancel: () -> Unit, onFinished: () -> Unit) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = ROUTE_EDIT) {
        composable(ROUTE_EDIT) {
            PageEditScreen(
                viewModel = viewModel,
                onDone = { navController.navigate(ROUTE_REVIEW) },
                onCancel = onCancel,
            )
        }
        composable(ROUTE_REVIEW) {
            ReviewScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onEditPage = { index ->
                    viewModel.setCurrentPage(index)
                    navController.popBackStack()
                },
                onCancel = onCancel,
                onSaved = onFinished,
            )
        }
    }
}
