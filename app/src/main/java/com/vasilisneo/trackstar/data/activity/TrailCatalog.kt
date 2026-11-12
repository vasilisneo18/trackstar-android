package com.vasilisneo.trackstar.data.activity

// Named hiking trails for the trail browser (OpenStreetMap · © OpenStreetMap contributors, ODbL),
// published on trackstar.fitness/trails: countries.json lists the countries, index/{CC}.json each
// country's trails, t/{id}.json a trail's full route. Cached on disk; a country can also be
// downloaded as one pack (every trail and route) for hikes without signal, and deleted again.
// New countries appear without an app update. Mirrors iOS's Store/TrailCatalog.swift.

import android.content.Context
import android.location.Location
import com.vasilisneo.trackstar.data.api.NetworkClient
import com.vasilisneo.trackstar.ui.i18n.I18n
import com.vasilisneo.trackstar.ui.i18n.tr
import com.vasilisneo.trackstar.ui.i18n.trf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

data class TrailInfo(
    val id: Long,
    val n: String,
    val en: String?,
    val el: String?,
    val ref: String?,
    val net: String?,
    val c: String?,
    val cc: String,
    val km: Double,
    val lat: Double,
    val lon: Double,
    /** [minLat, minLon, maxLat, maxLon] */
    val box: List<Double>,
    val near: String?,
    val nearEn: String?,
) {
    private val greek get() = I18n.locale.language == "el"

    /** The trail's name in the app language when tagged, otherwise as signposted. */
    val name: String get() = if (greek) el ?: n else en ?: n
    val country: String get() = SkiResortCatalog.countryName(cc)
    val flag: String get() = SkiResort.flag(cc)

    /** "near Troodos · Cyprus" */
    val place: String
        get() {
            val town = if (greek) near ?: nearEn else nearEn ?: near
            return listOfNotNull(town?.let { trf("near %s", it) }, country).joinToString(" · ")
        }

    /** Local / Regional / National / International (OSM network). */
    val networkLabel: String?
        get() = when (net) {
            "iwn" -> tr("International"); "nwn" -> tr("National"); "rwn" -> tr("Regional"); "lwn" -> tr("Local"); else -> null
        }

    fun distanceTo(lat: Double, lon: Double): Float {
        val out = FloatArray(1); Location.distanceBetween(this.lat, this.lon, lat, lon, out); return out[0]
    }

    fun withLines(lines: List<TrailLine>) = Trail(id, n, ref, net, c, lines)
    val color get() = withLines(emptyList()).color
}

data class TrailCountry(val cc: String, val count: Int, val packBytes: Long?, val box: List<Double>)

object TrailCatalog {
    private const val BASE = "https://trackstar.fitness/trails/"
    private const val MAX_AGE_MS = 7L * 86_400_000

    private val _countries = MutableStateFlow<List<TrailCountry>>(emptyList())
    val countries: StateFlow<List<TrailCountry>> = _countries
    private val _downloaded = MutableStateFlow<Set<String>>(emptySet())
    val downloaded: StateFlow<Set<String>> = _downloaded
    private val _downloading = MutableStateFlow<Set<String>>(emptySet())
    val downloading: StateFlow<Set<String>> = _downloading

    private lateinit var cacheDir: File
    private lateinit var packDir: File
    private val indexes = HashMap<String, List<TrailInfo>>()
    private val routes = HashMap<Long, List<TrailLine>>()
    private val searchText = HashMap<Long, String>()
    private val lock = Mutex()
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    private data class CountriesFile(val countries: List<TrailCountry>)
    private data class IndexFile(val trails: List<TrailInfo>)
    private data class RouteFile(val lines: List<TrailLine>)
    private data class Pack(val trails: List<TrailInfo>, val routes: Map<String, List<TrailLine>>)

    fun init(context: Context) {
        cacheDir = File(context.cacheDir, "TrailCatalog-v1").apply { mkdirs() }
        // filesDir (not cache), so the system doesn't purge a pack before a hike.
        packDir = File(context.filesDir, "TrailPacks").apply { mkdirs() }
        _downloaded.value = packDir.listFiles()?.filter { it.name.endsWith(".json") }?.map { it.name.removeSuffix(".json") }?.toSet() ?: emptySet()
    }

    suspend fun loadCountries() {
        if (_countries.value.isNotEmpty()) return
        load("countries.json", CountriesFile::class.java, refresh = true)?.let { _countries.value = it.countries }
    }

