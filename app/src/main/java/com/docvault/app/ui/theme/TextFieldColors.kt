package com.docvault.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable

/**
 * Text field colours that keep "focused" and "wrong" visually distinct.
 *
 * Material 3 paints a focused OutlinedTextField's border and label with
 * `primary`. DocVault's `primary` is the brand red-orange, so merely tapping
 * into the password field lit it up red and read as a rejected password.
 *
 * Focus is therefore neutral (`onSurface`), and red is reserved for genuine
 * errors. The brand accent still owns buttons, the active nav tab and other
 * primary actions — it just stops meaning "this input is bad".
 */
@Composable
fun docVaultTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.onSurface,
    focusedLabelColor = MaterialTheme.colorScheme.onSurface,
    cursorColor = MaterialTheme.colorScheme.onSurface,

    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,

    // Left explicit so the error state is unmistakably the odd one out.
    errorBorderColor = MaterialTheme.colorScheme.error,
    errorLabelColor = MaterialTheme.colorScheme.error,
    errorCursorColor = MaterialTheme.colorScheme.error,
)
