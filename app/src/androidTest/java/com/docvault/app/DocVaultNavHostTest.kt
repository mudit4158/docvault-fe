package com.docvault.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.docvault.app.ui.screens.auth.AuthScreenTestTag
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Navigation is gated on authentication, so a fresh install lands on the
 * sign-in screen rather than the vault.
 *
 * These cover only what works without a backend. Exercising the four tabs
 * needs a signed-in session, which needs a running server — that belongs in a
 * separate suite with a fake or a test double for DocVaultRepository, tracked
 * alongside FE slice 1.
 */
@RunWith(AndroidJUnit4::class)
class DocVaultNavHostTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun signOut() {
        // The token survives reinstall-free test runs, so clear it to make the
        // starting state deterministic.
        val app = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation()
            .targetContext
            .applicationContext as DocVaultApplication
        app.container.tokenStore.signOut()
    }

    @Test
    fun signedOut_showsAuthScreen() {
        composeTestRule.onNodeWithTag(AuthScreenTestTag).assertIsDisplayed()
    }

    @Test
    fun tooShortNumber_isRejectedBeforeAnyNetworkCall() {
        // The dial code comes from the country selector, so only the national
        // part is typed here. 5 digits is below E.164's minimum.
        composeTestRule.onNodeWithTag("auth_phone_number").performTextInput("98765")
        composeTestRule.onNodeWithTag("auth_password").performTextInput("correct-horse")
        composeTestRule.onNodeWithTag("auth_submit").performClick()

        // Validated locally against the same E.164 shape the backend enforces,
        // so the user is told without a round trip.
        composeTestRule.onNodeWithTag("auth_error").assertIsDisplayed()
    }

    @Test
    fun countrySelector_opensPickerAndChangesDialCode() {
        composeTestRule.onNodeWithTag("auth_phone_country").performClick()
        composeTestRule.onNodeWithTag("country_search").assertIsDisplayed()

        composeTestRule.onNodeWithTag("country_search").performTextInput("United Kingdom")
        composeTestRule.onNodeWithTag("country_GB").performClick()

        // Selector now shows the UK dial code.
        composeTestRule.onNodeWithText("🇬🇧 +44").assertIsDisplayed()
    }

    @Test
    fun serverSettings_canBeRevealed() {
        composeTestRule.onNodeWithTag("auth_server").assertDoesNotExist()
        composeTestRule.onNodeWithText("Server settings").performClick()
        composeTestRule.onNodeWithTag("auth_server").assertIsDisplayed()
    }
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onNodeWithText(text: String) =
    onNode(androidx.compose.ui.test.hasText(text))
