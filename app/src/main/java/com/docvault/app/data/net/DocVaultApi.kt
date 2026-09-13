package com.docvault.app.data.net

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

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
}
