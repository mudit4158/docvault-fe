package com.docvault.app.ui.screens.groups

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.docvault.app.data.net.GroupResponse
import com.docvault.app.data.net.InvitationResponse
import com.docvault.app.ui.components.TAB_SCREEN_ZERO_INSETS
import com.docvault.app.ui.theme.docVaultTextFieldColors

const val GroupsScreenTestTag = "groups_screen"

/**
 * Groups tab — the groups you belong to, plus invitations (prototype 15-16).
 *
 * Reloads on every return to the tab. Group membership changes on other
 * screens — leaving from the detail page, an admin removing you — so a
 * fetch-once list shows a group you are no longer in, or a stale member count.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    viewModel: GroupsViewModel,
    onOpenGroup: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Fires on first composition and on every return to this tab.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    LaunchedEffect(state.message, state.error) {
        val text = state.message ?: state.error
        if (text != null) {
            snackbarHostState.showSnackbar(text)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        modifier = modifier.testTag(GroupsScreenTestTag),
        // Nested inside MainTabs' own Scaffold (DocVaultNavHost.kt), which
        // already reserves status-bar/nav-bar insets — see VaultScreen's
        // matching comment for why this must be zeroed here, not left default.
        contentWindowInsets = TAB_SCREEN_ZERO_INSETS,
        // TopAppBar reserves status-bar height itself by default
        // (TopAppBarDefaults.windowInsets), separate from Scaffold's own
        // contentWindowInsets above — both need zeroing, see VaultScreen.
        topBar = { TopAppBar(title = { Text("Groups") }, windowInsets = TAB_SCREEN_ZERO_INSETS) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.showCreateDialog(true) }) {
                Icon(Icons.Filled.Add, contentDescription = "Create a group")
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh(fromPull = true) },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            if (state.isLoading && state.groups.isEmpty() && state.invitations.isEmpty()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                GroupsList(state = state, viewModel = viewModel, onOpenGroup = onOpenGroup)
            }
        }
    }

    if (state.showCreateDialog) {
        CreateGroupDialog(
            isSubmitting = state.isSubmitting,
            onDismiss = { viewModel.showCreateDialog(false) },
            onCreate = viewModel::createGroup,
        )
    }
}

@Composable
private fun GroupsList(
    state: GroupsUiState,
    viewModel: GroupsViewModel,
    onOpenGroup: (String) -> Unit,
) {
    // Pending starts open because it needs a decision; declined starts closed
    // because it is history the user rarely wants.
    var pendingExpanded by remember { mutableStateOf(true) }
    var declinedExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.pendingInvitations.isNotEmpty()) {
            item {
                CollapsibleHeader(
                    title = "Invitations",
                    count = state.pendingInvitations.size,
                    expanded = pendingExpanded,
                    highlight = true,
                    onToggle = { pendingExpanded = !pendingExpanded },
                    testTag = "section_pending",
                )
            }
            if (pendingExpanded) {
                items(state.pendingInvitations, key = { it.id }) { invitation ->
                    InvitationCard(
                        invitation = invitation,
                        enabled = !state.isSubmitting,
                        onAccept = { viewModel.accept(invitation) },
                        onDecline = { viewModel.decline(invitation) },
                    )
                }
            }
        }

        if (state.declinedInvitations.isNotEmpty()) {
            item {
                CollapsibleHeader(
                    title = "Declined",
                    count = state.declinedInvitations.size,
                    expanded = declinedExpanded,
                    highlight = false,
                    onToggle = { declinedExpanded = !declinedExpanded },
                    testTag = "section_declined",
                )
            }
            if (declinedExpanded) {
                items(state.declinedInvitations, key = { it.id }) { invitation ->
                    DeclinedInvitationCard(
                        invitation = invitation,
                        enabled = !state.isSubmitting,
                        onAccept = { viewModel.accept(invitation) },
                    )
                }
            }
        }

        item { SectionLabel("Your groups") }

        if (state.groups.isEmpty()) {
            item {
                Text(
                    text = "You are not in any groups yet. Create one to start sharing " +
                        "documents with people you trust.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(state.groups, key = { it.id }) { group ->
                GroupCard(group = group, onClick = { onOpenGroup(group.id) })
            }
        }
    }
}

@Composable
private fun CollapsibleHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    highlight: Boolean,
    onToggle: () -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (highlight) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(Modifier.size(8.dp))
            if (highlight) {
                Badge { Text("$count") }
            } else {
                Text(
                    "$count",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (expanded) "Collapse $title" else "Expand $title",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun GroupCard(group: GroupResponse, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("group_${group.id}"),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(group.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (group.isAdmin) "Admin" else "Member",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            group.description?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${group.memberCount} member${if (group.memberCount == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InvitationCard(
    invitation: InvitationResponse,
    enabled: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("invitation_${invitation.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(invitation.groupName, style = MaterialTheme.typography.titleMedium)
            invitation.invitedBy?.let {
                Spacer(Modifier.height(2.dp))
                Text("Invited by ${it.displayName}", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Equal visual weight, per handoff §3.4.
                Button(onClick = onAccept, enabled = enabled) { Text("Accept") }
                OutlinedButton(onClick = onDecline, enabled = enabled) { Text("Decline") }
            }
        }
    }
}

/** A declined invitation can still be accepted — declining is recoverable. */
@Composable
private fun DeclinedInvitationCard(
    invitation: InvitationResponse,
    enabled: Boolean,
    onAccept: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().testTag("declined_${invitation.id}")) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    invitation.groupName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                invitation.invitedBy?.let {
                    Text(
                        "Invited by ${it.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = onAccept, enabled = enabled) { Text("Accept") }
        }
    }
}

@Composable
private fun CreateGroupDialog(
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text("Create a group") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    enabled = !isSubmitting,
                    colors = docVaultTextFieldColors(),
                    modifier = Modifier.fillMaxWidth().testTag("create_group_name"),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    enabled = !isSubmitting,
                    colors = docVaultTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, description) },
                enabled = !isSubmitting,
                modifier = Modifier.testTag("create_group_confirm"),
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text("Cancel") }
        },
    )
}
