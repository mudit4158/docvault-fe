package com.docvault.app.ui.screens.auth

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docvault.app.ui.components.PhoneNumberField

const val ForgotPasswordScreenTestTag = "forgot_password_screen"

/**
 * Reset a forgotten password by proving phone ownership through the same
 * Firebase flow as OTP sign-in, then setting a new password server-side.
 * See [ForgotPasswordViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    viewModel: ForgotPasswordViewModel,
    onDone: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.done) {
        if (state.done) onDone()
    }

    Scaffold(
        modifier = modifier.testTag(ForgotPasswordScreenTestTag),
        topBar = {
            TopAppBar(
                title = { Text("Reset password") },
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
                ForgotPasswordStep.PHONE_ENTRY -> PhoneEntryStep(viewModel, state, context)
                ForgotPasswordStep.CODE_ENTRY -> CodeEntryStep(viewModel, state, context)
                ForgotPasswordStep.NEW_PASSWORD -> NewPasswordStep(viewModel, state)
            }

            state.error?.let { message ->
                Spacer(Modifier.height(16.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("forgot_password_error"),
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.PhoneEntryStep(
    viewModel: ForgotPasswordViewModel,
    state: ForgotPasswordUiState,
    context: android.content.Context,
) {
    Text("Enter your phone number", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(4.dp))
    Text(
        "We'll text you a one-time code to verify it's you.",
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
        testTagPrefix = "forgot_password_phone",
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(24.dp))
    Button(
        onClick = { (context as? Activity)?.let(viewModel::sendCode) },
        enabled = !state.isBusy,
        modifier = Modifier.fillMaxWidth().testTag("forgot_password_send_code"),
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
private fun ColumnScope.CodeEntryStep(
    viewModel: ForgotPasswordViewModel,
    state: ForgotPasswordUiState,
    context: android.content.Context,
) {
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
        modifier = Modifier.fillMaxWidth().testTag("forgot_password_code"),
    )

    Spacer(Modifier.height(24.dp))
    Button(
        onClick = viewModel::verifyCode,
        enabled = !state.isBusy,
        modifier = Modifier.fillMaxWidth().testTag("forgot_password_verify"),
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

@Composable
private fun ColumnScope.NewPasswordStep(viewModel: ForgotPasswordViewModel, state: ForgotPasswordUiState) {
    // Local, not in the ViewModel: pure view state, must reset on recreation
    // so a revealed password never survives a rotation — same reasoning as
    // AuthScreen's passwordVisible.
    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    Text("Choose a new password", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(4.dp))
    Text(
        "Your phone number is verified. Set a new password to sign in with.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))

    OutlinedTextField(
        value = state.newPassword,
        onValueChange = viewModel::onNewPasswordChange,
        label = { Text("New password") },
        singleLine = true,
        enabled = !state.isBusy,
        visualTransformation = if (newPasswordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            IconButton(
                onClick = { newPasswordVisible = !newPasswordVisible },
                modifier = Modifier.testTag("forgot_password_new_password_toggle"),
            ) {
                Icon(
                    imageVector = if (newPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (newPasswordVisible) "Hide password" else "Show password",
                )
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth().testTag("forgot_password_new_password"),
    )

    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = state.confirmPassword,
        onValueChange = viewModel::onConfirmPasswordChange,
        label = { Text("Confirm new password") },
        singleLine = true,
        enabled = !state.isBusy,
        visualTransformation = if (confirmPasswordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            IconButton(
                onClick = { confirmPasswordVisible = !confirmPasswordVisible },
                modifier = Modifier.testTag("forgot_password_confirm_password_toggle"),
            ) {
                Icon(
                    imageVector = if (confirmPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (confirmPasswordVisible) "Hide password" else "Show password",
                )
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth().testTag("forgot_password_confirm_password"),
    )

    Spacer(Modifier.height(24.dp))
    Button(
        onClick = viewModel::submitNewPassword,
        enabled = !state.isBusy,
        modifier = Modifier.fillMaxWidth().testTag("forgot_password_submit"),
    ) {
        if (state.isBusy) {
            CircularProgressIndicator(
                modifier = Modifier.height(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text("Reset password")
        }
    }
}
