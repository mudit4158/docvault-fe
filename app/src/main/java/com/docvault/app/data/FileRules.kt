package com.docvault.app.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Upload rules checked on the device before any bytes are sent.
 *
 * These mirror the backend (PRD §4.1) purely so the user hears "too big" or
 * "unsupported" instantly. The server re-checks everything — including the
 * file's real contents — and is the authority.
 */
object FileRules {
    const val MAX_UPLOAD_BYTES: Long = 20L * 1024 * 1024

    val ALLOWED_EXTENSIONS = setOf("pdf", "jpg", "jpeg", "png", "csv", "doc", "docx", "xls", "xlsx")

    /** What the system picker is allowed to show. */
    val PICKER_MIME_TYPES = arrayOf(
        "application/pdf",
        "image/jpeg",
        "image/png",
        "text/csv",
        "text/comma-separated-values",
        "application/msword",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    )

    fun extensionOf(fileName: String): String =
        fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.ROOT)

    /** Why this file can't be uploaded, or null if it looks fine. */
    fun rejectionReason(fileName: String, sizeBytes: Long): String? = when {
        extensionOf(fileName) !in ALLOWED_EXTENSIONS ->
            "Unsupported file type. Upload a PDF, CSV, JPEG, PNG, DOC, DOCX, XLS or XLSX file."
        sizeBytes == 0L -> "The file is empty."
        // -1 means the provider didn't say; the server will enforce the cap.
        sizeBytes > MAX_UPLOAD_BYTES -> "This file is larger than the 20 MB limit."
        else -> null
    }
}

/** Document types (PRD §4.9): wire value to label. */
object DocTypes {
    val ALL: List<Pair<String, String>> = listOf(
        "aadhaar" to "Aadhaar",
        "voter_id" to "Voter ID",
        "pan" to "PAN",
        "passport" to "Passport",
        "other" to "Other",
    )

    fun label(value: String): String = ALL.firstOrNull { it.first == value }?.second ?: "Other"
}

object Format {
    private val DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    fun bytes(bytes: Long): String = when {
        bytes < 0 -> "—"
        bytes < 1024 -> "$bytes B"
        bytes < 1024L * 1024 -> "${(bytes / 1024.0).roundToLong().coerceAtLeast(1)} KB"
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }

    /**
     * "13 Sep 2026" from an ISO timestamp. Tolerates values with or without a
     * UTC offset — SQLite in development returns them without one.
     */
    fun date(iso: String): String =
        runCatching { LocalDate.parse(iso.take(10)).format(DATE) }.getOrDefault(iso.take(10))

    /** "Private" / "1 group" / "3 groups" — the share state shown on a row. */
    fun shareState(count: Int): String = when (count) {
        0 -> "Private"
        1 -> "1 group"
        else -> "$count groups"
    }

    fun permission(value: String): String = when (value) {
        "download" -> "Can download"
        "view" -> "View only"
        "owner" -> "Owner"
        else -> value
    }
}
