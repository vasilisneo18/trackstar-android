package com.vasilisneo.trackstar.data.workout

// The live workout, held for the whole app rather than inside a screen: MainAppScreen leaves
// composition whenever a full-screen route (Profile, Diet, History…) opens on top of it, and a
// `remember`ed session would be lost with it — a minimized workout must survive that, like iOS's
// ActiveSessionViewModel outliving its fullScreenCover. Cleared on discard, finish and logout.

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vasilisneo.trackstar.ui.screens.main.workout.ActiveSessionViewModel

object ActiveWorkout {
    var session by mutableStateOf<ActiveSessionViewModel?>(null)
    /** Full screen (true) or minimized to the mini-bar (false). */
    var showFull by mutableStateOf(false)

    /** Finished or discarded: also drops the copy saved on the phone. clear() alone (sign-out)
     *  keeps it, so the same user gets the workout back after signing in again. */
    fun end() {
        session?.end()
        clear()
    }

    fun clear() {
        session?.dispose()
        session = null
        showFull = false
    }
}
