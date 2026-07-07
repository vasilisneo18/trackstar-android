package com.vasilisneo.trackstar.ui.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

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
