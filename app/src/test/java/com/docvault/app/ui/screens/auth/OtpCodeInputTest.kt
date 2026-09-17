package com.docvault.app.ui.screens.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [OtpAuthViewModel] itself needs a real [com.docvault.app.data.DocVaultRepository]
 * (Context-backed) and Firebase's `PhoneAuthProvider`/`FirebaseAuth` (a real
 * device/emulator, a provisioned Firebase project) — not unit-testable here,
 * same as ML Kit's scanner in the Scan feature. [OtpCodeInput] is the one
 * piece of its logic with no such dependency, so it's tested directly.
 */
class OtpCodeInputTest {

    @Test
    fun `accepts a 6-digit code`() {
        assertEquals("123456", OtpCodeInput.sanitize("123456"))
    }

    @Test
    fun `accepts a partial code while typing`() {
        assertEquals("123", OtpCodeInput.sanitize("123"))
    }

    @Test
    fun `rejects a 7th digit`() {
        assertNull(OtpCodeInput.sanitize("1234567"))
    }

    @Test
    fun `rejects a non-digit character`() {
        assertNull(OtpCodeInput.sanitize("12a456"))
    }

    @Test
    fun `accepts an empty string`() {
        assertEquals("", OtpCodeInput.sanitize(""))
    }
}
