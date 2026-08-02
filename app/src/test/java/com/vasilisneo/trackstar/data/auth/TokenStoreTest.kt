package com.vasilisneo.trackstar.data.auth

import androidx.test.core.app.ApplicationProvider
import com.vasilisneo.trackstar.data.api.AuthResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// TokenStore persists the session + identity to SharedPreferences and mirrors the token/userId into
// AuthTokenHolder for the networking layer. Robolectric provides the SharedPreferences + Context.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TokenStoreTest {

    private fun store() = TokenStore(ApplicationProvider.getApplicationContext())

    private val auth = AuthResponse(
        token = "tok", refreshToken = "ref", userId = "user-1", email = "a@b.com",
        firstName = "Vas", lastName = "Neo", role = "athlete",
    )

    @After fun tearDown() {
        // Leave global auth state clean for other suites.
        store().clearAll()
    }

    @Test fun `save populates identity getters and marks logged in`() {
        val s = store()
        s.save(auth)
        assertEquals("tok", s.token)
        assertEquals("ref", s.refreshToken)
        assertEquals("user-1", s.userId)
        assertEquals("a@b.com", s.email)
        assertEquals("Vas", s.firstName)
        assertEquals("Neo", s.lastName)
        assertEquals("athlete", s.role)
        assertTrue(s.isLoggedIn)
    }

    @Test fun `save mirrors token and userId into AuthTokenHolder`() {
        store().save(auth)
        assertEquals("tok", AuthTokenHolder.token)
        assertEquals("user-1", AuthTokenHolder.userId)
    }

    @Test fun `saveCredentials enables cached quick-login`() {
        val s = store()
        assertFalse(s.hasCachedCredentials)
        s.saveCredentials("a@b.com", "pw")
        assertTrue(s.hasCachedCredentials)
        assertEquals("a@b.com", s.lastEmail)
        assertEquals("pw", s.lastPassword)
    }

    @Test fun `clear drops the session but keeps cached credentials`() {
        val s = store()
        s.save(auth)
        s.saveCredentials("a@b.com", "pw")
        s.clear()
        assertNull(s.token)
        assertNull(s.userId)
        assertFalse(s.isLoggedIn)
        assertNull(AuthTokenHolder.token)
        assertNull(AuthTokenHolder.userId)
        // "Continue as" credentials survive a normal logout.
        assertTrue(s.hasCachedCredentials)
    }

    @Test fun `clearAll wipes everything including cached credentials`() {
        val s = store()
        s.save(auth)
        s.saveCredentials("a@b.com", "pw")
        s.clearAll()
        assertNull(s.token)
        assertFalse(s.hasCachedCredentials)
        assertNull(AuthTokenHolder.token)
        assertNull(AuthTokenHolder.userId)
    }
}
