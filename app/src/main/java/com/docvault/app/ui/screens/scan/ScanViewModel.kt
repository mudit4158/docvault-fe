package com.docvault.app.ui.screens.scan

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docvault.app.data.ApiResult
import com.docvault.app.data.DocVaultRepository
import com.docvault.app.data.FileRules
import com.docvault.app.data.describe
import com.docvault.app.ui.screens.scan.data.PageEdit
import com.docvault.app.ui.screens.scan.data.PageTransforms
import com.docvault.app.ui.screens.scan.data.PdfAssembler
import com.docvault.app.ui.screens.scan.data.ScanCacheStore
import com.docvault.app.ui.screens.scan.data.ScanSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** One captured/picked page in the session. */
data class ScanPage(
    val id: String = UUID.randomUUID().toString(),
    /** Untouched, downscaled-to-working-resolution copy — never re-derived from, only rendered from. */
    val originalFile: File,
    /** Current render — equal to [originalFile] until the page's first edit. */
    val workingFile: File,
    val edit: PageEdit = PageEdit(),
)

enum class SaveFormat { PDF, IMAGE }

data class ScanUiState(
    val sessionId: String,
    val pages: List<ScanPage> = emptyList(),
    val currentPageIndex: Int = 0,
    /** Non-null while ML Kit is being re-invoked to replace one specific page. */
    val retakingPageId: String? = null,
    /** Only meaningful when there's exactly one page; multi-page is always PDF. */
    val saveFormat: SaveFormat? = null,
    val isProcessing: Boolean = false,
    val isSaving: Boolean = false,
    val uploadProgress: Float? = null,
    val error: String? = null,
    val message: String? = null,
    val saved: Boolean = false,
) {
    val currentPage: ScanPage? get() = pages.getOrNull(currentPageIndex)
    val remainingPageSlots: Int get() = (ScanSpec.MAX_PAGES - pages.size).coerceAtLeast(0)
    val needsFormatChoice: Boolean get() = ScanPageOps.needsFormatChoice(pages.size, saveFormat)
}

/**
 * Holds one scan session end to end: capture -> per-page edit -> review/
 * reorder -> (format choice) -> save.
 *
 * A page's [ScanPage.originalFile] is never mutated — every edit re-renders
 * from it into a fresh [ScanPage.workingFile], so "adjust again" is a clean
 * re-render rather than a compounding transform (see [PageTransforms]).
 * Nothing here ever holds a live `Bitmap` in state — only [File] paths —
 * which is the load-bearing choice for staying within memory on low-end
 * devices while a multi-page session is open.
 */
