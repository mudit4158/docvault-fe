package com.docvault.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector
import com.docvault.app.R

/**
 * The four bottom-nav tabs (PRD §3.1 / engineering handoff §1). Vault is the deep branch;
 * Scan, Groups, and Me are each a shallow single-graph destination for now — see the
 * per-feature nav graphs Person 1's sprint items will add under each screen's own package.
 */
enum class DocVaultDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Vault(route = "vault", labelRes = R.string.nav_vault, icon = Icons.Filled.Folder),
    Scan(route = "scan", labelRes = R.string.nav_scan, icon = Icons.Filled.DocumentScanner),
    Groups(route = "groups", labelRes = R.string.nav_groups, icon = Icons.Filled.Groups),
    Me(route = "me", labelRes = R.string.nav_me, icon = Icons.Filled.Person),
}
