package com.docvault.app.data.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire models for the docvault-be API.
 *
 * These mirror the backend's Pydantic schemas exactly. When an endpoint
 * changes, check `docvault-be/app/user_management/schemas/` rather than
 * guessing — and read that module's `docs` folder for the rules behind a field.
 *
 * NOTE: never write a slash-star sequence inside a KDoc here. Kotlin block
 * comments nest, so it opens a comment that never closes and the whole file
 * fails with "Unclosed comment".
 */

// --- auth -----------------------------------------------------------------

@Serializable
data class RegisterRequest(
    val phone: String,
    @SerialName("display_name") val displayName: String,
    val password: String,
)

@Serializable
data class LoginRequest(
    val phone: String,
    val password: String,
    // The backend dispatches on this to pick an auth provider. Only "password"
    // exists today; OTP and SSO are additive on the server side.
    val mode: String = "password",
)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "bearer",
)

@Serializable
data class AccountResponse(
    val id: String,
    val phone: String,
    @SerialName("display_name") val displayName: String,
)

@Serializable
data class AccountSummary(
    val id: String,
    val phone: String,
    @SerialName("display_name") val displayName: String,
)

@Serializable
data class LookupRequest(val phone: String)

@Serializable
data class ChangePasswordRequest(
    @SerialName("current_password") val currentPassword: String,
    @SerialName("new_password") val newPassword: String,
)

@Serializable
data class QuotaResponse(
    @SerialName("files_used_today") val filesUsedToday: Int,
    @SerialName("cap_files") val capFiles: Int,
    @SerialName("period_reset_at") val periodResetAt: String,
)

// --- groups ---------------------------------------------------------------

@Serializable
data class CreateGroupRequest(
    val name: String,
    val description: String? = null,
)

@Serializable
data class GroupResponse(
    val id: String,
    val name: String,
    val description: String? = null,
    @SerialName("my_role") val myRole: String,
    @SerialName("member_count") val memberCount: Int,
) {
    val isAdmin: Boolean get() = myRole == "admin"
}

@Serializable
data class MemberResponse(
    val account: AccountSummary,
    val role: String,
    @SerialName("joined_at") val joinedAt: String,
) {
    val isAdmin: Boolean get() = role == "admin"
}

@Serializable
data class InviteRequest(val phone: String)

@Serializable
data class TransferAdminRequest(
    @SerialName("new_admin_id") val newAdminId: String,
)

// --- invitations ----------------------------------------------------------

@Serializable
data class InvitationResponse(
    val id: String,
    @SerialName("group_id") val groupId: String,
    @SerialName("group_name") val groupName: String,
    @SerialName("invited_by") val invitedBy: AccountSummary? = null,
    val status: String,
)

// --- errors ---------------------------------------------------------------

/** FastAPI's error envelope: `{"detail": "..."}`. */
@Serializable
data class ApiError(val detail: String? = null)
