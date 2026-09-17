package com.docvault.app.ui.screens.scan.data

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/**
 * Launches Google's ready-made document scanner: capture, live edge
 * detection, an adjustable-corner crop, multi-page sessions, retake, and
 * gallery import — all in one Google-maintained flow, so this app never
 * hand-rolls contour detection or a crop overlay.
 *
 * The backend has no opinion on how a page's boundary/crop was produced
 * (docvault-be's `scan_to_pdf.md`: "either way, the output goes through the
 * same upload endpoint") — this is purely a client-side choice.
 *
 * Yields image Uris (JPEG) in page order, or calls [onError] if the scanner
 * module can't be reached (it's fetched on first use and needs Play
 * Services + connectivity once). [onCancelled] fires when the user backs out
 * of the scanner without producing any pages — not an error, but callers
 * that launched it as their very first action (nothing captured yet) need to
 * know so they can leave the flow instead of sitting on a blank screen.
 */
@Composable
fun rememberDocumentScanner(
    pageLimit: Int,
    onResult: (List<Uri>) -> Unit,
    onCancelled: () -> Unit = {},
    onError: (String) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val pages = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                ?.pages
                ?.map { it.imageUri }
                .orEmpty()
            if (pages.isNotEmpty()) onResult(pages) else onCancelled()
        } else {
            onCancelled()
        }
    }

    return {
        val activity = context as? Activity
        if (activity == null) {
            onError("Can't open the scanner here.")
        } else {
            val options = GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(pageLimit)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build()
            GmsDocumentScanning.getClient(options)
                .getStartScanIntent(activity)
                .addOnSuccessListener { intentSender ->
                    launcher.launch(IntentSenderRequest.Builder(intentSender).build())
                }
                .addOnFailureListener { error ->
                    onError(
                        error.message
                            ?: "Couldn't open the scanner. Check your connection and try again.",
                    )
                }
        }
    }
}
