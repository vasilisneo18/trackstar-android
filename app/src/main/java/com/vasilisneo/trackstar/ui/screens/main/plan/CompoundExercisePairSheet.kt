package com.vasilisneo.trackstar.ui.screens.main.plan

// Ports iOS's CompoundExercisePairSheet.swift: configure two exercises performed back-to-back
// as a superset. Deliberately scoped to reps+weight only (matches iOS's own comment: "the
// overwhelming common case for compound-set training") — rounds and rest are shared between
// both exercises, but reps/weight are independent per exercise, each with its own rep-range
// toggle. Writes both exercises with a matching compoundGroupId so they render as one paired
// unit (see data/workout/ExerciseGrouping.kt).

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vasilisneo.trackstar.data.api.ExerciseData
import com.vasilisneo.trackstar.data.api.ExerciseSet
import com.vasilisneo.trackstar.data.api.FrequencyValue
import com.vasilisneo.trackstar.data.api.ResistanceUnit
import com.vasilisneo.trackstar.data.api.ResistanceValue
import com.vasilisneo.trackstar.ui.components.WheelColumn
import com.vasilisneo.trackstar.ui.components.WheelPickerRow
import com.vasilisneo.trackstar.ui.theme.TrackstarAccent
import com.vasilisneo.trackstar.ui.theme.trackstarBackground
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CompoundExercisePairSheet(
    initialExerciseA: ExerciseData?,
    initialExerciseB: ExerciseData?,
    onSave: (ExerciseData, ExerciseData) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // Locked like iOS's .interactiveDismissDisabled(): reject the Hidden target so a user swipe/scrim
    // snaps back — but allow our own programmatic hide (via `allowHide`) so the X/✓ buttons animate
    // the sheet down instead of removing it instantly. Paired with a no-op onDismissRequest.
    var allowHide by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || allowHide },
    )
    // Slide the sheet down, then run the close action (remove the composable) once it's off-screen.
    fun close(after: () -> Unit) {
        allowHide = true
        scope.launch {
            sheetState.hide()
            after()
        }
    }
    val firstSetA = initialExerciseA?.sets?.firstOrNull()
    val firstSetB = initialExerciseB?.sets?.firstOrNull()

    // Key all field state to the incoming exercise ids so opening the sheet for a *different* pair
    // (or switching between the new-superset and edit-superset call sites) re-initializes from that
    // pair's data instead of reusing stale state — otherwise an edit would show empty names/defaults.
    val editKey = initialExerciseA?.id to initialExerciseB?.id
    var nameA by remember(editKey) { mutableStateOf(initialExerciseA?.name ?: "") }
    var nameB by remember(editKey) { mutableStateOf(initialExerciseB?.name ?: "") }
    var repsA by remember(editKey) { mutableStateOf(firstSetA?.frequencyValue?.reps ?: 10) }
    var repsB by remember(editKey) { mutableStateOf(firstSetB?.frequencyValue?.reps ?: 10) }
    var repsAMax by remember(editKey) { mutableStateOf(firstSetA?.repsMax) }
    var repsBMax by remember(editKey) { mutableStateOf(firstSetB?.repsMax) }
    var weightA by remember(editKey) { mutableStateOf(firstSetA?.resistanceValue?.weight ?: "") }
    var weightB by remember(editKey) { mutableStateOf(firstSetB?.resistanceValue?.weight ?: "") }
    var rounds by remember(editKey) { mutableStateOf(initialExerciseA?.sets?.size?.takeIf { it > 0 } ?: 3) }
    val initialRest = firstSetA?.restSeconds ?: 60
    var restMinutes by remember(editKey) { mutableStateOf(initialRest / 60) }
    var restSeconds by remember(editKey) { mutableStateOf(initialRest % 60) }

    val isValid = nameA.isNotBlank() && nameB.isNotBlank() && rounds > 0

    ModalBottomSheet(
        // No-op: scrim taps / system back don't dismiss — only the X (onDismiss) or checkmark do.
        onDismissRequest = {},
        sheetState = sheetState,
        // Transparent container so the content paints the app's theme gradient edge-to-edge, like
        // iOS's AppTheme.gradient + .presentationBackground(.clear). No drag handle (iOS has none —
        // the X closes it); zero the sheet insets so we control spacing via statusBarsPadding.
        containerColor = Color.Transparent,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        // iOS's `.large` detent stops just below the status bar (not edge-to-edge): take 93% of the
        // height so the rounded top + a sliver of the screen behind show at the top, instead of
        // filling behind the camera. Rounded top matches iOS presentationCornerRadius 30.
        Column(
            modifier = Modifier
                .fillMaxHeight(0.93f)
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .trackstarBackground()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp)) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable { close(onDismiss) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.weight(1f))
                Text("Superset", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable(enabled = isValid) {
                            val (exerciseA, exerciseB) = buildPair(
                                initialExerciseA, initialExerciseB, nameA, nameB, repsA, repsB, repsAMax, repsBMax,
                                weightA, weightB, rounds, restMinutes * 60 + restSeconds,
                            )
                            close { onSave(exerciseA, exerciseB) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Check, contentDescription = "Save", tint = Color.White.copy(alpha = if (isValid) 1f else 0.35f), modifier = Modifier.size(16.dp))
                }
            }

            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                item {
                    Text(
                        "Two exercises performed back-to-back with no rest between them, sharing the same rounds and rest.",
                        fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f)
                    )
                }
                item {
                    ExerciseHalfCard(
                        title = "Exercise 1", name = nameA, onNameChange = { nameA = it },
                        reps = repsA, onRepsChange = { repsA = it }, repsMax = repsAMax, onRepsMaxChange = { repsAMax = it },
                        weight = weightA, onWeightChange = { weightA = it },
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.ArrowDownward, contentDescription = null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(14.dp))
                    }
                }
                item {
                    ExerciseHalfCard(
                        title = "Exercise 2", name = nameB, onNameChange = { nameB = it },
                        reps = repsB, onRepsChange = { repsB = it }, repsMax = repsBMax, onRepsMaxChange = { repsBMax = it },
                        weight = weightB, onWeightChange = { weightB = it },
                    )
                }
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.06f)).padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Rounds", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.5f))
                            Text("$rounds", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        MiniStepperShared(value = rounds, onDecrement = { if (rounds > 1) rounds-- }, onIncrement = { rounds++ })
                    }
                }
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.06f))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                            Text("Rest between rounds", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                restLabel(restMinutes * 60 + restSeconds),
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.45f)
                            )
                        }
                        WheelPickerRow(
                            columns = listOf(
                                WheelColumn((0..10).map { it.toString() }, restMinutes.coerceIn(0, 10), { restMinutes = it }, unit = "min"),
                                WheelColumn((0..59).map { it.toString() }, restSeconds.coerceIn(0, 59), { restSeconds = it }, unit = "sec"),
                            ),
                            // 3 visible rows (selected + one faded each side), matching iOS — 5 rows
                            // made the sheet overflow and forced scrolling.
                            visibleCount = 3,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun ExerciseHalfCard(
    title: String,
    name: String, onNameChange: (String) -> Unit,
    reps: Int, onRepsChange: (Int) -> Unit,
    repsMax: Int?, onRepsMaxChange: (Int?) -> Unit,
    weight: String, onWeightChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.06f)),
    ) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp))

        // Borderless name field (no outline), matching iOS — the card + divider frame it.
        BasicTextField(
            value = name,
            onValueChange = onNameChange,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(TrackstarAccent),
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            decorationBox = { inner ->
                if (name.isEmpty()) {
                    Text("Exercise name", color = Color.White.copy(alpha = 0.35f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
                inner()
            },
        )

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                if (repsMax != null) "Range ⇄" else "Reps ⇄",
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.1f))
                    .clickable {
                        if (repsMax != null) onRepsMaxChange(null) else { onRepsChange(1); onRepsMaxChange(2) }
                    }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            if (repsMax != null) {
                RepsRangeControlShared(
                    min = reps, max = repsMax,
                    onMinChange = { newMin -> onRepsChange(newMin); if (newMin >= repsMax) onRepsMaxChange(newMin + 1) },
                    onMaxChange = onRepsMaxChange,
                )
            } else {
                MiniStepperShared(value = reps, onDecrement = { if (reps > 0) onRepsChange(reps - 1) }, onIncrement = { onRepsChange(reps + 1) }, big = true)
            }
        }

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text("Weight", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.weight(1f))
            // Plain right-aligned field (iOS uses a bare TextField width 60), so 60.dp isn't eaten
            // by an OutlinedTextField's internal chrome.
            BasicTextField(
                value = weight,
                onValueChange = onWeightChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.End),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(TrackstarAccent),
                modifier = Modifier.width(60.dp),
                decorationBox = { inner ->
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        if (weight.isEmpty()) {
                            Text("0", color = Color.White.copy(alpha = 0.3f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                        inner()
                    }
                },
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("kg", fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun RepsRangeControlShared(min: Int, max: Int, onMinChange: (Int) -> Unit, onMaxChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StepperButtonsShared(onDecrement = { if (min > 1) onMinChange(min - 1) }, onIncrement = { onMinChange(min + 1) })
        Text("$min to $max", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.padding(horizontal = 4.dp))
        StepperButtonsShared(onDecrement = { if (max > min + 1) onMaxChange(max - 1) }, onIncrement = { onMaxChange(max + 1) })
    }
}

@Composable
private fun StepperButtonsShared(onDecrement: () -> Unit, onIncrement: () -> Unit) {
    Row(modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(Color.White.copy(alpha = 0.08f))) {
        Box(modifier = Modifier.size(30.dp).clickable(onClick = onDecrement), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(15.dp))
        }
        Box(modifier = Modifier.width(1.dp).size(18.dp).background(Color.White.copy(alpha = 0.15f)))
        Box(modifier = Modifier.size(30.dp).clickable(onClick = onIncrement), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Add, contentDescription = "Increase", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun MiniStepperShared(value: Int, suffix: String = "", onDecrement: () -> Unit, onIncrement: () -> Unit, big: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (big) 16.dp else 8.dp)) {
        Box(
            modifier = Modifier.size(if (big) 32.dp else 26.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).clickable(onClick = onDecrement),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease", tint = Color.White, modifier = Modifier.size(if (big) 14.dp else 12.dp))
        }
        Text(
            "$value$suffix", fontSize = if (big) 18.sp else 14.sp, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace, color = Color.White, modifier = Modifier.padding(horizontal = 2.dp)
        )
        Box(
            modifier = Modifier.size(if (big) 32.dp else 26.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).clickable(onClick = onIncrement),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Increase", tint = Color.White, modifier = Modifier.size(if (big) 14.dp else 12.dp))
        }
    }
}

private fun restLabel(totalSeconds: Int): String {
    if (totalSeconds <= 0) return "None"
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return buildList {
        if (m > 0) add("$m min")
        if (s > 0) add("$s sec")
    }.joinToString(" ")
}

private fun buildPair(
    initialA: ExerciseData?, initialB: ExerciseData?,
    nameA: String, nameB: String,
    repsA: Int, repsB: Int, repsAMax: Int?, repsBMax: Int?,
    weightA: String, weightB: String,
    rounds: Int, restSeconds: Int,
): kotlin.Pair<ExerciseData, ExerciseData> {
    val groupId = initialA?.compoundGroupId ?: UUID.randomUUID().toString()

    fun buildSets(reps: Int, repsMax: Int?, weight: String) = (0 until rounds).map {
        ExerciseSet(
            id = UUID.randomUUID().toString(),
            frequencyValue = FrequencyValue(reps = reps),
            resistanceValue = ResistanceValue(weight = weight),
            restSeconds = restSeconds,
            setType = "Normal",
            repsMax = repsMax,
        )
    }

    val exerciseA = ExerciseData(
        id = initialA?.id ?: UUID.randomUUID().toString(), name = nameA.trim(),
        sets = buildSets(repsA, repsAMax, weightA),
        frequencyType = "Repetitions", resistanceType = "Weight", resistanceUnit = ResistanceUnit(weight = "KG"),
        compoundGroupId = groupId,
    )
    val exerciseB = ExerciseData(
        id = initialB?.id ?: UUID.randomUUID().toString(), name = nameB.trim(),
        sets = buildSets(repsB, repsBMax, weightB),
        frequencyType = "Repetitions", resistanceType = "Weight", resistanceUnit = ResistanceUnit(weight = "KG"),
        compoundGroupId = groupId,
    )
    return exerciseA to exerciseB
}
