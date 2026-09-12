package com.docvault.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.docvault.app.navigation.DocVaultNavHost
import com.docvault.app.ui.theme.DocVaultTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as DocVaultApplication).container

        setContent {
            DocVaultTheme {
                DocVaultNavHost(repository = container.repository)
            }
        }
    }
}
