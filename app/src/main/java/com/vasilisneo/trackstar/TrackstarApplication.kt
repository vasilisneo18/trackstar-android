package com.vasilisneo.trackstar

import android.app.Application
import com.vasilisneo.trackstar.data.auth.TokenStore
import com.vasilisneo.trackstar.data.billing.AppIconManager
import com.vasilisneo.trackstar.data.billing.BillingManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// Seeds the in-memory JWT (AuthTokenHolder) from persisted prefs on launch, so the network
// auth interceptor has the token available before any screen constructs a TokenStore.
class TrackstarApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        val tokenStore = TokenStore(this) // constructor seeds AuthTokenHolder

        // Bring up RevenueCat and, if a session already exists, re-identify the customer so plan
        // state is current on cold launch. No-op until an RC Android key is configured.
        BillingManager.configure(this, BuildConfig.REVENUECAT_API_KEY)
        tokenStore.userId?.let { BillingManager.logIn(it) }

        // Keep the launcher icon in sync with the plan (mirrors iOS RevenueCatManager.updateAppIcon).
        // AppIconManager no-ops when the icon is already correct, so this is cheap on every launch.
        appScope.launch {
            BillingManager.currentPlan.collect { plan ->
                AppIconManager.set(this@TrackstarApplication, plan)
            }
        }
    }
}
