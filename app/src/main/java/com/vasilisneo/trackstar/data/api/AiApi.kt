package com.vasilisneo.trackstar.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

// Protected AI-generation endpoint (/api/ai). Uses NetworkClient.aiApi (120s timeout — a real
// Claude call, not a quick REST round-trip, matching iOS's APIEndpoint.timeoutInterval override).
interface AiApi {
    @POST("ai/workout-plan")
    suspend fun generateWorkoutPlan(@Body body: WorkoutPlanInput): Response<WorkoutPlanResponse>

    @GET("ai/usage")
    suspend fun getUsage(): Response<AiUsageResponse>
}

// Matches com.fitnessbook.service.AiService.WorkoutPlanInput exactly. iOS also sends
// `workoutSplit` and `equipment` fields the backend Java DTO doesn't declare (Jackson silently
// drops unmapped JSON keys) — those two have zero effect on generation, so they're omitted here
// rather than replicating a no-op.
data class WorkoutPlanInput(
    val goal: String,
    val trainingDaysPerWeek: Int,
    val sessionDurationMinutes: Int,
    val experienceLevel: String,
    val primaryCategory: String,
    val secondaryCategories: List<String>,
    val focusByCategory: Map<String, List<String>>,
    val injuriesOrLimitations: String,
    val additionalNotes: String,
)

// The backend returns the raw Claude JSON with no schema validation — this is the shape the
// prompt asks for and iOS decodes. `days` keys are lowercase day names ("monday".."sunday").
data class WorkoutPlanResponse(
    val days: Map<String, WorkoutPlanDay>?,
)

data class WorkoutPlanDay(
    val title: String?,
    val exercises: List<WorkoutPlanExercise>?,
)

data class WorkoutPlanExercise(
    val name: String?,
    val sets: Int?,
    val reps: Int?,
    val durationSeconds: Int?,
    val distanceMeters: Int?,
    val restSeconds: Int?,
)

data class AiUsageResponse(
    val workoutGenerationsUsed: Int?,
    val dietGenerationsUsed: Int?,
    val monthlyLimit: Int?,
)
