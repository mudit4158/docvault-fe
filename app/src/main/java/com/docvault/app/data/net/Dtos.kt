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
    val phone: String? = null,
    val password: String? = null,
    // Set for mode="otp" only: the ID token FirebaseAuth returned after the
    // user entered the SMS code. The backend verifies it server-side and
    // never sees the code itself — Firebase already did that check.
    @SerialName("firebase_id_token") val firebaseIdToken: String? = null,
    // The backend dispatches on this to pick an auth provider. "password" and
    // "otp" both exist server-side; SSO is additive future scope.
    //
    // Deliberately NOT sent when it equals this default: the JSON encoder
    // below (ApiProvider.kt) sets encodeDefaults=false, so a password login
    // omits `mode` from the wire entirely, matching the backend's own
    // model_validator design (see docvault-be's docs/auth_flow.md) rather
    // than a discriminated union that would require it to always be present.
    val mode: String = "password",
)

@Serializable
data class ForgotPasswordRequest(
    @SerialName("firebase_id_token") val firebaseIdToken: String,
    @SerialName("new_password") val newPassword: String,
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

// --- documents ------------------------------------------------------------

@Serializable
data class TagDto(val id: String, val label: String)

@Serializable
data class PersonSummary(
    val id: String,
    @SerialName("display_name") val displayName: String,
)

@Serializable
data class DocumentSummary(
    val id: String,
    val name: String,
    @SerialName("doc_type") val docType: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("size_bytes") val sizeBytes: Long,
    @SerialName("page_count") val pageCount: Int? = null,
    @SerialName("created_at") val createdAt: String,
    val tags: List<TagDto> = emptyList(),
    @SerialName("share_count") val shareCount: Int = 0,
)

@Serializable
data class DocumentPage(
    val items: List<DocumentSummary>,
    val total: Int,
    val page: Int,
    @SerialName("page_size") val pageSize: Int,
    @SerialName("has_next") val hasNext: Boolean,
)

@Serializable
data class ShareDto(
    val id: String,
    @SerialName("group_id") val groupId: String,
    @SerialName("group_name") val groupName: String,
    val permission: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class DocumentDetail(
    val id: String,
    val name: String,
    @SerialName("doc_type") val docType: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("size_bytes") val sizeBytes: Long,
    @SerialName("page_count") val pageCount: Int? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    val owner: PersonSummary,
    @SerialName("my_permission") val myPermission: String,
    // Owner-only on the server; empty / null for everyone else.
    val tags: List<TagDto> = emptyList(),
    val shares: List<ShareDto>? = null,
) {
    val isOwner: Boolean get() = myPermission == "owner"
    val canDownload: Boolean get() = myPermission == "owner" || myPermission == "download"
}

/** PATCH body. Null fields are omitted on the wire (explicitNulls = false). */
@Serializable
data class UpdateDocumentRequest(
    val name: String? = null,
    @SerialName("doc_type") val docType: String? = null,
)

@Serializable
data class AddTagRequest(val label: String)

@Serializable
data class CreateShareRequest(
    @SerialName("group_id") val groupId: String,
    val permission: String,
)

@Serializable
data class TrashItem(
    val id: String,
    val name: String,
    @SerialName("doc_type") val docType: String,
    @SerialName("size_bytes") val sizeBytes: Long,
    @SerialName("deleted_at") val deletedAt: String,
    @SerialName("purge_at") val purgeAt: String,
    val restorable: Boolean,
)

@Serializable
data class GroupDocument(
    val id: String,
    val name: String,
    @SerialName("doc_type") val docType: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("size_bytes") val sizeBytes: Long,
    @SerialName("page_count") val pageCount: Int? = null,
    @SerialName("created_at") val createdAt: String,
    val owner: PersonSummary,
    val permission: String,
    @SerialName("shared_at") val sharedAt: String,
)

// --- errors ---------------------------------------------------------------

/** FastAPI's error envelope for a typed exception: `{"detail": "..."}`. */
@Serializable
data class ApiError(val detail: String? = null)

/**
 * FastAPI's error envelope for a 422 request-validation failure — `detail`
 * here is a LIST of per-field errors, not a string, so it never matches
 * [ApiError]. A password missing an uppercase letter surfaces via exactly
 * this shape (a `ValueError` raised inside a Pydantic field validator).
 */
// No `loc` field: it can mix strings and ints (list indices), and the
// message alone is all that's ever shown to the user.
@Serializable
data class ValidationErrorDetail(val msg: String = "")

@Serializable
data class ValidationErrorBody(val detail: List<ValidationErrorDetail> = emptyList())
