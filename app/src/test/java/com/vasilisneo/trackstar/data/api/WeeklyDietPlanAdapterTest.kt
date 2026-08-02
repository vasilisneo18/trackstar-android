package com.vasilisneo.trackstar.data.api

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// The diet plan's `meals` map is (de)serialized specially so Android and iOS interop: iOS encodes a
// Swift dictionary as a flat JSON array [key, value, key, value, ...]. WeeklyDietPlanAdapter must
// write that array form and read BOTH the array form (iOS/our own writes) and the plain-object form.
class WeeklyDietPlanAdapterTest {

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(WeeklyDietPlanDto::class.java, WeeklyDietPlanAdapter())
        .create()

    private fun meal(id: String, type: String) = DietMeal(id = id, type = type, name = type)

    @Test
    fun `serialize writes meals as a flat key-value array`() {
        val plan = WeeklyDietPlanDto(mapOf("monday" to listOf(meal("m1", "breakfast"))))
        val json = JsonParser.parseString(gson.toJson(plan)).asJsonObject
        val meals = json.get("meals")
        assertTrue("meals should be a JSON array", meals.isJsonArray)
        // [ "monday", [ {meal} ] ]
        assertEquals("monday", meals.asJsonArray[0].asString)
        assertTrue(meals.asJsonArray[1].isJsonArray)
        assertEquals(1, meals.asJsonArray[1].asJsonArray.size())
    }

    @Test
    fun `round-trips through the array form`() {
        val plan = WeeklyDietPlanDto(
            mapOf(
                "monday" to listOf(meal("m1", "breakfast"), meal("m2", "lunch")),
                "tuesday" to listOf(meal("m3", "dinner")),
            ),
        )
        val back = gson.fromJson(gson.toJson(plan), WeeklyDietPlanDto::class.java)
        assertEquals(2, back.meals.size)
        assertEquals(listOf("m1", "m2"), back.meals["monday"]!!.map { it.id })
        assertEquals(listOf("m3"), back.meals["tuesday"]!!.map { it.id })
        assertEquals("breakfast", back.meals["monday"]!!.first().type)
    }

    @Test
    fun `deserializes the plain-object form too`() {
        val objForm = """{"meals":{"monday":[{"id":"m1","type":"breakfast","name":"Eggs"}]}}"""
        val plan = gson.fromJson(objForm, WeeklyDietPlanDto::class.java)
        assertEquals(1, plan.meals.size)
        assertEquals("m1", plan.meals["monday"]!!.single().id)
        assertEquals("Eggs", plan.meals["monday"]!!.single().name)
    }

    @Test
    fun `missing meals key yields an empty plan`() {
        val plan = gson.fromJson("""{}""", WeeklyDietPlanDto::class.java)
        assertTrue(plan.meals.isEmpty())
    }

    @Test
    fun `empty plan serializes to an empty meals array`() {
        val json = JsonParser.parseString(gson.toJson(WeeklyDietPlanDto())).asJsonObject
        assertTrue(json.get("meals").isJsonArray)
        assertEquals(0, json.get("meals").asJsonArray.size())
    }
}
