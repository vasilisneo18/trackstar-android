package com.vasilisneo.trackstar

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.vasilisneo.trackstar.data.auth.TokenStore
import com.vasilisneo.trackstar.data.billing.AppIconManager
import com.vasilisneo.trackstar.data.billing.BillingManager
import com.vasilisneo.trackstar.data.local.LocalStore
import com.vasilisneo.trackstar.data.local.PendingSyncService
import com.vasilisneo.trackstar.data.network.ConnectivityMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// Seeds the in-memory JWT (AuthTokenHolder) from persisted prefs on launch, so the network
// auth interceptor has the token available before any screen constructs a TokenStore.
class TrackstarApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Count of started (visible) activities. Drops to 0 exactly when the app goes to the
    // background — our cue to apply any queued launcher-icon change (see AppIconManager).
    private var startedActivities = 0

    override fun onCreate() {
        super.onCreate()

        // Bring up the local cache (Room) and connectivity tracking before any screen/repository
        // runs, so cache-then-network reads and the offline banner work from the first frame.
        LocalStore.init(this)
        ConnectivityMonitor.start(this)
        // Replays any offline writes queued in the outbox once connectivity is available.
        PendingSyncService.start()

        val tokenStore = TokenStore(this) // constructor seeds AuthTokenHolder

        // Bring up RevenueCat and, if a session already exists, re-identify the customer so plan
        // state is current on cold launch. No-op until an RC Android key is configured.
        BillingManager.configure(this, BuildConfig.REVENUECAT_API_KEY)
        tokenStore.userId?.let { BillingManager.logIn(it) }

        // Refresh the FCM push token with the backend on launch if already signed in (covers a token
        // that rotated while the app was closed).
        if (tokenStore.isLoggedIn) {
            appScope.launch { com.vasilisneo.trackstar.data.push.PushTokenRegistrar.register() }
        }

        // Keep the launcher icon in sync with the plan (mirrors iOS RevenueCatManager.updateAppIcon).
        // set() only *queues* the change — it's applied when the app next backgrounds so the
        // launcher re-home doesn't yank the user out mid-use (see AppIconManager).
        appScope.launch {
            BillingManager.currentPlan.collect { plan ->
                AppIconManager.set(this@TrackstarApplication, plan)
            }
        }

        // Apply the queued icon swap the moment the app enters the background.
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivities++
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities--
                if (startedActivities <= 0) {
                    AppIconManager.applyPending(this@TrackstarApplication)
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
