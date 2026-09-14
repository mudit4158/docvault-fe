package com.docvault.app.ui.screens.scan

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.docvault.app.ui.screens.scan.data.PageEdit
import com.docvault.app.ui.screens.scan.data.PageTransforms
import com.docvault.app.ui.screens.scan.data.rememberDocumentScanner

const val PageEditScreenTestTag = "scan_page_edit_screen"

/**
 * Per-page edit: pinch-zoom to inspect, rotate, brightness/contrast, a
 * black-and-white filter, delete, and retake (PRD §4.5; engineering handoff
 * §3.3). Boundary detection and crop already happened in ML Kit's scanner —
 * this screen only ever sees an already-cropped page.
 *
 * The zoom/pan gesture is purely a local view transform on the displayed
 * image (never applied to the exported file) — it exists so a page
 * photographed from a distance can still be checked for legibility before
 * saving.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageEditScreen(viewModel: ScanViewModel, onDone: () -> Unit, onCancel: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val page = state.currentPage
    var confirmingDiscard by remember { mutableStateOf(false) }
    var confirmingDeletePage by remember { mutableStateOf(false) }

    var scale by remember(page?.id) { mutableFloatStateOf(1f) }
    var offsetX by remember(page?.id) { mutableFloatStateOf(0f) }
    var offsetY by remember(page?.id) { mutableFloatStateOf(0f) }

    val addPage = rememberDocumentScanner(
        pageLimit = state.remainingPageSlots.coerceAtLeast(1),
        onResult = { uris -> viewModel.addCaptured(uris) },
        onError = { },
    )
    val retake = rememberDocumentScanner(
        pageLimit = 1,
        onResult = { uris -> viewModel.completeRetake(uris.firstOrNull()) },
        onCancelled = { viewModel.cancelRetake() },
        onError = { viewModel.cancelRetake() },
    )
    LaunchedEffect(state.retakingPageId) {
        if (state.retakingPageId != null) retake()
    }

    if (page == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    Scaffold(
        modifier = Modifier.testTag(PageEditScreenTestTag),
        topBar = {
            TopAppBar(
                title = { Text("Page ${state.currentPageIndex + 1} of ${state.pages.size}") },
                navigationIcon = {
                    IconButton(onClick = { confirmingDiscard = true }) {
                        Icon(Icons.Filled.Close, contentDescription = "Discard scan")
                    }
                },
                actions = {
                    if (state.remainingPageSlots > 0) {
                        IconButton(onClick = addPage, modifier = Modifier.testTag("scan_add_page")) {
                            Icon(Icons.Filled.Add, contentDescription = "Add another page")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(page.id) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = page.workingFile,
                    contentDescription = "Page ${state.currentPageIndex + 1}",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY),
                )
                if (scale != 1f) {
                    OutlinedButton(
                        onClick = { scale = 1f; offsetX = 0f; offsetY = 0f },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                    ) {
                        Icon(Icons.Filled.RestartAlt, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Reset zoom")
                    }
                }
            }

            EditControls(
                edit = page.edit,
                onEditChange = { viewModel.updateEdit(page.id, it) },
                onRetake = { viewModel.beginRetake(page.id) },
                onDelete = { confirmingDeletePage = true },
            )

            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                OutlinedButton(
                    onClick = { viewModel.setCurrentPage(state.currentPageIndex - 1) },
                    enabled = state.currentPageIndex > 0,
                ) { Text("Back") }

                Button(
                    onClick = {
                        if (state.currentPageIndex < state.pages.size - 1) {
                            viewModel.setCurrentPage(state.currentPageIndex + 1)
                        } else {
                            onDone()
                        }
                    },
                    modifier = Modifier.testTag("scan_edit_next"),
                ) { Text(if (state.currentPageIndex < state.pages.size - 1) "Next" else "Review") }
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
    if (confirmingDeletePage) {
        AlertDialog(
            onDismissRequest = { confirmingDeletePage = false },
            title = { Text("Delete this page?") },
            confirmButton = {
                Button(onClick = { confirmingDeletePage = false; viewModel.deletePage(page.id) }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmingDeletePage = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun EditControls(
    edit: PageEdit,
    onEditChange: (PageEdit) -> Unit,
    onRetake: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                onEditChange(edit.copy(rotationDegrees = PageTransforms.normalizeRotation(edit.rotationDegrees + 90)))
            }) {
                Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Rotate")
            }
            FilterChip(
                selected = edit.isBlackAndWhite,
                onClick = { onEditChange(edit.copy(isBlackAndWhite = !edit.isBlackAndWhite)) },
                leadingIcon = { Icon(Icons.Filled.FlashOn, contentDescription = null) },
                label = { Text("B&W") },
                modifier = Modifier.testTag("scan_bw_toggle"),
            )
            OutlinedButton(onClick = onRetake) { Text("Retake") }
            OutlinedButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            }
        }

        Spacer(Modifier.height(4.dp))
        Text("Brightness", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = edit.brightness,
            onValueChange = { onEditChange(edit.copy(brightness = PageTransforms.clampBrightness(it))) },
            valueRange = -100f..100f,
            modifier = Modifier.testTag("scan_brightness_slider"),
        )

        Text("Contrast", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = edit.contrast,
            onValueChange = { onEditChange(edit.copy(contrast = PageTransforms.clampContrast(it))) },
            valueRange = 0.5f..2f,
            modifier = Modifier.testTag("scan_contrast_slider"),
        )
    }
}
