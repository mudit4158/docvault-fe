package com.docvault.app.ui.screens.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docvault.app.data.ApiResult
import com.docvault.app.data.DocVaultRepository
import com.docvault.app.data.net.GroupResponse
import com.docvault.app.data.net.InvitationResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GroupsUiState(
    val groups: List<GroupResponse> = emptyList(),
    val invitations: List<InvitationResponse> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val showCreateDialog: Boolean = false,
    val isSubmitting: Boolean = false,
) {
    /** Awaiting a decision — expanded by default, and drives the tab badge. */
    val pendingInvitations: List<InvitationResponse>
        get() = invitations.filter { it.status == "pending" }

    /** Already turned down — collapsed by default, kept so a mistake is recoverable. */
    val declinedInvitations: List<InvitationResponse>
        get() = invitations.filter { it.status == "declined" }
}

class GroupsViewModel(private val repository: DocVaultRepository) : ViewModel() {

    private val _state = MutableStateFlow(GroupsUiState())
    val state: StateFlow<GroupsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /**
     * Reload groups and invitations.
     *
     * Called on first composition, on every return to this tab, and on pull —
     * group membership changes elsewhere (leaving from the detail screen,
     * being removed by an admin), so this list goes stale if it is only
     * fetched once.
     *
     * [fromPull] drives the pull-to-refresh spinner instead of the
     * full-screen one, so a manual refresh does not blank the list.
     */
    fun refresh(fromPull: Boolean = false) {
        viewModelScope.launch {
            _state.update {
                it.copy(isLoading = !fromPull && it.groups.isEmpty(), isRefreshing = fromPull)
            }

            when (val groups = repository.listGroups()) {
                is ApiResult.Ok -> _state.update { it.copy(groups = groups.value, error = null) }
                is ApiResult.Err ->
                    _state.update {
                        it.copy(isLoading = false, isRefreshing = false, error = groups.message)
                    }
            }

            // Pending ones drive the badge on this tab (handoff §3.4); declined
            // ones populate the collapsed section.
            when (val invites = repository.myInvitations()) {
                is ApiResult.Ok ->
                    _state.update {
                        it.copy(
                            invitations = invites.value,
                            isLoading = false,
                            isRefreshing = false,
                        )
                    }
                is ApiResult.Err ->
                    _state.update {
                        it.copy(isLoading = false, isRefreshing = false, error = invites.message)
                    }
            }
        }
    }

    fun showCreateDialog(show: Boolean) = _state.update { it.copy(showCreateDialog = show) }

    fun createGroup(name: String, description: String) {
        if (name.isBlank()) {
            _state.update { it.copy(error = "Give the group a name") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }
            when (val result = repository.createGroup(name.trim(), description.trim())) {
                is ApiResult.Ok -> {
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            showCreateDialog = false,
                            message = "Created \"${result.value.name}\"",
                        )
                    }
                    refresh()
                }
                is ApiResult.Err ->
                    _state.update { it.copy(isSubmitting = false, error = result.message) }
            }
        }
    }

    fun accept(invitation: InvitationResponse) = respond(invitation, accept = true)

    fun decline(invitation: InvitationResponse) = respond(invitation, accept = false)

    private fun respond(invitation: InvitationResponse, accept: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }
            val result = if (accept) {
                repository.acceptInvitation(invitation.id)
            } else {
                repository.declineInvitation(invitation.id)
            }
            when (result) {
                is ApiResult.Ok -> {
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            message = if (accept) {
                                "Joined ${invitation.groupName}"
                            } else {
                                "Declined ${invitation.groupName}"
                            },
                        )
                    }
                    refresh()
                }
                is ApiResult.Err ->
                    _state.update { it.copy(isSubmitting = false, error = result.message) }
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null, error = null) }
}
