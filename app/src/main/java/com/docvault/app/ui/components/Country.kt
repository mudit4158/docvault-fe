package com.docvault.app.ui.components

/**
 * A dialling country.
 *
 * Curated rather than the full ISO 3166 set — this covers the markets DocVault
 * targets plus the common diaspora destinations, and keeps the picker short
 * enough to scroll.
 *
 * NOTE: [PhoneNumber.isValid] now checks real per-country validity via
 * libphonenumber (correct national length, plausible mobile prefix), not
 * just E.164 shape. This curated country list only decides which dial codes
 * the picker offers — it isn't itself a source of validation rules.
 */
data class Country(
    val isoCode: String,
    val name: String,
    val dialCode: String,
    val flag: String,
) {
    /** Shown in the compact selector next to the number field. */
    val shortLabel: String get() = "$flag $dialCode"
}

object Countries {

    /** Default selection — the product's primary market (Aadhaar, PAN, deeds). */
    val DEFAULT_ISO = "IN"

    val ALL: List<Country> = listOf(
        Country("IN", "India", "+91", "🇮🇳"),
        Country("US", "United States", "+1", "🇺🇸"),
        Country("GB", "United Kingdom", "+44", "🇬🇧"),
        Country("AE", "United Arab Emirates", "+971", "🇦🇪"),
        Country("SG", "Singapore", "+65", "🇸🇬"),
        Country("AU", "Australia", "+61", "🇦🇺"),
        Country("CA", "Canada", "+1", "🇨🇦"),
        Country("DE", "Germany", "+49", "🇩🇪"),
        Country("FR", "France", "+33", "🇫🇷"),
        Country("NL", "Netherlands", "+31", "🇳🇱"),
        Country("IE", "Ireland", "+353", "🇮🇪"),
        Country("NZ", "New Zealand", "+64", "🇳🇿"),
        Country("SA", "Saudi Arabia", "+966", "🇸🇦"),
        Country("QA", "Qatar", "+974", "🇶🇦"),
        Country("KW", "Kuwait", "+965", "🇰🇼"),
        Country("OM", "Oman", "+968", "🇴🇲"),
        Country("BH", "Bahrain", "+973", "🇧🇭"),
        Country("MY", "Malaysia", "+60", "🇲🇾"),
        Country("ID", "Indonesia", "+62", "🇮🇩"),
        Country("TH", "Thailand", "+66", "🇹🇭"),
        Country("PH", "Philippines", "+63", "🇵🇭"),
        Country("JP", "Japan", "+81", "🇯🇵"),
        Country("KR", "South Korea", "+82", "🇰🇷"),
        Country("CN", "China", "+86", "🇨🇳"),
        Country("HK", "Hong Kong", "+852", "🇭🇰"),
        Country("LK", "Sri Lanka", "+94", "🇱🇰"),
        Country("NP", "Nepal", "+977", "🇳🇵"),
        Country("BD", "Bangladesh", "+880", "🇧🇩"),
        Country("PK", "Pakistan", "+92", "🇵🇰"),
        Country("ZA", "South Africa", "+27", "🇿🇦"),
        Country("KE", "Kenya", "+254", "🇰🇪"),
        Country("NG", "Nigeria", "+234", "🇳🇬"),
        Country("CH", "Switzerland", "+41", "🇨🇭"),
        Country("SE", "Sweden", "+46", "🇸🇪"),
        Country("IT", "Italy", "+39", "🇮🇹"),
        Country("ES", "Spain", "+34", "🇪🇸"),
    )

    val DEFAULT: Country = ALL.first { it.isoCode == DEFAULT_ISO }

    fun byIso(isoCode: String): Country? = ALL.firstOrNull { it.isoCode == isoCode }

    /**
     * Longest matching dial code for an E.164 number.
     *
     * Longest-first matters: "+1" (US) would otherwise shadow nothing, but
     * "+97" would shadow "+971", and "+9" would shadow both.
     *
     * Ambiguous codes (+1 is US and Canada) resolve to whichever appears first
     * in [ALL]. That is cosmetic — the dial code, and so the E.164 number, is
     * identical either way.
     */
    fun matchDialCode(e164: String): Country? =
        ALL.filter { e164.startsWith(it.dialCode) }.maxByOrNull { it.dialCode.length }
}
