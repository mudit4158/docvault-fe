package com.docvault.app.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.docvault.app.navigation.DocVaultDestination

fun bottomBarItemTestTag(destination: DocVaultDestination) = "bottom_bar_${destination.route}"

@Composable
fun DocVaultBottomBar(
    currentDestination: DocVaultDestination,
    onDestinationSelected: (DocVaultDestination) -> Unit,
) {
    NavigationBar {
        DocVaultDestination.entries.forEach { destination ->
            NavigationBarItem(
                modifier = Modifier.testTag(bottomBarItemTestTag(destination)),
                selected = destination == currentDestination,
                onClick = { onDestinationSelected(destination) },
                icon = { Icon(imageVector = destination.icon, contentDescription = null) },
                label = { Text(text = stringResource(destination.labelRes)) },
            )
        }
    }
}
