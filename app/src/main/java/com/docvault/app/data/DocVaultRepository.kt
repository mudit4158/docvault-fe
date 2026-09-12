package com.docvault.app.data

import com.docvault.app.data.net.AccountResponse
import com.docvault.app.data.net.AccountSummary
import com.docvault.app.data.net.ApiProvider
import com.docvault.app.data.net.ChangePasswordRequest
import com.docvault.app.data.net.LookupRequest
import com.docvault.app.data.net.CreateGroupRequest
import com.docvault.app.data.net.GroupResponse
import com.docvault.app.data.net.InvitationResponse
import com.docvault.app.data.net.InviteRequest
import com.docvault.app.data.net.LoginRequest
import com.docvault.app.data.net.MemberResponse
import com.docvault.app.data.net.QuotaResponse
import com.docvault.app.data.net.RegisterRequest
import com.docvault.app.data.net.TransferAdminRequest
import kotlinx.coroutines.flow.StateFlow

/**
 * Single entry point to the backend for the UI layer.
 *
 * ViewModels talk to this, never to Retrofit directly, so swapping transport
 * or adding caching later touches one file.
 */
class DocVaultRepository(
    private val apiProvider: ApiProvider,
    private val tokenStore: TokenStore,
) {

    val isSignedIn: Boolean get() = tokenStore.isSignedIn

    /** Emits true when the server rejects the stored token. See [TokenStore]. */
    val sessionExpired: StateFlow<Boolean> get() = tokenStore.sessionExpired

    fun acknowledgeSessionExpired() = tokenStore.acknowledgeSessionExpired()

    var baseUrl: String
        get() = tokenStore.baseUrl
        set(value) {
            tokenStore.baseUrl = value
        }

    // --- auth ---------------------------------------------------------

    suspend fun register(
        phone: String,
        displayName: String,
        password: String,
    ): ApiResult<AccountResponse> =
        apiCall { apiProvider.api().register(RegisterRequest(phone, displayName, password)) }

    /** On success the token is persisted, so later calls are authenticated. */
    suspend fun login(phone: String, password: String): ApiResult<Unit> =
        when (val result = apiCall { apiProvider.api().login(LoginRequest(phone, password)) }) {
            is ApiResult.Ok -> {
                tokenStore.token = result.value.accessToken
                ApiResult.Ok(Unit)
            }
            is ApiResult.Err -> result
        }

    fun signOut() = tokenStore.signOut()

    suspend fun me(): ApiResult<AccountResponse> = apiCall { apiProvider.api().me() }

    suspend fun quota(): ApiResult<QuotaResponse> = apiCall { apiProvider.api().quota() }

    suspend fun changePassword(current: String, new: String): ApiResult<Unit> =
        apiCall { apiProvider.api().changePassword(ChangePasswordRequest(current, new)) }

    /** Resolve a phone number to an account. Err(404) when not registered. */
    suspend fun lookupAccount(phone: String): ApiResult<AccountSummary> =
        apiCall { apiProvider.api().lookupAccount(LookupRequest(phone)) }

    // --- groups -------------------------------------------------------

    suspend fun listGroups(): ApiResult<List<GroupResponse>> =
        apiCall { apiProvider.api().listGroups() }

    suspend fun createGroup(name: String, description: String?): ApiResult<GroupResponse> =
        apiCall {
            apiProvider.api().createGroup(
                CreateGroupRequest(name, description?.takeIf { it.isNotBlank() })
            )
        }

    suspend fun getGroup(groupId: String): ApiResult<GroupResponse> =
        apiCall { apiProvider.api().getGroup(groupId) }

    suspend fun deleteGroup(groupId: String): ApiResult<Unit> =
        apiCall { apiProvider.api().deleteGroup(groupId) }

    // --- members ------------------------------------------------------

    suspend fun listMembers(groupId: String): ApiResult<List<MemberResponse>> =
        apiCall { apiProvider.api().listMembers(groupId) }

    suspend fun removeMember(groupId: String, memberId: String): ApiResult<Unit> =
        apiCall { apiProvider.api().removeMember(groupId, memberId) }

    suspend fun leaveGroup(groupId: String): ApiResult<Unit> =
        apiCall { apiProvider.api().leaveGroup(groupId) }

    suspend fun transferAdmin(groupId: String, newAdminId: String): ApiResult<Unit> =
        apiCall { apiProvider.api().transferAdmin(groupId, TransferAdminRequest(newAdminId)) }

    // --- invitations --------------------------------------------------

    suspend fun invite(groupId: String, phone: String): ApiResult<InvitationResponse> =
        apiCall { apiProvider.api().invite(groupId, InviteRequest(phone)) }

    suspend fun myInvitations(): ApiResult<List<InvitationResponse>> =
        apiCall { apiProvider.api().myInvitations() }

    suspend fun acceptInvitation(invitationId: String): ApiResult<Unit> =
        apiCall { apiProvider.api().acceptInvitation(invitationId) }

    suspend fun declineInvitation(invitationId: String): ApiResult<Unit> =
        apiCall { apiProvider.api().declineInvitation(invitationId) }
}
