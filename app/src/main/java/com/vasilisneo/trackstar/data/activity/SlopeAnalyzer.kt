package com.vasilisneo.trackstar.data.activity

// Splits a day on the slopes into downhill runs and lifts from the altitude profile — a zig-zag:
// a turn only counts once the altitude has moved `turnThreshold` meters back the other way, so
// noise and bumps don't split a run; a climb only ends a run once it proves to be a lift. Works live
// (sample by sample). Line-for-line port of iOS's ViewModel/SlopeAnalyzer.swift.

import com.vasilisneo.trackstar.data.api.SlopeRun
import com.vasilisneo.trackstar.data.api.SlopeStats

data class SlopeSample(
    val timeMs: Long,
    val altitude: Double,
    /** Cumulative horizontal distance since the start of the recording, in meters. */
    val distance: Double,
    /** Doppler speed from the GPS (m/s), null when not reliable. */
    val speed: Double?,
    /** Index of this point in the recorded route. */
    val routeIndex: Int,
)

class SlopeAnalyzer(
    private val turnThreshold: Double = 12.0,
    private val minRunDrop: Double = 30.0,
    private val minRunDistance: Double = 80.0,
    private val minLiftRise: Double = 30.0,
    private val maxPlausibleSpeed: Double = 45.0,
) {
    enum class Phase { Waiting, Skiing, OnLift }
    private enum class Trend { None, Up, Down }
    private data class Leg(val trend: Trend, val from: Int, val to: Int)

    val samples = ArrayList<SlopeSample>()
    private var trend = Trend.None
    private var pivot = 0
    private var extreme = 0
    private var maxIndex = 0
    private var minIndex = 0
    private var pendingDown: Leg? = null
    private val closedRuns = ArrayList<SlopeRun>()
    private val closedLifts = ArrayList<Pair<Int, Int>>()

    fun add(sample: SlopeSample) {
        samples.add(sample)
        val i = samples.size - 1
        if (i == 0) return
        val alt = sample.altitude
        when (trend) {
            Trend.None -> {
                if (alt > samples[maxIndex].altitude) maxIndex = i
                if (alt < samples[minIndex].altitude) minIndex = i
                if (alt <= samples[maxIndex].altitude - turnThreshold) {
                    trend = Trend.Down; pivot = maxIndex; extreme = i
                } else if (alt >= samples[minIndex].altitude + turnThreshold) {
                    trend = Trend.Up; pivot = minIndex; extreme = i
                }
            }
            Trend.Down -> {
                if (alt < samples[extreme].altitude) {
                    extreme = i
                } else if (alt >= samples[extreme].altitude + turnThreshold) {
                    pendingDown = Leg(Trend.Down, pivot, extreme)
                    trend = Trend.Up; pivot = extreme; extreme = i
                }
            }
            Trend.Up -> {
                if (alt > samples[extreme].altitude) extreme = i
                val isLift = samples[extreme].altitude - samples[pivot].altitude >= minLiftRise
                val down = pendingDown
                if (isLift && down != null) {
                    run(down)?.let { closedRuns.add(it) }
                    pendingDown = null
                }
                if (alt <= samples[extreme].altitude - turnThreshold) {
                    val bump = pendingDown
                    if (bump != null) {
                        // Only a bump: carry on with the same run.
                        trend = Trend.Down; pivot = bump.from
                        extreme = if (alt < samples[bump.to].altitude) i else bump.to
                        pendingDown = null
                    } else {
                        lift(Leg(Trend.Up, pivot, extreme))?.let { closedLifts.add(it) }
                        trend = Trend.Down; pivot = extreme; extreme = i
                    }
                }
            }
        }
    }

    private fun lift(leg: Leg): Pair<Int, Int>? =
        if (samples[leg.to].altitude - samples[leg.from].altitude >= minLiftRise) leg.from to leg.to else null

    private val openLeg: Leg? get() = if (trend == Trend.None) null else Leg(trend, pivot, extreme)

    val runs: List<SlopeRun>
        get() {
            val open = pendingDown ?: openLeg?.takeIf { it.trend == Trend.Down }
            val r = open?.let { run(it) } ?: return closedRuns
            return closedRuns + r
        }

    val lifts: List<Pair<Int, Int>>
        get() {
            val leg = openLeg?.takeIf { it.trend == Trend.Up } ?: return closedLifts
            val l = lift(leg) ?: return closedLifts
            return closedLifts + l
        }

    val stats: SlopeStats
        get() {
            val r = runs; val l = lifts
            return SlopeStats(
                runCount = r.size,
                liftCount = l.size,
                verticalMeters = r.sumOf { it.verticalMeters },
                topSpeedKmh = r.maxOfOrNull { it.topSpeedKmh } ?: 0.0,
                liftSeconds = l.sumOf { ((samples[it.second].timeMs - samples[it.first].timeMs) / 1000).toInt() },
                runs = r,
            )
        }

    val phase: Phase
        get() {
            val last = samples.lastOrNull() ?: return Phase.Waiting
            val moving = (last.speed ?: recentSpeed) > 2
            return when (trend) {
                Trend.Up -> if (pendingDown == null) Phase.OnLift else if (moving) Phase.Skiing else Phase.Waiting
                Trend.Down -> if (moving) Phase.Skiing else Phase.Waiting
                Trend.None -> Phase.Waiting
            }
        }

    /** Speed over the last ~5 seconds from distance, when the GPS gives no Doppler speed. */
    val recentSpeed: Double
        get() {
            val last = samples.lastOrNull() ?: return 0.0
            val earlier = samples.lastOrNull { last.timeMs - it.timeMs >= 5000 } ?: return 0.0
            val dt = (last.timeMs - earlier.timeMs) / 1000.0
            return if (dt > 0) (last.distance - earlier.distance) / dt else 0.0
        }

    private fun run(leg: Leg): SlopeRun? {
        var start = leg.from
        while (start < leg.to && !isMoving(start + 1)) start++
        var end = leg.to
        while (end > start && !isMoving(end)) end--
        if (end <= start) return null
        val drop = samples[leg.from].altitude - samples[leg.to].altitude
        val distance = samples[end].distance - samples[start].distance
        if (drop < minRunDrop || distance < minRunDistance) return null
        val seconds = (samples[end].timeMs - samples[start].timeMs) / 1000.0
        val top = topSpeed(start, end)
        return SlopeRun(
            startedAt = samples[start].timeMs, endedAt = samples[end].timeMs,
            distanceMeters = distance, verticalMeters = drop,
            topSpeedKmh = top * 3.6, avgSpeedKmh = if (seconds > 0) distance / seconds * 3.6 else 0.0,
            startIndex = samples[start].routeIndex, endIndex = samples[end].routeIndex,
        )
    }

    private fun isMoving(i: Int): Boolean {
        if (i <= 0 || i >= samples.size) return false
        samples[i].speed?.let { return it > 1.5 }
        val dt = (samples[i].timeMs - samples[i - 1].timeMs) / 1000.0
        return dt > 0 && (samples[i].distance - samples[i - 1].distance) / dt > 1.5
    }

    private fun topSpeed(start: Int, end: Int): Double {
        var best = 0.0
        for (i in start..end) samples[i].speed?.let { if (it <= maxPlausibleSpeed) best = maxOf(best, it) }
        if (best > 0) return best
        var j = start
        for (i in start..end) {
            while (j < i && (samples[i].timeMs - samples[j + 1].timeMs) >= 3000) j++
            val dt = (samples[i].timeMs - samples[j].timeMs) / 1000.0
            if (dt < 3) continue
            val v = (samples[i].distance - samples[j].distance) / dt
            if (v <= maxPlausibleSpeed) best = maxOf(best, v)
        }
        return best
    }
}
