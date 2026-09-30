package com.erohsik.pixels.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.erohsik.pixels.data.ThemeMode

private val LightScheme = lightColorScheme(
    primary = Accent,
    onPrimary = Paper,
    primaryContainer = AccentContainer,
    onPrimaryContainer = Ink,
    secondary = Accent,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = PaperRaised,
    onSurfaceVariant = InkMuted,
    surfaceContainerLow = PaperRaised,
    surfaceContainer = PaperRaised,
    surfaceContainerHigh = PaperRaised,
    error = Danger,
)

private val DarkScheme = darkColorScheme(
    primary = NightAccent,
    onPrimary = Night,
    primaryContainer = NightAccentContainer,
    onPrimaryContainer = NightInk,
    secondary = NightAccent,
    background = Night,
    onBackground = NightInk,
    surface = Night,
    onSurface = NightInk,
    surfaceVariant = NightRaised,
    onSurfaceVariant = NightInkMuted,
    surfaceContainerLow = NightRaised,
    surfaceContainer = NightRaised,
    surfaceContainerHigh = NightRaised,
    error = NightDanger,
)

/** Dynamic colour is deliberately not used: it would drift the chrome and tempt palette tinting. */
@Composable
fun PixelsTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = PixelsTypography,
        content = content,
    )
}
