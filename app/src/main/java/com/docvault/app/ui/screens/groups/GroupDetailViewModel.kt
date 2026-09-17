package com.docvault.app.ui.screens.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docvault.app.data.ApiResult
import com.docvault.app.data.DocVaultRepository
import com.docvault.app.data.net.AccountSummary
import com.docvault.app.data.net.GroupDocument
import com.docvault.app.data.net.GroupResponse
import com.docvault.app.data.net.MemberResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Wait for typing to settle before hitting the lookup endpoint. */
private const val LOOKUP_DEBOUNCE_MS = 400L

/**
 * Result of resolving a typed or picked phone number to a DocVault account.
 *
 * Exactly one of [resolved] / [error] is set once [isSearching] is false.
 */
data class LookupState(
    val isSearching: Boolean = false,
    val resolved: AccountSummary? = null,
    val error: String? = null,
) {
    val isIdle: Boolean get() = !isSearching && resolved == null && error == null
}

data class GroupDetailUiState(
    val group: GroupResponse? = null,
    val members: List<MemberResponse> = emptyList(),
    /** Documents shared into this group (the Documents tab, screen 18). */
    val documents: List<GroupDocument> = emptyList(),
    val myAccountId: String? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val showInviteDialog: Boolean = false,
    val lookup: LookupState = LookupState(),
    /** Set once the group is gone (left or deleted) so the UI can navigate back. */
    val closed: Boolean = false,
) {
    val isAdmin: Boolean get() = group?.isAdmin == true
}

class GroupDetailViewModel(
    private val repository: DocVaultRepository,
    private val groupId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(GroupDetailUiState())
    val state: StateFlow<GroupDetailUiState> = _state.asStateFlow()

    private var lookupJob: Job? = null

    init {
        refresh()
    }

    /** [fromPull] drives the pull-to-refresh spinner instead of the full-screen one. */
    fun refresh(fromPull: Boolean = false) {
        viewModelScope.launch {
            _state.update {
                it.copy(isLoading = !fromPull, isRefreshing = fromPull, error = null)
            }

            // Needed to tell "you" apart from other members in the roster, and
            // to suppress the remove action on your own row.
            (repository.me() as? ApiResult.Ok)?.let { me ->
                _state.update { it.copy(myAccountId = me.value.id) }
            }

            when (val group = repository.getGroup(groupId)) {
                is ApiResult.Ok -> _state.update { it.copy(group = group.value) }
                is ApiResult.Err -> {
                    _state.update {
                        it.copy(isLoading = false, isRefreshing = false, error = group.message)
                    }
                    return@launch
                }
            }

            (repository.groupDocuments(groupId) as? ApiResult.Ok)?.let { docs ->
                _state.update { it.copy(documents = docs.value) }
            }

            when (val members = repository.listMembers(groupId)) {
                is ApiResult.Ok ->
                    _state.update {
                        it.copy(
                            members = members.value,
                            isLoading = false,
                            isRefreshing = false,
                        )
                    }
                is ApiResult.Err ->
                    _state.update {
                        it.copy(isLoading = false, isRefreshing = false, error = members.message)
                    }
            }
        }
    }

    fun showInviteDialog(show: Boolean) = _state.update {
        it.copy(showInviteDialog = show, lookup = LookupState())
    }

    /**
     * Resolve a phone number to a person before anything is sent.
     *
     * An invitation goes to a real human, so the admin should see *who* they
     * are about to add rather than trusting a string of digits. A typo now
     * fails here, with a name-shaped answer, instead of silently inviting the
     * wrong person or 404-ing after the fact.
     */
    fun lookup(phone: String) {
        lookupJob?.cancel()
        _state.update { it.copy(lookup = LookupState(isSearching = true)) }

        lookupJob = viewModelScope.launch {
            // Settle before calling: the user is still typing digits.
            delay(LOOKUP_DEBOUNCE_MS)
            when (val result = repository.lookupAccount(phone)) {
                is ApiResult.Ok ->
                    _state.update { it.copy(lookup = LookupState(resolved = result.value)) }
                is ApiResult.Err ->
                    _state.update { it.copy(lookup = LookupState(error = result.message)) }
            }
        }
    }

    fun clearLookup() {
        lookupJob?.cancel()
        _state.update { it.copy(lookup = LookupState()) }
    }

    /** Only callable once [lookup] has resolved someone. */
    fun inviteResolved() {
        val resolved = _state.value.lookup.resolved ?: return

        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }
            when (val result = repository.invite(groupId, resolved.phone)) {
                is ApiResult.Ok -> {
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            showInviteDialog = false,
                            lookup = LookupState(),
                            message = "Invited ${resolved.displayName}",
                        )
                    }
                    refresh()
                }
                is ApiResult.Err ->
                    // Shown inside the dialog, next to the field it concerns —
                    // "already a member", "group is full" and so on.
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            lookup = it.lookup.copy(error = result.message),
                        )
                    }
            }
        }
    }

    fun removeMember(member: MemberResponse) = act("Removed ${member.account.displayName}") {
        repository.removeMember(groupId, member.account.id)
    }

    fun makeAdmin(member: MemberResponse) =
        act("${member.account.displayName} is now admin") {
            repository.transferAdmin(groupId, member.account.id)
        }

    fun rename(name: String, description: String?) {
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }
            when (val result = repository.renameGroup(groupId, name, description)) {
                is ApiResult.Ok ->
                    _state.update { it.copy(isSubmitting = false, group = result.value, message = "Renamed") }
                is ApiResult.Err -> _state.update { it.copy(isSubmitting = false, error = result.message) }
            }
        }
    }

    fun leaveGroup() = act("You left the group", closesGroup = true) {
        repository.leaveGroup(groupId)
    }

    fun deleteGroup() = act("Group deleted", closesGroup = true) {
        repository.deleteGroup(groupId)
    }

    private fun act(
        successMessage: String,
        closesGroup: Boolean = false,
        block: suspend () -> ApiResult<*>,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }
            when (val result = block()) {
                is ApiResult.Ok -> {
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            message = successMessage,
                            closed = closesGroup,
                        )
                    }
                    if (!closesGroup) refresh()
                }
                is ApiResult.Err ->
                    // Carries the backend's own wording — e.g. "Transfer admin
                    // rights to another member before leaving this group".
                    _state.update { it.copy(isSubmitting = false, error = result.message) }
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null, error = null) }
}
