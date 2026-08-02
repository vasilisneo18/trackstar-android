package com.vasilisneo.trackstar.ui.screens.main

import androidx.test.core.app.ApplicationProvider
import com.vasilisneo.trackstar.data.api.AuthResponse
import com.vasilisneo.trackstar.data.api.ProfileResponse
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.auth.ProfileRepository
import com.vasilisneo.trackstar.data.auth.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ProfileViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() {
        Dispatchers.resetMain()
        TokenStore(ApplicationProvider.getApplicationContext()).clearAll()
    }

    private class FakeProfileRepo(private val result: ApiResult<ProfileResponse>) : ProfileRepository() {
        override suspend fun getProfile() = result
    }

    private fun profile(first: String? = "First", last: String? = "Last") = ProfileResponse(
        id = "u1", email = "a@b.com", firstName = first, lastName = last, age = 30, role = "athlete",
        gender = null, height = null, weight = null, targetWeight = null, country = null,
        coachName = null, coachingSince = null,
    )

    private fun app() = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun seedSession(first: String?, last: String?, email: String) {
        TokenStore(app()).save(
            AuthResponse(token = "t", refreshToken = "r", userId = "u1", email = email, firstName = first, lastName = last, role = "athlete"),
        )
    }

    @Test fun `fetch populates the profile from the repository`() = runTest(dispatcher.scheduler) {
        val vm = ProfileViewModel(app(), FakeProfileRepo(ApiResult.Success(profile())))
        advanceUntilIdle()
        assertEquals("u1", vm.profile?.id)
        assertEquals("First", vm.profile?.firstName)
    }

    @Test fun `fetch leaves the profile null on error`() = runTest(dispatcher.scheduler) {
        val vm = ProfileViewModel(app(), FakeProfileRepo(ApiResult.Error("boom")))
        advanceUntilIdle()
        assertNull(vm.profile)
    }

    @Test fun `cached name and email come from the signed-in session`() = runTest(dispatcher.scheduler) {
        seedSession("Vas", "Neo", "vas@x.com")
        val vm = ProfileViewModel(app(), FakeProfileRepo(ApiResult.Error("offline")))
        assertEquals("Vas Neo", vm.cachedFullName)
        assertEquals("vas@x.com", vm.cachedEmail)
    }

    @Test fun `cached identity falls back when nothing is stored`() = runTest(dispatcher.scheduler) {
        val vm = ProfileViewModel(app(), FakeProfileRepo(ApiResult.Error("offline")))
        assertEquals("Trackstar User", vm.cachedFullName)
        assertEquals("—", vm.cachedEmail)
    }

    @Test fun `logout clears the session`() = runTest(dispatcher.scheduler) {
        seedSession("Vas", "Neo", "vas@x.com")
        val vm = ProfileViewModel(app(), FakeProfileRepo(ApiResult.Error("offline")))
        vm.logout()
        assertFalse(TokenStore(app()).isLoggedIn)
        assertNull(TokenStore(app()).token)
    }
}
