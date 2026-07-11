package com.vasilisneo.trackstar.ui.screens.main.plan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vasilisneo.trackstar.data.api.ExerciseData
import com.vasilisneo.trackstar.data.api.ExerciseSet
import com.vasilisneo.trackstar.data.api.FrequencyValue
import com.vasilisneo.trackstar.data.api.PlannedSessionRequest
import com.vasilisneo.trackstar.data.api.ResistanceUnit
import com.vasilisneo.trackstar.data.api.ResistanceValue
import com.vasilisneo.trackstar.data.api.WorkoutPlanDay
import com.vasilisneo.trackstar.data.api.WorkoutPlanInput
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.workout.AiRepository
import com.vasilisneo.trackstar.data.workout.PlanRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID

// Fixed option lists copied verbatim from AIWorkoutPlanViewModel on iOS — not invented — since
// these are exactly the values the generation prompt understands.
val AiGoals = listOf("Build Muscle", "Lose Fat", "Get Stronger", "Improve Endurance", "Athletic Performance", "Stay Active")
val AiDurations = listOf(30, 45, 60, 90, 120)
val AiExperienceLevels = listOf("Beginner", "Intermediate", "Advanced")
val AiTrainingCategories = listOf("Gym", "Calisthenics", "Powerlifting", "Olympic", "Running", "Cycling", "Track & Field", "Swimming", "HIIT", "Yoga", "Stretching")
val AiWorkoutSplits = listOf("Push / Pull / Legs", "Upper / Lower", "Full Body", "Bro Split", "Any")

data class CategoryFocus(val label: String, val options: List<String>)

val AiCategoryFocusOptions: Map<String, CategoryFocus> = mapOf(
    "Gym" to CategoryFocus("Muscle groups", listOf("Chest", "Back", "Shoulders", "Arms", "Legs", "Core", "Full Body")),
    "Calisthenics" to CategoryFocus("Muscle groups", listOf("Chest", "Back", "Shoulders", "Arms", "Legs", "Core", "Full Body")),
    "Powerlifting" to CategoryFocus("Focus lifts", listOf("Squat", "Bench", "Deadlift", "Overhead Press")),
    "Olympic" to CategoryFocus("Focus movements", listOf("Snatch", "Clean & Jerk", "Front Squat")),
    "Running" to CategoryFocus("Distance goal", listOf("5K", "10K", "Half Marathon", "Marathon", "Ultra")),
    "Cycling" to CategoryFocus("Focus", listOf("Endurance", "Intervals", "Climbs", "Sprints")),
    "Swimming" to CategoryFocus("Strokes", listOf("Freestyle", "Backstroke", "Breaststroke", "Butterfly")),
    "Track & Field" to CategoryFocus("Events", listOf("Sprints", "Middle Distance", "Long Distance", "Jumps", "Throws")),
    "HIIT" to CategoryFocus("Format", listOf("Tabata", "EMOM", "AMRAP", "Circuits")),
    "Yoga" to CategoryFocus("Style", listOf("Vinyasa", "Hatha", "Power", "Yin")),
    "Stretching" to CategoryFocus("Areas", listOf("Hips", "Back", "Shoulders", "Full Body")),
)

private val DayOrder = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
private val DayDisplayName = mapOf(
    "monday" to "Monday", "tuesday" to "Tuesday", "wednesday" to "Wednesday", "thursday" to "Thursday",
    "friday" to "Friday", "saturday" to "Saturday", "sunday" to "Sunday",
)