class ScanViewModel(
    private val repository: DocVaultRepository,
    private val resolver: ContentResolver,
    private val cacheStore: ScanCacheStore,
) : ViewModel() {

    private val sessionId = cacheStore.newSession()
    private val _state = MutableStateFlow(ScanUiState(sessionId = sessionId))
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    // --- capture --------------------------------------------------------

    /** Copies freshly captured/picked pages into the session cache, downscaled to the working resolution. */
    fun addCaptured(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true) }
            val imported = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri -> runCatching { importPage(uri) }.getOrNull() }
            }
            _state.update {
                it.copy(
                    pages = it.pages + imported,
                    currentPageIndex = it.pages.size,
                    isProcessing = false,
                    error = if (imported.size < uris.size) "Couldn't process one of the pages." else null,
                )
            }
        }
    }

    fun beginRetake(pageId: String) {
        _state.update { it.copy(retakingPageId = pageId) }
    }

    fun cancelRetake() {
        _state.update { it.copy(retakingPageId = null) }
    }

    fun completeRetake(uri: Uri?) {
        val pageId = _state.value.retakingPageId
        _state.update { it.copy(retakingPageId = null) }
        if (pageId == null || uri == null) return
        viewModelScope.launch {
            val replacement = withContext(Dispatchers.IO) { runCatching { importPage(uri) }.getOrNull() }
            if (replacement == null) {
                _state.update { it.copy(error = "Couldn't process the retaken page.") }
                return@launch
            }
            _state.update { state ->
                val old = state.pages.firstOrNull { it.id == pageId }
                old?.deleteFiles()
                state.copy(pages = state.pages.map { if (it.id == pageId) replacement.copy(id = pageId) else it })
            }
        }
    }

    /** Reads [uri], downscales it to [ScanSpec.TARGET_LONGEST_SIDE_PX], stores it as a fresh page's original. */
    private fun importPage(uri: Uri): ScanPage {
        val temp = cacheStore.newFile(sessionId, ".src.jpg")
        resolver.openInputStream(uri)?.use { input -> temp.outputStream().use { input.copyTo(it) } }
            ?: error("Couldn't read the page")

        val original = cacheStore.newFile(sessionId)
        val decoded = PageTransforms.decodeDownsampled(temp, ScanSpec.TARGET_LONGEST_SIDE_PX)
        try {
            PageTransforms.apply(decoded, PageEdit(), original, ScanSpec.WORKING_JPEG_QUALITY)
        } finally {
            decoded.recycle()
        }
        temp.delete()
        return ScanPage(originalFile = original, workingFile = original)
    }

    // --- per-page edit ----------------------------------------------------

    fun setCurrentPage(index: Int) {
        _state.update { it.copy(currentPageIndex = index.coerceIn(0, (it.pages.size - 1).coerceAtLeast(0))) }
    }

    /** Re-renders [pageId] from its untouched original with [edit] applied. */
    fun updateEdit(pageId: String, edit: PageEdit) {
        val page = _state.value.pages.firstOrNull { it.id == pageId } ?: return
        viewModelScope.launch {
            val rendered = withContext(Dispatchers.IO) {
                val destination = cacheStore.newFile(sessionId)
                val bitmap = BitmapFactory.decodeFile(page.originalFile.absolutePath)
                    ?: return@withContext null
                try {
                    PageTransforms.apply(bitmap, edit, destination, ScanSpec.WORKING_JPEG_QUALITY)
                } finally {
                    bitmap.recycle()
                }
                destination
            } ?: return@launch

            val previousWorking = page.workingFile
            _state.update { state ->
                state.copy(
                    pages = state.pages.map {
                        if (it.id == pageId) it.copy(workingFile = rendered, edit = edit) else it
                    },
                )
            }
            if (previousWorking != page.originalFile && previousWorking.exists()) previousWorking.delete()
        }
    }

    fun deletePage(pageId: String) {
        _state.update { state ->
            state.pages.firstOrNull { it.id == pageId }?.deleteFiles()
            val remaining = ScanPageOps.remove(state.pages, pageId)
            state.copy(
                pages = remaining,
                currentPageIndex = ScanPageOps.clampIndex(state.currentPageIndex, remaining.size),
                saveFormat = if (remaining.size == 1) state.saveFormat else null,
            )
        }
    }

    fun movePage(fromIndex: Int, toIndex: Int) {
        _state.update { state -> state.copy(pages = ScanPageOps.move(state.pages, fromIndex, toIndex)) }
    }

    // --- format + save ------------------------------------------------------

    fun chooseFormat(format: SaveFormat) {
        _state.update { it.copy(saveFormat = format) }
    }

    fun save(docType: String = "other") {
        val current = _state.value
        if (current.pages.isEmpty() || current.isSaving) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null, uploadProgress = 0f) }

            val exportFile = withContext(Dispatchers.IO) { runCatching { buildExport(current) }.getOrNull() }
            if (exportFile == null) {
                _state.update { it.copy(isSaving = false, uploadProgress = null, error = "Couldn't prepare the file to save.") }
                return@launch
            }

            val rejection = FileRules.rejectionReason(exportFile.name, exportFile.length())
            if (rejection != null) {
                _state.update { it.copy(isSaving = false, uploadProgress = null, error = rejection) }
                return@launch
            }

            val pickedFile = resolver.describe(cacheStore.uriFor(exportFile))
            val result = repository.uploadDocument(resolver, pickedFile, docType) { sent, total ->
                if (total > 0) {
                    _state.update { it.copy(uploadProgress = (sent.toFloat() / total).coerceIn(0f, 1f)) }
                }
            }

            when (result) {
                is ApiResult.Ok -> {
                    cacheStore.delete(sessionId)
                    _state.update {
                        it.copy(isSaving = false, saved = true, message = "Saved ${result.value.name}")
                    }
                }
                is ApiResult.Err -> {
                    // Cache is kept — retrying the save doesn't require redoing any editing.
                    _state.update { it.copy(isSaving = false, uploadProgress = null, error = result.message) }
                }
            }
        }
    }

    private fun buildExport(state: ScanUiState): File {
        val single = state.pages.size == 1
        return if (single && state.saveFormat == SaveFormat.IMAGE) {
            val destination = cacheStore.exportFile(sessionId, "jpg")
            state.pages.first().workingFile.copyTo(destination, overwrite = true)
            destination
        } else {
            val destination = cacheStore.exportFile(sessionId, "pdf")
            PdfAssembler.assemble(state.pages.map { it.workingFile }, destination)
            destination
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null, error = null) }

    /** Discards every file this session ever produced. Safe to call even after a successful save. */
    fun abandon() = cacheStore.delete(sessionId)

    private fun ScanPage.deleteFiles() {
        if (workingFile != originalFile) workingFile.delete()
        originalFile.delete()
    }
}
