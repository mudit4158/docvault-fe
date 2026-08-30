package com.docvault.app.ui.screens.me

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

const val MeScreenTestTag = "me_screen"

/**
 * Placeholder for the Me tab (account, upload allowance, retention, app lock settings).
 * Owned by Person 2's Account track — stubbed here so the tab shell is complete.
 */
@Composable
fun MeScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().testTag(MeScreenTestTag),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "Me", style = MaterialTheme.typography.titleLarge)
    }
}
