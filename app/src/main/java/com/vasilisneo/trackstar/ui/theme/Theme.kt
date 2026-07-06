package com.vasilisneo.trackstar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Trackstar is dark-first — like the iOS app, theming is a deliberate in-app choice
// (AppTheme enum, defaults to "Midnight"), not something that should flip based on
// the OS light/dark setting. No light color scheme exists yet; add one alongside a
// real in-app theme picker later, matching iOS's AppearanceView.
private val DarkColors = darkColorScheme(
    primary = TrackstarAccent,
    onPrimary = TrackstarOnPrimary,
    background = TrackstarBackground,
    surface = TrackstarSurface,
)

@Composable
fun TrackstarTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = Typography,
        content = content
    )
}
