package com.vasilisneo.trackstar.ui.screens.main.activity

// Debug-only harness (like iOS's -activityDemo): opens a sample activity's detail without signing in.
//   adb shell am start -n com.vasilisneo.trackstar/.MainActivity --es activityDemo run|walk|hike [--ei demoSteps 6240]
// Tapping Share shows the drawn image full screen instead of opening the share sheet.
//   ... --es activityDemo session [--ei logSets N]: a sample workout (180 s rests, a timed plank),
//   with the first N sets logged after 1.5 s.

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng
import com.vasilisneo.trackstar.data.activity.Polyline
import com.vasilisneo.trackstar.data.api.ActivitySplit
import com.vasilisneo.trackstar.data.api.RecordedActivity
import java.time.LocalDate
import kotlin.math.sin

@Composable
fun ActivityDemo(type: String, steps: Int?) {
    var image by remember { mutableStateOf<Bitmap?>(null) }
    DisposableEffect(Unit) {
        ActivityShare.debugSteps = steps
        ActivityShare.debugImageSink = { image = it }
        onDispose { ActivityShare.debugSteps = null; ActivityShare.debugImageSink = null }
    }
    val activity = remember(type) { sample(type) }
    Box(Modifier.fillMaxSize()) {
        ActivityDetailContent(activity = activity, isNew = false, onDelete = {}, onClose = {})
        image?.let {
            Box(Modifier.fillMaxSize().background(Color.Black).clickable { image = null }) {
                Image(it.asImageBitmap(), null, modifier = Modifier.fillMaxSize().padding(20.dp))
            }
        }
    }
}

private fun sample(type: String): RecordedActivity {
    val hike = type == "hike"
    val (lat, lng) = if (hike) 34.95 to 32.83 else 35.165 to 33.36
    val route = (0..80).map { i -> LatLng(lat + i * 0.0003, lng + sin(i / 12.0) * 0.003) }
    val end = System.currentTimeMillis()
    val moving = if (hike) 8420 else 1690
    return RecordedActivity(
        clientId = "demo-$type", type = type, date = LocalDate.now().toString(),
        startedAt = end - (moving + 120) * 1000L, endedAt = end, movingSeconds = moving, elapsedSeconds = moving + 120,
        distanceMeters = if (hike) 7400.0 else 5240.0, elevationGainMeters = if (hike) 512.0 else 42.0,
        polyline = Polyline.encode(route),
        splits = (1..5).map { ActivitySplit(1000.0, 310 + it * 4) },
    )
}

@Composable
fun SessionDemo(logSets: Int) {
    val vm = remember {
        fun set(id: String, reps: Int? = null, duration: String? = null, weight: String? = null) =
            com.vasilisneo.trackstar.data.api.ExerciseSet(id, com.vasilisneo.trackstar.data.api.FrequencyValue(reps = reps, duration = duration),
                com.vasilisneo.trackstar.data.api.ResistanceValue(weight = weight), restSeconds = 180, setType = null, repsMax = null)
        fun exercise(id: String, name: String, sets: List<com.vasilisneo.trackstar.data.api.ExerciseSet>) =
            com.vasilisneo.trackstar.data.api.ExerciseData(id, name, sets, null, null, com.vasilisneo.trackstar.data.api.ResistanceUnit(weight = "KG"), null)
        com.vasilisneo.trackstar.ui.screens.main.workout.ActiveSessionViewModel(LocalDate.now(), "demo", listOf(
            exercise("e1", "Bench Press", listOf(set("s1", 8, weight = "80"), set("s2", 8, weight = "80"))),
            exercise("e2", "Plank", listOf(set("s3", duration = "30 sec"), set("s4", duration = "30 sec"))),
            exercise("e3", "Dips", listOf(set("s5", 10), set("s6", 10))),
        ))
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1500)
        vm.exercises.flatMap { it.sets.orEmpty() }.take(logSets).forEach { vm.completeSet(it, it.frequencyValue?.reps, it.resistanceValue?.weight, it.frequencyValue?.duration) }
    }
    com.vasilisneo.trackstar.ui.screens.main.workout.ActiveSessionScreen(viewModel = vm, onMinimize = {}, onDiscard = {}, onFinish = {})
}
