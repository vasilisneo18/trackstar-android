package com.vasilisneo.trackstar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Trackstar is dark-first, matching the iOS app's default "Midnight" theme.
private val DarkColors = darkColorScheme(
    primary = TrackstarAccent,
    onPrimary = TrackstarOnPrimary,
    background = TrackstarBackground,
    surface = TrackstarSurface,
)

private val LightColors = lightColorScheme(
    primary = TrackstarAccent,
    onPrimary = TrackstarOnPrimary,
)

@Composable
fun TrackstarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
