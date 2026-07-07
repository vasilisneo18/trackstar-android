package com.vasilisneo.trackstar

import android.app.Application
import com.vasilisneo.trackstar.data.auth.TokenStore

// Seeds the in-memory JWT (AuthTokenHolder) from persisted prefs on launch, so the network
// auth interceptor has the token available before any screen constructs a TokenStore.
class TrackstarApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        TokenStore(this) // constructor seeds AuthTokenHolder
    }
}
