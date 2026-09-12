package com.docvault.app.ui.theme

import androidx.compose.ui.graphics.Color

// Brand accent — the red-orange used for primary actions, alerts, and the active nav tab
// across the DocVault v2 prototype (scan capture, upload CTA, blocked-state banners).
val VaultAccent = Color(0xFFE6472A)
val VaultAccentDark = Color(0xFFB8371F)

// Error — deliberately a COOLER, deeper red than the warm brand accent.
//
// The brand accent is itself a red, and Material 3 uses `primary` for a text
// field's focused border and label. That made simply focusing the password
// field look like a validation failure. Focus now uses a neutral tone (see
// docVaultTextFieldColors), so red means "wrong" and only "wrong".
val VaultError = Color(0xFFD32F2F)
val VaultErrorDark = Color(0xFFEF5350)

// Dark palette — DocVault's primary surface treatment (the vault "stays encrypted" look).
val VaultBackgroundDark = Color(0xFF121212)
val VaultSurfaceDark = Color(0xFF1C1C1C)
val VaultSurfaceVariantDark = Color(0xFF2A2A2A)
val VaultOnSurfaceDark = Color(0xFFF2F2F2)
val VaultOnSurfaceMutedDark = Color(0xFFA3A3A3)

// Light palette — used only if the device is forced to light mode; the product is dark-first.
val VaultBackgroundLight = Color(0xFFFAFAFA)
val VaultSurfaceLight = Color(0xFFFFFFFF)
val VaultSurfaceVariantLight = Color(0xFFECECEC)
val VaultOnSurfaceLight = Color(0xFF1A1A1A)
val VaultOnSurfaceMutedLight = Color(0xFF5C5C5C)
