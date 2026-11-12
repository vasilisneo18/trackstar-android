package com.vasilisneo.trackstar.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

// Activity groups: people who share their live position while recording and compare today's stats.
// Joined by QR code or link (https://trackstar.fitness/group.html?token=… → trackstar://group/…).
// /api/groups and /api/live on the backend (GroupController). Mirrors iOS's Model/ActivityGroup.swift.
interface GroupApi {
    @GET("groups")
    suspend fun list(): Response<List<GroupSummary>>

    @POST("groups")
    suspend fun create(@Body body: Map<String, String>): Response<GroupSummary>

    @GET("groups/invite/{token}")
    suspend fun preview(@Path("token") token: String): Response<GroupPreview>

    @POST("groups/join/{token}")
    suspend fun join(@Path("token") token: String): Response<GroupSummary>

    @GET("groups/{id}")
    suspend fun detail(@Path("id") id: String, @Query("date") date: String): Response<GroupDetail>

    @POST("groups/{id}/leave")
    suspend fun leave(@Path("id") id: String): Response<Map<String, Boolean>>

    @PUT("groups/{id}")
    suspend fun rename(@Path("id") id: String, @Body body: Map<String, String>): Response<GroupSummary>

    @DELETE("groups/{id}/members/{memberId}")
    suspend fun removeMember(@Path("id") id: String, @Path("memberId") memberId: String): Response<Map<String, Boolean>>

    @POST("groups/{id}/invite/reset")
    suspend fun resetInvite(@Path("id") id: String): Response<GroupSummary>

    @PUT("live")
    suspend fun updateLive(@Body body: LiveUpdate): Response<Map<String, Boolean>>

    @DELETE("live")
    suspend fun stopLive(): Response<Map<String, Boolean>>
}

data class GroupSummary(
    val id: String,
    val name: String,
    val memberCount: Int,
    val liveCount: Int,
    val isOwner: Boolean,
    val inviteLink: String,
)

data class GroupPreview(
    val id: String,
    val name: String,
    val memberCount: Int,
    val alreadyMember: Boolean,
)

data class GroupMember(
    val userId: String,
    val name: String,
    val initials: String,
    val isMe: Boolean,
    val isOwner: Boolean,
)

data class LiveMember(
    val userId: String,
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Double,
    val activityType: String?,
    val phase: String?,
    val updatedAt: Long,
)

/** Today's totals for one member in one category (slopes / run / walk / ride). */
data class LeaderboardEntry(
    val userId: String,
    val category: String,
    val distanceMeters: Double,
    val movingSeconds: Int,
    val topSpeedKmh: Double,
    val verticalMeters: Double,
    val runs: Int,
    val elevationGainMeters: Double,
    val bestPaceSecondsPerKm: Double?,
    val live: Boolean,
)

data class GroupDetail(
    val id: String,
    val name: String,
    val isOwner: Boolean,
    val inviteLink: String,
    val members: List<GroupMember>,
    val live: List<LiveMember>,
    val leaderboard: List<LeaderboardEntry>,
)

/** What the recorder sends every few seconds while sharing live. */
data class LiveUpdate(
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Double,
    val activityType: String,
    val phase: String?,
    val startedAt: Long,
    val distanceMeters: Double,
    val movingSeconds: Int,
    val topSpeedKmh: Double,
    val verticalMeters: Double,
    val runs: Int,
    val elevationGainMeters: Double,
)
