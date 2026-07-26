package com.vasilisneo.trackstar.data.local

import android.content.Context
import com.google.gson.Gson
import com.vasilisneo.trackstar.data.api.NetworkClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// Process-wide handle to the Room cache, so the context-free repositories can reach it without
// plumbing a Context through every call site. Initialized once from Application.onCreate before any
// screen/repository runs. Reuses the app's configured Gson (NetworkClient.gson) so cached JSON
// decodes exactly like a network response.
object LocalStore {

    private lateinit var database: AppDatabase
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val isReady: Boolean get() = ::database.isInitialized
    val db: AppDatabase get() = database
    val gson: Gson get() = NetworkClient.gson

    fun init(context: Context) {
        if (!::database.isInitialized) database = AppDatabase.get(context)
    }

    // Fire-and-forget cache wipe on logout. Clears the derived read caches (sessions + KV) for
    // per-user isolation, but deliberately KEEPS pending_actions so an unsynced offline write isn't
    // lost if the user logs out before it syncs — it replays when they're back online.
    fun wipeAsync() {
        if (!isReady) return
        scope.launch {
            runCatching {
                database.workoutSessionDao().clearAll()
                database.cacheDao().clearAll()
            }
        }
    }
}
