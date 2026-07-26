package com.vasilisneo.trackstar.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PendingActionDao {

    @Insert
    suspend fun insert(action: PendingActionEntity): Long

    // FIFO order — replay writes in the order they were made so later edits win.
    @Query("SELECT * FROM pending_actions WHERE userId = :userId ORDER BY id ASC")
    suspend fun forUser(userId: String): List<PendingActionEntity>

    @Delete
    suspend fun delete(action: PendingActionEntity)

    @Query("SELECT COUNT(*) FROM pending_actions WHERE userId = :userId")
    suspend fun countForUser(userId: String): Int
}
