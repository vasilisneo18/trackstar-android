package com.vasilisneo.trackstar.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CacheDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: CacheDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.cacheDao()
    }

    @After fun tearDown() = db.close()

    @Test fun `put then get returns the stored json`() = runBlocking {
        dao.put(CacheEntry("u1", "profile", "{\"name\":\"Vas\"}"))
        assertEquals("{\"name\":\"Vas\"}", dao.get("u1", "profile"))
    }

    @Test fun `get returns null for a missing key`() = runBlocking {
        assertNull(dao.get("u1", "nope"))
    }

    @Test fun `put replaces an existing entry`() = runBlocking {
        dao.put(CacheEntry("u1", "diet", "v1"))
        dao.put(CacheEntry("u1", "diet", "v2"))
        assertEquals("v2", dao.get("u1", "diet"))
    }

    @Test fun `entries are isolated per user under the same key`() = runBlocking {
        dao.put(CacheEntry("u1", "plan:2026-W31", "u1-plan"))
        dao.put(CacheEntry("u2", "plan:2026-W31", "u2-plan"))
        assertEquals("u1-plan", dao.get("u1", "plan:2026-W31"))
        assertEquals("u2-plan", dao.get("u2", "plan:2026-W31"))
    }

    @Test fun `delete removes only the targeted entry`() = runBlocking {
        dao.put(CacheEntry("u1", "a", "1"))
        dao.put(CacheEntry("u1", "b", "2"))
        dao.delete("u1", "a")
        assertNull(dao.get("u1", "a"))
        assertEquals("2", dao.get("u1", "b"))
    }

    @Test fun `clearAll empties the table`() = runBlocking {
        dao.put(CacheEntry("u1", "a", "1"))
        dao.put(CacheEntry("u2", "b", "2"))
        dao.clearAll()
        assertNull(dao.get("u1", "a"))
        assertNull(dao.get("u2", "b"))
    }
}