// 5-step wizard state (goal, schedule, training type, focus & split, injuries/notes) — one step
// fewer than iOS's 6, since the equipment step has zero effect on generation (see AiApi.kt).
// Review is a checklist (checkbox per exercise, default all checked) instead of iOS's swipe-
// accept/reject cards — same outcome, simpler gesture surface.
class AIWorkoutPlannerViewModel(private val weekIdentifier: String) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val aiRepository = AiRepository()
    private val planRepository = PlanRepository()

    var step by mutableStateOf(0)
        private set
    val totalSteps = 5

    var selectedGoal by mutableStateOf(AiGoals.first())
    var trainingDaysPerWeek by mutableStateOf(4)
    var sessionDuration by mutableStateOf(60)
    var experienceLevel by mutableStateOf("Intermediate")
    var selectedPrimaryCategory by mutableStateOf<String?>(null)
        private set
    var selectedSecondaryCategories by mutableStateOf<List<String>>(emptyList())
        private set
    var focusByCategory by mutableStateOf<Map<String, Set<String>>>(emptyMap())
        private set
    var selectedWorkoutSplit by mutableStateOf("Any")
    var injuriesText by mutableStateOf("")
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
    var generatedDays by mutableStateOf<Map<String, WorkoutPlanDay>>(emptyMap())
        private set
    var checkedKeys by mutableStateOf<Set<String>>(emptySet())
        private set
    var isApplying by mutableStateOf(false)
        private set

    val hasGeneratedPlan: Boolean get() = generatedDays.isNotEmpty()

    init { checkUsage() }

    private fun checkUsage() {
        scope.launch {
            when (val result = aiRepository.getUsage()) {
                is ApiResult.Success -> {
                    val used = result.data.workoutGenerationsUsed ?: 0
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

    fun selectPrimary(category: String) {
        if (selectedPrimaryCategory == category) {
            selectedPrimaryCategory = null
            focusByCategory = focusByCategory - category
        } else {
            val old = selectedPrimaryCategory
            selectedPrimaryCategory = category
            selectedSecondaryCategories = selectedSecondaryCategories - category
            focusByCategory = if (old != null) focusByCategory - old else focusByCategory
        }
    }

    fun toggleSecondary(category: String) {
        selectedSecondaryCategories = if (selectedSecondaryCategories.contains(category)) {
            focusByCategory = focusByCategory - category
            selectedSecondaryCategories - category
        } else if (selectedSecondaryCategories.size < 2) {
            selectedSecondaryCategories + category
        } else selectedSecondaryCategories
    }

    val selectedCategories: List<String>
        get() = listOfNotNull(selectedPrimaryCategory) + selectedSecondaryCategories

    fun toggleFocus(category: String, option: String) {
        val current = focusByCategory[category] ?: emptySet()
        focusByCategory = focusByCategory + (category to (if (current.contains(option)) current - option else current + option))
    }

    fun goNext() { if (step < totalSteps - 1) step++ }
    fun goBack() { if (step > 0) step-- }

    fun generate() {
        if (isGenerating) return
        isGenerating = true
        errorMessage = null
        scope.launch {
            val input = WorkoutPlanInput(
                goal = selectedGoal,
                trainingDaysPerWeek = trainingDaysPerWeek,
                sessionDurationMinutes = sessionDuration,
                experienceLevel = experienceLevel,
                primaryCategory = selectedPrimaryCategory ?: "Gym",
                secondaryCategories = selectedSecondaryCategories,
                focusByCategory = focusByCategory.mapValues { it.value.toList() },
                injuriesOrLimitations = injuriesText,
                additionalNotes = additionalNotes,
            )
            when (val result = aiRepository.generateWorkoutPlan(input)) {
                is ApiResult.Success -> {
                    val days = result.data.days.orEmpty()
                    generatedDays = days
                    // Default every generated exercise to checked, matching iOS's "accept by default"
                    // starting point (there it's a swipe deck defaulting to all-pending/acceptable).
                    checkedKeys = days.flatMap { (day, dayPlan) ->
                        dayPlan.exercises.orEmpty().indices.map { index -> "$day::$index" }
                    }.toSet()
                }
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

    fun toggleExercise(key: String) {
        checkedKeys = if (checkedKeys.contains(key)) checkedKeys - key else checkedKeys + key
    }

    fun checkedCount(day: String): Int =
        (generatedDays[day]?.exercises.orEmpty().indices).count { checkedKeys.contains("$day::$it") }

    fun apply(onDone: (Boolean) -> Unit) {
        if (isApplying) return
        isApplying = true
        scope.launch {
            val requests = DayOrder.mapNotNull { day ->
                val dayPlan = generatedDays[day] ?: return@mapNotNull null
                val exercises = dayPlan.exercises.orEmpty().filterIndexed { index, _ -> checkedKeys.contains("$day::$index") }
                if (exercises.isEmpty()) return@mapNotNull null
                PlannedSessionRequest(
                    id = UUID.randomUUID().toString(),
                    weekIdentifier = weekIdentifier,
                    day = DayDisplayName[day] ?: return@mapNotNull null,
                    orderIndex = 0,
                    title = dayPlan.title?.takeIf { it.isNotBlank() } ?: "Workout",
                    exercises = exercises.map { it.toExerciseData() },
                )
            }
            when (planRepository.upsertBatch(requests)) {
                is ApiResult.Success -> onDone(true)
                is ApiResult.Error -> onDone(false)
            }
            isApplying = false
        }
    }

    fun dispose() {
        scope.cancel()
    }
}

private fun com.vasilisneo.trackstar.data.api.WorkoutPlanExercise.toExerciseData(): ExerciseData {
    val setCount = (sets ?: 1).coerceAtLeast(1)
    val freqType: String
    val freqValue: FrequencyValue
    when {
        durationSeconds != null -> {
            freqType = "Duration"
            freqValue = FrequencyValue(duration = formatSeconds(durationSeconds))
        }
        distanceMeters != null -> {
            freqType = "Distance"
            freqValue = FrequencyValue(distance = "$distanceMeters m")
        }
        else -> {
            freqType = "Repetitions"
            freqValue = FrequencyValue(reps = reps ?: 10)
        }
    }
    val sets = (0 until setCount).map {
        ExerciseSet(
            id = UUID.randomUUID().toString(),
            frequencyValue = freqValue,
            resistanceValue = ResistanceValue(),
            restSeconds = restSeconds ?: 90,
            setType = "Normal",
            repsMax = null,
        )
    }
    return ExerciseData(
        id = UUID.randomUUID().toString(),
        name = name ?: "Exercise",
        sets = sets,
        frequencyType = freqType,
        resistanceType = "None",
        resistanceUnit = ResistanceUnit(),
        compoundGroupId = null,
    )
}

private fun formatSeconds(seconds: Int): String =
    if (seconds >= 60) "${seconds / 60} minute${if (seconds % 60 != 0) " ${seconds % 60} sec" else ""}" else "$seconds sec"
