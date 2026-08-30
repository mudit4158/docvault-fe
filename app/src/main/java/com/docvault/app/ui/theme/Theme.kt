package com.docvault.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = VaultAccent,
    onPrimary = VaultOnSurfaceDark,
    secondary = VaultAccentDark,
    background = VaultBackgroundDark,
    surface = VaultSurfaceDark,
    surfaceVariant = VaultSurfaceVariantDark,
    onBackground = VaultOnSurfaceDark,
    onSurface = VaultOnSurfaceDark,
    onSurfaceVariant = VaultOnSurfaceMutedDark,
)

private val LightColors = lightColorScheme(
    primary = VaultAccent,
    onPrimary = VaultSurfaceLight,
    secondary = VaultAccentDark,
    background = VaultBackgroundLight,
    surface = VaultSurfaceLight,
    surfaceVariant = VaultSurfaceVariantLight,
    onBackground = VaultOnSurfaceLight,
    onSurface = VaultOnSurfaceLight,
    onSurfaceVariant = VaultOnSurfaceMutedLight,
)

/**
 * DocVault is dark-first per the v2 prototype; [dynamicColor] defaults off so the brand accent
 * stays fixed instead of being derived from the device wallpaper (Material You).
 */
@Composable
fun DocVaultTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        val activity = view.context as Activity
        androidx.compose.runtime.SideEffect {
            activity.window.statusBarColor = colorScheme.background.toArgb()
            activity.window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(activity.window, view).isAppearanceLightStatusBars =
                !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
