package com.docvault.app.ui.screens.documents

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docvault.app.data.ApiResult
import com.docvault.app.data.DocVaultRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PreviewUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val name: String = "",
    val mimeType: String = "",
    /** Set once the file loads and the mime type is a plain image. */
    val imageFile: File? = null,
    val pdfPageCount: Int = 0,
    val pdfCurrentPage: Int = 0,
    val pdfBitmap: Bitmap? = null,
)

/**
 * Loads a document's bytes for in-app viewing (never a saved copy) and
 * renders them — a plain image is shown directly; a PDF is rendered page by
 * page with [android.graphics.pdf.PdfRenderer], the platform API, so no new
 * dependency is needed for something ML Kit/Coil don't already cover.
 *
 * Bytes land in an app-private cache file (never a user-chosen Uri, unlike
 * [com.docvault.app.data.DocVaultRepository.downloadDocument]) and are
 * deleted in [onCleared] — this is a view, not a copy.
 *
 * Screenshots are already blocked for this screen like every other one —
 * see `SecureScreen()` in `DocVaultNavHost`, applied app-wide rather than
 * per-route.
 */
class PreviewViewModel(
    private val repository: DocVaultRepository,
    private val cacheDir: File,
    private val documentId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(PreviewUiState())
    val state: StateFlow<PreviewUiState> = _state.asStateFlow()

    private var pdfRenderer: PdfRenderer? = null
    private var pdfFd: ParcelFileDescriptor? = null
    private var cacheFile: File? = null

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            val detail = when (val result = repository.documentDetail(documentId)) {
                is ApiResult.Ok -> result.value
                is ApiResult.Err -> {
                    _state.update { it.copy(isLoading = false, error = result.message) }
                    return@launch
                }
            }

            val file = File(cacheDir, "preview_$documentId").also { cacheFile = it }
            when (val result = repository.previewDocument(documentId, file)) {
                is ApiResult.Err -> _state.update { it.copy(isLoading = false, error = result.message) }
                is ApiResult.Ok -> {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            name = detail.name,
                            mimeType = detail.mimeType,
                            imageFile = file.takeIf { detail.mimeType.startsWith("image/") },
                        )
                    }
                    if (detail.mimeType == "application/pdf") openPdf(file)
                }
            }
        }
    }

    private fun openPdf(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fd)
                pdfFd = fd
                pdfRenderer = renderer
                _state.update { it.copy(pdfPageCount = renderer.pageCount) }
                renderPage(0)
            }.onFailure { e ->
                _state.update { it.copy(error = e.message ?: "Couldn't open this PDF") }
            }
        }
    }

    fun goToPage(index: Int) {
        if (index < 0 || index >= _state.value.pdfPageCount) return
        viewModelScope.launch(Dispatchers.IO) { renderPage(index) }
    }

    private fun renderPage(index: Int) {
        val renderer = pdfRenderer ?: return
        val page = renderer.openPage(index)
        // 2x the PDF's own point size gives a reasonably crisp render without
        // the memory cost of an arbitrarily high-resolution bitmap.
        val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        _state.update { it.copy(pdfCurrentPage = index, pdfBitmap = bitmap) }
    }

    override fun onCleared() {
        super.onCleared()
        pdfRenderer?.close()
        runCatching { pdfFd?.close() }
        cacheFile?.delete()
    }
}
