package com.vasilisneo.trackstar.data.local

import androidx.room.Entity

// Generic per-user JSON key-value cache. One row = one cached API response (a whole object or list)
// stored as JSON under a namespaced key (e.g. "profile", "diet", "plan:2026-W30",
// "athletePlan:{id}:{week}", "templates", "roster"). Used for every read-cached domain except
// workout sessions, which keep their own row-per-session table. `userId` isolates accounts; the
// whole cache is wiped on logout.
@Entity(tableName = "cache_entries", primaryKeys = ["userId", "cacheKey"])
data class CacheEntry(
    val userId: String,
    val cacheKey: String,
    val json: String,
)
