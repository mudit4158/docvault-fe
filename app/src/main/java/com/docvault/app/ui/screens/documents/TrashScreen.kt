package com.docvault.app.ui.screens.documents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.docvault.app.data.ApiResult
import com.docvault.app.data.DocTypes
import com.docvault.app.data.DocVaultRepository
import com.docvault.app.data.Format
import com.docvault.app.data.net.TrashItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrashUiState(
    val items: List<TrashItem> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val busyId: String? = null,
    val error: String? = null,
    val message: String? = null,
)

class TrashViewModel(private val repository: DocVaultRepository) : ViewModel() {
    private val _state = MutableStateFlow(TrashUiState())
    val state: StateFlow<TrashUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh(fromPull: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = !fromPull && it.items.isEmpty(), isRefreshing = fromPull) }
            when (val result = repository.trash()) {
                is ApiResult.Ok -> _state.update {
                    it.copy(items = result.value, isLoading = false, isRefreshing = false, error = null)
                }
                is ApiResult.Err -> _state.update {
                    it.copy(isLoading = false, isRefreshing = false, error = result.message)
                }
            }
        }
    }

    fun restore(item: TrashItem) {
        viewModelScope.launch {
            _state.update { it.copy(busyId = item.id) }
            when (val result = repository.restoreDocument(item.id)) {
                is ApiResult.Ok -> _state.update {
                    it.copy(
                        busyId = null,
                        items = it.items.filterNot { t -> t.id == item.id },
                        message = "Restored ${item.name}. Share it again if needed.",
                    )
                }
                is ApiResult.Err -> _state.update { it.copy(busyId = null, error = result.message) }
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null, error = null) }
}

/** Trash — soft-deleted documents, restorable until they are purged. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(viewModel: TrashViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message, state.error) {
        val text = state.message ?: state.error
        if (text != null) {
            snackbarHostState.showSnackbar(text)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        modifier = modifier.testTag("trash_screen"),
        topBar = {
            TopAppBar(
                title = { Text("Trash") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        "Deleted documents are removed permanently 10 days after deletion.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when {
                    state.isLoading -> item {
                        Row(Modifier.fillMaxWidth().padding(32.dp), horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    state.items.isEmpty() -> item {
                        Text(
                            "Trash is empty.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                    else -> items(state.items, key = { it.id }) { item ->
                        Card(Modifier.fillMaxWidth().testTag("trash_${item.id}")) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${DocTypes.label(item.docType)} · ${Format.bytes(item.sizeBytes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        if (item.restorable) {
                                            "Deletes permanently on ${Format.date(item.purgeAt)}"
                                        } else {
                                            "Being removed permanently"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (item.restorable) {
                                    TextButton(
                                        onClick = { viewModel.restore(item) },
                                        enabled = state.busyId == null,
                                    ) { Text("Restore") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
