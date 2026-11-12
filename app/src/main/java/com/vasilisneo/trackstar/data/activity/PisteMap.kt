package com.vasilisneo.trackstar.data.activity

// Ski pistes and lifts from OpenStreetMap (© OpenStreetMap contributors, ODbL): tiles built from
// OpenSkiMap's exports and published on trackstar.fitness, with our backend (which caches
// OpenStreetMap's Overpass API) as a fallback. The world is split into 0.1° tiles (~11 km); each
// tile is fetched once and cached on disk, so a resort is downloaded on the first visit and works
// offline after that. Only the tile number is sent — never the route. Mirrors iOS's Store/PisteMap.swift.

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.google.android.gms.maps.model.LatLng
import com.vasilisneo.trackstar.data.api.NetworkClient
import com.vasilisneo.trackstar.ui.i18n.tr
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.math.min
import kotlin.math.sqrt

data class Piste(
    val id: Long,
    /** "piste" or "lift". */
    val kind: String,
    val name: String?,
    /** OSM piste:difficulty (novice, easy, intermediate, advanced, expert, freeride, extreme). */
    val difficulty: String?,
    /** OSM aerialway type for lifts (chair_lift, gondola, t-bar, drag_lift, …). */
    val liftType: String?,
    val latitudes: List<Double>,
    val longitudes: List<Double>,
    /** Ski areas (resort ids, as in ski_resorts.json) this piste / lift belongs to. */
    val areas: List<String>? = null,
) {
    val isLift: Boolean get() = kind == "lift"

    val coordinates: List<LatLng> get() = latitudes.zip(longitudes) { la, lo -> LatLng(la, lo) }

    fun color(convention: String? = null): Color = colorFor(difficulty, convention)

    fun difficultyLabel(convention: String? = null): String = label(difficulty, convention)

    companion object {
        val Green = Color(0xFF33BF59)
        val Blue = Color(0xFF3380FF)
        val Red = Color(0xFFF24040)
        val Orange = Color(0xFFFF9500)
        val Unrated = Color(0xFFBFBFBF)

        /** Colour by difficulty in the resort's convention (OpenSkiMap's runConvention):
         *  Europe green · blue · red · black, North America green · blue · black, Japan green · red · black. */
        fun colorFor(difficulty: String?, convention: String? = null): Color {
            val c = convention ?: "europe"
            return when {
                difficulty == "novice" -> Green
                difficulty == "easy" && (c == "north_america" || c == "japan") -> Green
                difficulty == "intermediate" && c == "north_america" -> Blue
                difficulty == "easy" -> Blue
                difficulty == "intermediate" -> Red
                difficulty == "advanced" || difficulty == "expert" -> Color.Black
                difficulty == "freeride" || difficulty == "extreme" -> Orange
                else -> Unrated
            }
        }

        /** "Red piste" etc. — for unnamed pistes and the legend. */
        fun label(difficulty: String?, convention: String? = null): String {
            val na = convention == "north_america"; val jp = convention == "japan"
            return when (difficulty) {
                "novice" -> tr("Green piste")
                "easy" -> if (na || jp) tr("Green piste") else tr("Blue piste")
                "intermediate" -> if (na) tr("Blue piste") else tr("Red piste")
                "advanced" -> tr("Black piste")
                "expert" -> if (na) tr("Double black") else tr("Black piste")
                "freeride", "extreme" -> tr("Freeride")
                else -> tr("Unrated")
            }
        }
    }
}

data class PisteTile(val pistes: List<Piste>)

object PisteMapStore {
    private const val TILE_SIZE = 0.1
    private const val STATIC_BASE = "https://trackstar.fitness/pistes/"
    private const val REFRESH_MS = 30L * 86_400_000

    private lateinit var dir: File
    private val memory = HashMap<String, List<Piste>>()
    private val inFlight = HashMap<String, CompletableDeferred<List<Piste>?>>()
    private val lock = Mutex()
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

    private data class CachedTile(val fetchedAt: Long, val pistes: List<Piste>)

    fun init(context: Context) {
        dir = File(context.cacheDir, "Pistes-v2").apply { mkdirs() }
    }

    // MARK: Public

    /** Pistes and lifts around a set of coordinates (e.g. a recorded route). */
    suspend fun pistesCovering(coordinates: List<LatLng>): List<Piste> {
        if (coordinates.isEmpty()) return emptyList()
        return pistesIn(coordinates.minOf { it.latitude }, coordinates.minOf { it.longitude },
            coordinates.maxOf { it.latitude }, coordinates.maxOf { it.longitude }) ?: emptyList()
    }

    /** Pistes and lifts inside a box [minLat, minLon, maxLat, maxLon] (a resort's extent). */
    suspend fun pistesInBox(box: List<Double>): List<Piste>? {
        if (box.size != 4) return null
        val pad = 0.003
        return pistesIn(box[0] - pad, box[1] - pad, box[2] + pad, box[3] + pad, maxTiles = 80)
    }

