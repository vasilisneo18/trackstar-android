package com.vasilisneo.trackstar.ui.screens.main.workout

// Ports iOS's MissedSessionSheet.swift: the detail surface opened by tapping a missed-session
// card. Shows the missed workout's title, an exercise/sets summary, the full exercise list, and
// (when a plan session id is available) Start Now / Quick Log actions. Presented as a modal
// bottom sheet from WorkoutScreen, matching the ExerciseCommentsSheet scaffold (transparent
// container + self-painted rounded gradient) so it reads as an elevated Midnight sheet.

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.vasilisneo.trackstar.data.api.ExerciseData
import com.vasilisneo.trackstar.data.workout.ExerciseDisplayUnit
import com.vasilisneo.trackstar.data.workout.groupedForDisplay
import com.vasilisneo.trackstar.ui.theme.TrackstarAccent
import com.vasilisneo.trackstar.ui.theme.trackstarBackground

private val MissedOrange = Color(0xFFFF9500)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissedSessionSheet(
    workoutTitle: String,
    exercises: List<ExerciseData>,
    onStart: (() -> Unit)? = null,
    onQuickLog: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    // Locked sheet (same idiom as ExercisePickerSheet): reject user swipe/scrim dismissal via
    // confirmValueChange, and make onDismissRequest a no-op so tapping outside does nothing. Only
    // the X / action buttons close it — they flip allowHide and animate the sheet down first.
    val scope = rememberCoroutineScope()
    var allowHide by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || allowHide },
    )
    fun close(after: () -> Unit) {
        allowHide = true
        scope.launch {
            sheetState.hide()
            after()
        }
    }
    // Swallow leftover downward drag/fling so the locked sheet stays put and the content bounces
    // instead (iOS-style). See QuickLogSheet for the same guard.
    val keepSheetStill = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                if (available.y > 0f) Offset(0f, available.y) else Offset.Zero
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                if (available.y > 0f) Velocity(0f, available.y) else Velocity.Zero
        }
    }
    val units = exercises.groupedForDisplay()
    val setCount = exercises.sumOf { it.sets.orEmpty().size }

    ModalBottomSheet(
        onDismissRequest = {},
        sheetState = sheetState,
        containerColor = Color.Transparent,
        dragHandle = null,
    ) {
        // Fixed ~90% height (matches the app's other locked sheets, e.g. ExercisePickerSheet). The
        // content column scrolls internally when a session has more exercises than fit.
        Column(
            modifier = Modifier
                .fillMaxHeight(0.9f)
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .trackstarBackground()
                .background(Color.White.copy(alpha = 0.06f))
        ) {
            Spacer(modifier = Modifier.height(10.dp))
            // Header: close button + centered title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).height(44.dp)
            ) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).clickable { close(onDismiss) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                }
                Spacer(modifier = Modifier.weight(1f))
                Text("Missed Session", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Spacer(modifier = Modifier.weight(1f))
                // Balance the close button so the title stays optically centered.
                Box(modifier = Modifier.size(36.dp))
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .nestedScroll(keepSheetStill)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 40.dp)
            ) {
                // Warning summary card
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(16.dp)
                ) {
                    Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = MissedOrange, modifier = Modifier.size(26.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(workoutTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            "${exercises.size} exercise${if (exercises.size == 1) "" else "s"} · $setCount sets",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.55f)
                        )
                    }
                }

                // Exercise list (supersets collapse to one row, like iOS's groupedForDisplay)
                Column(modifier = Modifier.clip(RoundedCornerShape(16.dp))) {
                    units.forEachIndexed { index, unit ->
                        if (index > 0) Spacer(modifier = Modifier.height(1.dp))
                        when (unit) {
                            is ExerciseDisplayUnit.Single ->
                                MissedExerciseRow(name = unit.exercise.name ?: "Exercise", setCount = unit.exercise.sets.orEmpty().size)
                            is ExerciseDisplayUnit.Pair ->
                                MissedExerciseRow(
                                    name = "${unit.a.name ?: "Exercise"} + ${unit.b.name ?: "Exercise"}",
                                    setCount = unit.a.sets.orEmpty().size + unit.b.sets.orEmpty().size,
                                )
                        }
                    }
                }

                // Actions
                if (onStart != null || onQuickLog != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        if (onStart != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(TrackstarAccent.copy(alpha = 0.85f))
                                    .clickable { close(onStart) },
                            ) {
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                                Text("Start Now", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                        if (onQuickLog != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.15f))
                                    .clickable { close(onQuickLog) },
                            ) {
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Text("Quick Log", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MissedExerciseRow(name: String, setCount: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 16.dp, vertical = 13.dp)
    ) {
        Text(name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White, modifier = Modifier.weight(1f))
        Text("$setCount set${if (setCount == 1) "" else "s"}", fontSize = 13.sp, color = Color.White.copy(alpha = 0.4f))
    }
}
