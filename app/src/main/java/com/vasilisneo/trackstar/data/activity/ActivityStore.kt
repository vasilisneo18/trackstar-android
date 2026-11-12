package com.vasilisneo.trackstar.data.activity

// The signed-in user's recorded activities: cached to a per-user JSON file so they show offline
// and instantly, uploaded to the backend on save (retried later if offline), and refreshed from the
// backend on demand. Upload is an upsert by clientId, so retries never duplicate. Mirrors iOS's
// Store/ActivityStore.swift.

import android.content.Context
import com.google.gson.reflect.TypeToken
import com.vasilisneo.trackstar.data.api.NetworkClient
import com.vasilisneo.trackstar.data.api.RecordedActivity
import com.vasilisneo.trackstar.data.auth.AuthTokenHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

object ActivityStore {
    private val _activities = MutableStateFlow<List<RecordedActivity>>(emptyList())
    val activities: StateFlow<List<RecordedActivity>> = _activities
    private val _hasLoaded = MutableStateFlow(false)
    val hasLoaded: StateFlow<Boolean> = _hasLoaded

    private lateinit var dir: File
    private var userId: String? = null
    private var pendingUploads = mutableSetOf<String>()
    private var pendingDeletes = mutableSetOf<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val api get() = NetworkClient.activityApi

    fun init(context: Context) {
        dir = File(context.filesDir, "activities").apply { mkdirs() }
    }

    private data class Cache(
        val activities: List<RecordedActivity>,
        val pendingUploads: Set<String>,
        val pendingDeletes: Set<String>,
    )

    // MARK: Load / refresh

    /** Loads the cache for the current user (cheap) and then refreshes from the backend. */
    suspend fun refresh() = mutex.withLock {
        loadCacheIfNeeded()
        flushPendingLocked()
        val remote = runCatching { api.list() }.getOrNull()?.takeIf { it.isSuccessful }?.body()
        if (remote != null) {
            // Skip types this app version doesn't know, and keep local ones still uploading.
            val known = remote.filter { r -> ActivityType.entries.any { it.raw == r.type } }
            val remoteIds = known.map { it.clientId }.toSet()
            val localOnly = _activities.value.filter { it.clientId !in remoteIds && it.clientId in pendingUploads }
            _activities.value = (known.filter { it.clientId !in pendingDeletes } + localOnly)
                .sortedByDescending { it.startedAt }
            persist()
        }
        _hasLoaded.value = true
    }

    private fun loadCacheIfNeeded() {
        val current = AuthTokenHolder.userId
        if (current == userId) return
        userId = current
        _activities.value = emptyList()
        pendingUploads = mutableSetOf(); pendingDeletes = mutableSetOf()
        val file = cacheFile() ?: return
        if (!file.exists()) return
        runCatching {
            val type = object : TypeToken<Cache>() {}.type
            val cache: Cache = NetworkClient.gson.fromJson(file.readText(), type)
            _activities.value = cache.activities
            pendingUploads = cache.pendingUploads.toMutableSet()
            pendingDeletes = cache.pendingDeletes.toMutableSet()
            _hasLoaded.value = cache.activities.isNotEmpty()
        }
    }

    // MARK: Mutations

    fun save(activity: RecordedActivity) {
        scope.launch {
            mutex.withLock {
                loadCacheIfNeeded()
                _activities.value = (_activities.value.filter { it.clientId != activity.clientId } + activity)
                    .sortedByDescending { it.startedAt }
                pendingUploads.add(activity.clientId)
                persist()
                flushPendingLocked()
            }
        }
    }

    fun delete(activity: RecordedActivity) {
        scope.launch {
            mutex.withLock {
                _activities.value = _activities.value.filter { it.clientId != activity.clientId }
                pendingUploads.remove(activity.clientId)
                pendingDeletes.add(activity.clientId)
                persist()
                flushPendingLocked()
            }
        }
    }

    private suspend fun flushPendingLocked() {
        for (id in pendingUploads.toList()) {
            val activity = _activities.value.firstOrNull { it.clientId == id }
            if (activity == null) { pendingUploads.remove(id); continue }
            val ok = runCatching { api.save(activity).isSuccessful }.getOrDefault(false)
            if (ok) pendingUploads.remove(id)
        }
        for (id in pendingDeletes.toList()) {
            val ok = runCatching { api.delete(id).isSuccessful }.getOrDefault(false)
            if (ok) pendingDeletes.remove(id)
        }
        persist()
    }

    // MARK: Queries

    fun activitiesOn(date: LocalDate, all: List<RecordedActivity> = _activities.value): List<RecordedActivity> =
        all.filter { it.date == date.toString() }

    /** Total distance this week (Monday-based), in meters. */
    fun weekDistance(containing: LocalDate, all: List<RecordedActivity> = _activities.value): Double {
        val start = containing.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val end = start.plusDays(7)
        return all.filter {
            val d = Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            !d.isBefore(start) && d.isBefore(end)
        }.sumOf { it.distanceMeters }
    }

    /** Ski / snowboard totals for the season containing `date` (a season runs August → July). */
    fun slopeSeason(containing: LocalDate, all: List<RecordedActivity> = _activities.value): Triple<Int, Int, Double> {
        val startYear = if (containing.monthValue >= 8) containing.year else containing.year - 1
        val start = LocalDate.of(startYear, 8, 1)
        val season = all.filter {
            ActivityType.of(it.type).isSlope && !LocalDate.parse(it.date).isBefore(start) && !LocalDate.parse(it.date).isAfter(containing)
        }
        return Triple(season.map { it.date }.toSet().size, season.sumOf { it.slopes?.runCount ?: 0 },
            season.sumOf { it.slopes?.verticalMeters ?: 0.0 })
    }

    // MARK: Persistence

    private fun cacheFile(): File? = userId?.let { File(dir, "$it.json") }

    private fun persist() {
        val file = cacheFile() ?: return
        runCatching {
            file.writeText(NetworkClient.gson.toJson(Cache(_activities.value, pendingUploads, pendingDeletes)))
        }
    }
}