    /** Pistes and lifts within ~`radius` meters of a point. Null when the data couldn't be loaded yet. */
    suspend fun pistesAround(around: LatLng, radius: Double = 3000.0): List<Piste>? {
        val dLat = radius / 111_000
        val dLon = radius / (111_000 * max(cos(Math.toRadians(around.latitude)), 0.2))
        return pistesIn(around.latitude - dLat, around.longitude - dLon, around.latitude + dLat, around.longitude + dLon)
    }

    // MARK: Tiles

    private suspend fun pistesIn(minLat: Double, minLon: Double, maxLat: Double, maxLon: Double, maxTiles: Int = 16): List<Piste>? {
        val keys = ArrayList<Pair<Int, Int>>()
        for (la in floor(minLat / TILE_SIZE).toInt()..floor(maxLat / TILE_SIZE).toInt())
            for (lo in floor(minLon / TILE_SIZE).toInt()..floor(maxLon / TILE_SIZE).toInt()) keys.add(la to lo)
        if (keys.size > maxTiles) return emptyList()   // not a ski area — don't fetch half a country
        val seen = HashSet<Long>(); val result = ArrayList<Piste>(); var missing = false
        for ((la, lo) in keys) {
            val tile = tile(la, lo)
            if (tile == null) { missing = true; continue }
            for (p in tile) if (seen.add(p.id)) result.add(p)
        }
        return if (result.isEmpty() && missing) null else result
    }

    private suspend fun tile(lat: Int, lon: Int): List<Piste>? {
        val key = "${lat}_$lon"
        val (existing, owner) = lock.withLock {
            memory[key]?.let { return it }
            val cached = withContext(Dispatchers.IO) { readCache(key) }
            if (cached != null) {
                memory[key] = cached.pistes
                // Refresh in the background once a month (pistes rarely change).
                if (System.currentTimeMillis() - cached.fetchedAt < REFRESH_MS) return cached.pistes
            }
            inFlight[key]?.let { return@withLock it to false }
            CompletableDeferred<List<Piste>?>().also { inFlight[key] = it } to true
        }
        if (!owner) return existing.await() ?: lock.withLock { memory[key] }
        val fetched = fetch(lat, lon)
        lock.withLock {
            inFlight.remove(key)
            if (fetched != null) memory[key] = fetched
        }
        if (fetched != null) withContext(Dispatchers.IO) { writeCache(key, CachedTile(System.currentTimeMillis(), fetched)) }
        existing.complete(fetched)
        return fetched ?: lock.withLock { memory[key] }   // offline / server busy: keep what we had
    }

    // MARK: Sources

    /** The tile from our site (404 = no pistes there); if the site can't be reached, from the backend. */
    private suspend fun fetch(lat: Int, lon: Int): List<Piste>? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url("$STATIC_BASE${lat}_$lon.json").build()).execute().use { r ->
                if (r.code == 404) return@withContext emptyList()
                if (r.isSuccessful) {
                    val body = r.body?.string() ?: ""
                    NetworkClient.gson.fromJson(body, PisteTile::class.java)?.pistes?.let { return@withContext it }
                }
            }
        }
        runCatching { NetworkClient.activityApi.pisteTile(lat, lon) }.getOrNull()
            ?.takeIf { it.isSuccessful }?.body()?.pistes
    }

    // MARK: Disk cache

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

// MARK: - Matching runs to pistes

object PisteMatcher {
    /** The piste a run followed: the one most of the run's points lie within ~35 m of. Null when no
     *  piste covers at least 40% of the run (off-piste, or the resort isn't mapped). */
    fun piste(run: List<LatLng>, pistes: List<Piste>): Piste? {
        val candidates = pistes.filter { !it.isLift }
        if (run.isEmpty() || candidates.isEmpty()) return null
        val step = max(1, run.size / 40)
        val points = run.indices.step(step).map { run[it] }
        var best: Piste? = null; var bestShare = 0.0
        for (p in candidates) {
            val coords = p.coordinates
            val share = points.count { distance(it, coords) <= 35 }.toDouble() / points.size
            if (share > bestShare) { best = p; bestShare = share }
        }
        return if (bestShare >= 0.4) best else null
    }

    /** Shortest distance in meters from a point to a polyline (local flat projection). */
    fun distance(p: LatLng, line: List<LatLng>): Double {
        val mPerLat = 111_320.0
        val mPerLon = 111_320.0 * cos(Math.toRadians(p.latitude))
        var best = Double.POSITIVE_INFINITY
        for (i in 0 until max(line.size - 1, 0)) {
            val ax = (line[i].longitude - p.longitude) * mPerLon; val ay = (line[i].latitude - p.latitude) * mPerLat
            val bx = (line[i + 1].longitude - p.longitude) * mPerLon; val by = (line[i + 1].latitude - p.latitude) * mPerLat
            val dx = bx - ax; val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 > 0) max(0.0, min(1.0, -(ax * dx + ay * dy) / len2)) else 0.0
            val cx = ax + t * dx; val cy = ay + t * dy
            best = min(best, sqrt(cx * cx + cy * cy))
        }
        return best
    }
}
