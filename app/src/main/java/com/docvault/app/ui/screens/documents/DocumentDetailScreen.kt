package com.docvault.app.ui.screens.documents

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docvault.app.data.DocTypes
import com.docvault.app.data.Format
import com.docvault.app.data.net.DocumentDetail
import com.docvault.app.data.net.GroupResponse
import com.docvault.app.data.net.ShareDto
import com.docvault.app.ui.components.documentIcon
import com.docvault.app.ui.theme.docVaultTextFieldColors

const val DocumentDetailScreenTestTag = "document_detail_screen"

/**
 * Document detail (prototype screen 06): identity, download, sharing, tags.
 *
 * The owner sees everything; a group member sees what the document is and,
 * if allowed, a download button. The server decides what each person gets —
 * the screen only hides controls that would be refused anyway.
 *
 * In-app preview is not built yet (tracker). When it is, it must run with
 * screenshots blocked.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DocumentDetailScreen(
    viewModel: DocumentDetailViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var changingType by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var tagging by remember { mutableStateOf(false) }

    val detail = state.detail

    // The user picks where the file goes; writing there needs no permission.
    val saveFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(detail?.mimeType ?: "application/octet-stream"),
    ) { uri -> if (uri != null) viewModel.download(uri) }

    LaunchedEffect(state.message, state.error) {
        val text = state.message ?: state.error
        if (text != null) {
            snackbarHostState.showSnackbar(text)
            viewModel.clearMessage()
        }
    }

    // Move to trash, then offer Undo for the standard short snackbar (~4 s,
    // handoff §4). No undo → leave the screen.
    LaunchedEffect(state.deleted) {
        if (state.deleted) {
            val result = snackbarHostState.showSnackbar(
                message = "Moved to trash",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete() else onBack()
        }
    }

    LaunchedEffect(state.savedTo) {
        val uri = state.savedTo ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar("Saved", actionLabel = "Open")
        viewModel.consumeSaved()
        if (result == SnackbarResult.ActionPerformed) {
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, detail?.mimeType)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            runCatching { context.startActivity(intent) }
        }
    }

    Scaffold(
        modifier = modifier.testTag(DocumentDetailScreenTestTag),
        topBar = {
            TopAppBar(
                title = { Text("Document") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (detail?.isOwner == true) {
                        IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag("document_menu")) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More actions")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                onClick = { menuOpen = false; renaming = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Change type") },
                                onClick = { menuOpen = false; changingType = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Move to trash", color = MaterialTheme.colorScheme.error) },
                                onClick = { menuOpen = false; confirmingDelete = true },
                            )
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
            when {
                state.unavailable != null -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                ) {
                    Text(state.unavailable.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = onBack) { Text("Back") }
                }

                detail == null -> Row(
                    Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) { CircularProgressIndicator() }

                else -> Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    Header(detail)
                    Spacer(Modifier.height(16.dp))

                    if (detail.canDownload) {
                        Button(
                            onClick = { saveFile.launch(detail.name) },
                            enabled = !state.isBusy,
                            modifier = Modifier.fillMaxWidth().testTag("download_button"),
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Download · ${Format.bytes(detail.sizeBytes)}")
                        }
                    } else {
                        Text(
                            "View only — the owner hasn't allowed downloads.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (detail.isOwner) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.loadOwnerOptions(); sharing = true },
                            enabled = !state.isBusy,
                            modifier = Modifier.fillMaxWidth().testTag("share_button"),
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Share · ${Format.shareState(detail.shares.orEmpty().size)}")
                        }

                        SectionTitle("Shared with")
                        val shares = detail.shares.orEmpty()
                        if (shares.isEmpty()) {
                            Muted("Only you can see this document.")
                        } else {
                            shares.forEach { share ->
                                ShareRow(
                                    share = share,
                                    enabled = !state.isBusy,
                                    onStop = { viewModel.setShare(share.groupId, null) },
                                )
                            }
                        }

                        SectionTitle("Tags")
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            detail.tags.forEach { tag ->
                                InputChip(
                                    selected = false,
                                    onClick = { viewModel.removeTag(tag.id) },
                                    label = { Text(tag.label) },
                                    trailingIcon = {
                                        Icon(Icons.Filled.Close, contentDescription = "Remove ${tag.label}", Modifier.size(16.dp))
                                    },
                                )
                            }
                            AssistChip(
                                onClick = { viewModel.loadOwnerOptions(); tagging = true },
                                label = { Text("Add tag") },
                                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, Modifier.size(16.dp)) },
                                modifier = Modifier.testTag("add_tag"),
                            )
                        }
                    }
                }
            }
        }
    }

    if (detail != null) {
        if (renaming) {
            RenameDialog(
                current = detail.name,
                onDismiss = { renaming = false },
                onConfirm = { renaming = false; viewModel.rename(it) },
            )
        }
        if (changingType) {
            TypeDialog(
                current = detail.docType,
                onDismiss = { changingType = false },
                onSelect = { changingType = false; viewModel.changeType(it) },
            )
        }
        if (confirmingDelete) {
            AlertDialog(
                onDismissRequest = { confirmingDelete = false },
                title = { Text("Move to trash?") },
                text = {
                    Text(
                        "It stops being shared with every group straight away. " +
                            "You can restore it from Trash for 10 days, but sharing won't come back.",
                    )
                },
                confirmButton = {
                    Button(onClick = { confirmingDelete = false; viewModel.delete() }) { Text("Move to trash") }
                },
                dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") } },
            )
        }
        if (sharing) {
            ShareDialog(
                detail = detail,
                groups = state.groups,
                enabled = !state.isBusy,
                onDismiss = { sharing = false },
                onChange = viewModel::setShare,
            )
        }
        if (tagging) {
            TagDialog(
                existing = detail.tags.map { it.label.lowercase() }.toSet(),
                suggestions = state.tagSuggestions,
                onDismiss = { tagging = false },
                onAdd = { tagging = false; viewModel.addTag(it) },
            )
        }
    }
}

@Composable
private fun Header(detail: DocumentDetail) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(
                documentIcon(detail.mimeType),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(detail.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("document_name"))
                Spacer(Modifier.height(4.dp))
                val pages = detail.pageCount?.let { " · $it page${if (it == 1) "" else "s"}" }.orEmpty()
                Muted("${DocTypes.label(detail.docType)} · ${Format.bytes(detail.sizeBytes)}$pages")
                Muted("Uploaded ${Format.date(detail.createdAt)}")
                if (!detail.isOwner) {
                    Muted("Shared by ${detail.owner.displayName} · ${Format.permission(detail.myPermission)}")
                }
                Muted("Encrypted at rest")
            }
        }
    }
}

@Composable
private fun ShareRow(share: ShareDto, enabled: Boolean, onStop: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(share.groupName, style = MaterialTheme.typography.bodyLarge)
            Muted(Format.permission(share.permission))
        }
        TextButton(onClick = onStop, enabled = enabled) {
            Text("Stop sharing", color = MaterialTheme.colorScheme.error)
        }
    }
}

/**
 * Per-group access: Not shared / View / View + download (prototype screen 08).
 * Only the owner's own groups are offered — the server refuses any other.
 */
