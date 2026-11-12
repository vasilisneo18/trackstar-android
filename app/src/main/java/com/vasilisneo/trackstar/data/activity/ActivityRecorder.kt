package com.vasilisneo.trackstar.data.activity

// Records a run / walk / ride (or a day on the slopes) with GPS. Lives as a singleton so the screen
// can come and go; a foreground service (ActivityRecordingService) keeps location flowing with the
// screen off and shows the ongoing notification. Distance only counts while recording — pausing and
// resuming starts a new segment. Noisy fixes (poor accuracy, stale, impossible jumps) are dropped.
// Slopes: altitude from the barometer when the phone has one (much steadier than GPS altitude),
// fed to SlopeAnalyzer to split runs and lifts. Mirrors iOS's ViewModel/ActivityRecorder.swift.

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.vasilisneo.trackstar.data.api.ActivitySplit
import com.vasilisneo.trackstar.data.api.RecordedActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.math.abs

object ActivityRecorder {
    enum class State { Idle, Recording, Paused, Finished }

    /** Everything the recorder screen shows, as one snapshot. */
    data class Snapshot(
        val type: ActivityType = ActivityType.Run,
        val state: State = State.Idle,
        val hasGoodSignal: Boolean = false,
        val lastLocation: LatLng? = null,
        val route: List<LatLng> = emptyList(),
        val distanceMeters: Double = 0.0,
        val movingSeconds: Int = 0,
        val elevationGainMeters: Double = 0.0,
        val currentPaceSecondsPerKm: Double? = null,
        val currentSpeedKmh: Double = 0.0,
        val splits: List<ActivitySplit> = emptyList(),
        // Slopes
        val slopePhase: SlopeAnalyzer.Phase = SlopeAnalyzer.Phase.Waiting,
        val slopeRuns: Int = 0,
        val slopeVerticalMeters: Double = 0.0,
        val slopeTopSpeedKmh: Double = 0.0,
        val startedAtMs: Long? = null,
    )

    private val _snapshot = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = _snapshot

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var timerJob: Job? = null
    private var appContext: Context? = null
    private var callback: LocationCallback? = null

