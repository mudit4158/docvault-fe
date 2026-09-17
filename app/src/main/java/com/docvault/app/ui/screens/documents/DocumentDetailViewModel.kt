package com.docvault.app.ui.screens.documents

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docvault.app.data.ApiResult
import com.docvault.app.data.DocVaultRepository
import com.docvault.app.data.net.DocumentDetail
import com.docvault.app.data.net.GroupResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DocumentDetailUiState(
    val detail: DocumentDetail? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isBusy: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    /** The document is gone for this user — trashed, or access revoked. */
    val unavailable: String? = null,
    /** Groups the owner belongs to, for the share sheet. */
    val groups: List<GroupResponse> = emptyList(),
    val tagSuggestions: List<String> = emptyList(),
    /** Set after a successful move to trash, to offer Undo. */
    val deleted: Boolean = false,
    /** Set after a successful download, to offer Open. */
    val savedTo: Uri? = null,
)

class DocumentDetailViewModel(
    private val repository: DocVaultRepository,
    private val resolver: ContentResolver,
    private val documentId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(DocumentDetailUiState())
    val state: StateFlow<DocumentDetailUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh(fromPull: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = !fromPull && it.detail == null, isRefreshing = fromPull) }
            when (val result = repository.documentDetail(documentId)) {
                is ApiResult.Ok ->
                    _state.update {
                        it.copy(detail = result.value, isLoading = false, isRefreshing = false, error = null)
                    }
                is ApiResult.Err ->
                    _state.update {
                        if (result.status == 404) {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                unavailable = "This document is no longer available to you.",
                            )
                        } else {
                            it.copy(isLoading = false, isRefreshing = false, error = result.message)
                        }
                    }
            }
        }
    }

    /** Groups and tag suggestions — fetched when a sheet opens, not up front. */
    fun loadOwnerOptions() {
        viewModelScope.launch {
            (repository.listGroups() as? ApiResult.Ok)?.let { groups ->
                _state.update { it.copy(groups = groups.value) }
            }
            (repository.tagSuggestions() as? ApiResult.Ok)?.let { tags ->
                _state.update { it.copy(tagSuggestions = tags.value) }
            }
        }
    }

    fun rename(name: String) = act("Renamed") {
        repository.renameDocument(documentId, name).also { r ->
            if (r is ApiResult.Ok) _state.update { it.copy(detail = r.value) }
        }
    }

    fun changeType(docType: String) = act("Type updated") {
        repository.changeDocumentType(documentId, docType).also { r ->
            if (r is ApiResult.Ok) _state.update { it.copy(detail = r.value) }
        }
    }

    fun addTag(label: String) {
        if (label.isBlank()) return
        act(null) {
            repository.addTag(documentId, label).also { r ->
                if (r is ApiResult.Ok) {
                    _state.update { it.copy(detail = it.detail?.copy(tags = r.value)) }
                }
            }
        }
    }

    fun removeTag(tagId: String) = act(null) {
        repository.removeTag(documentId, tagId).also { r ->
            if (r is ApiResult.Ok) {
                _state.update { it.copy(detail = it.detail?.copy(tags = r.value)) }
            }
        }
    }

    /**
     * Set this document's access for one group. [permission] null = stop sharing.
     *
     * Re-sharing with a different permission updates the existing grant on the
     * server rather than creating a second one.
     */
    fun setShare(groupId: String, permission: String?) {
        val existing = _state.value.detail?.shares?.firstOrNull { it.groupId == groupId }
        act(if (permission == null) "Stopped sharing" else "Shared") {
            val result = if (permission == null) {
                if (existing == null) ApiResult.Ok(Unit) else repository.revokeShare(documentId, existing.id)
            } else {
                repository.shareDocument(documentId, groupId, permission)
            }
            if (result is ApiResult.Ok) refreshDetailQuietly()
            result
        }
    }

    fun download(destination: Uri) = act(null) {
        repository.downloadDocument(documentId, resolver, destination).also { r ->
            if (r is ApiResult.Ok) _state.update { it.copy(savedTo = destination) }
        }
    }

    fun consumeSaved() = _state.update { it.copy(savedTo = null) }

    fun delete() = act(null) {
        repository.deleteDocument(documentId).also { r ->
            if (r is ApiResult.Ok) _state.update { it.copy(deleted = true) }
        }
    }

    /**
     * Undo straight after deleting.
     *
     * The delete has already happened on the server; undo is a restore. That
     * is more robust than delaying the delete until the snackbar closes — an
     * app killed mid-countdown cannot lose or half-apply the action — and the
     * access log records both events truthfully. Shares revoked by the delete
     * stay revoked (decision D2).
     */
    fun undoDelete() {
        viewModelScope.launch {
            when (val result = repository.restoreDocument(documentId)) {
                is ApiResult.Ok -> {
                    _state.update { it.copy(deleted = false, message = "Restored") }
                    refreshDetailQuietly()
                }
                is ApiResult.Err -> _state.update { it.copy(error = result.message) }
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null, error = null) }

    private suspend fun refreshDetailQuietly() {
        (repository.documentDetail(documentId) as? ApiResult.Ok)?.let { r ->
            _state.update { it.copy(detail = r.value) }
        }
    }

    private fun act(successMessage: String?, block: suspend () -> ApiResult<*>) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, error = null) }
            when (val result = block()) {
                is ApiResult.Ok -> _state.update { it.copy(isBusy = false, message = successMessage) }
                is ApiResult.Err -> _state.update { it.copy(isBusy = false, error = result.message) }
            }
        }
    }
}
