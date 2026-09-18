package com.docvault.app.ui.screens.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docvault.app.data.ApiResult
import com.docvault.app.data.DocVaultRepository
import com.docvault.app.ui.components.Countries
import com.docvault.app.ui.components.Country
import com.docvault.app.ui.components.PhoneNumber
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

enum class ForgotPasswordStep { PHONE_ENTRY, CODE_ENTRY, NEW_PASSWORD }

data class ForgotPasswordUiState(
    val step: ForgotPasswordStep = ForgotPasswordStep.PHONE_ENTRY,
    val country: Country = Countries.DEFAULT,
    val nationalNumber: String = "",
    val code: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isBusy: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
) {
    val e164: String get() = PhoneNumber.toE164(country, nationalNumber)
}

/**
 * Phone -> Firebase-verified code -> new password -> `POST /auth/password/forgot`.
 *
 * Deliberately duplicates [OtpAuthViewModel]'s Firebase glue rather than
 * sharing it — the two flows diverge at the last step (this one never signs
 * the user in; it resets a credential and sends them back to the normal
 * sign-in form), and the glue itself carries none of the shared-invariant
 * risk that justified extracting the backend's
 * `verify_phone_and_resolve_account` helper. Three similar screens beat a
 * premature shared abstraction here.
 */
class ForgotPasswordViewModel(private val repository: DocVaultRepository) : ViewModel() {

    private val _state = MutableStateFlow(ForgotPasswordUiState())
    val state: StateFlow<ForgotPasswordUiState> = _state.asStateFlow()

    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    private var verifiedIdToken: String? = null

    fun onCountryChange(country: Country) =
        _state.update { it.copy(country = country, error = null) }

    fun onNationalNumberChange(value: String) =
        _state.update { it.copy(nationalNumber = value, error = null) }

    fun onCodeChange(value: String) {
        val sanitized = OtpCodeInput.sanitize(value) ?: return
        _state.update { it.copy(code = sanitized, error = null) }
    }

    fun onNewPasswordChange(value: String) = _state.update { it.copy(newPassword = value, error = null) }

    fun onConfirmPasswordChange(value: String) =
        _state.update { it.copy(confirmPassword = value, error = null) }

    /**
     * Checks the phone is actually registered before spending an OTP on it —
     * see [DocVaultRepository.checkPhoneRegistered]'s KDoc for the accepted
     * phone-enumeration tradeoff this makes. Firebase is only triggered on
     * a 204; a 404 short-circuits straight to an actionable error, with no
     * SMS sent and no wait.
     */
    fun sendCode(activity: Activity) {
        val current = _state.value
        if (current.isBusy) return
        if (!PhoneNumber.isValid(current.country, current.nationalNumber)) {
            _state.update { it.copy(error = "That does not look like a valid ${current.country.name} number") }
            return
        }

        _state.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.checkPhoneRegistered(current.e164)) {
                is ApiResult.Ok -> startVerification(activity, current.e164, resend = false)
                is ApiResult.Err -> _state.update {
                    it.copy(
                        isBusy = false,
                        error = if (result.status == 404) {
                            "No DocVault account found for this number. Check the number, or create an account instead."
                        } else {
                            result.message
                        },
                    )
                }
            }
        }
    }

    fun resendCode(activity: Activity) {
        val current = _state.value
        if (current.isBusy) return
        _state.update { it.copy(isBusy = true, error = null) }
        startVerification(activity, current.e164, resend = true)
    }

    private fun startVerification(activity: Activity, e164: String, resend: Boolean) {
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                resolveIdToken(credential)
            }

            override fun onVerificationFailed(exception: FirebaseException) {
                _state.update {
                    it.copy(isBusy = false, error = exception.message ?: "Couldn't send the code")
                }
            }

            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                verificationId = id
                resendToken = token
                _state.update { it.copy(isBusy = false, step = ForgotPasswordStep.CODE_ENTRY, error = null) }
            }
        }

        val builder = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
            .setPhoneNumber(e164)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
        if (resend) {
            resendToken?.let { builder.setForceResendingToken(it) }
        }
        PhoneAuthProvider.verifyPhoneNumber(builder.build())
    }

    fun verifyCode() {
        val current = _state.value
        if (current.isBusy) return
        val id = verificationId
        if (id == null || current.code.length < 6) {
            _state.update { it.copy(error = "Enter the 6-digit code") }
            return
        }
        _state.update { it.copy(isBusy = true, error = null) }
        resolveIdToken(PhoneAuthProvider.getCredential(id, current.code))
    }

    /**
     * Verifies the phone via Firebase and stashes the resulting ID token —
     * unlike [OtpAuthViewModel], this never calls `signIn`. The token is
     * spent later, in [submitNewPassword], against `/auth/password/forgot`.
     */
    private fun resolveIdToken(credential: PhoneAuthCredential) {
        viewModelScope.launch {
            try {
                val authResult = FirebaseAuth.getInstance().signInWithCredential(credential).await()
                val idToken = authResult.user?.getIdToken(false)?.await()?.token
                if (idToken == null) {
                    _state.update { it.copy(isBusy = false, error = "Couldn't verify the code") }
                    return@launch
                }
                verifiedIdToken = idToken
                _state.update { it.copy(isBusy = false, step = ForgotPasswordStep.NEW_PASSWORD) }
            } catch (e: Exception) {
                _state.update { it.copy(isBusy = false, error = e.message ?: "Incorrect code") }
            }
        }
    }

    fun submitNewPassword() {
        val current = _state.value
        if (current.isBusy) return
        val idToken = verifiedIdToken
        if (idToken == null) {
            _state.update { it.copy(error = "Verification expired — start again", step = ForgotPasswordStep.PHONE_ENTRY) }
            return
        }
        if (current.newPassword.length < MIN_PASSWORD || current.newPassword.length > MAX_PASSWORD) {
            _state.update { it.copy(error = "Password must be $MIN_PASSWORD-$MAX_PASSWORD characters") }
            return
        }
        if (current.newPassword != current.confirmPassword) {
            _state.update { it.copy(error = "Passwords don't match") }
            return
        }

        _state.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.resetPassword(idToken, current.newPassword)) {
                is ApiResult.Ok -> _state.update { it.copy(isBusy = false, done = true) }
                is ApiResult.Err -> _state.update { it.copy(isBusy = false, error = result.message) }
            }
        }
    }

    fun backToPhoneEntry() {
        verificationId = null
        resendToken = null
        verifiedIdToken = null
        _state.update { it.copy(step = ForgotPasswordStep.PHONE_ENTRY, code = "", error = null) }
    }

    private companion object {
        const val MIN_PASSWORD = 8
        const val MAX_PASSWORD = 72
    }
}
