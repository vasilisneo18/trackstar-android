package com.vasilisneo.trackstar.ui.screens.main.diet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vasilisneo.trackstar.data.api.DietMeal
import com.vasilisneo.trackstar.data.api.DietPlanDay
import com.vasilisneo.trackstar.data.api.DietPlanInput
import com.vasilisneo.trackstar.data.api.FoodItem
import com.vasilisneo.trackstar.data.api.WeeklyDietPlanDto
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.workout.AiRepository
import com.vasilisneo.trackstar.data.workout.DietRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID

// Fixed option lists, matching the fields the diet-generation prompt understands. The food chips
// are preferences passed straight through ("no preference" when none picked), so any sensible set
// works — these mirror iOS's AIDietPlannerSheet steps.
val AiTrainingTypes = listOf("Strength Training", "Bodybuilding", "Powerlifting", "CrossFit", "Running", "Cycling", "Team Sports", "General Fitness")
val AiBreakfastFoods = listOf("Eggs", "Oats", "Greek Yogurt", "Fruit", "Toast", "Pancakes", "Smoothie", "Cereal", "Bacon", "Avocado")
val AiLunchDinnerFoods = listOf("Chicken", "Beef", "Fish", "Turkey", "Rice", "Pasta", "Salad", "Potatoes", "Vegetables", "Tofu", "Beans", "Quinoa")
val AiSnackFoods = listOf("Nuts", "Protein Bar", "Fruit", "Yogurt", "Cheese", "Crackers", "Dark Chocolate", "Hummus")
val AiRestrictions = listOf("Vegetarian", "Vegan", "Pescatarian", "Gluten-Free", "Dairy-Free", "Nut-Free", "Halal", "Kosher", "Keto", "Low-Carb")
val AiCuisines = listOf("No preference", "Mediterranean", "Asian", "Italian", "Mexican", "Indian", "American", "Middle Eastern")

private val DayOrder = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
// The diet plan is keyed by full weekday names ("Monday".."Sunday") to match DietViewModel/getDiet.
private val DayDisplayName = mapOf(
    "monday" to "Monday", "tuesday" to "Tuesday", "wednesday" to "Wednesday", "thursday" to "Thursday",
    "friday" to "Friday", "saturday" to "Saturday", "sunday" to "Sunday",
)
private val MealTypeLabel = mapOf(
    "breakfast" to "Breakfast", "lunch" to "Lunch", "dinner" to "Dinner", "snack" to "Snack",
)

