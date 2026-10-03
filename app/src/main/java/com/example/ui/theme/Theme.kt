package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = ShadcnWhite,
    onPrimary = ShadcnBlack,
    primaryContainer = ShadcnDarkSurface,
    onPrimaryContainer = ShadcnWhite,
    secondary = ShadcnWhite,
    onSecondary = ShadcnBlack,
    secondaryContainer = ShadcnDarkSurface,
    onSecondaryContainer = ShadcnWhite,
    background = ShadcnDarkBg,
    surface = ShadcnDarkSurface,
    onBackground = ShadcnWhite,
    onSurface = ShadcnWhite,
    outline = ShadcnDarkBorder
)

private val LightColorScheme = lightColorScheme(
    primary = ShadcnBlack,
    onPrimary = ShadcnWhite,
    primaryContainer = ShadcnMutedBg,
    onPrimaryContainer = ShadcnBlack,
    secondary = ShadcnBlack,
    onSecondary = ShadcnWhite,
    secondaryContainer = ShadcnMutedBg,
    onSecondaryContainer = ShadcnBlack,
    background = CreamBackground,
    surface = CreamSurface,
    onBackground = ShadcnBlack,
    onSurface = ShadcnBlack,
    outline = ShadcnBorder
)

@Composable
fun FiltrTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = LightColorScheme

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
