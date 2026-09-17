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

enum class OtpStep { PHONE_ENTRY, CODE_ENTRY }

/**
 * Pure input-sanitizing logic, extracted so it's unit-testable without
 * constructing a [OtpAuthViewModel] (which needs a real [DocVaultRepository]
 * — Context-backed, not worth faking just to test string filtering).
 */
object OtpCodeInput {
    private const val MAX_LENGTH = 6

    /** Digits only, capped at 6 — anything else in [candidate] is rejected outright, not truncated. */
    fun sanitize(candidate: String): String? =
        candidate.takeIf { it.length <= MAX_LENGTH && it.all(Char::isDigit) }
}

data class OtpAuthUiState(
    val step: OtpStep = OtpStep.PHONE_ENTRY,
    val country: Country = Countries.DEFAULT,
    val nationalNumber: String = "",
    val code: String = "",
    val isBusy: Boolean = false,
    val error: String? = null,
    val signedIn: Boolean = false,
) {
    val e164: String get() = PhoneNumber.toE164(country, nationalNumber)
}

/**
 * Phone -> Firebase-verified code -> DocVault session.
 *
 * Firebase Phone Auth is entirely client-driven: THIS app talks to Firebase
 * directly (`PhoneAuthProvider.verifyPhoneNumber`), and Firebase sends the
 * actual SMS and owns the resend cooldown — there is no backend endpoint
 * involved until the very last step, where the ID token Firebase handed back
 * is sent to `/auth/login` (mode="otp") for the backend to verify. See
 * docvault-be's `FirebaseOtpProvider` and its docs/auth_flow.md.
 *
 * Password login is unaffected — this is an additional sign-in path, not a
 * replacement (`AuthIdentity` supports both on the same account).
 */
class OtpAuthViewModel(private val repository: DocVaultRepository) : ViewModel() {

    private val _state = MutableStateFlow(OtpAuthUiState())
    val state: StateFlow<OtpAuthUiState> = _state.asStateFlow()

    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null

    fun onCountryChange(country: Country) =
        _state.update { it.copy(country = country, error = null) }

    fun onNationalNumberChange(value: String) =
        _state.update { it.copy(nationalNumber = value, error = null) }

    fun onCodeChange(value: String) {
        val sanitized = OtpCodeInput.sanitize(value) ?: return
        _state.update { it.copy(code = sanitized, error = null) }
    }

    /** Triggers Firebase's own SMS send. Needs an Activity for the (usually invisible) Play Integrity check. */
    fun sendCode(activity: Activity) {
        val current = _state.value
        if (current.isBusy) return
        if (!PhoneNumber.isValid(current.country, current.nationalNumber)) {
            _state.update { it.copy(error = "That does not look like a valid ${current.country.name} number") }
            return
        }

        _state.update { it.copy(isBusy = true, error = null) }
        startVerification(activity, current.e164, resend = false)
    }

    fun resendCode(activity: Activity) {
        val current = _state.value
        if (current.isBusy) return
        _state.update { it.copy(isBusy = true, error = null) }
        startVerification(activity, current.e164, resend = true)
    }

    private fun startVerification(activity: Activity, e164: String, resend: Boolean) {
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            // Auto-retrieval (SMS Retriever) sometimes verifies before the
            // user types anything — sign in immediately rather than making
            // them enter a code that already succeeded.
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                signInWithCredential(credential)
            }

            override fun onVerificationFailed(exception: FirebaseException) {
                _state.update {
                    it.copy(isBusy = false, error = exception.message ?: "Couldn't send the code")
                }
            }

            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                verificationId = id
                resendToken = token
                _state.update { it.copy(isBusy = false, step = OtpStep.CODE_ENTRY, error = null) }
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
        signInWithCredential(PhoneAuthProvider.getCredential(id, current.code))
    }

    private fun signInWithCredential(credential: PhoneAuthCredential) {
        viewModelScope.launch {
            try {
                val authResult = FirebaseAuth.getInstance().signInWithCredential(credential).await()
                val idToken = authResult.user?.getIdToken(false)?.await()?.token
                if (idToken == null) {
                    _state.update { it.copy(isBusy = false, error = "Couldn't verify the code") }
                    return@launch
                }
                when (val result = repository.loginWithOtp(idToken)) {
                    is ApiResult.Ok -> _state.update { it.copy(isBusy = false, signedIn = true) }
                    is ApiResult.Err ->
                        _state.update { it.copy(isBusy = false, error = result.message) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isBusy = false, error = e.message ?: "Incorrect code") }
            }
        }
    }

    fun backToPhoneEntry() {
        verificationId = null
        resendToken = null
        _state.update { it.copy(step = OtpStep.PHONE_ENTRY, code = "", error = null) }
    }
}
