package com.vasilisneo.trackstar.data.auth

/** Holds off a forced sign-out (the session died — e.g. this account signed in on another device)
 *  while an activity is recording or waiting to be saved: signing out leaves the recorder's screen,
 *  and the recording would go with it. The sign-out runs once the activity is saved or discarded; a
 *  saved activity stays on the phone and uploads after the next sign-in. Mirrors iOS's SignOutGuard. */
object SignOutGuard {
    private var held = false
    private var pending: (() -> Unit)? = null

    @Synchronized fun hold() { held = true }

    fun release() {
        val signOut = synchronized(this) { held = false; pending.also { pending = null } }
        signOut?.invoke()
    }

    /** Runs [signOut] now, or once the hold is released. */
    fun signOut(signOut: () -> Unit) {
        val now = synchronized(this) { if (held) { pending = signOut; false } else true }
        if (now) signOut()
    }
}
