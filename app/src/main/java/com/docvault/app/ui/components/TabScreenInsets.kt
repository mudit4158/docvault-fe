package com.docvault.app.ui.components

import androidx.compose.foundation.layout.WindowInsets

/**
 * Zero insets, for a tab screen nested inside `MainTabs`' own `Scaffold`
 * (`DocVaultNavHost.kt`) — Vault, Groups, Me.
 *
 * That outer Scaffold already reserves status-bar/nav-bar space for every
 * tab (it has the bottom nav bar and no topBar of its own, so its default
 * insets cover the whole tab area). Each tab screen's own `Scaffold` AND
 * its `TopAppBar` independently default to reserving that same space again
 * — two separate defaults, `Scaffold.contentWindowInsets` and
 * `TopAppBarDefaults.windowInsets`, so both need this override, not just
 * one. Left at the default on either, the space gets reserved twice:
 * invisible on a phone with thin gesture-nav insets, glaringly visible on
 * one with a tall fixed-height 3-button nav bar (reported from exactly
 * that device — twice, once per default that still needed overriding).
 *
 * Every OTHER Scaffold in the app (document detail, group detail, trash,
 * preview, auth, OTP, forgot-password, scan) is a root-level route with no
 * parent Scaffold — those must keep the real default, not this constant,
 * or their content would draw underneath the status bar.
 */
val TAB_SCREEN_ZERO_INSETS = WindowInsets(0, 0, 0, 0)
