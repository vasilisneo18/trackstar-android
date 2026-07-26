package com.vasilisneo.trackstar.data.local

import androidx.room.Entity

// One cached completed-session row per user. We store the full WorkoutSessionResponse as JSON
// (the session shape is an opaque blob the stats/history screens decode in memory anyway), keyed
// by (userId, cacheKey) so multiple accounts on one device stay isolated — the Room analogue of
// iOS's per-user Realm. `date` is duplicated out as a column only so the cache can be pruned/queried
// by day later if needed; today the repository loads a user's whole set and computes in memory.
@Entity(tableName = "workout_sessions", primaryKeys = ["userId", "cacheKey"])
data class WorkoutSessionEntity(
    val userId: String,
    val cacheKey: String, // clientId, falling back to id
    val date: String?,
    val json: String,
)
