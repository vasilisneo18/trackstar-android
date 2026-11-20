package com.vasilisneo.trackstar.data.auth

/** Holds off a forced sign-out (the session died — e.g. this account signed in on another device)
 *  while an activity is recording or a workout is in progress (or either waits to be saved):
 *  signing out leaves their screens, and they'd go with them. The sign-out runs once everything
 *  holding it is saved or discarded; what was saved stays on the phone and uploads after the next
 *  sign-in. Mirrors iOS's SignOutGuard. */
object SignOutGuard {
    /** One token per recording / workout holding it off. */
    private val holds = mutableSetOf<Any>()
    private var pending: (() -> Unit)? = null

    @Synchronized fun hold(token: Any) { holds.add(token) }

    fun release(token: Any) {
        val signOut = synchronized(this) {
            holds.remove(token)
            if (holds.isEmpty()) pending.also { pending = null } else null
        }
        signOut?.invoke()
    }

    /** Runs [signOut] now, or once nothing holds it off. */
    fun signOut(signOut: () -> Unit) {
        val now = synchronized(this) { if (holds.isEmpty()) true else { pending = signOut; false } }
        if (now) signOut()
    }
}
