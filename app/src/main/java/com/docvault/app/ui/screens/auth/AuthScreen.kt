package com.docvault.app.ui.screens.auth

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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docvault.app.ui.components.PhoneNumberField
import com.docvault.app.ui.theme.docVaultTextFieldColors

const val AuthScreenTestTag = "auth_screen"

/**
 * Sign in or create an account (prototype screen 01's entry point).
 *
 * Carries a "Server" field, collapsed by default. The backend runs on a
 * developer laptop during testing, and its address differs between an emulator
 * (10.0.2.2) and a physical device (the laptop's LAN IP) — making it editable
 * means one APK covers both.
 */
@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onSignedIn: () -> Unit,
    onOtpLogin: () -> Unit,
    onForgotPassword: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Local, not in the ViewModel: it is pure view state and must reset if the
    // screen is recreated, so a revealed password never survives a rotation.
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(state.signedIn) {
        if (state.signedIn) onSignedIn()
    }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .testTag(AuthScreenTestTag),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (state.isRegisterMode) "Create your vault" else "Welcome back",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (state.isRegisterMode) {
                    "Your documents stay encrypted and private to you."
                } else {
                    "Sign in to unlock your vault."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(32.dp))

            // Country is a separate selector, so the user never has to know
            // the API wants E.164.
            PhoneNumberField(
                country = state.country,
                nationalNumber = state.nationalNumber,
                onCountryChange = viewModel::onCountryChange,
                onNationalNumberChange = viewModel::onNationalNumberChange,
                enabled = !state.isBusy,
                imeAction = ImeAction.Next,
                testTagPrefix = "auth_phone",
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.isRegisterMode) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.displayName,
                    onValueChange = viewModel::onNameChange,
                    label = { Text("Your name") },
                    singleLine = true,
                    enabled = !state.isBusy,
                    colors = docVaultTextFieldColors(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().testTag("auth_name"),
                )
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text("Password") },
                singleLine = true,
                enabled = !state.isBusy,
                colors = docVaultTextFieldColors(),
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        modifier = Modifier.testTag("auth_password_toggle"),
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Filled.VisibilityOff
                            } else {
                                Icons.Filled.Visibility
                            },
                            contentDescription = if (passwordVisible) {
                                "Hide password"
                            } else {
                                "Show password"
                            },
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth().testTag("auth_password"),
            )

            if (!state.isRegisterMode) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onOtpLogin,
                    enabled = !state.isBusy,
                    modifier = Modifier.testTag("auth_otp_login"),
                ) { Text("Sign in with OTP instead") }

                TextButton(
                    onClick = onForgotPassword,
                    enabled = !state.isBusy,
                    modifier = Modifier.testTag("auth_forgot_password"),
                ) { Text("Forgot password?") }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = viewModel::toggleServerField) {
                Text(if (state.showServerField) "Hide server settings" else "Server settings")
            }

            if (state.showServerField) {
                OutlinedTextField(
                    value = state.serverUrl,
                    onValueChange = viewModel::onServerUrlChange,
                    label = { Text("Server address") },
                    colors = docVaultTextFieldColors(),
                    supportingText = {
                        Text("Emulator: http://10.0.2.2:8000  ·  Phone: your laptop's IP")
                    },
                    singleLine = true,
                    enabled = !state.isBusy,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("auth_server"),
                )
            }

            state.error?.let { message ->
                Spacer(Modifier.height(16.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("auth_error"),
                )
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = viewModel::submit,
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth().testTag("auth_submit"),
            ) {
                if (state.isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(if (state.isRegisterMode) "Create account" else "Sign in")
                }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = viewModel::toggleMode,
                enabled = !state.isBusy,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(
                    if (state.isRegisterMode) {
                        "Already have an account? Sign in"
                    } else {
                        "New here? Create an account"
                    }
                )
            }
        }
    }
}
