package com.vasilisneo.trackstar.data.auth

import org.junit.Assert.assertEquals
import org.junit.Test

/** A forced sign-out waits while an activity is recording or a workout is in progress, then runs
 *  once both are saved / discarded. */
class SignOutGuardTest {

    @Test
    fun signsOutRightAwayWhenNothingIsInProgress() {
        var signOuts = 0
        SignOutGuard.signOut { signOuts++ }
        assertEquals(1, signOuts)
    }

    @Test
    fun waitsForTheRecordingThenSignsOutOnce() {
        var signOuts = 0
        val activity = Any()
        SignOutGuard.hold(activity)
        SignOutGuard.signOut { signOuts++ }
        SignOutGuard.signOut { signOuts++ }   // another failed request meanwhile
        assertEquals(0, signOuts)

        SignOutGuard.release(activity)
        assertEquals(1, signOuts)

        SignOutGuard.release(activity)        // a second release does nothing
        assertEquals(1, signOuts)
    }

    @Test
    fun waitsForBothAWorkoutAndARecording() {
        var signOuts = 0
        val workout = Any()
        val activity = Any()
        SignOutGuard.hold(workout)
        SignOutGuard.hold(activity)
        SignOutGuard.signOut { signOuts++ }

        SignOutGuard.release(activity)
        assertEquals(0, signOuts)             // the workout still holds it

        SignOutGuard.release(workout)
        assertEquals(1, signOuts)
    }
}
