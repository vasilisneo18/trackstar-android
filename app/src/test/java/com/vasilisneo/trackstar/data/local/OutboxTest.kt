package com.vasilisneo.trackstar.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vasilisneo.trackstar.data.api.PlannedSessionRequest
import com.vasilisneo.trackstar.data.api.WeeklyDietPlanDto
import com.vasilisneo.trackstar.data.api.WorkoutSessionData
import com.vasilisneo.trackstar.data.api.WorkoutSessionRequest
import com.vasilisneo.trackstar.data.auth.AuthTokenHolder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Verifies the offline-write outbox actually persists queued writes to pending_actions with the
// right type (complements OutboxPayloadTest, which only covered payload JSON round-trips).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class OutboxTest {

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

    private fun planRequest() = PlannedSessionRequest(
        id = "s1", weekIdentifier = "2026-W31", day = "monday", orderIndex = 0, title = "Push", exercises = emptyList(),
    )

    private fun sessionRequest() = WorkoutSessionRequest(
        clientId = "c1", date = "2026-01-01", durationSeconds = 60,
        sessionData = WorkoutSessionData(
            id = "sd", date = 0.0, completedAt = 0.0, durationSeconds = 60,
            exercises = emptyList(), planSessionId = null, title = "S",
        ),
    )

    @Test fun `enqueuePlanUpsert persists a PLAN_UPSERT action`() = runBlocking {
        assertTrue(Outbox.enqueuePlanUpsert(planRequest(), athleteId = null))
        val actions = db.pendingActionDao().forUser("u1")
        assertEquals(1, actions.size)
        assertEquals(Outbox.PLAN_UPSERT, actions.single().type)
    }

    @Test fun `enqueueDietSave persists a DIET_SAVE action`() = runBlocking {
        assertTrue(Outbox.enqueueDietSave(WeeklyDietPlanDto(), athleteId = "a1"))
        assertEquals(Outbox.DIET_SAVE, db.pendingActionDao().forUser("u1").single().type)
    }

    @Test fun `enqueueSessionSave persists a SESSION_SAVE action`() = runBlocking {
        assertTrue(Outbox.enqueueSessionSave(sessionRequest()))
        assertEquals(Outbox.SESSION_SAVE, db.pendingActionDao().forUser("u1").single().type)
    }

    @Test fun `multiple enqueues accumulate in order`() = runBlocking {
        Outbox.enqueuePlanUpsert(planRequest(), null)
        Outbox.enqueuePlanDelete("s9", null)
        Outbox.enqueueDietSave(WeeklyDietPlanDto(), null)
        assertEquals(
            listOf(Outbox.PLAN_UPSERT, Outbox.PLAN_DELETE, Outbox.DIET_SAVE),
            db.pendingActionDao().forUser("u1").map { it.type },
        )
    }

    @Test fun `enqueue is a no-op without a signed-in user`() = runBlocking {
        AuthTokenHolder.userId = null
        assertFalse(Outbox.enqueuePlanUpsert(planRequest(), null))
        // Nothing was written under any (previous) user.
        assertEquals(0, db.pendingActionDao().countForUser("u1"))
    }
}
