package com.vasilisneo.trackstar.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface WorkoutSessionDao {

    @Query("SELECT json FROM workout_sessions WHERE userId = :userId")
    suspend fun loadJson(userId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<WorkoutSessionEntity>)

    @Query("DELETE FROM workout_sessions WHERE userId = :userId")
    suspend fun clearUser(userId: String)

    @Query("DELETE FROM workout_sessions")
    suspend fun clearAll()

    // Replace a user's cached set with exactly what the server returned, in one transaction, so the
    // cache always mirrors the backend after a successful fetch (no stale leftovers).
    @Transaction
    suspend fun replaceUser(userId: String, rows: List<WorkoutSessionEntity>) {
        clearUser(userId)
        insertAll(rows)
    }
}
