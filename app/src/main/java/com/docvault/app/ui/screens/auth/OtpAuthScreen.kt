package com.docvault.app.ui.screens.auth

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docvault.app.ui.components.PhoneNumberField

const val OtpAuthScreenTestTag = "otp_auth_screen"

/**
 * Sign in with a Firebase-verified phone code, as an alternative to the
 * password form on [AuthScreen] — not a replacement (see
 * [OtpAuthViewModel]'s KDoc).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtpAuthScreen(
    viewModel: OtpAuthViewModel,
    onSignedIn: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.signedIn) {
        if (state.signedIn) onSignedIn()
    }

    Scaffold(
        modifier = modifier.testTag(OtpAuthScreenTestTag),
        topBar = {
            TopAppBar(
                title = { Text("Sign in with OTP") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            when (state.step) {
                OtpStep.PHONE_ENTRY -> PhoneEntryStep(viewModel, state, context)
                OtpStep.CODE_ENTRY -> CodeEntryStep(viewModel, state, context)
            }

            state.error?.let { message ->
                Spacer(Modifier.height(16.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("otp_error"),
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.PhoneEntryStep(viewModel: OtpAuthViewModel, state: OtpAuthUiState, context: android.content.Context) {
    Text("Enter your phone number", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(4.dp))
    Text(
        "We'll text you a one-time code.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))

    PhoneNumberField(
        country = state.country,
        nationalNumber = state.nationalNumber,
        onCountryChange = viewModel::onCountryChange,
        onNationalNumberChange = viewModel::onNationalNumberChange,
        enabled = !state.isBusy,
        imeAction = ImeAction.Done,
        testTagPrefix = "otp_phone",
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(24.dp))
    Button(
        onClick = { (context as? Activity)?.let(viewModel::sendCode) },
        enabled = !state.isBusy,
        modifier = Modifier.fillMaxWidth().testTag("otp_send_code"),
    ) {
        if (state.isBusy) {
            CircularProgressIndicator(
                modifier = Modifier.height(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text("Send code")
        }
    }
}

@Composable
private fun ColumnScope.CodeEntryStep(viewModel: OtpAuthViewModel, state: OtpAuthUiState, context: android.content.Context) {
    Text("Enter the code", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(4.dp))
    Text(
        "Sent to ${state.country.dialCode} ${state.nationalNumber}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))

    OutlinedTextField(
        value = state.code,
        onValueChange = viewModel::onCodeChange,
        label = { Text("6-digit code") },
        singleLine = true,
        enabled = !state.isBusy,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth().testTag("otp_code"),
    )

    Spacer(Modifier.height(24.dp))
    Button(
        onClick = viewModel::verifyCode,
        enabled = !state.isBusy,
        modifier = Modifier.fillMaxWidth().testTag("otp_verify"),
    ) {
        if (state.isBusy) {
            CircularProgressIndicator(
                modifier = Modifier.height(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text("Verify")
        }
    }

    Spacer(Modifier.height(8.dp))
    TextButton(
        onClick = { (context as? Activity)?.let(viewModel::resendCode) },
        enabled = !state.isBusy,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    ) { Text("Resend code") }

    TextButton(
        onClick = viewModel::backToPhoneEntry,
        enabled = !state.isBusy,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    ) { Text("Use a different number") }
}
