package com.vasilisneo.trackstar.data.activity

// The world's downhill ski resorts (OpenSkiMap · © OpenStreetMap contributors, ODbL), bundled in
// assets/ski_resorts.json so search is instant and works offline. Piste lines for a resort come from
// PisteMapStore. Mirrors iOS's Model/SkiResort.swift.

import android.content.Context
import android.location.Location
import com.google.android.gms.maps.model.LatLng
import com.google.gson.annotations.SerializedName
import com.vasilisneo.trackstar.data.api.NetworkClient
import com.vasilisneo.trackstar.ui.i18n.I18n
import java.text.Normalizer
import java.util.Locale

data class SkiResort(
    val id: String,
    @SerializedName("n") val name: String,
    @SerializedName("cc") val countryCode: String?,
    @SerializedName("c") val countryEnglish: String?,
    @SerializedName("r") val region: String?,
    @SerializedName("lat") val latitude: Double,
    @SerializedName("lon") val longitude: Double,
    /** Downhill runs per OSM difficulty (novice, easy, intermediate, advanced, expert, freeride, other). */
    val runs: Map<String, Int>?,
    @SerializedName("km") val kilometers: Double,
    val lifts: Int,
    @SerializedName("minE") val minElevation: Int?,
    @SerializedName("maxE") val maxElevation: Int?,
    /** Colour convention for difficulties: "europe", "north_america" or "japan". */
    @SerializedName("conv") val convention: String?,
    /** Extent of the resort's runs and lifts: [minLat, minLon, maxLat, maxLon]. */
    val box: List<Double>?,
) {
    val coordinate: LatLng get() = LatLng(latitude, longitude)
    val runCount: Int get() = runs?.values?.sum() ?: 0

    /** Country name in the app language ("Κύπρος" in Greek). */
    val country: String? get() = countryCode?.let { SkiResortCatalog.countryName(it) } ?: countryEnglish

    val flag: String get() = countryCode?.let { flag(it) } ?: ""

    /** "Limassol · Cyprus" */
    val place: String get() = listOfNotNull(region, country).joinToString(" · ")

    /** Runs grouped into the colours people know, in display order (unrated runs last). */
    val difficultySummary: List<Pair<String, Int>>
        get() = listOf("novice", "easy", "intermediate", "advanced", "expert", "freeride", "other")
            .mapNotNull { d -> runs?.get(d)?.takeIf { it > 0 }?.let { d to it } }

    fun distanceTo(lat: Double, lon: Double): Float {
        val out = FloatArray(1)
        Location.distanceBetween(latitude, longitude, lat, lon, out)
        return out[0]
    }

    companion object {
        fun flag(code: String): String = code.uppercase().map { Character.toChars(127_397 + it.code).concatToString() }.joinToString("")
    }
}

object SkiResortCatalog {
    var resorts: List<SkiResort> = emptyList()
        private set
    /** Lowercased, accent-free search text per resort (name, region, country in both languages). */
    private val searchText = HashMap<String, String>()
    private val countryNames = HashMap<String, String>()

    private data class FileShape(val resorts: List<SkiResort>)

    /** Loads the bundled index once (call off the main thread — it's ~1 MB). */
    @Synchronized
    fun load(context: Context) {
        if (resorts.isNotEmpty()) return
        val file = runCatching {
            context.assets.open("ski_resorts.json").bufferedReader().use { NetworkClient.gson.fromJson(it, FileShape::class.java) }
        }.getOrNull() ?: return
        resorts = file.resorts
        for (r in resorts) {
            searchText[r.id] = fold(listOfNotNull(r.name, r.region, r.countryEnglish, r.country).joinToString(" "))
        }
    }

    fun countryName(code: String): String =
        countryNames.getOrPut(code) { Locale("", code).getDisplayCountry(I18n.locale).ifBlank { code } }

    fun fold(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

    /** Resorts whose name / region / country contain every word of the query. */
    fun search(query: String, candidates: List<SkiResort> = resorts): List<SkiResort> {
        val words = fold(query).split(" ").filter { it.isNotBlank() }
        if (words.isEmpty()) return candidates
        return candidates.filter { r -> val text = searchText[r.id] ?: ""; words.all { text.contains(it) } }
    }

    /** Countries with resorts: the phone's own country first (Cyprus and Greece together for
     *  Cypriot / Greek users, or when the app is in Greek), then by number of resorts. */
    private var cachedCountries: List<Pair<String, Int>>? = null
    val countries: List<Pair<String, Int>>
        get() = cachedCountries ?: computeCountries().also { if (resorts.isNotEmpty()) cachedCountries = it }

    private fun computeCountries(): List<Pair<String, Int>> {
        val counts = resorts.mapNotNull { it.countryCode }.groupingBy { it }.eachCount()
        val home = Locale.getDefault().country.takeIf { it.isNotBlank() }
        val first = mutableListOf<String>().apply { home?.let { add(it) } }
        if (home == "CY" || home == "GR" || I18n.locale.language == "el") listOf("CY", "GR").forEach { if (it !in first) first.add(it) }
        fun rank(c: String) = first.indexOf(c).let { if (it < 0) Int.MAX_VALUE else it }
        return counts.toList().sortedWith(compareBy<Pair<String, Int>> { rank(it.first) }.thenByDescending { it.second })
    }

    fun nearest(lat: Double, lon: Double, limit: Int = 40): List<SkiResort> =
        resorts.map { it to it.distanceTo(lat, lon) }.sortedBy { it.second }.take(limit).map { it.first }

    /** The resort a route is at (within ~15 km), for the run-colour convention. */
    fun resortNear(point: LatLng): SkiResort? =
        resorts.minByOrNull { it.distanceTo(point.latitude, point.longitude) }
            ?.takeIf { it.distanceTo(point.latitude, point.longitude) < 15_000 }
}
