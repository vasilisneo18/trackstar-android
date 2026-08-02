package com.vasilisneo.trackstar.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.auth.AuthTokenHolder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Exercises cachedRead — the shared cache-then-network helper every read-cached repository delegates
// to. The important behaviours: cache on success, fall back to cache ONLY on an offline failure, and
// never mask a real (non-offline) server error with stale cache.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CachedReadTest {

    private data class Foo(val a: Int, val b: String)

    private lateinit var db: AppDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        LocalStore.setDatabaseForTest(db)
        AuthTokenHolder.userId = "u1"
    }

    @After fun tearDown() {
        db.close()
        AuthTokenHolder.userId = null
    }

    @Test fun `success returns the value and caches it`() = runBlocking {
        val result = cachedRead("foo") { ApiResult.Success(Foo(1, "x")) }
        assertTrue(result is ApiResult.Success)
        // The response JSON was written to the cache under (userId, key).
        assertNotNull(db.cacheDao().get("u1", "foo"))
    }

    @Test fun `offline failure falls back to the cached value`() = runBlocking {
        // Prime the cache with a success.
        cachedRead("foo") { ApiResult.Success(Foo(7, "cached")) }
        // Now the network is offline — should serve the cached Foo.
        val result = cachedRead<Foo>("foo") { ApiResult.Error("no net", offline = true) }
        assertTrue(result is ApiResult.Success)
        assertEquals(Foo(7, "cached"), (result as ApiResult.Success).data)
    }

    @Test fun `offline failure with no cache returns the error`() = runBlocking {
        val result = cachedRead<Foo>("missing") { ApiResult.Error("no net", offline = true) }
        assertTrue(result is ApiResult.Error)
        assertTrue((result as ApiResult.Error).offline)
    }

    @Test fun `a real server error is never masked by stale cache`() = runBlocking {
        // Cache holds a good value...
        cachedRead("foo") { ApiResult.Success(Foo(1, "old")) }
        // ...but a genuine (non-offline) error must surface, not the stale cache.
        val result = cachedRead<Foo>("foo") { ApiResult.Error("403 forbidden", offline = false) }
        assertTrue(result is ApiResult.Error)
        assertEquals("403 forbidden", (result as ApiResult.Error).message)
    }

    @Test fun `a later success overwrites the cached value`() = runBlocking {
        cachedRead("foo") { ApiResult.Success(Foo(1, "first")) }
        cachedRead("foo") { ApiResult.Success(Foo(2, "second")) }
        // Offline now serves the most recent success.
        val result = cachedRead<Foo>("foo") { ApiResult.Error("x", offline = true) }
        assertEquals(Foo(2, "second"), (result as ApiResult.Success).data)
    }

    @Test fun `with no signed-in user the fetch result passes through uncached`() = runBlocking {
        AuthTokenHolder.userId = null
        val ok = cachedRead("foo") { ApiResult.Success(Foo(9, "n")) }
        assertTrue(ok is ApiResult.Success)
        // Nothing was cached (no user to scope it to), so an offline read can't recover it.
        val offline = cachedRead<Foo>("foo") { ApiResult.Error("x", offline = true) }
        assertTrue(offline is ApiResult.Error)
    }
}
