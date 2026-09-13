package com.docvault.app.ui.screens.groups

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docvault.app.data.Format
import com.docvault.app.data.net.GroupDocument
import com.docvault.app.data.net.MemberResponse
import com.docvault.app.ui.components.documentIcon
import com.docvault.app.ui.components.Countries
import com.docvault.app.ui.components.Country
import com.docvault.app.ui.components.PhoneNumber
import com.docvault.app.ui.components.PhoneNumberField
import com.docvault.app.ui.components.rememberContactNumberPicker

const val GroupDetailScreenTestTag = "group_detail_screen"

/**
 * Group detail — members, invite, and the admin actions (prototype screen 18).
 *
 * Admin-only controls are hidden for ordinary members, but the backend is the
 * real authority: it returns 403 regardless of what the client shows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    viewModel: GroupDetailViewModel,
    onBack: () -> Unit,
    onOpenDocument: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // Documents first, as in the prototype (screen 18).
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state.message, state.error) {
        val text = state.message ?: state.error
        if (text != null) {
            snackbarHostState.showSnackbar(text)
            viewModel.clearMessage()
        }
    }

    // Left or deleted — the group no longer exists for this user.
    LaunchedEffect(state.closed) {
        if (state.closed) onBack()
    }

    Scaffold(
        modifier = modifier.testTag(GroupDetailScreenTestTag),
        topBar = {
            TopAppBar(
                title = { Text(state.group?.name ?: "Group") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.isAdmin) {
                        IconButton(
                            onClick = { viewModel.showInviteDialog(true) },
                            modifier = Modifier.testTag("invite_button"),
                        ) {
                            Icon(Icons.Filled.PersonAdd, contentDescription = "Invite a member")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh(fromPull = true) },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            if (state.isLoading && state.members.isEmpty()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.group?.description?.let {
                        item {
                            Text(it, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    item {
                        TabRow(selectedTabIndex = tab) {
                            Tab(
                                selected = tab == 0,
                                onClick = { tab = 0 },
                                text = { Text("Documents · ${state.documents.size}") },
                                modifier = Modifier.testTag("tab_documents"),
                            )
                            Tab(
                                selected = tab == 1,
                                onClick = { tab = 1 },
                                text = { Text("Members · ${state.members.size}") },
                                modifier = Modifier.testTag("tab_members"),
                            )
                        }
                    }

                    if (tab == 0) {
                        if (state.documents.isEmpty()) {
                            item {
                                Text(
                                    "Nothing shared with this group yet. To share, open a document " +
                                        "in your Vault and tap Share.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 16.dp),
                                )
                            }
                        } else {
                            items(state.documents, key = { "doc_${it.id}" }) { document ->
                                GroupDocumentRow(document = document, onClick = { onOpenDocument(document.id) })
                            }
                        }
                    }

                    if (tab == 1) items(state.members, key = { it.account.id }) { member ->
                        MemberRow(
                            member = member,
                            isMe = member.account.id == state.myAccountId,
                            viewerIsAdmin = state.isAdmin,
                            enabled = !state.isSubmitting,
                            onMakeAdmin = { viewModel.makeAdmin(member) },
                            onRemove = { viewModel.removeMember(member) },
                        )
                    }

                    if (tab == 1) item {
                        Spacer(Modifier.height(24.dp))
                        OutlinedButton(
                            onClick = { confirmLeave = true },
                            enabled = !state.isSubmitting,
                            modifier = Modifier.fillMaxWidth().testTag("leave_group"),
                        ) { Text("Leave group") }
                    }

                    if (tab == 1 && state.isAdmin) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { confirmDelete = true },
                                enabled = !state.isSubmitting,
                                modifier = Modifier.fillMaxWidth().testTag("delete_group"),
                            ) {
                                Text("Delete group", color = MaterialTheme.colorScheme.error)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Deleting removes the group for everyone. " +
                                    "Documents stay in each owner's vault.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.showInviteDialog) {
        InviteDialog(
            isSubmitting = state.isSubmitting,
            lookup = state.lookup,
            onDismiss = { viewModel.showInviteDialog(false) },
            onLookup = viewModel::lookup,
            onClearLookup = viewModel::clearLookup,
            onInvite = viewModel::inviteResolved,
        )
    }

    if (confirmLeave) {
        ConfirmDialog(
            title = "Leave this group?",
            body = "You will lose access to documents shared with it.",
            confirmLabel = "Leave",
            onConfirm = { confirmLeave = false; viewModel.leaveGroup() },
            onDismiss = { confirmLeave = false },
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this group?",
            body = "It disappears for everyone. Documents stay in each owner's vault.",
            confirmLabel = "Delete",
            onConfirm = { confirmDelete = false; viewModel.deleteGroup() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun GroupDocumentRow(document: GroupDocument, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("group_document_${document.id}"),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                documentIcon(document.mimeType),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(document.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Shared by ${document.owner.displayName} · ${Format.permission(document.permission)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${Format.bytes(document.sizeBytes)} · ${Format.date(document.sharedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MemberRow(
    member: MemberResponse,
    isMe: Boolean,
    viewerIsAdmin: Boolean,
    enabled: Boolean,
    onMakeAdmin: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().testTag("member_${member.account.id}")) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = member.account.displayName + if (isMe) " · you" else "",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = if (member.isAdmin) "Group admin" else "Member",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // An admin cannot remove or promote themselves — the backend
            // rejects both with 409, so the actions are not offered.
            if (viewerIsAdmin && !isMe) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!member.isAdmin) {
                        TextButton(
                            onClick = onMakeAdmin,
                            enabled = enabled,
                            modifier = Modifier.testTag("make_admin_${member.account.id}"),
                        ) { Text("Make admin") }
                    }
                    TextButton(
                        onClick = onRemove,
                        enabled = enabled,
                        modifier = Modifier.testTag("remove_${member.account.id}"),
                    ) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

/**
 * Invite by resolving a number to a real person first.
 *
 * The Invite button stays disabled until the lookup returns an account, so an
 * admin can never send an invitation into the void or to a mistyped number —
 * they always see the name of who they are adding.
 */
