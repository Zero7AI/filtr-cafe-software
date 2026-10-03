package com.example.kitchen.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = ShadcnBlack,
    onPrimary = ShadcnWhite,
    primaryContainer = ShadcnMutedBg,
    onPrimaryContainer = ShadcnBlack,
    secondary = ShadcnBlack,
    onSecondary = ShadcnWhite,
    background = CreamBackground,
    onBackground = ShadcnBlack,
    surface = CreamSurface,
    onSurface = ShadcnBlack,
    surfaceVariant = ShadcnMutedBg,
    onSurfaceVariant = ShadcnMutedText,
    outline = ShadcnBorder
)

@Composable
fun FiltrKitchenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
