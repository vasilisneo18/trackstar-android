package com.vasilisneo.trackstar.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Room DAO tests run on the JVM via Robolectric against an in-memory database — no emulator.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class WorkoutSessionDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: WorkoutSessionDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.workoutSessionDao()
    }

    @After fun tearDown() = db.close()

    private fun row(user: String, key: String, json: String = "{}") =
        WorkoutSessionEntity(userId = user, cacheKey = key, date = "2026-01-01", json = json)

    @Test fun `insertAll then loadJson returns that user's rows`() = runBlocking {
        dao.insertAll(listOf(row("u1", "a", "{\"a\":1}"), row("u1", "b", "{\"b\":2}")))
        val loaded = dao.loadJson("u1")
        assertEquals(2, loaded.size)
        assertTrue(loaded.contains("{\"a\":1}"))
        assertTrue(loaded.contains("{\"b\":2}"))
    }

    @Test fun `loadJson is scoped per user`() = runBlocking {
        dao.insertAll(listOf(row("u1", "a"), row("u2", "b")))
        assertEquals(1, dao.loadJson("u1").size)
        assertEquals(1, dao.loadJson("u2").size)
    }

    @Test fun `insert conflict on userId plus cacheKey replaces the row`() = runBlocking {
        dao.insertAll(listOf(row("u1", "a", "{\"v\":1}")))
        dao.insertAll(listOf(row("u1", "a", "{\"v\":2}")))
        assertEquals(listOf("{\"v\":2}"), dao.loadJson("u1"))
    }

    @Test fun `replaceUser swaps a user's whole set atomically`() = runBlocking {
        dao.insertAll(listOf(row("u1", "old1"), row("u1", "old2")))
        dao.replaceUser("u1", listOf(row("u1", "new1")))
        assertEquals(1, dao.loadJson("u1").size)
    }

    @Test fun `clearUser only wipes that user`() = runBlocking {
        dao.insertAll(listOf(row("u1", "a"), row("u2", "b")))
        dao.clearUser("u1")
        assertTrue(dao.loadJson("u1").isEmpty())
        assertEquals(1, dao.loadJson("u2").size)
    }

    @Test fun `clearAll wipes every user`() = runBlocking {
        dao.insertAll(listOf(row("u1", "a"), row("u2", "b")))
        dao.clearAll()
        assertTrue(dao.loadJson("u1").isEmpty())
        assertTrue(dao.loadJson("u2").isEmpty())
    }
}
