package com.docvault.app.data

import android.content.ContentResolver
import android.net.Uri
import com.docvault.app.data.net.AccountResponse
import com.docvault.app.data.net.AccountSummary
import com.docvault.app.data.net.AddTagRequest
import com.docvault.app.data.net.ApiProvider
import com.docvault.app.data.net.ChangePasswordRequest
import com.docvault.app.data.net.CreateGroupRequest
import com.docvault.app.data.net.CreateShareRequest
import com.docvault.app.data.net.DocumentDetail
import com.docvault.app.data.net.DocumentPage
import com.docvault.app.data.net.DocumentSummary
import com.docvault.app.data.net.ForgotPasswordRequest
import com.docvault.app.data.net.GroupDocument
import com.docvault.app.data.net.GroupResponse
import com.docvault.app.data.net.InvitationResponse
import com.docvault.app.data.net.InviteRequest
import com.docvault.app.data.net.LoginRequest
import com.docvault.app.data.net.LookupRequest
import com.docvault.app.data.net.MemberResponse
import com.docvault.app.data.net.QuotaResponse
import com.docvault.app.data.net.RegisterRequest
import com.docvault.app.data.net.ShareDto
import com.docvault.app.data.net.TagDto
import com.docvault.app.data.net.TransferAdminRequest
import com.docvault.app.data.net.TrashItem
import com.docvault.app.data.net.UpdateDocumentRequest
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

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
        signIn(LoginRequest(phone = phone, password = password, mode = "password"))

    /**
     * Sign in with the ID token FirebaseAuth returned after the user entered
     * the SMS code Firebase itself sent — this backend never sees the code.
     */
    suspend fun loginWithOtp(firebaseIdToken: String): ApiResult<Unit> =
        signIn(LoginRequest(firebaseIdToken = firebaseIdToken, mode = "otp"))

    private suspend fun signIn(body: LoginRequest): ApiResult<Unit> =
        when (val result = apiCall { apiProvider.api().login(body) }) {
            is ApiResult.Ok -> {
                tokenStore.token = result.value.accessToken
                ApiResult.Ok(Unit)
            }
            is ApiResult.Err -> result
        }

    fun signOut() {
        tokenStore.signOut()
        // Only relevant if this device ever signed in via OTP. Guarded: if
        // google-services.json isn't in place yet, FirebaseAuth has no
        // default app and getInstance() would throw.
        runCatching { com.google.firebase.auth.FirebaseAuth.getInstance().signOut() }
    }

    suspend fun me(): ApiResult<AccountResponse> = apiCall { apiProvider.api().me() }

    suspend fun quota(): ApiResult<QuotaResponse> = apiCall { apiProvider.api().quota() }

    suspend fun changePassword(current: String, new: String): ApiResult<Unit> =
        apiCall { apiProvider.api().changePassword(ChangePasswordRequest(current, new)) }

    /** Resolve a phone number to an account. Err(404) when not registered. */
    suspend fun lookupAccount(phone: String): ApiResult<AccountSummary> =
        apiCall { apiProvider.api().lookupAccount(LookupRequest(phone)) }

    /**
     * Reset a forgotten password using a verified Firebase phone ID token.
     *
     * Unlike [signIn], this never persists a token — the 204 response carries
     * none. The caller still has to sign in normally afterwards.
     */
    suspend fun resetPassword(firebaseIdToken: String, newPassword: String): ApiResult<Unit> =
        apiCall {
            apiProvider.api().forgotPassword(ForgotPasswordRequest(firebaseIdToken, newPassword))
        }

    /**
     * Err(404) if [phone] has no account. Called before triggering Firebase's
     * OTP send in the forgot-password flow, so an unregistered number never
     * wastes one — see the backend's matching docstring for the accepted
     * phone-enumeration tradeoff this makes.
     */
    suspend fun checkPhoneRegistered(phone: String): ApiResult<Unit> =
        apiCall { apiProvider.api().checkPhoneRegistered(LookupRequest(phone)) }

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

    /** Admin only — the server enforces that; this just calls the same PATCH the create form would. */
    suspend fun renameGroup(groupId: String, name: String, description: String?): ApiResult<GroupResponse> =
        apiCall {
            apiProvider.api().updateGroup(
                groupId,
                CreateGroupRequest(name, description?.takeIf { it.isNotBlank() }),
            )
        }

    suspend fun deleteGroup(groupId: String): ApiResult<Unit> =
        apiCall { apiProvider.api().deleteGroup(groupId) }

    suspend fun groupDocuments(groupId: String): ApiResult<List<GroupDocument>> =
        apiCall { apiProvider.api().groupDocuments(groupId) }

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

    // --- documents ----------------------------------------------------

    suspend fun listDocuments(
        query: String?,
        docType: String?,
        page: Int,
        pageSize: Int = PAGE_SIZE,
    ): ApiResult<DocumentPage> = apiCall {
        apiProvider.api().listDocuments(query?.takeIf { it.isNotBlank() }, docType, page, pageSize)
    }

    /**
     * Upload one picked file, reporting bytes sent as it goes.
     *
     * The file is read straight from its content Uri while sending — never
     * copied into app storage first.
     */
    suspend fun uploadDocument(
        resolver: ContentResolver,
        file: PickedFile,
        docType: String = "other",
        onProgress: (sent: Long, total: Long) -> Unit,
    ): ApiResult<DocumentSummary> {
        val body = ProgressRequestBody(resolver, file.uri, file.mimeType, file.size, onProgress)
        val part = MultipartBody.Part.createFormData("file", file.name, body)
        val type = docType.toRequestBody("text/plain".toMediaType())
        return apiCall { apiProvider.api().uploadDocument(part, type) }
    }

    suspend fun documentDetail(documentId: String): ApiResult<DocumentDetail> =
        apiCall { apiProvider.api().documentDetail(documentId) }

    suspend fun renameDocument(documentId: String, name: String): ApiResult<DocumentDetail> =
        apiCall { apiProvider.api().updateDocument(documentId, UpdateDocumentRequest(name = name)) }

    suspend fun changeDocumentType(documentId: String, docType: String): ApiResult<DocumentDetail> =
        apiCall {
            apiProvider.api().updateDocument(documentId, UpdateDocumentRequest(docType = docType))
        }

    suspend fun deleteDocument(documentId: String): ApiResult<Unit> =
        apiCall { apiProvider.api().deleteDocument(documentId) }

    suspend fun restoreDocument(documentId: String): ApiResult<DocumentSummary> =
        apiCall { apiProvider.api().restoreDocument(documentId) }

    suspend fun trash(): ApiResult<List<TrashItem>> = apiCall { apiProvider.api().trash() }

    /**
     * Download into a location the user picked with the system file picker.
     *
     * Writing to a user-chosen Uri needs no storage permission, and the file
     * never lands in a shared folder the user did not choose.
     */
    suspend fun downloadDocument(
        documentId: String,
        resolver: ContentResolver,
        destination: Uri,
    ): ApiResult<Unit> =
        when (val result = apiCall { apiProvider.api().downloadDocument(documentId) }) {
            is ApiResult.Err -> result
            is ApiResult.Ok -> withContext(Dispatchers.IO) {
                runCatching {
                    val output = resolver.openOutputStream(destination)
                        ?: error("Couldn't open the chosen location")
                    output.use { out -> result.value.byteStream().use { it.copyTo(out) } }
                    ApiResult.Ok(Unit)
                }.getOrElse { ApiResult.Err("Couldn't save the file. ${it.message.orEmpty()}") }
            }
        }

    /**
     * Fetch the same bytes as [downloadDocument], for in-app viewing only.
     *
     * Written to an app-private cache [destination] — never a user-chosen
     * Uri — since this is never meant to leave the app as a saved file. The
     * caller deletes it when the preview closes.
     */
    suspend fun previewDocument(documentId: String, destination: File): ApiResult<Unit> =
        when (val result = apiCall { apiProvider.api().previewDocument(documentId) }) {
            is ApiResult.Err -> result
            is ApiResult.Ok -> withContext(Dispatchers.IO) {
                runCatching {
                    destination.outputStream().use { out -> result.value.byteStream().use { it.copyTo(out) } }
                    ApiResult.Ok(Unit)
                }.getOrElse { ApiResult.Err("Couldn't load the preview. ${it.message.orEmpty()}") }
            }
        }

    suspend fun addTag(documentId: String, label: String): ApiResult<List<TagDto>> =
        apiCall { apiProvider.api().addTag(documentId, AddTagRequest(label)) }

    suspend fun removeTag(documentId: String, tagId: String): ApiResult<List<TagDto>> =
        apiCall { apiProvider.api().removeTag(documentId, tagId) }

    suspend fun tagSuggestions(): ApiResult<List<String>> =
        apiCall { apiProvider.api().tagSuggestions() }

    suspend fun shareDocument(
        documentId: String,
        groupId: String,
        permission: String,
    ): ApiResult<ShareDto> =
        apiCall { apiProvider.api().shareDocument(documentId, CreateShareRequest(groupId, permission)) }

    suspend fun revokeShare(documentId: String, grantId: String): ApiResult<Unit> =
        apiCall { apiProvider.api().revokeShare(documentId, grantId) }

    companion object {
        const val PAGE_SIZE = 50
    }
}
