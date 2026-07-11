package com.vasilisneo.trackstar.ui.theme

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// The accent follows the user's selected Appearance theme (Trackstar/Model/AppTheme.swift on
// iOS). It's a getter over reactive theme state, so the ~60 existing `TrackstarAccent` reads
// across the app recompose automatically when the theme changes — no per-call-site refactor.
val TrackstarAccent: Color get() = currentAppTheme.accent

// The base background and neutral surfaces are constant across all themes (only the accent and
// the top gradient glow change), matching iOS where every theme's gradient bottoms out at this base.
val TrackstarBackground = Color(0xFF0D0D17)
val TrackstarSurface = Color(0xFF16161F)
val TrackstarOnPrimary = Color(0xFFFFFFFF)

// Faithful replication of AppTheme.gradient: a top→bottom LinearGradient whose top stop is the
// current theme's accent at a low alpha and whose bottom stop is the opaque base. Drawing it over
// a solid black underlay reproduces iOS's alpha-composited top glow exactly. Reads the reactive
// theme state, so full-screen roots re-tint when the user picks a new theme.
fun Modifier.trackstarBackground(): Modifier =
    this
        .background(Color.Black)
        .background(Brush.verticalGradient(listOf(currentAppTheme.gradientTop, TrackstarBackground)))
