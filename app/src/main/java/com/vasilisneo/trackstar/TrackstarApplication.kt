package com.vasilisneo.trackstar

import android.app.Application
import com.vasilisneo.trackstar.data.auth.TokenStore
import com.vasilisneo.trackstar.data.billing.BillingManager

// Seeds the in-memory JWT (AuthTokenHolder) from persisted prefs on launch, so the network
// auth interceptor has the token available before any screen constructs a TokenStore.
class TrackstarApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val tokenStore = TokenStore(this) // constructor seeds AuthTokenHolder

        // Bring up RevenueCat and, if a session already exists, re-identify the customer so plan
        // state is current on cold launch. No-op until an RC Android key is configured.
        BillingManager.configure(this, BuildConfig.REVENUECAT_API_KEY)
        tokenStore.userId?.let { BillingManager.logIn(it) }
    }
}
