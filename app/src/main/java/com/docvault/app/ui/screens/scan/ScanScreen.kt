package com.docvault.app.ui.screens.scan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

const val ScanScreenTestTag = "scan_screen"

/**
 * Placeholder for the Scan tab (capture, edit pages, save to vault — screens 13-14). A
 * full-screen modal flow outside the tab bar once built; built out as part of Person 1's
 * Scan sprint items.
 */
@Composable
fun ScanScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().testTag(ScanScreenTestTag),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "Scan", style = MaterialTheme.typography.titleLarge)
    }
}
