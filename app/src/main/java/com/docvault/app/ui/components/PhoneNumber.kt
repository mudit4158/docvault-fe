package com.docvault.app.ui.components

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil

/**
 * Splitting and joining phone numbers around a country dial code.
 *
 * The app captures the country and the national number separately, and the API
 * only ever sees the joined E.164 string.
 */
object PhoneNumber {

    /** Backend contract: leading +, non-zero country digit, 8-15 digits total. */
    private val E164 = Regex("""^\+[1-9]\d{7,14}$""")

    private val phoneUtil = PhoneNumberUtil.getInstance()

    /** Join a selection into the E.164 form the API expects. */
    fun toE164(country: Country, nationalNumber: String): String =
        country.dialCode + nationalNumber.filter(Char::isDigit)

    /**
     * Real per-country validation via libphonenumber — correct national
     * length and a plausible mobile prefix for [country], not just "looks
     * like a phone number."
     *
     * This does NOT catch every fake-looking number: e.g. 9999999999 is a
     * structurally valid Indian mobile shape (starts with 9, 10 digits) per
     * the numbering plan, so libphonenumber has no basis to reject it — it
     * validates numbering-plan structure, not "is this a real person's
     * number." The all-same-digit check below catches that one specific,
     * unambiguous case. Deliberately NOT a broader "looks sequential"
     * heuristic — 9876543210 (descending) is this file's own canonical
     * example of a real valid number, so that would reject real numbers.
     */
    fun isValid(country: Country, nationalNumber: String): Boolean {
        val digits = nationalNumber.filter(Char::isDigit)
        if (digits.isNotEmpty() && digits.all { it == digits[0] }) return false

        val e164 = toE164(country, nationalNumber)
        if (!E164.matches(e164)) return false
        return try {
            phoneUtil.isValidNumber(phoneUtil.parse(e164, country.isoCode))
        } catch (e: NumberParseException) {
            false
        }
    }

    /**
     * Strip everything a person or a contacts entry might include around the
     * digits — spaces, dashes, brackets, non-breaking spaces.
     */
    fun digitsOnly(raw: String): String = raw.filter(Char::isDigit)

    /**
     * Best-effort split of an arbitrary number into (country, national part).
     *
     * Handles what actually turns up in a phone's contacts:
     *   "+91 98765 43210"  -> IN, 9876543210
     *   "0091-9876543210"  -> IN, 9876543210   (00 international prefix)
     *   "09876543210"      -> fallback, 9876543210  (national trunk prefix)
     *   "9876543210"       -> fallback, 9876543210
     *
     * [fallback] is used when the number carries no country code — usually
     * whatever the user currently has selected.
     */
    fun parse(raw: String, fallback: Country): Pair<Country, String> {
        val trimmed = raw.trim()

        // "00" is the international access prefix in much of the world; treat
        // it as an alias for "+".
        val normalised = when {
            trimmed.startsWith("+") -> "+" + digitsOnly(trimmed)
            trimmed.startsWith("00") -> "+" + digitsOnly(trimmed).removePrefix("00")
            else -> digitsOnly(trimmed)
        }

        if (normalised.startsWith("+")) {
            val country = Countries.matchDialCode(normalised)
            if (country != null) {
                return country to normalised.removePrefix(country.dialCode)
            }
            // Unknown dial code — keep the digits, let the user pick a country.
            return fallback to normalised.removePrefix("+")
        }

        // No country code. A single leading zero is a national trunk prefix
        // (common in IN, GB, DE contact entries) and is not part of E.164.
        return fallback to normalised.removePrefix("0")
    }
}
