package com.docvault.app.data.net

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * The docvault-be API surface.
 *
 * Every endpoint except register and login requires a bearer token, attached
 * automatically by [AuthInterceptor].
 *
 * Returns [Response] rather than the body directly so callers can distinguish
 * status codes — the backend uses them meaningfully (404 vs 403 vs 409 carry
 * different product rules), and the error body holds a `detail` message worth
 * showing the user.
 */
interface DocVaultApi {

    // --- auth ---------------------------------------------------------

    @POST("api/v1/auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<AccountResponse>

    @POST("api/v1/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<TokenResponse>

    @GET("api/v1/auth/me")
    suspend fun me(): Response<AccountResponse>

    @POST("api/v1/auth/me/password")
    suspend fun changePassword(@Body body: ChangePasswordRequest): Response<Unit>

    /** No auth header needed — that's the point, the caller can't log in. */
    @POST("api/v1/auth/password/forgot")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequest): Response<Unit>

    @GET("api/v1/auth/me/quota")
    suspend fun quota(): Response<QuotaResponse>

    /**
     * Resolve a phone number to an account, so the invite flow can show who it
     * is about to invite. 404 when the number is not registered.
     *
     * POST, not GET: a phone number is personal data and a query string ends up
     * in access logs.
     */
    @POST("api/v1/accounts/lookup")
    suspend fun lookupAccount(@Body body: LookupRequest): Response<AccountSummary>

    // --- groups -------------------------------------------------------

    @GET("api/v1/groups")
    suspend fun listGroups(): Response<List<GroupResponse>>

    @POST("api/v1/groups")
    suspend fun createGroup(@Body body: CreateGroupRequest): Response<GroupResponse>

    @GET("api/v1/groups/{id}")
    suspend fun getGroup(@Path("id") groupId: String): Response<GroupResponse>

    @PATCH("api/v1/groups/{id}")
    suspend fun updateGroup(
        @Path("id") groupId: String,
        @Body body: CreateGroupRequest,
    ): Response<GroupResponse>

    @DELETE("api/v1/groups/{id}")
    suspend fun deleteGroup(@Path("id") groupId: String): Response<Unit>

    /** Documents shared into a group — the group's Documents tab. */
    @GET("api/v1/groups/{id}/documents")
    suspend fun groupDocuments(@Path("id") groupId: String): Response<List<GroupDocument>>

    // --- members ------------------------------------------------------

    @GET("api/v1/groups/{id}/members")
    suspend fun listMembers(@Path("id") groupId: String): Response<List<MemberResponse>>

    @DELETE("api/v1/groups/{id}/members/me")
    suspend fun leaveGroup(@Path("id") groupId: String): Response<Unit>

    @DELETE("api/v1/groups/{id}/members/{memberId}")
    suspend fun removeMember(
        @Path("id") groupId: String,
        @Path("memberId") memberId: String,
    ): Response<Unit>

    @POST("api/v1/groups/{id}/transfer-admin")
    suspend fun transferAdmin(
        @Path("id") groupId: String,
        @Body body: TransferAdminRequest,
    ): Response<Unit>

    // --- invitations --------------------------------------------------

    @POST("api/v1/groups/{id}/invite")
    suspend fun invite(
        @Path("id") groupId: String,
        @Body body: InviteRequest,
    ): Response<InvitationResponse>

    /** Defaults to pending + declined; accepted ones show up as memberships. */
    @GET("api/v1/invitations")
    suspend fun myInvitations(): Response<List<InvitationResponse>>

    @POST("api/v1/invitations/{id}/accept")
    suspend fun acceptInvitation(@Path("id") invitationId: String): Response<Unit>

    @POST("api/v1/invitations/{id}/decline")
    suspend fun declineInvitation(@Path("id") invitationId: String): Response<Unit>

    // --- documents ----------------------------------------------------

    /** The caller's own documents. Null query parameters are omitted. */
    @GET("api/v1/documents")
    suspend fun listDocuments(
        @Query("q") query: String?,
        @Query("doc_type") docType: String?,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
    ): Response<DocumentPage>

    /** One file per request, so each has its own progress and retry. */
    @Multipart
    @POST("api/v1/documents")
    suspend fun uploadDocument(
        @Part file: MultipartBody.Part,
        @Part("doc_type") docType: RequestBody,
    ): Response<DocumentSummary>

    @GET("api/v1/documents/trash")
    suspend fun trash(): Response<List<TrashItem>>

    @GET("api/v1/documents/{id}")
    suspend fun documentDetail(@Path("id") documentId: String): Response<DocumentDetail>

    @PATCH("api/v1/documents/{id}")
    suspend fun updateDocument(
        @Path("id") documentId: String,
        @Body body: UpdateDocumentRequest,
    ): Response<DocumentDetail>

    @DELETE("api/v1/documents/{id}")
    suspend fun deleteDocument(@Path("id") documentId: String): Response<Unit>

    @POST("api/v1/documents/{id}/restore")
    suspend fun restoreDocument(@Path("id") documentId: String): Response<DocumentSummary>

    /** Streamed, so a large file is never held twice in memory. */
    @Streaming
    @GET("api/v1/documents/{id}/download")
    suspend fun downloadDocument(@Path("id") documentId: String): Response<ResponseBody>

    /**
     * Same bytes as [downloadDocument], for in-app viewing only.
     *
     * The server allows this to a `view`-only member too — that permission
     * tier exists precisely so they can look without a saved copy, and
     * [downloadDocument] 403s them.
     */
    @Streaming
    @GET("api/v1/documents/{id}/preview")
    suspend fun previewDocument(@Path("id") documentId: String): Response<ResponseBody>

    @POST("api/v1/documents/{id}/tags")
    suspend fun addTag(
        @Path("id") documentId: String,
        @Body body: AddTagRequest,
    ): Response<List<TagDto>>

    @DELETE("api/v1/documents/{id}/tags/{tagId}")
    suspend fun removeTag(
        @Path("id") documentId: String,
        @Path("tagId") tagId: String,
    ): Response<List<TagDto>>

    @GET("api/v1/tags")
    suspend fun tagSuggestions(): Response<List<String>>

    @POST("api/v1/documents/{id}/shares")
    suspend fun shareDocument(
        @Path("id") documentId: String,
        @Body body: CreateShareRequest,
    ): Response<ShareDto>

    @DELETE("api/v1/documents/{id}/shares/{grantId}")
    suspend fun revokeShare(
        @Path("id") documentId: String,
        @Path("grantId") grantId: String,
    ): Response<Unit>
}
