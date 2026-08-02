package com.vasilisneo.trackstar.data.local

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.vasilisneo.trackstar.data.api.DietMeal
import com.vasilisneo.trackstar.data.api.ExerciseData
import com.vasilisneo.trackstar.data.api.PlannedSessionRequest
import com.vasilisneo.trackstar.data.api.WeeklyDietPlanAdapter
import com.vasilisneo.trackstar.data.api.WeeklyDietPlanDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Guards the offline-sync serialization contract: Outbox writes each queued write as JSON, and
// PendingSyncService reads it back with fromJson(payload, XPayload::class.java). These round-trips
// ensure the two stay in sync (a dropped/renamed field would surface here). Uses a Gson configured
// like the app's (diet adapter registered) so DietSavePayload round-trips faithfully.
class OutboxPayloadTest {

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(WeeklyDietPlanDto::class.java, WeeklyDietPlanAdapter())
        .create()

    private inline fun <reified T> roundTrip(value: T): T = gson.fromJson(gson.toJson(value), T::class.java)

    private fun request(id: String) = PlannedSessionRequest(
        id = id, weekIdentifier = "2026-W31", day = "monday", orderIndex = 0, title = "Push",
        exercises = listOf(
            ExerciseData(id = "e1", name = "Bench", sets = emptyList(), frequencyType = null,
                resistanceType = null, resistanceUnit = null, compoundGroupId = null),
        ),
    )

    @Test fun `plan upsert payload round-trips with athleteId`() {
        val p = Outbox.PlanUpsertPayload(request("s1"), athleteId = "athlete-9")
        val back = roundTrip(p)
        assertEquals("s1", back.request.id)
        assertEquals("2026-W31", back.request.weekIdentifier)
        assertEquals("Bench", back.request.exercises.single().name)
        assertEquals("athlete-9", back.athleteId)
    }

    @Test fun `plan upsert payload round-trips with a null athleteId`() {
        val back = roundTrip(Outbox.PlanUpsertPayload(request("s2"), athleteId = null))
        assertNull(back.athleteId)
        assertEquals("s2", back.request.id)
    }

    @Test fun `plan delete payload round-trips`() {
        val back = roundTrip(Outbox.PlanDeletePayload(sessionId = "sess-7", athleteId = "a1"))
        assertEquals("sess-7", back.sessionId)
        assertEquals("a1", back.athleteId)
    }

    @Test fun `plan batch payload preserves order and size`() {
        val p = Outbox.PlanBatchPayload(listOf(request("a"), request("b"), request("c")), athleteId = null)
        val back = roundTrip(p)
        assertEquals(listOf("a", "b", "c"), back.requests.map { it.id })
        assertNull(back.athleteId)
    }

    @Test fun `diet save payload round-trips through the custom adapter`() {
        val plan = WeeklyDietPlanDto(
            mapOf("monday" to listOf(DietMeal(id = "m1", type = "breakfast", name = "Oats"))),
        )
        val back = roundTrip(Outbox.DietSavePayload(plan, athleteId = "coach-athlete"))
        assertEquals("coach-athlete", back.athleteId)
        assertEquals("Oats", back.plan.meals["monday"]!!.single().name)
    }
}
