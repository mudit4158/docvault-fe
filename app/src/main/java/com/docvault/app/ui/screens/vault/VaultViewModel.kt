package com.docvault.app.ui.screens.vault

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docvault.app.data.ApiResult
import com.docvault.app.data.DocVaultRepository
import com.docvault.app.data.FileRules
import com.docvault.app.data.PickedFile
import com.docvault.app.data.describe
import com.docvault.app.data.net.DocumentSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** One file in the upload queue (prototype screen 11). */
data class UploadItem(
    val key: String,
    val file: PickedFile,
    val progress: Float = 0f,
    val error: String? = null,
    /** False for problems a retry cannot fix: wrong type, too big. */
    val canRetry: Boolean = true,
) {
    val isFailed: Boolean get() = error != null
}

data class VaultUiState(
    val documents: List<DocumentSummary> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val hasNext: Boolean = false,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val query: String = "",
    val docType: String? = null,
    val uploads: List<UploadItem> = emptyList(),
    /** Set when today's upload allowance is used up (screen 12). */
    val quotaMessage: String? = null,
    val error: String? = null,
    val message: String? = null,
) {
    val isFiltered: Boolean get() = query.isNotBlank() || docType != null
}

class VaultViewModel(
    private val repository: DocVaultRepository,
    private val resolver: ContentResolver,
) : ViewModel() {

    private val _state = MutableStateFlow(VaultUiState())
    val state: StateFlow<VaultUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    // Uploads run one at a time. Parallel uploads would race each other for
    // the last unit of daily quota and make the resulting errors confusing.
    private val uploadLock = Mutex()

    /** Reload page one. [fromPull] shows the pull spinner instead of a full-screen one. */
    fun refresh(fromPull: Boolean = false) {
        viewModelScope.launch { load(page = 1, fromPull = fromPull) }
    }

    fun loadMore() {
        val current = _state.value
        if (!current.hasNext || current.isLoadingMore) return
        viewModelScope.launch { load(page = current.page + 1, fromPull = false) }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            load(page = 1, fromPull = false)
        }
    }

    fun onDocTypeSelected(docType: String?) {
        _state.update { it.copy(docType = docType) }
        refresh()
    }

    fun clearFilters() {
        _state.update { it.copy(query = "", docType = null) }
        refresh()
    }

    private suspend fun load(page: Int, fromPull: Boolean) {
        val appending = page > 1
        _state.update {
            it.copy(
                isLoading = !fromPull && !appending && it.documents.isEmpty(),
                isRefreshing = fromPull,
                isLoadingMore = appending,
            )
        }
        val current = _state.value
        when (val result = repository.listDocuments(current.query, current.docType, page)) {
            is ApiResult.Ok -> _state.update {
                it.copy(
                    documents = if (appending) it.documents + result.value.items else result.value.items,
                    total = result.value.total,
                    page = page,
                    hasNext = result.value.hasNext,
                    isLoading = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                    error = null,
                )
            }
            is ApiResult.Err -> _state.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                    error = result.message,
                )
            }
        }
    }

    // --- uploads -----------------------------------------------------------

    fun upload(uris: List<Uri>) {
        for (uri in uris) {
            val file = resolver.describe(uri)
            val problem = FileRules.rejectionReason(file.name, file.size)
            val item = UploadItem(
                key = UUID.randomUUID().toString(),
                file = file,
                error = problem,
                canRetry = problem == null,
            )
            _state.update { it.copy(uploads = it.uploads + item) }
            if (problem == null) start(item.key)
        }
    }

    fun retry(key: String) {
        updateItem(key) { it.copy(error = null, progress = 0f) }
        start(key)
    }

    fun dismissUpload(key: String) = _state.update {
        it.copy(uploads = it.uploads.filterNot { item -> item.key == key })
    }

    private fun start(key: String) {
        viewModelScope.launch {
            uploadLock.withLock {
                val item = _state.value.uploads.firstOrNull { it.key == key } ?: return@withLock
                var lastReported = 0f

                val result = repository.uploadDocument(resolver, item.file) { sent, total ->
                    if (total > 0) {
                        val fraction = (sent.toFloat() / total).coerceIn(0f, 1f)
                        // Every chunk would flood recomposition; 2% steps are smooth enough.
                        if (fraction - lastReported >= 0.02f || fraction == 1f) {
                            lastReported = fraction
                            updateItem(key) { it.copy(progress = fraction) }
                        }
                    }
                }

                when (result) {
                    is ApiResult.Ok -> {
                        _state.update {
                            it.copy(
                                uploads = it.uploads.filterNot { u -> u.key == key },
                                message = "Uploaded ${result.value.name}",
                                quotaMessage = null,
                            )
                        }
                        load(page = 1, fromPull = false)
                    }
                    is ApiResult.Err -> {
                        // The row stays in place with its error (screen 11) — never a silent drop.
                        updateItem(key) {
                            it.copy(
                                error = result.message,
                                canRetry = result.status !in NON_RETRYABLE,
                            )
                        }
                        if (result.status == 429) {
                            _state.update { it.copy(quotaMessage = result.message) }
                        }
                    }
                }
            }
        }
    }

    private fun updateItem(key: String, change: (UploadItem) -> UploadItem) = _state.update {
        it.copy(uploads = it.uploads.map { item -> if (item.key == key) change(item) else item })
    }

    fun clearMessage() = _state.update { it.copy(message = null, error = null) }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L

        /** Too large / unsupported: retrying the same file cannot succeed. */
        val NON_RETRYABLE = setOf(413, 415, 422)
    }
}
