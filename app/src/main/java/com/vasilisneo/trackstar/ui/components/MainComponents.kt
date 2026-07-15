package com.vasilisneo.trackstar.ui.components

// Shared building blocks for the main app's tab screens (Workout, Stats, MyTeam, Diet),
// replicating Trackstar/UI/UIComponents/ProfileNavButton.swift on iOS.

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vasilisneo.trackstar.data.billing.AppPlan
import com.vasilisneo.trackstar.data.billing.BillingManager

// Subscription-tier accent, matching iOS's ProfileNavButton / AppPlan.accentColor. Free = white.
fun tierAccentColor(plan: AppPlan): Color = when (plan) {
    AppPlan.GOLD -> Color(0xFFFFC61A)
    AppPlan.SILVER -> Color(0xFFB8BFD1)
    AppPlan.BRONZE -> Color(0xFFCC8033)
    AppPlan.FREE -> Color.White
}

/**
 * Matches ProfileNavButton on iOS: a 44dp glass circle containing two concentric tinted
 * circles (30dp/20dp) + centered initials, tinted by the signed-in user's subscription tier
 * (gold/silver/bronze, white for free) — observed live from BillingManager.currentPlan.
 */
@Composable
fun ProfileAvatarButton(
    initials: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val plan by BillingManager.currentPlan.collectAsState()
    val tier = tierAccentColor(plan)
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.15f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.size(30.dp).background(tier.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier.size(20.dp).background(tier.copy(alpha = 0.18f), CircleShape)
            )
        }
        Text(initials, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

/** Derives initials the same way iOS's MyWorkoutView.userInitials does: first + last
 *  initial from a display name, "?" if blank. No session/Keychain-equivalent exists on
 *  Android yet, so callers currently always pass null/blank until login persists a name. */
fun initialsFrom(name: String?): String {
    if (name.isNullOrBlank()) return "?"
    val parts = name.trim().split(" ")
    val first = parts.firstOrNull()?.take(1) ?: "?"
    val last = if (parts.size > 1) parts.last().take(1) else ""
    return (first + last).uppercase()
}
