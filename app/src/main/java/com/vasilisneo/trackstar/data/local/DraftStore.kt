package com.vasilisneo.trackstar.data.local

// Small JSON files on the phone for work in progress — the live workout and the activity being
// recorded — so they survive the app being closed. Writes run in order on one background thread
// (a later save or delete never loses to an earlier one). iOS keeps the same two files in
// Application Support.

import android.content.Context
import com.vasilisneo.trackstar.data.api.NetworkClient
import java.io.File
import java.util.concurrent.Executors

object DraftStore {
    const val WORKOUT = "active-workout.json"
    const val ACTIVITY = "activity-recording.json"

    private var dir: File? = null
    private val writer = Executors.newSingleThreadExecutor()

    fun init(context: Context) {
        dir = File(context.filesDir, "drafts").apply { mkdirs() }
    }

    fun <T> read(name: String, type: Class<T>): T? {
        val file = dir?.let { File(it, name) }?.takeIf { it.exists() } ?: return null
        return runCatching { NetworkClient.gson.fromJson(file.readText(), type) }.getOrNull()
    }

    fun write(name: String, value: Any) {
        val file = dir?.let { File(it, name) } ?: return
        val json = runCatching { NetworkClient.gson.toJson(value) }.getOrNull() ?: return
        writer.execute {
            runCatching {
                val tmp = File(file.parentFile, "$name.tmp")
                tmp.writeText(json)
                tmp.renameTo(file)
            }
        }
    }

    fun delete(name: String) {
        val file = dir?.let { File(it, name) } ?: return
        writer.execute { runCatching { file.delete() } }
    }
}