    private var startedAtMs: Long? = null
    private var segmentStartMs: Long? = null
    private var accumulatedMovingMs = 0L
    private var previousInSegment: Location? = null
    private val recentWindow = ArrayDeque<Pair<Long, Double>>()
    private var smoothedAltitude: Double? = null
    private var lastSplitDistance = 0.0
    private var lastSplitSeconds = 0
    private var totalDistance = 0.0
    private var slopeAnalyzer = SlopeAnalyzer()
    private var baroAltitude: Double? = null
    private var baroBase: Double? = null
    private var sensorManager: SensorManager? = null
    private val pressureListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            baroAltitude = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, event.values[0]).toDouble()
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun update(block: Snapshot.() -> Snapshot) { _snapshot.value = _snapshot.value.block() }

    fun setType(type: ActivityType) { if (_snapshot.value.state == State.Idle) update { copy(type = type) } }

    // MARK: Lifecycle

    /** Starts GPS so the user sees signal before tapping Start (permission must already be granted). */
    @SuppressLint("MissingPermission")
    fun prepare(context: Context, type: ActivityType) {
        appContext = context.applicationContext
        if (_snapshot.value.state == State.Idle || _snapshot.value.state == State.Finished) {
            reset(type)
        }
        if (callback != null) return
        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateDistanceMeters(3f)
            .build()
        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { handle(it) }
            }
        }
        callback = cb
        runCatching { client.requestLocationUpdates(request, cb, Looper.getMainLooper()) }
    }

    fun start() {
        val ctx = appContext ?: return
        if (_snapshot.value.state != State.Idle) return
        val now = System.currentTimeMillis()
        startedAtMs = now
        segmentStartMs = now
        update { copy(state = State.Recording, startedAtMs = now) }
        ContextCompat.startForegroundService(ctx, Intent(ctx, ActivityRecordingService::class.java))
        if (_snapshot.value.type.isSlope) startBarometer(ctx)
        startTimer()
    }

    fun pause() {
        if (_snapshot.value.state != State.Recording) return
        segmentStartMs?.let { accumulatedMovingMs += System.currentTimeMillis() - it }
        segmentStartMs = null
        previousInSegment = null
        recentWindow.clear()
        update { copy(state = State.Paused, currentPaceSecondsPerKm = null, currentSpeedKmh = 0.0) }
        tick()
    }

    fun resume() {
        if (_snapshot.value.state != State.Paused) return
        segmentStartMs = System.currentTimeMillis()
        update { copy(state = State.Recording) }
    }

    /** Stops recording and returns the finished activity (null if nothing was recorded). */
    fun finish(): RecordedActivity? {
        if (_snapshot.value.state == State.Recording) pause()
        stopEverything()
        val started = startedAtMs ?: return null.also { update { copy(state = State.Finished) } }
        val s = _snapshot.value
        val end = System.currentTimeMillis()
        val allSplits = s.splits.toMutableList()
        val remainder = s.distanceMeters - lastSplitDistance
        if (remainder >= 50) allSplits.add(ActivitySplit(remainder, s.movingSeconds - lastSplitSeconds))
        val slopes = if (s.type.isSlope) slopeAnalyzer.stats else null
        update { copy(state = State.Finished) }
        return RecordedActivity(
            clientId = UUID.randomUUID().toString(),
            type = s.type.raw,
            date = Instant.ofEpochMilli(started).atZone(ZoneId.systemDefault()).toLocalDate().toString(),
            startedAt = started,
            endedAt = end,
            movingSeconds = s.movingSeconds,
            elapsedSeconds = ((end - started) / 1000).toInt(),
            distanceMeters = slopes?.runs?.sumOf { it.distanceMeters } ?: s.distanceMeters,
            elevationGainMeters = s.elevationGainMeters,
            polyline = Polyline.encode(s.route),
            splits = if (s.type.isSlope) emptyList() else allSplits,
            slopes = slopes,
        )
    }

    /** Abandons the recording (or stops GPS if never started). */
    fun cancel() {
        stopEverything()
        reset(_snapshot.value.type)
    }

    private fun reset(type: ActivityType) {
        startedAtMs = null; segmentStartMs = null; accumulatedMovingMs = 0
        previousInSegment = null; recentWindow.clear(); smoothedAltitude = null
        lastSplitDistance = 0.0; lastSplitSeconds = 0; totalDistance = 0.0
        slopeAnalyzer = SlopeAnalyzer(); baroAltitude = null; baroBase = null
        _snapshot.value = Snapshot(type = type, lastLocation = _snapshot.value.lastLocation,
            hasGoodSignal = _snapshot.value.hasGoodSignal)
    }

    private fun stopEverything() {
        timerJob?.cancel(); timerJob = null
        appContext?.let { ctx ->
            callback?.let { LocationServices.getFusedLocationProviderClient(ctx).removeLocationUpdates(it) }
            ctx.stopService(Intent(ctx, ActivityRecordingService::class.java))
        }
        callback = null
        sensorManager?.unregisterListener(pressureListener)
        sensorManager = null
    }

    private fun startBarometer(ctx: Context) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_PRESSURE) ?: return
        sensorManager = sm
        sm.registerListener(pressureListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    // MARK: Timer

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) { tick(); delay(1000) }
        }
    }

    private fun tick() {
        var moving = accumulatedMovingMs
        segmentStartMs?.let { moving += System.currentTimeMillis() - it }
        update { copy(movingSeconds = (moving / 1000).toInt()) }
    }

    // MARK: Location processing

    private fun handle(location: Location) {
        val accurate = location.hasAccuracy() && location.accuracy <= 25f
        val point = LatLng(location.latitude, location.longitude)
        update { copy(lastLocation = point, hasGoodSignal = accurate) }
        val s = _snapshot.value
        if (s.state != State.Recording || !accurate) return
        if (abs(System.currentTimeMillis() - location.time) > 10_000) return

        val prev = previousInSegment
        if (prev != null) {
            val delta = location.distanceTo(prev).toDouble()
            val dt = (location.time - prev.time) / 1000.0
            if (dt <= 0) return
            // Reject teleports: faster than a sprinter on foot / a fast descent on a bike / a racer.
            val maxSpeed = if (s.type.isSlope) 45.0 else if (s.type == ActivityType.Ride) 30.0 else 12.0
            if (delta / dt > maxSpeed) return
            if (delta < 2) return
            if (s.type.isSlope) {
                totalDistance += delta
            } else {
                update { copy(distanceMeters = distanceMeters + delta) }
                updatePace(location.time)
                updateSplits()
            }
        }
        updateElevation(location)
        previousInSegment = location
        update { copy(route = route + point) }
        if (s.type.isSlope) addSlopeSample(location)
    }

    private fun updatePace(timeMs: Long) {
        val d = _snapshot.value.distanceMeters
        recentWindow.addLast(timeMs to d)
        while (recentWindow.isNotEmpty() && timeMs - recentWindow.first().first > 30_000) recentWindow.removeFirst()
        val first = recentWindow.firstOrNull() ?: return
        val dd = d - first.second
        val t = (timeMs - first.first) / 1000.0
        val pace = if (dd > 10 && t > 5) t / (dd / 1000) else null
        update { copy(currentPaceSecondsPerKm = pace, currentSpeedKmh = pace?.let { 3600 / it } ?: 0.0) }
    }

    private fun updateSplits() {
        tick()
        val s = _snapshot.value
        val splits = s.splits.toMutableList()
        while (s.distanceMeters - lastSplitDistance >= 1000) {
            splits.add(ActivitySplit(1000.0, s.movingSeconds - lastSplitSeconds))
            lastSplitDistance += 1000
            lastSplitSeconds = s.movingSeconds
        }
        if (splits.size != s.splits.size) update { copy(splits = splits) }
    }

    /** Sums climbs from a smoothed altitude so GPS noise doesn't inflate the total. */
    private fun updateElevation(location: Location) {
        if (!location.hasAltitude()) return
        if (location.hasVerticalAccuracy() && location.verticalAccuracyMeters > 15f) return
        val smoothed = smoothedAltitude ?: run { smoothedAltitude = location.altitude; return }
        val next = smoothed * 0.8 + location.altitude * 0.2
        if (next > smoothed) update { copy(elevationGainMeters = elevationGainMeters + (next - smoothed)) }
        smoothedAltitude = next
    }

    private fun addSlopeSample(location: Location) {
        val altitude = baroAltitude?.let { baro ->
            if (baroBase == null && location.hasAltitude()) baroBase = location.altitude - baro
            (baroBase ?: 0.0) + baro
        } ?: smoothedAltitude ?: (if (location.hasAltitude()) location.altitude else return)
        val speed = if (location.hasSpeed() && (!location.hasSpeedAccuracy() || location.speedAccuracyMetersPerSecond < 3f))
            location.speed.toDouble() else null
        val routeIndex = _snapshot.value.route.size - 1
        slopeAnalyzer.add(SlopeSample(location.time, altitude, totalDistance, speed, routeIndex))
        val stats = slopeAnalyzer.stats
        update {
            copy(
                slopePhase = slopeAnalyzer.phase,
                slopeRuns = stats.runCount,
                slopeVerticalMeters = stats.verticalMeters,
                slopeTopSpeedKmh = stats.topSpeedKmh,
                distanceMeters = stats.runs.sumOf { it.distanceMeters },
                currentSpeedKmh = (speed ?: slopeAnalyzer.recentSpeed) * 3.6,
            )
        }
    }
}
