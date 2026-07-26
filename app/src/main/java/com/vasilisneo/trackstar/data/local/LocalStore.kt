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

    // Fire-and-forget full cache wipe on logout. Cache is derived, disposable data and typically
    // single-user, so clearing every table is the simplest correct isolation.
    fun wipeAsync() {
        if (!isReady) return
        scope.launch { runCatching { database.clearAllTables() } }
    }
}
