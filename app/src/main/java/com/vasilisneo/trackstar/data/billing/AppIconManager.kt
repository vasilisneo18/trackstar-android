package com.vasilisneo.trackstar.data.billing

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

// Android analogue of iOS's AppIcon: swaps the launcher icon to match the subscription plan by
// enabling exactly one <activity-alias> (declared in AndroidManifest) and disabling the others.
// Free -> Default (graphite); Bronze/Silver/Gold -> their tier icon.
//
// A stored marker guards against re-toggling when the icon is already correct: flipping an
// activity-alias can make the launcher entry flicker (and some launchers drop/re-add the icon or
// kill the task), so we only touch components when the plan actually changed.
object AppIconManager {

    private const val PREFS = "app_icon"
    private const val KEY_VARIANT = "variant"

    // Must match the android:name of each <activity-alias> in AndroidManifest.xml.
    private const val DEFAULT = ".MainActivityDefault"
    private const val BRONZE = ".MainActivityBronze"
    private const val SILVER = ".MainActivitySilver"
    private const val GOLD = ".MainActivityGold"
    private val allAliases = listOf(DEFAULT, BRONZE, SILVER, GOLD)

    private fun aliasFor(plan: AppPlan): String = when (plan) {
        AppPlan.FREE -> DEFAULT
        AppPlan.BRONZE -> BRONZE
        AppPlan.SILVER -> SILVER
        AppPlan.GOLD -> GOLD
    }

    fun set(context: Context, plan: AppPlan) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Default state matches the manifest (DEFAULT alias enabled) = Free on a fresh install.
        if (prefs.getString(KEY_VARIANT, AppPlan.FREE.name) == plan.name) return

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
    }
}
