package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ViaAlertaDarkColorScheme = darkColorScheme(
    primary = Amber500,
    onPrimary = Color.Black,
    primaryContainer = Amber600,
    onPrimaryContainer = Color.White,
    secondary = AlertRed500,
    onSecondary = Color.White,
    secondaryContainer = AlertRed700,
    onSecondaryContainer = Color.White,
    tertiary = SafetyGreen500,
    onTertiary = Color.Black,
    tertiaryContainer = SafetyGreen400,
    onTertiaryContainer = Color.Black,
    background = Slate950,
    onBackground = Slate50,
    surface = Slate900,
    onSurface = Slate50,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate200,
    outline = Slate700,
    outlineVariant = Slate800
)

@Composable
fun ViaAlertaTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ViaAlertaDarkColorScheme,
        typography = Typography,
        content = content
    )
}
