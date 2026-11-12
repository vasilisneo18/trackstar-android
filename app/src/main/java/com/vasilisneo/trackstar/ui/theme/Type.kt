package com.vasilisneo.trackstar.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

val Typography = Typography(
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        // Relative, not a fixed 24sp: every Text inherits this style and most override only
        // fontSize, so a fixed value gave 12–13sp captions a 24sp line (cards taller than iOS).
        lineHeight = 1.25.em,
        letterSpacing = 0.5.sp
    )
)