@Composable
private fun InviteDialog(
    isSubmitting: Boolean,
    lookup: LookupState,
    onDismiss: () -> Unit,
    onLookup: (String) -> Unit,
    onClearLookup: () -> Unit,
    onInvite: () -> Unit,
) {
    var country by remember { mutableStateOf(Countries.DEFAULT) }
    var nationalNumber by remember { mutableStateOf("") }

    // Resolve whenever the number becomes complete, and drop any previous
    // result the moment it stops being complete.
    fun onNumberChanged(newCountry: Country, newNumber: String) {
        country = newCountry
        nationalNumber = newNumber
        if (PhoneNumber.isValid(newCountry, newNumber)) {
            onLookup(PhoneNumber.toE164(newCountry, newNumber))
        } else {
            onClearLookup()
        }
    }

    // System contacts picker. No READ_CONTACTS permission: it hands back only
    // the single number the user chose.
    val pickContact = rememberContactNumberPicker { raw ->
        val (parsedCountry, parsedNumber) = PhoneNumber.parse(raw, country)
        onNumberChanged(parsedCountry, parsedNumber)
    }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text("Invite a member") },
        text = {
            Column {
                PhoneNumberField(
                    country = country,
                    nationalNumber = nationalNumber,
                    onCountryChange = { onNumberChanged(it, nationalNumber) },
                    onNationalNumberChange = { onNumberChanged(country, it) },
                    enabled = !isSubmitting,
                    isError = lookup.error != null,
                    imeAction = ImeAction.Done,
                    testTagPrefix = "invite_phone",
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        // A small action inside the field rather than a
                        // full-width button — picking a contact is a shortcut
                        // to filling this field, not a separate path.
                        IconButton(
                            onClick = pickContact,
                            enabled = !isSubmitting,
                            modifier = Modifier.testTag("invite_from_contacts"),
                        ) {
                            Icon(
                                Icons.Filled.Contacts,
                                contentDescription = "Choose from contacts",
                            )
                        }
                    },
                )

                Spacer(Modifier.height(8.dp))
                InviteLookupStatus(lookup)
            }
        },
        confirmButton = {
            Button(
                onClick = onInvite,
                // Only ever enabled for a resolved account.
                enabled = !isSubmitting && lookup.resolved != null,
                modifier = Modifier.testTag("invite_confirm"),
            ) { Text("Invite") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text("Cancel") }
        },
    )
}

/** Searching / resolved / error, shown directly under the phone field. */
@Composable
private fun InviteLookupStatus(lookup: LookupState) {
    when {
        lookup.isSearching -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            Text(
                "  Looking up…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        lookup.resolved != null -> Row(
            modifier = Modifier.fillMaxWidth().testTag("invite_resolved"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Column(Modifier.padding(start = 8.dp)) {
                Text(
                    lookup.resolved.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    lookup.resolved.phone,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        lookup.error != null -> Text(
            text = lookup.error,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("invite_error"),
        )

        else -> Text(
            "Enter a number or pick a contact. They need a DocVault account already.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { Button(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
