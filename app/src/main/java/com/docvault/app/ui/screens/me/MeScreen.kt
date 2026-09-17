package com.docvault.app.ui.screens.me

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.docvault.app.data.ApiResult
import com.docvault.app.data.DocVaultRepository
import com.docvault.app.data.net.AccountResponse
import com.docvault.app.data.net.QuotaResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val MeScreenTestTag = "me_screen"

data class MeUiState(
    val account: AccountResponse? = null,
    val quota: QuotaResponse? = null,
    val serverUrl: String = "",
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

class MeViewModel(private val repository: DocVaultRepository) : ViewModel() {

    private val _state = MutableStateFlow(MeUiState(serverUrl = repository.baseUrl))
    val state: StateFlow<MeUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** [fromPull] drives the pull-to-refresh spinner instead of the full-screen one. */
    fun refresh(fromPull: Boolean = false) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = !fromPull && it.account == null,
                    isRefreshing = fromPull,
                )
            }

            when (val me = repository.me()) {
                is ApiResult.Ok -> _state.update { it.copy(account = me.value, error = null) }
                is ApiResult.Err ->
                    _state.update {
                        it.copy(isLoading = false, isRefreshing = false, error = me.message)
                    }
            }

            // Quota changes as documents are uploaded, so it is worth re-reading
            // rather than caching for the life of the session.
            when (val quota = repository.quota()) {
                is ApiResult.Ok ->
                    _state.update {
                        it.copy(quota = quota.value, isLoading = false, isRefreshing = false)
                    }
                is ApiResult.Err ->
                    _state.update {
                        it.copy(isLoading = false, isRefreshing = false, error = quota.message)
                    }
            }
        }
    }

    fun signOut() = repository.signOut()
}

/** Me tab — profile, upload allowance, sign out (prototype screen 20). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeScreen(
    viewModel: MeViewModel,
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.testTag(MeScreenTestTag),
        topBar = { TopAppBar(title = { Text("Me") }) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh(fromPull = true) },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            if (state.isLoading && state.account == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        // verticalScroll is required for the pull gesture to
                        // have anything to attach to on a short screen.
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    state.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(16.dp))
                    }

                    state.account?.let { account ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    account.displayName,
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    account.phone,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    state.quota?.let { quota ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        "Upload allowance",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text("${quota.filesUsedToday}/${quota.capFiles} today")
                                }
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = {
                                        if (quota.capFiles == 0) 0f
                                        else quota.filesUsedToday.toFloat() / quota.capFiles
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Server", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                state.serverUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    OutlinedButton(
                        onClick = { viewModel.signOut(); onSignedOut() },
                        modifier = Modifier.fillMaxWidth().testTag("sign_out"),
                    ) { Text("Sign out") }
                }
            }
        }
    }
}