// 5-step wizard (body goal, training, food preferences, restrictions & cuisine, notes) that calls
// the same backend Claude diet endpoint iOS uses, then previews the 7-day plan before applying it
// to the user's weekly diet (replacing it, like iOS's onApply). Mirrors AIWorkoutPlannerViewModel.
class AIDietPlannerViewModel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val aiRepository = AiRepository()
    private val dietRepository = DietRepository()

    var step by mutableStateOf(0)
        private set
    val totalSteps = 5

    var currentWeightKg by mutableStateOf(80)
    var targetWeightKg by mutableStateOf(75)
    var timelineWeeks by mutableStateOf(12)
    var trainingDaysPerWeek by mutableStateOf(4)
    var trainingType by mutableStateOf(AiTrainingTypes.first())
    var mealsPerDay by mutableStateOf(4)
    var breakfastFoods by mutableStateOf<Set<String>>(emptySet())
        private set
    var lunchFoods by mutableStateOf<Set<String>>(emptySet())
        private set
    var dinnerFoods by mutableStateOf<Set<String>>(emptySet())
        private set
    var snackFoods by mutableStateOf<Set<String>>(emptySet())
        private set
    var restrictions by mutableStateOf<Set<String>>(emptySet())
        private set
    var cuisine by mutableStateOf(AiCuisines.first())
    var additionalNotes by mutableStateOf("")

    var isCheckingUsage by mutableStateOf(true)
        private set
    var limitReached by mutableStateOf(false)
        private set
    var usageUsed by mutableStateOf(0)
        private set
    var usageLimit by mutableStateOf(6)
        private set

    var isGenerating by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var generatedDays by mutableStateOf<Map<String, DietPlanDay>>(emptyMap())
        private set
    var isApplying by mutableStateOf(false)
        private set

    val hasGeneratedPlan: Boolean get() = generatedDays.isNotEmpty()

    init { checkUsage() }

    private fun checkUsage() {
        scope.launch {
            when (val result = aiRepository.getUsage()) {
                is ApiResult.Success -> {
                    val used = result.data.dietGenerationsUsed ?: 0
                    val limit = result.data.monthlyLimit ?: 6
                    usageUsed = used
                    usageLimit = limit
                    limitReached = used >= limit
                }
                is ApiResult.Error -> Unit // don't block the wizard on a failed usage check
            }
            isCheckingUsage = false
        }
    }

    fun toggleBreakfast(f: String) { breakfastFoods = breakfastFoods.toggle(f) }
    fun toggleLunch(f: String) { lunchFoods = lunchFoods.toggle(f) }
    fun toggleDinner(f: String) { dinnerFoods = dinnerFoods.toggle(f) }
    fun toggleSnack(f: String) { snackFoods = snackFoods.toggle(f) }
    fun toggleRestriction(r: String) { restrictions = restrictions.toggle(r) }

    private fun Set<String>.toggle(v: String) = if (contains(v)) this - v else this + v

    fun goNext() { if (step < totalSteps - 1) step++ }
    fun goBack() { if (step > 0) step-- }

    fun generate() {
        if (isGenerating) return
        isGenerating = true
        errorMessage = null
        scope.launch {
            val input = DietPlanInput(
                currentWeightKg = currentWeightKg,
                targetWeightKg = targetWeightKg,
                timelineWeeks = timelineWeeks,
                trainingDaysPerWeek = trainingDaysPerWeek,
                trainingType = trainingType,
                mealsPerDay = mealsPerDay,
                breakfastFoods = breakfastFoods.toList(),
                lunchFoods = lunchFoods.toList(),
                dinnerFoods = dinnerFoods.toList(),
                snackFoods = snackFoods.toList(),
                selectedRestrictions = restrictions.toList(),
                cuisinePreference = if (cuisine == AiCuisines.first()) "" else cuisine,
                additionalNotes = additionalNotes,
            )
            when (val result = aiRepository.generateDietPlan(input)) {
                is ApiResult.Success -> generatedDays = result.data.days.orEmpty()
                is ApiResult.Error -> {
                    errorMessage = result.message
                    if (result.message.contains("limit", ignoreCase = true) || result.message.contains("quota", ignoreCase = true)) {
                        limitReached = true
                    }
                }
            }
            isGenerating = false
        }
    }

    fun mealCount(day: String): Int = generatedDays[day]?.meals?.size ?: 0

    // Replaces the whole weekly diet with the generated plan (matching iOS's onApply).
    fun apply(onDone: (Boolean) -> Unit) {
        if (isApplying) return
        isApplying = true
        scope.launch {
            val week = mutableMapOf<String, List<DietMeal>>()
            for (day in DayOrder) {
                val dayPlan = generatedDays[day] ?: continue
                val meals = dayPlan.meals.orEmpty().map { it.toDietMeal() }
                if (meals.isNotEmpty()) week[DayDisplayName[day] ?: continue] = meals
            }
            when (dietRepository.saveDiet(WeeklyDietPlanDto(week))) {
                is ApiResult.Success -> onDone(true)
                is ApiResult.Error -> onDone(false)
            }
            isApplying = false
        }
    }

    fun dispose() { scope.cancel() }
}

private fun com.vasilisneo.trackstar.data.api.DietPlanMeal.toDietMeal(): DietMeal = DietMeal(
    id = UUID.randomUUID().toString(),
    type = MealTypeLabel[type?.lowercase()] ?: "Snack",
    name = name ?: "",
    foods = foods.orEmpty().map { food ->
        FoodItem(
            id = UUID.randomUUID().toString(),
            name = food.name ?: "",
            amount = food.amount ?: "",
            calories = food.calories ?: 0,
            protein = (food.protein ?: 0).toDouble(),
            carbs = (food.carbs ?: 0).toDouble(),
            fat = (food.fat ?: 0).toDouble(),
        )
    },
    notes = notes ?: "",
)
