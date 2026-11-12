package com.vasilisneo.trackstar.data.activity

// Marked hiking trails from OpenStreetMap (© OpenStreetMap contributors, ODbL) for the hike maps.
// Same 0.1° tiles as the piste map: Cyprus and Greece come from static files on trackstar.fitness
// (coverage.json lists them; a missing file there means no trails), elsewhere from our backend,
// which asks OpenStreetMap's Overpass API and caches. Tiles are cached on disk. Mirrors iOS's
// Store/TrailMap.swift.

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.google.android.gms.maps.model.LatLng
import com.vasilisneo.trackstar.data.api.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max

data class TrailLine(val latitudes: List<Double>, val longitudes: List<Double>) {
    val coordinates: List<LatLng> get() = latitudes.zip(longitudes) { la, lo -> LatLng(la, lo) }
}

data class Trail(
    val id: Long,
    val name: String?,
    val ref: String?,
    val network: String?,
    /** The trail's marking colour when tagged (red, yellow, blue…). */
    val colour: String?,
    val lines: List<TrailLine>,
) {
    /** Marking colour where it reads on the map; a hiking magenta otherwise. */
    val color: Color
        get() = when (colour?.lowercase()) {
            "red" -> Color(0xFFF24545)
            "yellow" -> Color(0xFFFFD133)
            "blue" -> Color(0xFF59D9FF)   // cyan — the route itself is blue
            "green" -> Color(0xFF4DCC66)
            "orange" -> Color(0xFFFF9500)
            "purple" -> Color(0xFFAF52DE)
            else -> Color(0xFFED599E)
        }
}

data class TrailTile(val trails: List<Trail>)

object TrailMapStore {
    private const val TILE_SIZE = 0.1
    private const val STATIC_BASE = "https://trackstar.fitness/trails/"
    private const val REFRESH_MS = 30L * 86_400_000

    private lateinit var dir: File
    private val memory = HashMap<String, List<Trail>>()
    private var coverage: Set<String>? = null
    private val lock = Mutex()
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

    private data class CachedTile(val fetchedAt: Long, val trails: List<Trail>)
    private data class Coverage(val tiles: List<String>)

    fun init(context: Context) {
        dir = File(context.cacheDir, "Trails-v1").apply { mkdirs() }
    }

    /** Trails around a recorded route, merged across tiles by trail id. */
    suspend fun trailsCovering(coordinates: List<LatLng>): List<Trail> {
        if (coordinates.isEmpty()) return emptyList()
        return trailsIn(coordinates.minOf { it.latitude } - 0.01, coordinates.minOf { it.longitude } - 0.01,
            coordinates.maxOf { it.latitude } + 0.01, coordinates.maxOf { it.longitude } + 0.01)
    }

    /** Trails within ~`radius` meters of a point (the live recorder). */
    suspend fun trailsAround(center: LatLng, radius: Double = 4000.0): List<Trail> {
        val dLat = radius / 111_000
        val dLon = radius / (111_000 * max(cos(Math.toRadians(center.latitude)), 0.2))
        return trailsIn(center.latitude - dLat, center.longitude - dLon, center.latitude + dLat, center.longitude + dLon)
    }

    private suspend fun trailsIn(minLat: Double, minLon: Double, maxLat: Double, maxLon: Double): List<Trail> {
        val keys = ArrayList<Pair<Int, Int>>()
        for (la in floor(minLat / TILE_SIZE).toInt()..floor(maxLat / TILE_SIZE).toInt())
            for (lo in floor(minLon / TILE_SIZE).toInt()..floor(maxLon / TILE_SIZE).toInt()) keys.add(la to lo)
        if (keys.size > 25) return emptyList()
        val byId = LinkedHashMap<Long, Trail>()
        for ((la, lo) in keys) {
            for (t in tile(la, lo).orEmpty()) {
                val existing = byId[t.id]
                byId[t.id] = if (existing == null) t else existing.copy(lines = existing.lines + t.lines)
            }
        }
        // No signal (or nothing loaded yet): use downloaded trail packs.
        if (byId.isEmpty()) return TrailCatalog.packedTrails(minLat, minLon, maxLat, maxLon)
        return byId.values.toList()
    }

    private suspend fun tile(lat: Int, lon: Int): List<Trail>? = lock.withLock {
        val key = "${lat}_$lon"
        memory[key]?.let { return@withLock it }
        val cached = withContext(Dispatchers.IO) { readCache(key) }
        if (cached != null) {
            memory[key] = cached.trails
            if (System.currentTimeMillis() - cached.fetchedAt < REFRESH_MS) return@withLock cached.trails
        }
        val fetched = fetch(lat, lon, loadCoverage().contains(key))
        if (fetched != null) {
            memory[key] = fetched
            withContext(Dispatchers.IO) { writeCache(key, CachedTile(System.currentTimeMillis(), fetched)) }
        }
        fetched ?: memory[key]
    }

    /** Tiles published on trackstar.fitness (once per launch; empty if unreachable). */
    private suspend fun loadCoverage(): Set<String> {
        coverage?.let { return it }
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                http.newCall(Request.Builder().url(STATIC_BASE + "coverage.json").build()).execute().use { r ->
                    if (!r.isSuccessful) null
                    else NetworkClient.gson.fromJson(r.body?.string() ?: "", Coverage::class.java)?.tiles?.toSet()
                }
            }.getOrNull()
        }
        if (loaded != null) coverage = loaded
        return loaded ?: emptySet()
    }

    private suspend fun fetch(lat: Int, lon: Int, covered: Boolean): List<Trail>? = withContext(Dispatchers.IO) {
        if (covered) {
            runCatching {
                http.newCall(Request.Builder().url("$STATIC_BASE${lat}_$lon.json").build()).execute().use { r ->
                    if (r.code == 404) return@withContext emptyList()
                    if (r.isSuccessful) {
                        NetworkClient.gson.fromJson(r.body?.string() ?: "", TrailTile::class.java)?.trails?.let { return@withContext it }
                    }
                }
            }
        }
        runCatching { NetworkClient.activityApi.trailTile(lat, lon) }.getOrNull()
            ?.takeIf { it.isSuccessful }?.body()?.trails
    }

    private fun readCache(key: String): CachedTile? = runCatching {
        val f = File(dir, "$key.json")
        if (!f.exists()) null else NetworkClient.gson.fromJson(f.readText(), CachedTile::class.java)
    }.getOrNull()

    private fun writeCache(key: String, tile: CachedTile) {
        runCatching {
            val tmp = File(dir, "$key.json.tmp")
            tmp.writeText(NetworkClient.gson.toJson(tile))
            tmp.renameTo(File(dir, "$key.json"))
        }
    }
}
