package com.vasilisneo.trackstar.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CacheDao {

    @Query("SELECT json FROM cache_entries WHERE userId = :userId AND cacheKey = :key")
    suspend fun get(userId: String, key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: CacheEntry)

    @Query("DELETE FROM cache_entries WHERE userId = :userId AND cacheKey = :key")
    suspend fun delete(userId: String, key: String)
}
