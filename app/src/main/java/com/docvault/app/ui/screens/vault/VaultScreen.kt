package com.docvault.app.ui.screens.vault

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

const val VaultScreenTestTag = "vault_screen"

/**
 * Placeholder for the Vault tab (list/grid, search, filter, detail, upload, trash — screens
 * 01-12 of the prototype). Built out as part of Person 1's Vault/Upload sprint items.
 */
@Composable
fun VaultScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().testTag(VaultScreenTestTag),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "Vault", style = MaterialTheme.typography.titleLarge)
    }
}
