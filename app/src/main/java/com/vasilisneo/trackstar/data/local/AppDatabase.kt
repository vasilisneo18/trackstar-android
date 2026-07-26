package com.vasilisneo.trackstar.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// The app's local SQLite cache (Room). One shared database file; per-user isolation is done with a
// userId column on each entity (and a full wipe on logout) rather than separate files, so switching
// accounts is cheap. New cached domains (plan, diet, templates, roster, profile) get added here as
// their own entities/DAOs in later phases — bump `version` and add a migration when the schema
// changes after this ships.
@Database(
    entities = [WorkoutSessionEntity::class, CacheEntry::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun cacheDao(): CacheDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "trackstar_cache.db",
                )
                    // Cache is derived, disposable data — if a future schema change ships without a
                    // migration, drop and rebuild rather than crash. The next fetch re-fills it.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