    /** A country's trails: from its offline pack when downloaded, else the index (cached a week). */
    suspend fun trails(cc: String): List<TrailInfo> = lock.withLock {
        indexes[cc]?.let { return@withLock it }
        if (cc in _downloaded.value) {
            readPack(cc)?.let { pack ->
                remember(pack.trails, cc)
                pack.routes.forEach { (id, lines) -> id.toLongOrNull()?.let { routes[it] = lines } }
                return@withLock pack.trails
            }
        }
        val file = load("index/$cc.json", IndexFile::class.java, refresh = false) ?: return@withLock emptyList()
        remember(file.trails, cc)
        file.trails
    }

    private fun remember(trails: List<TrailInfo>, cc: String) {
        indexes[cc] = trails
        trails.forEach { t ->
            searchText[t.id] = SkiResortCatalog.fold(listOfNotNull(t.n, t.en, t.el, t.ref, t.near, t.nearEn, t.country).joinToString(" "))
        }
    }

    /** Countries whose trails are near a point (their trail extent, padded ~30 km). */
    fun countriesNear(lat: Double, lon: Double): List<String> = _countries.value.filter { c ->
        c.box.size == 4 && lat > c.box[0] - 0.3 && lat < c.box[2] + 0.3 && lon > c.box[1] - 0.3 && lon < c.box[3] + 0.3
    }.map { it.cc }

    fun search(query: String, pool: List<TrailInfo>): List<TrailInfo> {
        val words = SkiResortCatalog.fold(query).split(" ").filter { it.isNotBlank() }
        if (words.isEmpty()) return pool
        return pool.filter { t -> val text = searchText[t.id] ?: ""; words.all { text.contains(it) } }
    }

    /** The trail's full route. */
    suspend fun route(info: TrailInfo): Trail? {
        routes[info.id]?.let { return info.withLines(it) }
        val file = load("t/${info.id}.json", RouteFile::class.java, refresh = false) ?: return null
        routes[info.id] = file.lines
        return info.withLines(file.lines)
    }

    // MARK: Offline packs

    suspend fun download(cc: String): Boolean {
        if (cc in _downloading.value) return false
        _downloading.value = _downloading.value + cc
        try {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    http.newCall(Request.Builder().url("${BASE}pack/$cc.json").build()).execute().use { r ->
                        if (!r.isSuccessful) return@runCatching false
                        val body = r.body?.string() ?: return@runCatching false
                        NetworkClient.gson.fromJson(body, Pack::class.java) ?: return@runCatching false
                        val tmp = File(packDir, "$cc.json.tmp")
                        tmp.writeText(body)
                        tmp.renameTo(File(packDir, "$cc.json"))
                    }
                }.getOrDefault(false)
            }
            if (ok) {
                _downloaded.value = _downloaded.value + cc
                lock.withLock { indexes.remove(cc) }   // reload from the pack (with routes)
            }
            return ok
        } finally {
            _downloading.value = _downloading.value - cc
        }
    }

    fun deletePack(cc: String) {
        File(packDir, "$cc.json").delete()
        _downloaded.value = _downloaded.value - cc
    }

    /** Downloaded trails crossing an area — the hike maps' fallback without signal. */
    suspend fun packedTrails(minLat: Double, minLon: Double, maxLat: Double, maxLon: Double): List<Trail> {
        val out = ArrayList<Trail>()
        for (cc in _downloaded.value) {
            for (t in trails(cc)) {
                if (t.box.size == 4 && t.box[0] <= maxLat && t.box[2] >= minLat && t.box[1] <= maxLon && t.box[3] >= minLon) {
                    routes[t.id]?.let { out.add(t.withLines(it)) }
                }
            }
        }
        return out
    }

    private suspend fun readPack(cc: String): Pack? = withContext(Dispatchers.IO) {
        runCatching { NetworkClient.gson.fromJson(File(packDir, "$cc.json").readText(), Pack::class.java) }.getOrNull()
    }

    // MARK: Network + disk cache

    /** The file from disk when cached (and fresh unless `refresh`), else from the site; stale cache offline. */
    private suspend fun <T> load(path: String, type: Class<T>, refresh: Boolean): T? = withContext(Dispatchers.IO) {
        val cache = File(cacheDir, path.replace("/", "_"))
        val fresh = cache.exists() && System.currentTimeMillis() - cache.lastModified() < MAX_AGE_MS
        if (fresh && !refresh) runCatching { NetworkClient.gson.fromJson(cache.readText(), type) }.getOrNull()?.let { return@withContext it }
        runCatching {
            http.newCall(Request.Builder().url(BASE + path).build()).execute().use { r ->
                if (r.isSuccessful) {
                    val body = r.body?.string() ?: ""
                    NetworkClient.gson.fromJson(body, type)?.also { cache.writeText(body) }
                } else null
            }
        }.getOrNull() ?: if (cache.exists()) runCatching { NetworkClient.gson.fromJson(cache.readText(), type) }.getOrNull() else null
    }
}
