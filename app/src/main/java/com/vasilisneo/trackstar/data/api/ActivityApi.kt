package com.vasilisneo.trackstar.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

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

    /** Fallback for the static piste tiles: the backend's cached Overpass query (tile = floor(deg × 10)). */
    @GET("pistes/tile")
    suspend fun pisteTile(@Query("lat") lat: Int, @Query("lon") lon: Int): Response<com.vasilisneo.trackstar.data.activity.PisteTile>

    /** Marked hiking trails outside the static coverage: the backend's cached Overpass query. */
    @GET("trails/tile")
    suspend fun trailTile(@Query("lat") lat: Int, @Query("lon") lon: Int): Response<com.vasilisneo.trackstar.data.activity.TrailTile>
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

/** One point of the elevation profile: distance along the route (d) and altitude (a), in meters. */
data class ElevationSample(val d: Double, val a: Double)

data class RecordedActivity(
    val clientId: String,
    val type: String,            // run | walk | hike | ride | ski | snowboard
    val title: String? = null,
    val date: String,            // "yyyy-MM-dd", local start day
    val startedAt: Long,         // epoch ms
    val endedAt: Long,
    val movingSeconds: Int,
    val elapsedSeconds: Int,
    val distanceMeters: Double,
    val elevationGainMeters: Double,
    /** Total descent and the altitude along the route (newer recordings; null on older ones). */
    val elevationLossMeters: Double? = null,
    val elevationProfile: List<ElevationSample>? = null,
    val polyline: String,
    val splits: List<ActivitySplit>? = emptyList(),
    val slopes: SlopeStats? = null,
)
