package com.vasilisneo.trackstar.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

// GPS activities (run / walk / ride / ski / snowboard) — /api/activities on the backend
// (ActivityController). Upload is an upsert by clientId, so retries never duplicate. Mirrors
// iOS's Model/Activity.swift.
interface ActivityApi {
    @POST("activities")
    suspend fun save(@Body activity: RecordedActivity): Response<RecordedActivity>

    @GET("activities")
    suspend fun list(): Response<List<RecordedActivity>>

    @DELETE("activities/{clientId}")
    suspend fun delete(@Path("clientId") clientId: String): Response<Map<String, String>>
}

data class ActivitySplit(
    val distanceMeters: Double,
    val seconds: Int,
)

/** A day on the slopes: totals plus every downhill run (lifts are only counted). */
data class SlopeStats(
    val runCount: Int,
    val liftCount: Int,
    val verticalMeters: Double,
    val topSpeedKmh: Double,
    val liftSeconds: Int,
    val runs: List<SlopeRun>,
)

/** One downhill run; startIndex/endIndex point into the decoded route. */
data class SlopeRun(
    val startedAt: Long,
    val endedAt: Long,
    val distanceMeters: Double,
    val verticalMeters: Double,
    val topSpeedKmh: Double,
    val avgSpeedKmh: Double,
    val startIndex: Int,
    val endIndex: Int,
) {
    val seconds: Int get() = ((endedAt - startedAt) / 1000).toInt()
}

data class RecordedActivity(
    val clientId: String,
    val type: String,            // run | walk | ride | ski | snowboard
    val title: String? = null,
    val date: String,            // "yyyy-MM-dd", local start day
    val startedAt: Long,         // epoch ms
    val endedAt: Long,
    val movingSeconds: Int,
    val elapsedSeconds: Int,
    val distanceMeters: Double,
    val elevationGainMeters: Double,
    val polyline: String,
    val splits: List<ActivitySplit>? = emptyList(),
    val slopes: SlopeStats? = null,
)
