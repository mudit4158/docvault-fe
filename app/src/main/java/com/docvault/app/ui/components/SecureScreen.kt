package com.docvault.app.ui.components

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Blocks screenshots and screen recording for as long as the caller stays in
 * composition — a captured or shared document can be an Aadhaar, PAN or
 * passport (PRD §4.9).
 *
 * A window flag, not a manifest setting, because this is a single-Activity
 * app: it must be toggled on entry/exit of the sensitive flow rather than
 * declared once for the whole app. Scope it tightly — leaving it set after
 * leaving the flow blocks screenshots app-wide; never setting it here at all
 * leaves identity documents unprotected.
 */
@Composable
fun SecureScreen() {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(Unit) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}
