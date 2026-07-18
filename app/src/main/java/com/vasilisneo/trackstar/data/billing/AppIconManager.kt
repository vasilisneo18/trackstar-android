package com.vasilisneo.trackstar.data.billing

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

// Android analogue of iOS's AppIcon: swaps the launcher icon to match the subscription plan by
// enabling exactly one <activity-alias> (declared in AndroidManifest) and disabling the others.
// Free -> Default (graphite); Bronze/Silver/Gold -> their tier icon.
//
// IMPORTANT — why the swap is deferred: toggling an activity-alias that carries the LAUNCHER
// intent makes the launcher re-home the running task, which yanks the app to the background the
// instant we do it. If we swapped the moment the plan changed (e.g. right after login/logout),
// the user would be bounced out of the app on every plan change. So set() only *queues* the
// desired plan; applyPending() performs the actual component toggle, and it's called only when
// the app has gone to the background (see TrackstarApplication) — so the unavoidable launcher
// flicker happens while the user is already away, never mid-use. The launcher icon isn't visible
// while the app is foreground anyway, so there's nothing to gain by swapping sooner.
//
// A stored marker (KEY_VARIANT) records which alias is currently enabled so we skip the toggle
// entirely when the icon already matches the plan.
object AppIconManager {

    private const val PREFS = "app_icon"
    private const val KEY_VARIANT = "variant"

    // Must match the android:name of each <activity-alias> in AndroidManifest.xml.
    private const val DEFAULT = ".MainActivityDefault"
    private const val BRONZE = ".MainActivityBronze"
    private const val SILVER = ".MainActivitySilver"
    private const val GOLD = ".MainActivityGold"
    private val allAliases = listOf(DEFAULT, BRONZE, SILVER, GOLD)

    // The plan the launcher icon should reflect, applied the next time the app backgrounds.
    // null = nothing queued (icon already correct, or the queued change was applied).
    @Volatile
    private var pendingPlan: AppPlan? = null

    private fun aliasFor(plan: AppPlan): String = when (plan) {
        AppPlan.FREE -> DEFAULT
        AppPlan.BRONZE -> BRONZE
        AppPlan.SILVER -> SILVER
        AppPlan.GOLD -> GOLD
    }

    // Queue the icon to match `plan`. Cheap and non-disruptive: no components are touched here.
    // If the icon already matches (or matches a still-pending queued change), this is a no-op.
    fun set(context: Context, plan: AppPlan) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Default state matches the manifest (DEFAULT alias enabled) = Free on a fresh install.
        if (prefs.getString(KEY_VARIANT, AppPlan.FREE.name) == plan.name) {
            pendingPlan = null
            return
        }
        pendingPlan = plan
    }

    // Apply any queued icon change now. Called from TrackstarApplication when the app enters the
    // background, so the launcher re-home happens while the user is away rather than mid-use.
    fun applyPending(context: Context) {
        val plan = pendingPlan ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_VARIANT, AppPlan.FREE.name) == plan.name) {
            pendingPlan = null
            return
        }

        val pkg = context.packageName
        val target = aliasFor(plan)
        val pm = context.packageManager
        allAliases.forEach { alias ->
            pm.setComponentEnabledSetting(
                ComponentName(pkg, "$pkg$alias"),
                if (alias == target) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        prefs.edit().putString(KEY_VARIANT, plan.name).apply()
        pendingPlan = null
    }
}
