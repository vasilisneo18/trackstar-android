package com.vasilisneo.trackstar.data.activity

// Activity types, formatting and the encoded-polyline codec — mirrors iOS's Model/Activity.swift.

import com.google.android.gms.maps.model.LatLng
import com.vasilisneo.trackstar.data.api.RecordedActivity
import com.vasilisneo.trackstar.ui.i18n.tr
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

enum class ActivityType(val raw: String, val label: String) {
    Run("run", "Run"), Walk("walk", "Walk"), Ride("ride", "Ride"),
    Ski("ski", "Ski"), Snowboard("snowboard", "Snowboard");

    val isSlope: Boolean get() = this == Ski || this == Snowboard
    /** Runs and walks read naturally as pace (min/km); rides and slopes as speed (km/h). */
    val usesPace: Boolean get() = this == Run || this == Walk

    companion object {
        val everyday = listOf(Run, Walk, Ride)
        val slopes = listOf(Ski, Snowboard)
        fun of(raw: String?): ActivityType = entries.firstOrNull { it.raw == raw } ?: Run
    }
}

val RecordedActivity.activityType: ActivityType get() = ActivityType.of(type)
val RecordedActivity.coordinates: List<LatLng> get() = Polyline.decode(polyline)

/** Seconds per km, null when there's no meaningful distance yet. */
val RecordedActivity.paceSecondsPerKm: Double?
    get() = if (distanceMeters >= 50) movingSeconds / (distanceMeters / 1000) else null

val RecordedActivity.speedKmh: Double
    get() = if (movingSeconds > 0) (distanceMeters / 1000) / (movingSeconds / 3600.0) else 0.0

/** Default title: "Morning Run", "Ski day"… */
val RecordedActivity.displayTitle: String
    get() = title?.takeIf { it.isNotBlank() } ?: ActivityFormat.defaultTitle(activityType, startedAt)

object ActivityFormat {
    fun distance(meters: Double): String {
        val km = meters / 1000
        return if (km >= 100) String.format(Locale.US, "%.0f", km) else String.format(Locale.US, "%.2f", km)
    }

    fun pace(secondsPerKm: Double?): String {
        val s = secondsPerKm ?: return "–:––"
        if (!s.isFinite() || s <= 0 || s >= 3600) return "–:––"
        val total = s.roundToInt()
        return String.format(Locale.US, "%d:%02d", total / 60, total % 60)
    }

    fun speed(kmh: Double): String = String.format(Locale.US, "%.1f", kmh)

    fun duration(seconds: Int): String {
        val h = seconds / 3600; val m = (seconds % 3600) / 60; val s = seconds % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
    }

    fun defaultTitle(type: ActivityType, startedAt: Long): String {
        if (type.isSlope) return tr(if (type == ActivityType.Ski) "Ski day" else "Snowboard day")
        val hour = Instant.ofEpochMilli(startedAt).atZone(ZoneId.systemDefault()).hour
        val part = when (hour) {
            in 5..11 -> "Morning"
            in 12..16 -> "Afternoon"
            in 17..21 -> "Evening"
            else -> "Night"
        }
        return tr("$part ${type.label}")
    }

    /** The one number for a card pill: pace for runs / walks, speed for rides, runs for slopes. */
    fun headline(a: RecordedActivity): String {
        a.slopes?.let { return if (it.runCount == 1) tr("1 run") else com.vasilisneo.trackstar.ui.i18n.trf("%s runs", it.runCount) }
        return if (a.activityType.usesPace) "${pace(a.paceSecondsPerKm)} /km" else "${speed(a.speedKmh)} km/h"
    }
}

/** Google encoded polyline (precision 1e5) — same format the backend stores and iOS writes. */
object Polyline {
    fun encode(coords: List<LatLng>): String {
        val sb = StringBuilder()
        var prevLat = 0; var prevLng = 0
        for (c in coords) {
            val lat = (c.latitude * 1e5).roundToInt()
            val lng = (c.longitude * 1e5).roundToInt()
            encodeValue(lat - prevLat, sb)
            encodeValue(lng - prevLng, sb)
            prevLat = lat; prevLng = lng
        }
        return sb.toString()
    }

    private fun encodeValue(value: Int, sb: StringBuilder) {
        var v = if (value < 0) (value shl 1).inv() else (value shl 1)
        while (v >= 0x20) {
            sb.append(((0x20 or (v and 0x1f)) + 63).toChar())
            v = v shr 5
        }
        sb.append((v + 63).toChar())
    }

    fun decode(encoded: String): List<LatLng> {
        val out = ArrayList<LatLng>()
        var index = 0; var lat = 0; var lng = 0
        fun next(): Int? {
            var result = 0; var shift = 0
            while (index < encoded.length) {
                val b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
                if (b < 0x20) return if (result and 1 != 0) (result shr 1).inv() else (result shr 1)
            }
            return null
        }
        while (index < encoded.length) {
            val dLat = next() ?: break
            val dLng = next() ?: break
            lat += dLat; lng += dLng
            out.add(LatLng(lat / 1e5, lng / 1e5))
        }
        return out
    }
}
