package com.vasilisneo.trackstar.data.workout

import com.vasilisneo.trackstar.data.api.AddCommentRequest
import com.vasilisneo.trackstar.data.api.ExerciseComment
import com.vasilisneo.trackstar.data.api.MessageResponse
import com.vasilisneo.trackstar.data.api.NetworkClient
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.auth.apiCall

// Per-exercise comments/notes (/exercises/comments). See CommentApi for endpoint shapes.
class CommentRepository {
    private val api = NetworkClient.commentApi

    suspend fun getWeekComments(weekIdentifier: String): ApiResult<List<ExerciseComment>> =
        apiCall { api.getWeekComments(weekIdentifier) }

    suspend fun getExerciseComments(exerciseId: String, weekIdentifier: String): ApiResult<List<ExerciseComment>> =
        apiCall { api.getExerciseComments(exerciseId, weekIdentifier) }

    suspend fun addComment(exerciseId: String, request: AddCommentRequest): ApiResult<ExerciseComment> =
        apiCall { api.addComment(exerciseId, request) }

    suspend fun deleteComment(commentId: String): ApiResult<MessageResponse> =
        apiCall { api.deleteComment(commentId) }
}
