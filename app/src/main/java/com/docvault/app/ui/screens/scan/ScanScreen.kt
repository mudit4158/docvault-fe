package com.docvault.app.ui.screens.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docvault.app.ui.screens.scan.data.ScanSpec
import com.docvault.app.ui.screens.scan.data.rememberDocumentScanner

const val ScanScreenTestTag = "scan_screen"

/**
 * Full-screen modal Scan flow (prototype screens 13-14; engineering handoff
 * §3.3), reached outside the bottom tab bar — see [DocVaultNavHost].
 *
 * Launches ML Kit's scanner immediately on entry. Once at least one page
 * comes back, hands off to [ScanNavHost] for per-page edit -> review ->
 * save. Backing out of the scanner before capturing anything leaves the
 * flow entirely rather than sitting on a blank screen.
 *
 * Screenshots are blocked here too, but via a single app-wide SecureScreen
 * call in DocVaultNavHost rather than one local to this screen — a call
 * here would clear the flag on the way out of Scan even though the rest of
 * the signed-in app still needs it protected.
 */
@Composable
fun ScanScreen(viewModel: ScanViewModel, onFinished: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var hasLaunched by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }

    // Any exit before a successful save — cancelling the very first capture,
    // or discarding mid-edit/review — discards the whole session's cache.
    val exit: () -> Unit = { viewModel.abandon(); onFinished() }

    val scan = rememberDocumentScanner(
        pageLimit = ScanSpec.MAX_PAGES,
        onResult = { uris -> scanError = null; viewModel.addCaptured(uris) },
        onCancelled = { if (state.pages.isEmpty()) exit() },
        onError = { message -> scanError = message },
    )

    LaunchedEffect(Unit) {
        if (!hasLaunched) {
            hasLaunched = true
            scan()
        }
    }

    when {
        state.pages.isNotEmpty() -> ScanNavHost(viewModel = viewModel, onCancel = exit, onFinished = onFinished)

        scanError != null -> ScanErrorPlaceholder(
            message = scanError.orEmpty(),
            onRetry = { scanError = null; scan() },
            onCancel = exit,
        )

        else -> Box(
            modifier = Modifier.fillMaxSize().testTag(ScanScreenTestTag),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun ScanErrorPlaceholder(message: String, onRetry: () -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Try again") }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onCancel) { Text("Cancel") }
    }
}
