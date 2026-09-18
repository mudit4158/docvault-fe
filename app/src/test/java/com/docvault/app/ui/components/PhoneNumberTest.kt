package com.docvault.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phone normalisation. Pure JVM tests — no device or emulator needed.
 *
 * The cases below are the shapes that actually arrive from a phone's contacts
 * list, which is where most of the mess comes from.
 */
class PhoneNumberTest {

    private val india = Countries.byIso("IN")!!
    private val uk = Countries.byIso("GB")!!

    // --- joining ----------------------------------------------------------

    @Test
    fun `joins country and national number into E164`() {
        assertEquals("+919876543210", PhoneNumber.toE164(india, "9876543210"))
    }

    @Test
    fun `strips formatting when joining`() {
        assertEquals("+919876543210", PhoneNumber.toE164(india, "98765 43210"))
        assertEquals("+919876543210", PhoneNumber.toE164(india, "98765-43210"))
        assertEquals("+919876543210", PhoneNumber.toE164(india, "(98765) 43210"))
    }

    // --- validation -------------------------------------------------------

    @Test
    fun `accepts a well formed number`() {
        assertTrue(PhoneNumber.isValid(india, "9876543210"))
    }

    @Test
    fun `rejects a number that is too short`() {
        assertFalse(PhoneNumber.isValid(india, "98765"))
    }

    @Test
    fun `rejects a number that is too long`() {
        assertFalse(PhoneNumber.isValid(india, "9876543210987654"))
    }

    @Test
    fun `rejects an empty number`() {
        assertFalse(PhoneNumber.isValid(india, ""))
    }

    @Test
    fun `rejects a number that is all one repeated digit`() {
        // libphonenumber alone does NOT catch this: 9999999999 is a
        // structurally valid Indian mobile shape (starts with 9, 10 digits)
        // per the numbering plan, so the library has no basis to reject it.
        // This needs its own explicit heuristic — see PhoneNumber.isValid.
        // Deliberately narrow: only "every digit identical", not "looks
        // suspicious" in some fuzzier sense — 9876543210 (descending) is
        // this file's own canonical example of a REAL valid number, so any
        // broader sequential-digit heuristic would reject valid numbers.
        assertFalse(PhoneNumber.isValid(india, "9999999999"))
        assertFalse(PhoneNumber.isValid(india, "8888888888"))
    }

    // --- parsing what contacts actually contain ---------------------------

    @Test
    fun `parses a number with its country code`() {
        val (country, national) = PhoneNumber.parse("+91 98765 43210", fallback = uk)
        assertEquals("IN", country.isoCode)
        assertEquals("9876543210", national)
    }

    @Test
    fun `parses the 00 international prefix as a plus`() {
        val (country, national) = PhoneNumber.parse("0091-9876543210", fallback = uk)
        assertEquals("IN", country.isoCode)
        assertEquals("9876543210", national)
    }

    @Test
    fun `strips a national trunk prefix when there is no country code`() {
        // "09876543210" as stored in an Indian contacts entry.
        val (country, national) = PhoneNumber.parse("09876543210", fallback = india)
        assertEquals("IN", country.isoCode)
        assertEquals("9876543210", national)
    }

    @Test
    fun `falls back to the given country for a bare national number`() {
        val (country, national) = PhoneNumber.parse("9876543210", fallback = uk)
        assertEquals("GB", country.isoCode)
        assertEquals("9876543210", national)
    }

    @Test
    fun `keeps digits when the dial code is unknown`() {
        // +999 is not in the curated list; the digits survive so the user can
        // pick a country rather than losing what they entered.
        val (_, national) = PhoneNumber.parse("+9991234567", fallback = india)
        assertTrue(national.isNotEmpty())
    }

    @Test
    fun `round trips a picked contact back to the same E164`() {
        val (country, national) = PhoneNumber.parse("+44 7911 123456", fallback = india)
        assertEquals("+447911123456", PhoneNumber.toE164(country, national))
    }

    // --- dial code matching -----------------------------------------------

    @Test
    fun `matches the longest dial code`() {
        // +971 (UAE) must win over +97 being a prefix of it.
        assertEquals("AE", Countries.matchDialCode("+971501234567")?.isoCode)
        assertEquals("IN", Countries.matchDialCode("+919876543210")?.isoCode)
    }

    @Test
    fun `returns null for an unknown dial code`() {
        assertNull(Countries.matchDialCode("+9991234567"))
    }

    @Test
    fun `every country has a plausible dial code`() {
        for (country in Countries.ALL) {
            assertTrue(
                "bad dial code for ${country.isoCode}: ${country.dialCode}",
                country.dialCode.matches(Regex("""^\+[1-9]\d{0,3}$""")),
            )
        }
    }

    @Test
    fun `iso codes are unique`() {
        val codes = Countries.ALL.map { it.isoCode }
        assertEquals(codes.size, codes.toSet().size)
    }
}