@Composable
private fun ShareDialog(
    detail: DocumentDetail,
    groups: List<GroupResponse>,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onChange: (groupId: String, permission: String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Who can open this file") },
        text = {
            if (groups.isEmpty()) {
                Muted("You're not in any groups yet. Create one on the Groups tab to share.")
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(groups, key = { it.id }) { group ->
                        val current = detail.shares?.firstOrNull { it.groupId == group.id }?.permission
                        Column(Modifier.padding(vertical = 6.dp).testTag("share_group_${group.id}")) {
                            Text(group.name, style = MaterialTheme.typography.bodyLarge)
                            Muted("${group.memberCount} member${if (group.memberCount == 1) "" else "s"}")
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = current == null,
                                    enabled = enabled,
                                    onClick = { if (current != null) onChange(group.id, null) },
                                    label = { Text("Not shared") },
                                )
                                FilterChip(
                                    selected = current == "view",
                                    enabled = enabled,
                                    onClick = { if (current != "view") onChange(group.id, "view") },
                                    label = { Text("View") },
                                )
                                FilterChip(
                                    selected = current == "download",
                                    enabled = enabled,
                                    onClick = { if (current != "download") onChange(group.id, "download") },
                                    label = { Text("View + download") },
                                )
                            }
                            HorizontalDivider(Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagDialog(
    existing: Set<String>,
    suggestions: List<String>,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    var label by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a tag") },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { if (it.length <= 64) label = it },
                    label = { Text("Tag") },
                    singleLine = true,
                    colors = docVaultTextFieldColors(),
                    modifier = Modifier.fillMaxWidth().testTag("tag_input"),
                )
                val available = suggestions.filter { it.lowercase() !in existing }
                if (available.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        available.forEach { suggestion ->
                            AssistChip(onClick = { onAdd(suggestion) }, label = { Text(suggestion) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(label.trim()) }, enabled = label.isNotBlank()) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val extension = current.substringAfterLast('.', "")
    var name by remember { mutableStateOf(current.substringBeforeLast('.', current)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    suffix = { if (extension.isNotEmpty()) Text(".$extension") },
                    colors = docVaultTextFieldColors(),
                    modifier = Modifier.fillMaxWidth().testTag("rename_input"),
                )
                Spacer(Modifier.height(6.dp))
                Muted("The file type can't be changed by renaming.")
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TypeDialog(current: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Document type") },
        text = {
            Column {
                DocTypes.ALL.forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(value) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == current, onClick = { onSelect(value) })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(20.dp))
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
