package com.vasilisneo.trackstar.data.auth

import org.junit.Assert.assertEquals
import org.junit.Test

/** A forced sign-out waits while an activity is recording, then runs once it's saved / discarded. */
class SignOutGuardTest {

    @Test
    fun signsOutRightAwayWhenNothingIsRecording() {
        var signOuts = 0
        SignOutGuard.signOut { signOuts++ }
        assertEquals(1, signOuts)
    }

    @Test
    fun waitsForTheRecordingThenSignsOutOnce() {
        var signOuts = 0
        SignOutGuard.hold()
        SignOutGuard.signOut { signOuts++ }
        SignOutGuard.signOut { signOuts++ }   // another failed request meanwhile
        assertEquals(0, signOuts)

        SignOutGuard.release()
        assertEquals(1, signOuts)

        SignOutGuard.release()                // a second release does nothing
        assertEquals(1, signOuts)
    }
}
