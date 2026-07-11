package com.vasilisneo.trackstar.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// Per-exercise comments/notes (athlete ↔ coach), matching the iOS CommentService endpoints
// (/exercises/comments). Comments live in their own collection server-side — they are NOT part of
// the plan document — so they're fetched separately and merged onto exercises by id in the view
// models. Requires the Bearer token (added by NetworkClient's interceptor).
interface CommentApi {
    @GET("exercises/comments")
    suspend fun getWeekComments(
        @Query("weekIdentifier") weekIdentifier: String,
        @Query("userId") userId: String? = null,
    ): Response<List<ExerciseComment>>

    @GET("exercises/{exerciseId}/comments")
    suspend fun getExerciseComments(
        @Path("exerciseId") exerciseId: String,
        @Query("weekIdentifier") weekIdentifier: String,
        @Query("userId") userId: String? = null,
    ): Response<List<ExerciseComment>>

    @POST("exercises/{exerciseId}/comments")
    suspend fun addComment(
        @Path("exerciseId") exerciseId: String,
        @Body request: AddCommentRequest,
    ): Response<ExerciseComment>

    @DELETE("exercises/comments/{commentId}")
    suspend fun deleteComment(@Path("commentId") commentId: String): Response<MessageResponse>
}

// Matches iOS's ExerciseComment. `timestamp` is epoch milliseconds (a JSON number), as iOS decodes.
data class ExerciseComment(
    val id: String? = null,
    val exerciseId: String? = null,
    val text: String,
    val authorName: String,
    val authorRole: String,
    val timestamp: Long? = null,
)

// Matches iOS's AddCommentRequest. `userId` is null when posting on your own exercise; a coach
// posting on an athlete's exercise sends the athlete's id.
data class AddCommentRequest(
    val weekIdentifier: String,
    val userId: String? = null,
    val text: String,
    val authorName: String,
    val authorRole: String,
)
