package com.docvault.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.docvault.app.navigation.DocVaultDestination
import com.docvault.app.ui.components.bottomBarItemTestTag
import com.docvault.app.ui.screens.groups.GroupsScreenTestTag
import com.docvault.app.ui.screens.me.MeScreenTestTag
import com.docvault.app.ui.screens.scan.ScanScreenTestTag
import com.docvault.app.ui.screens.vault.VaultScreenTestTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocVaultNavHostTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun bottomBar_switchesBetweenAllFourTabs() {
        composeTestRule.onNodeWithTag(VaultScreenTestTag).assertExists()

        composeTestRule.onNodeWithTag(bottomBarItemTestTag(DocVaultDestination.Scan)).performClick()
        composeTestRule.onNodeWithTag(ScanScreenTestTag).assertExists()

        composeTestRule.onNodeWithTag(bottomBarItemTestTag(DocVaultDestination.Groups)).performClick()
        composeTestRule.onNodeWithTag(GroupsScreenTestTag).assertExists()

        composeTestRule.onNodeWithTag(bottomBarItemTestTag(DocVaultDestination.Me)).performClick()
        composeTestRule.onNodeWithTag(MeScreenTestTag).assertExists()
    }
}
