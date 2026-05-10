package com.petesmapper.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val IndustrialDarkScheme = darkColorScheme(
    primary = AccentCyan,
    secondary = AccentAmber,
    tertiary = AccentGreen,
    background = Base900,
    surface = Base800,
    surfaceVariant = Base700,
    error = AccentRed,
    onPrimary = Base900,
    onSecondary = Base900,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
)

@Composable
fun PetesMapperTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) IndustrialDarkScheme else IndustrialDarkScheme,
        typography = IndustrialTypography,
        content = content,
    )
}
