package com.docvault.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docvault.app.data.ApiResult
import com.docvault.app.data.DocVaultRepository
import com.docvault.app.ui.components.Countries
import com.docvault.app.ui.components.Country
import com.docvault.app.ui.components.PhoneNumber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val isRegisterMode: Boolean = false,
    val country: Country = Countries.DEFAULT,
    /** National part only — the dial code comes from [country]. */
    val nationalNumber: String = "",
    val displayName: String = "",
    val password: String = "",
    /** Register mode only — ignored (and irrelevant) for sign-in. */
    val confirmPassword: String = "",
    val serverUrl: String = "",
    val showServerField: Boolean = false,
    val isBusy: Boolean = false,
    val error: String? = null,
    val signedIn: Boolean = false,
) {
    /** What the API actually receives. */
    val e164: String get() = PhoneNumber.toE164(country, nationalNumber)
}

class AuthViewModel(private val repository: DocVaultRepository) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState(serverUrl = repository.baseUrl))
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onCountryChange(country: Country) =
        _state.update { it.copy(country = country, error = null) }

    fun onNationalNumberChange(value: String) {
        // A pasted full number carries its own country code; adopt it rather
        // than leaving the selector disagreeing with what is on screen.
        if (value.startsWith("+") || value.startsWith("00")) {
            val (country, national) = PhoneNumber.parse(value, _state.value.country)
            _state.update { it.copy(country = country, nationalNumber = national, error = null) }
        } else {
            _state.update { it.copy(nationalNumber = value, error = null) }
        }
    }

    fun onNameChange(value: String) = _state.update { it.copy(displayName = value, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }
    fun onConfirmPasswordChange(value: String) =
        _state.update { it.copy(confirmPassword = value, error = null) }
    fun onServerUrlChange(value: String) = _state.update { it.copy(serverUrl = value, error = null) }
    fun toggleServerField() = _state.update { it.copy(showServerField = !it.showServerField) }

    fun toggleMode() =
        _state.update { it.copy(isRegisterMode = !it.isRegisterMode, confirmPassword = "", error = null) }

    fun submit() {
        val current = _state.value
        if (current.isBusy) return

        validate(current)?.let { message ->
            _state.update { it.copy(error = message) }
            return
        }

        // Persist the server address before the call, so the API client is
        // rebuilt against whatever the user just typed.
        repository.baseUrl = current.serverUrl

        _state.update { it.copy(isBusy = true, error = null) }

        viewModelScope.launch {
            if (current.isRegisterMode) {
                when (
                    val result = repository.register(
                        phone = current.e164,
                        displayName = current.displayName.trim(),
                        password = current.password,
                    )
                ) {
                    is ApiResult.Err ->
                        _state.update { it.copy(isBusy = false, error = result.message) }
                    is ApiResult.Ok -> signIn(current)  // register then log straight in
                }
            } else {
                signIn(current)
            }
        }
    }

    private suspend fun signIn(current: AuthUiState) {
        when (val result = repository.login(current.e164, current.password)) {
            is ApiResult.Ok -> _state.update { it.copy(isBusy = false, signedIn = true) }
            is ApiResult.Err -> _state.update { it.copy(isBusy = false, error = result.message) }
        }
    }

    private fun validate(s: AuthUiState): String? = when {
        s.serverUrl.isBlank() -> "Enter the server address"
        s.nationalNumber.isBlank() -> "Enter your phone number"
        // Checked locally against the same E.164 shape the backend enforces,
        // so the user is told without a round trip.
        !PhoneNumber.isValid(s.country, s.nationalNumber) ->
            "That does not look like a valid ${s.country.name} number"
        s.isRegisterMode && s.displayName.isBlank() -> "Enter your name"
        s.password.length < MIN_PASSWORD -> "Password must be at least $MIN_PASSWORD characters"
        // bcrypt truncates beyond 72 bytes, so the backend rejects longer ones.
        s.password.length > MAX_PASSWORD -> "Password must be at most $MAX_PASSWORD characters"
        s.isRegisterMode && s.confirmPassword != s.password -> "Passwords don't match"
        else -> null
    }

    private companion object {
        const val MIN_PASSWORD = 8
        const val MAX_PASSWORD = 72
    }
}
