package com.vasilisneo.trackstar.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.vasilisneo.trackstar.data.billing.AppPlan

// Kotlin mirror of iOS's AppTheme (Trackstar/Model/AppTheme.swift): the nine selectable
// background themes, their accent colors, and the top-of-screen gradient glow. The accent
// values are the exact iOS RGB/HSB colors converted to sRGB hex. Bronze/Silver/Gold are the
// subscription-gated tiers — each unlocks at its own plan (mirrors iOS FeatureGate.canSelectTheme:
// bronze theme needs Bronze+, silver needs Silver+, gold needs Gold).
enum class AppTheme(
    val id: String,
    val displayName: String,
    val accent: Color,
    // Alpha applied to the accent for the gradient's top stop. iOS uses 0.12 for Midnight's
    // subtle blue haze and 0.65 for the bolder accent glow of every other theme.
    private val gradientTopAlpha: Float,
    // Minimum plan required to select this theme; FREE = always available.
    val requiredPlan: AppPlan = AppPlan.FREE,
) {
    MIDNIGHT("midnight", "Midnight", Color(0xFF2E80FF), 0.12f),
    BLUE("blue", "Ocean", Color(0xFF2E80FF), 0.65f),
    GREEN("green", "Forest", Color(0xFF2EB852), 0.65f),
    ORANGE("orange", "Sunset", Color(0xFFFF8C1A), 0.65f),
    RED("red", "Fire", Color(0xFFF24747), 0.65f),
    PINK("pink", "Rose", Color(0xFFF24D99), 0.65f),
    BRONZE("bronze", "Bronze", Color(0xFFD98436), 0.65f, requiredPlan = AppPlan.BRONZE),
    SILVER("silver", "Silver", Color(0xFFD1D6EB), 0.65f, requiredPlan = AppPlan.SILVER),
    GOLD("gold", "Gold", Color(0xFFFFBE2E), 0.65f, requiredPlan = AppPlan.GOLD);

    // The gradient's top stop (semi-transparent accent); the bottom stop is always the opaque
    // base TrackstarBackground. Drawn over black, this composites to iOS's exact top glow.
    val gradientTop: Color get() = accent.copy(alpha = gradientTopAlpha)

    // Whether the given plan may select this theme (iOS FeatureGate.canSelectTheme).
    fun isUnlocked(plan: AppPlan): Boolean = plan.atLeast(requiredPlan)

    companion object {
        fun fromId(id: String?): AppTheme = entries.firstOrNull { it.id == id } ?: MIDNIGHT
    }
}

// Process-global reactive theme. Reading `currentAppTheme` (directly, or transitively via
// TrackstarAccent / Modifier.trackstarBackground()) inside composition subscribes to it, so a
// theme change from the Appearance screen recomposes the whole app — the same effect iOS gets
// from @AppStorage(AppTheme.storageKey) driving .appBackground()/.accentColor everywhere.
private var themeState by mutableStateOf(AppTheme.MIDNIGHT)

val currentAppTheme: AppTheme get() = themeState

private const val THEME_PREFS = "trackstar_prefs"
private const val THEME_KEY = "appTheme" // matches iOS AppTheme.storageKey

// Call once at startup (before setContent) so the first frame already reflects the saved theme.
fun loadSavedTheme(context: Context) {
    val prefs = context.getSharedPreferences(THEME_PREFS, Context.MODE_PRIVATE)
    themeState = AppTheme.fromId(prefs.getString(THEME_KEY, null))
}

// Persist + apply a theme selection.
fun selectAppTheme(context: Context, theme: AppTheme) {
    context.getSharedPreferences(THEME_PREFS, Context.MODE_PRIVATE).edit()
        .putString(THEME_KEY, theme.id).apply()
    themeState = theme
}
