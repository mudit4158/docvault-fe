package com.docvault.app.ui.screens.vault

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docvault.app.data.DocTypes
import com.docvault.app.data.FileRules
import com.docvault.app.data.Format
import com.docvault.app.data.net.DocumentSummary
import com.docvault.app.ui.components.documentIcon
import com.docvault.app.ui.theme.docVaultTextFieldColors

const val VaultScreenTestTag = "vault_screen"

/**
 * Vault tab — the caller's documents, search, type filter and upload
 * (prototype screens 02–05, 11, 12).
 *
 * List and grid are the same data; only the list layout is built for now.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    viewModel: VaultViewModel,
    onOpenDocument: (String) -> Unit,
    onOpenTrash: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmingSignOut by remember { mutableStateOf(false) }

    // System picker: returns only the files chosen, so no storage permission.
    val pickFiles = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> if (uris.isNotEmpty()) viewModel.upload(uris) }

    // Reload on every return — a document renamed, shared or trashed on the
    // detail screen must not show stale here.
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
        modifier = modifier.testTag(VaultScreenTestTag),
        // This screen is nested inside MainTabs' own Scaffold (DocVaultNavHost.kt),
        // which already reserves status-bar/nav-bar space around the tab content.
        // Scaffold's default contentWindowInsets would otherwise reserve that
        // space a SECOND time here, showing up as extra empty gaps above the
        // top bar and below the FAB — barely visible on a phone with thin
        // gesture-nav insets, much more visible on one with a tall fixed-height
        // 3-button nav bar. Zero insets here; the outer Scaffold already owns them.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Vault") },
                actions = {
                    IconButton(onClick = onOpenTrash, modifier = Modifier.testTag("open_trash")) {
                        // A plain trash-can icon reads as "gone for good" — this one
                        // exists specifically to signal "still recoverable" (10-day
                        // retention window), which a generic delete icon doesn't.
                        Icon(Icons.Filled.RestoreFromTrash, contentDescription = "Trash (restorable for 10 days)")
                    }
                    IconButton(
                        onClick = { confirmingSignOut = true },
                        modifier = Modifier.testTag("vault_sign_out"),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { pickFiles.launch(FileRules.PICKER_MIME_TYPES) },
                icon = { Icon(Icons.Filled.Upload, contentDescription = null) },
                text = { Text("Upload") },
                modifier = Modifier.testTag("upload_fab"),
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh(fromPull = true) },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // Bottom padding so the last row clears the Upload button.
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChange,
                        placeholder = { Text("Search by name or tag") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (state.query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onQueryChange("") }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        colors = docVaultTextFieldColors(),
                        modifier = Modifier.fillMaxWidth().testTag("vault_search"),
                    )
                }

                item { TypeFilterRow(selected = state.docType, onSelect = viewModel::onDocTypeSelected) }

                state.quotaMessage?.let { message ->
                    item { QuotaBanner(message) }
                }

                items(state.uploads, key = { it.key }) { upload ->
                    UploadRow(
                        item = upload,
                        onRetry = { viewModel.retry(upload.key) },
                        onDismiss = { viewModel.dismissUpload(upload.key) },
                    )
                }

                when {
                    state.isLoading && state.documents.isEmpty() -> item {
                        Row(Modifier.fillMaxWidth().padding(32.dp), horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    state.documents.isEmpty() && state.isFiltered -> item {
                        EmptyMessage(
                            text = "No documents match.",
                            actionLabel = "Clear filters",
                            onAction = viewModel::clearFilters,
                        )
                    }

                    state.documents.isEmpty() -> item {
                        EmptyMessage(
                            text = "Your vault is empty. Upload your first document to keep it safe here.",
                            actionLabel = "Upload",
                            onAction = { pickFiles.launch(FileRules.PICKER_MIME_TYPES) },
                        )
                    }

                    else -> {
                        items(state.documents, key = { it.id }) { document ->
                            DocumentRow(document = document, onClick = { onOpenDocument(document.id) })
                        }
                        if (state.hasNext) {
                            item {
                                TextButton(
                                    onClick = viewModel::loadMore,
                                    enabled = !state.isLoadingMore,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(if (state.isLoadingMore) "Loading…" else "Load more") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmingSignOut) {
        AlertDialog(
            onDismissRequest = { confirmingSignOut = false },
            title = { Text("Sign out?") },
            confirmButton = {
                Button(onClick = { confirmingSignOut = false; onSignOut() }) { Text("Sign out") }
            },
            dismissButton = { TextButton(onClick = { confirmingSignOut = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TypeFilterRow(selected: String?, onSelect: (String?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("All") })
        DocTypes.ALL.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(if (selected == value) null else value) },
                label = { Text(label) },
                modifier = Modifier.testTag("filter_$value"),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DocumentRow(document: DocumentSummary, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("document_${document.id}"),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(
                documentIcon(document.mimeType),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        document.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        Format.shareState(document.shareCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (document.shareCount > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "${DocTypes.label(document.docType)} · ${Format.bytes(document.sizeBytes)} · " +
                        Format.date(document.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (document.tags.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        document.tags.forEach { tag -> SmallTag(tag.label) }
                    }
                }
            }
        }
    }
}

@Composable
fun SmallTag(label: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun UploadRow(item: UploadItem, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().testTag("upload_${item.key}")) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        item.file.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // Lets the user see up front whether a file is anywhere near
                    // the 20MB cap, rather than finding out only from a 413 error.
                    Text(
                        Format.bytes(item.file.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (item.isFailed) {
                    if (item.canRetry) TextButton(onClick = onRetry) { Text("Retry") }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss")
                    }
                } else {
                    Text(
                        "${(item.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (item.isFailed) {
                Text(
                    item.error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun QuotaBanner(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("quota_banner"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Daily upload limit reached",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                // Viewing, sharing and downloading still work — only new uploads stop.
                "$message Everything already in your vault is still available.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun EmptyMessage(text: String, actionLabel: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onAction) { Text(actionLabel) }
    }
}
