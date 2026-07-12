package com.vasilisneo.trackstar.data.api

import com.google.gson.JsonArray
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

// Weekly diet plan endpoints (/api/diet). The backend stores planData as an opaque blob, so the
// shape here must match iOS's WeeklyDietPlan Codable exactly for cross-platform interop:
//   { "planData": { "meals": { "Monday": [DietMeal...], ..., "Sunday": [...] } } }
interface DietApi {
    @GET("diet")
    suspend fun getDiet(): Response<DietSyncResponse>

    @POST("diet")
    suspend fun saveDiet(@Body request: DietSyncRequest): Response<DietSyncResponse>
}

data class DietSyncRequest(val planData: WeeklyDietPlanDto)
data class DietSyncResponse(val userId: String? = null, val planData: WeeklyDietPlanDto = WeeklyDietPlanDto())

// meals keyed by weekday name ("Monday" … "Sunday"), matching iOS's [DayTabModel: [DietMeal]].
data class WeeklyDietPlanDto(val meals: Map<String, List<DietMeal>> = emptyMap())

// iOS's WeeklyDietPlan.meals is [DayTabModel: [DietMeal]] with a plain String-enum key that doesn't
// conform to CodingKeyRepresentable, so Swift's JSONEncoder emits it as a FLAT ARRAY, not an object:
//   "meals": ["Monday", [DietMeal...], "Tuesday", [...], ...]
// Gson can't map that array to a Map, so without this adapter Android reads an empty plan. We read
// both the array form (iOS) and the object form (defensive), and always WRITE the array form so iOS
// can read Android's saves too. Register on the shared Gson for WeeklyDietPlanDto only.
class WeeklyDietPlanAdapter : JsonDeserializer<WeeklyDietPlanDto>, JsonSerializer<WeeklyDietPlanDto> {
    private val mealListType: Type = object : TypeToken<List<DietMeal>>() {}.type

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): WeeklyDietPlanDto {
        val mealsEl = json.asJsonObject.get("meals") ?: return WeeklyDietPlanDto()
        val map = LinkedHashMap<String, List<DietMeal>>()
        when {
            mealsEl.isJsonArray -> {
                val arr = mealsEl.asJsonArray
                var i = 0
                while (i + 1 < arr.size()) {
                    val day = arr[i].asString
                    map[day] = context.deserialize(arr[i + 1], mealListType)
                    i += 2
                }
            }
            mealsEl.isJsonObject -> {
                for ((day, meals) in mealsEl.asJsonObject.entrySet()) {
                    map[day] = context.deserialize(meals, mealListType)
                }
            }
        }
        return WeeklyDietPlanDto(map)
    }

    override fun serialize(src: WeeklyDietPlanDto, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val arr = JsonArray()
        for ((day, meals) in src.meals) {
            arr.add(day)
            arr.add(context.serialize(meals, mealListType))
        }
        return JsonObject().apply { add("meals", arr) }
    }
}

// Mirrors iOS DietMeal. `type` is a raw label ("Breakfast"/"Lunch"/"Dinner"/"Snack"); `time` is
// epoch seconds (iOS's default JSONEncoder .deferredToDate) or null.
data class DietMeal(
    val id: String,
    val type: String,
    val name: String = "",
    val foods: List<FoodItem> = emptyList(),
    val notes: String = "",
    val time: Double? = null,
    val isConsumed: Boolean = false,
) {
    val totalCalories: Int get() = foods.sumOf { it.calories }
    val totalProtein: Double get() = foods.sumOf { it.protein }
    val totalCarbs: Double get() = foods.sumOf { it.carbs }
    val totalFat: Double get() = foods.sumOf { it.fat }
}

data class FoodItem(
    val id: String,
    val name: String = "",
    val amount: String = "",      // e.g. "100g", "1 cup"
    val calories: Int = 0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
)
