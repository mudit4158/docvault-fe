package com.docvault.app.ui.screens.scan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.docvault.app.ui.screens.scan.data.rememberDocumentScanner

const val ReviewScreenTestTag = "scan_review_screen"

/**
 * Review: thumbnail strip, reorder, delete, add a page, format choice
 * (single page only), and save (prototype screens 13-14; engineering
 * handoff §3.3 — one save call produces one Document, never per-page rows).
 *
 * Reorder is arrow-buttons rather than drag-and-drop — simplest thing that
 * works for the handful of pages a scan session realistically has; revisit
 * with a drag library only if that proves clunky in practice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    viewModel: ScanViewModel,
    onBack: () -> Unit,
    onEditPage: (index: Int) -> Unit,
    onCancel: () -> Unit,
    onSaved: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmingDiscard by remember { mutableStateOf(false) }

    val addPage = rememberDocumentScanner(
        pageLimit = state.remainingPageSlots.coerceAtLeast(1),
        onResult = { uris -> viewModel.addCaptured(uris) },
        onError = { },
    )

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    Scaffold(
        modifier = Modifier.testTag(ReviewScreenTestTag),
        topBar = {
            TopAppBar(
                title = { Text("Review") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmingDiscard = true }) {
                        Icon(Icons.Filled.Close, contentDescription = "Discard scan")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                "${state.pages.size} page${if (state.pages.size == 1) "" else "s"}",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().testTag("scan_page_strip"),
            ) {
                itemsIndexed(state.pages, key = { _, page -> page.id }) { index, page ->
                    PageThumbnail(
                        thumbnailFile = page.workingFile,
                        index = index,
                        canMoveLeft = index > 0,
                        canMoveRight = index < state.pages.size - 1,
                        onClick = { onEditPage(index) },
                        onMoveLeft = { viewModel.movePage(index, index - 1) },
                        onMoveRight = { viewModel.movePage(index, index + 1) },
                        onDelete = { viewModel.deletePage(page.id) },
                    )
                }
                if (state.remainingPageSlots > 0) {
                    item { AddPageTile(onClick = addPage) }
                }
            }

            Spacer(Modifier.height(24.dp))

            if (state.needsFormatChoice) {
                Text("Save as", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.saveFormat == SaveFormat.PDF,
                        onClick = { viewModel.chooseFormat(SaveFormat.PDF) },
                        label = { Text("PDF") },
                        modifier = Modifier.testTag("scan_format_pdf"),
                    )
                    FilterChip(
                        selected = state.saveFormat == SaveFormat.IMAGE,
                        onClick = { viewModel.chooseFormat(SaveFormat.IMAGE) },
                        label = { Text("Image") },
                        modifier = Modifier.testTag("scan_format_image"),
                    )
                }
                Spacer(Modifier.height(24.dp))
            }

            Spacer(Modifier.weight(1f))

            state.error?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
            }

            if (state.isSaving) {
                LinearProgressIndicator(
                    progress = { state.uploadProgress ?: 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = { viewModel.save() },
                enabled = !state.isSaving && state.pages.isNotEmpty() && !state.needsFormatChoice,
                modifier = Modifier.fillMaxWidth().testTag("scan_save_button"),
            ) {
                Text(if (state.isSaving) "Saving…" else "Save to vault")
            }
        }
    }

    if (confirmingDiscard) {
        AlertDialog(
            onDismissRequest = { confirmingDiscard = false },
            title = { Text("Discard this scan?") },
            text = { Text("Every page you've captured will be lost.") },
            confirmButton = {
                Button(onClick = { confirmingDiscard = false; onCancel() }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmingDiscard = false }) { Text("Keep editing") } },
        )
    }
}

@Composable
private fun PageThumbnail(
    thumbnailFile: java.io.File,
    index: Int,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onClick: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier.width(120.dp).testTag("scan_page_thumb_$index"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(
            modifier = Modifier.size(width = 120.dp, height = 160.dp).clickable(onClick = onClick),
            shape = RoundedCornerShape(8.dp),
        ) {
            AsyncImage(
                model = thumbnailFile,
                contentDescription = "Page ${index + 1}",
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(2.dp))
        Text("Page ${index + 1}", style = MaterialTheme.typography.labelSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMoveLeft, enabled = canMoveLeft, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Move earlier")
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete page ${index + 1}", tint = MaterialTheme.colorScheme.error)
            }
            IconButton(onClick = onMoveRight, enabled = canMoveRight, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Move later")
            }
        }
    }
}

@Composable
private fun AddPageTile(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 120.dp, height = 160.dp)
            .clickable(onClick = onClick)
            .testTag("scan_add_page_tile"),
        contentAlignment = Alignment.Center,
    ) {
        Card(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(8.dp)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Add, contentDescription = "Add another page")
            }
        }
    }
}
