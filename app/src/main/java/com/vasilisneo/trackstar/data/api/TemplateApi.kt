package com.vasilisneo.trackstar.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

// Coach workout-plan templates (/api/coach/templates). A template is a named weekly plan a coach can
// build once and apply to any athlete. Matches iOS's TemplateService / TemplateSyncModels. Sessions
// are stored/fetched flat (one row per day-session) and grouped by day client-side, like the plan.
interface TemplateApi {
    @GET("coach/templates")
    suspend fun getTemplates(): Response<List<TemplateDto>>

    @POST("coach/templates")
    suspend fun createTemplate(@Body request: TemplateSyncRequest): Response<TemplateDto>

    @PATCH("coach/templates/{id}")
    suspend fun renameTemplate(@Path("id") id: String, @Body request: TemplateSyncRequest): Response<TemplateDto>

    @DELETE("coach/templates/{id}")
    suspend fun deleteTemplate(@Path("id") id: String): Response<MessageResponse>

    @GET("coach/templates/{id}/sessions")
    suspend fun getTemplateSessions(@Path("id") templateId: String): Response<List<TemplateSessionDto>>

    @POST("coach/templates/{id}/sessions")
    suspend fun upsertTemplateSession(@Path("id") templateId: String, @Body request: TemplateSessionSyncRequest): Response<TemplateSessionDto>

    @DELETE("coach/templates/{id}/sessions/{sessionId}")
    suspend fun deleteTemplateSession(@Path("id") templateId: String, @Path("sessionId") sessionId: String): Response<MessageResponse>
}

data class TemplateDto(val id: String, val name: String)
data class TemplateSyncRequest(val id: String, val name: String)

// A template's session (one weekday's workout). `exercises` reuses the plan's opaque shape.
data class TemplateSessionDto(
    val id: String,
    val templateId: String? = null,
    val day: String,
    val orderIndex: Int = 0,
    val title: String = "",
    val exercises: List<ExerciseData> = emptyList(),
)

data class TemplateSessionSyncRequest(
    val id: String,
    val day: String,
    val orderIndex: Int,
    val title: String,
    val exercises: List<ExerciseData>,
)
