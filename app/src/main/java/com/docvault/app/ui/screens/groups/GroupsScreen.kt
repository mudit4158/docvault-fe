package com.docvault.app.ui.screens.groups

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

const val GroupsScreenTestTag = "groups_screen"

/**
 * Placeholder for the Groups tab (group list, invitations, group detail — screens 15-18).
 * Built out as part of Person 1's Groups & Invites and Sharing sprint items.
 */
@Composable
fun GroupsScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().testTag(GroupsScreenTestTag),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "Groups", style = MaterialTheme.typography.titleLarge)
    }
}
