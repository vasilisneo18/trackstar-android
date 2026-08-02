package com.vasilisneo.trackstar.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PendingActionDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: PendingActionDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.pendingActionDao()
    }

    @After fun tearDown() = db.close()

    private fun action(user: String, type: String, payload: String = "{}") =
        PendingActionEntity(userId = user, type = type, payload = payload)

    @Test fun `forUser returns queued actions in FIFO insertion order`() = runBlocking {
        dao.insert(action("u1", "first"))
        dao.insert(action("u1", "second"))
        dao.insert(action("u1", "third"))
        assertEquals(listOf("first", "second", "third"), dao.forUser("u1").map { it.type })
    }

    @Test fun `insert assigns increasing ids`() = runBlocking {
        val id1 = dao.insert(action("u1", "a"))
        val id2 = dao.insert(action("u1", "b"))
        assertEquals(true, id2 > id1)
    }

    @Test fun `countForUser reflects inserts and is scoped per user`() = runBlocking {
        dao.insert(action("u1", "a"))
        dao.insert(action("u1", "b"))
        dao.insert(action("u2", "c"))
        assertEquals(2, dao.countForUser("u1"))
        assertEquals(1, dao.countForUser("u2"))
    }

    @Test fun `delete removes the given action, leaving the rest`() = runBlocking {
        dao.insert(action("u1", "drop-me"))
        dao.insert(action("u1", "keep-me"))
        val target = dao.forUser("u1").first { it.type == "drop-me" }
        dao.delete(target)
        assertEquals(listOf("keep-me"), dao.forUser("u1").map { it.type })
    }

    @Test fun `forUser is scoped per user`() = runBlocking {
        dao.insert(action("u1", "mine"))
        dao.insert(action("u2", "theirs"))
        assertEquals(listOf("mine"), dao.forUser("u1").map { it.type })
    }
}
