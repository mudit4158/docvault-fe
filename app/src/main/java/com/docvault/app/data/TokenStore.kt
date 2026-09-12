package com.docvault.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.docvault.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the bearer token and the backend URL.
 *
 * The token is a live credential to a vault of identity documents, so it is
 * kept in [EncryptedSharedPreferences] — backed by a key in the Android
 * Keystore, so it is not readable from a backup or an adb pull.
 *
 * Falls back to plain SharedPreferences only if the encrypted store cannot be
 * opened (a known failure mode on a small number of devices with a corrupted
 * keystore). Losing the session is preferable to the app refusing to start.
 */
class TokenStore(context: Context) {

    // MasterKeys is deprecated in favour of MasterKey.Builder, which only
    // exists in security-crypto 1.1.0-alpha. Staying on the stable 1.0.0
    // release and accepting the deprecation warning is the better trade for a
    // credential store — revisit when 1.1.0 ships stable.
    @Suppress("DEPRECATION")
    private val prefs: SharedPreferences = runCatching {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            ENCRYPTED_FILE,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        ) as SharedPreferences
    }.getOrElse {
        context.getSharedPreferences(FALLBACK_FILE, Context.MODE_PRIVATE)
    }

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_TOKEN) else putString(KEY_TOKEN, value)
        }.apply()

    /**
     * Backend base URL. Editable on the sign-in screen so one APK works on an
     * emulator (10.0.2.2) and on a phone pointed at the laptop's LAN address.
     */
    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, null) ?: BuildConfig.DEFAULT_API_BASE_URL
        set(value) = prefs.edit().putString(KEY_BASE_URL, normalizeUrl(value)).apply()

    val isSignedIn: Boolean get() = token != null

    private val _sessionExpired = MutableStateFlow(false)

    /**
     * Set when the server rejects the stored token.
     *
     * Tokens are stateless and last 60 minutes, so this fires routinely rather
     * than only on compromise. The UI observes it and returns to sign-in —
     * without that, an expired session strands the user on a screen whose
     * every request fails.
     */
    val sessionExpired: StateFlow<Boolean> = _sessionExpired.asStateFlow()

    /** Called when any authenticated request comes back 401. */
    fun onUnauthorized() {
        token = null
        _sessionExpired.value = true
    }

    /** Acknowledge the expiry after navigating, so it does not re-fire. */
    fun acknowledgeSessionExpired() {
        _sessionExpired.value = false
    }

    fun signOut() {
        // Deliberately keeps baseUrl — signing out should not make the user
        // retype the server address.
        token = null
        _sessionExpired.value = false
    }

    private companion object {
        const val ENCRYPTED_FILE = "docvault_secure_prefs"
        const val FALLBACK_FILE = "docvault_prefs"
        const val KEY_TOKEN = "access_token"
        const val KEY_BASE_URL = "api_base_url"
    }
}

/** Retrofit requires a trailing slash; users will not type one. */
fun normalizeUrl(raw: String): String {
    val trimmed = raw.trim()
    val withScheme = if (trimmed.startsWith("http")) trimmed else "http://$trimmed"
    return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
}
