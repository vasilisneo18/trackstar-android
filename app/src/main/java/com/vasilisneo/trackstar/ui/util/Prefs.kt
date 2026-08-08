package com.vasilisneo.trackstar.ui.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect

const val PREF_SESSION_BOOKING = "sessionBookingEnabled"

// One-shot read of a boolean pref (non-composable), same store as rememberBooleanPref.
fun booleanPref(context: Context, key: String, default: Boolean): Boolean =
    context.getSharedPreferences("trackstar_prefs", Context.MODE_PRIVATE).getBoolean(key, default)

// Read-only boolean pref that re-reads whenever the screen resumes, so a change made on the Settings
// screen is reflected when the user navigates back (rememberBooleanPref only reads once).
@Composable
fun rememberResumingBooleanPref(key: String, default: Boolean): Boolean {
    val context = LocalContext.current
    var value by remember { mutableStateOf(booleanPref(context, key, default)) }
    LifecycleResumeEffect(key) {
        value = booleanPref(context, key, default)
        onPauseOrDispose { }
    }
    return value
}

// SharedPreferences-backed boolean state — the Android equivalent of iOS's @AppStorage,
// so settings toggles actually persist across launches instead of resetting. Writing to
// the returned state's value both updates the UI and saves to prefs.
@Composable
fun rememberBooleanPref(key: String, default: Boolean): MutableState<Boolean> {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("trackstar_prefs", Context.MODE_PRIVATE) }
    return remember {
        val backing = mutableStateOf(prefs.getBoolean(key, default))
        object : MutableState<Boolean> by backing {
            override var value: Boolean
                get() = backing.value
                set(v) {
                    backing.value = v
                    prefs.edit().putBoolean(key, v).apply()
                }
        }
    }
}
