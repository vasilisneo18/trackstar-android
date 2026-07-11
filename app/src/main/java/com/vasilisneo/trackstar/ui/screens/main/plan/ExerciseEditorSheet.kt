package com.vasilisneo.trackstar.ui.screens.main.plan

// Add/edit an exercise within a planned session. Ports iOS's CreateExerciseBottomSheet's
// SetGroupConfig model: an exercise is one or more GROUPS of identical sets (not per-set
// editing, not fully uniform either) — "3 sets of 10 @ 60kg, then 2 sets of 8 @ 70kg" is two
// groups. Saving flattens groups into the literal ExerciseSet array, matching
// applyGroupsToExercise on iOS. Field layout mirrors iOS: a big name field, boxed segmented
// type selectors, a labelled scrollable set-type row, rep steppers / rep-range control, and
// scroll-wheel pickers (WheelPickerRow) for duration, distance, and rest.

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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

private val RowBackground = Color.White.copy(alpha = 0.07f)
private val CompletedGreen = Color(0xFF34C759)

enum class FreqKind(val backendValue: String) { REPS("Repetitions"), DURATION("Duration"), DISTANCE("Distance") }
enum class ResistKind(val backendValue: String) { WEIGHT("Weight"), BAND("Band"), NONE("None") }

// Matches iOS SetType raw values. NONE is not user-selectable (auto-applied to duration/distance
// groups, mirroring `group.setType = .none` on iOS when switching away from Reps).
enum class SetTypeOption(val backendValue: String, val shortLabel: String, val badgeColor: Color?) {
    NORMAL("Normal", "Normal", null),
    WARMUP("Warm-up", "Warm-up", Color(0xFFFF9F0A)),
    DROP("Drop set", "Drop", Color(0xFFAF52DE)),
    FAILURE("Failure", "Failure", Color(0xFFFF453A)),
    BACKOFF("Backoff set", "Backoff", Color(0xFF0A84FF)),
}

// Mirrors iOS's SetGroupConfig — one card of the editor represents `count` identical sets.
// Individual mutableStateOf fields (not a data class in a list) so steppers/toggles can mutate
// a single field in place without recomposing/copying the whole group list.
class ExerciseSetGroupState(
    val id: String = UUID.randomUUID().toString(),
    freqKind: FreqKind,
    resistKind: ResistKind,
    count: Int,
    reps: Int,
    repsMax: Int?,
    durationText: String,
    distanceText: String,
    weightText: String,
    bandText: String,
    restSeconds: Int,
    setType: SetTypeOption,
) {
    var freqKind by mutableStateOf(freqKind)
    var resistKind by mutableStateOf(resistKind)
    var count by mutableStateOf(count)
    var reps by mutableStateOf(reps)
    var repsMax by mutableStateOf(repsMax)
    var durationText by mutableStateOf(durationText)
    var distanceText by mutableStateOf(distanceText)
    var weightText by mutableStateOf(weightText)
    var bandText by mutableStateOf(bandText)
    var restSeconds by mutableStateOf(restSeconds)
    var setType by mutableStateOf(setType)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseEditorSheet(
    existing: ExerciseData?,
    onSave: (ExerciseData) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // Locked + animated like the add-exercise / superset sheets (iOS .interactiveDismissDisabled):
    // reject a user swipe/scrim, but allow our own hide so X / checkmark slide the sheet down.
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
    var name by remember { mutableStateOf(existing?.name ?: "") }
    val groups = remember { mutableStateListOf<ExerciseSetGroupState>().apply { addAll(groupsFromSets(existing)) } }

    val isValid = name.isNotBlank() && groups.any { it.count > 0 }

    ModalBottomSheet(
        // No-op: scrim taps / back don't dismiss — only the X or checkmark do (iOS locks this sheet).
        onDismissRequest = {},
        sheetState = sheetState,
        containerColor = Color.Transparent,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
      // 93%-height sheet (stops below the status bar), rounded top + theme gradient — matching the
      // add-exercise / superset sheets. iOS: .large detent, presentationCornerRadius 30, theme gradient.
      Column(
          modifier = Modifier
              .fillMaxHeight(0.93f)
              .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
              .trackstarBackground()
      ) {
        // Static iOS-style header: X discards in-sheet edits, checkmark commits (dimmed until
        // valid). No bottom Save/Cancel — commit/discard live here. Delete is handled by the
        // exercise row itself (this sheet has no delete, matching iOS).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
        ) {
            HeaderCircle(icon = Icons.Filled.Close, contentDescription = "Discard", onClick = { close(onDismiss) })
            Spacer(modifier = Modifier.weight(1f))
            Text(if (existing == null) "Add Exercise" else "Edit Exercise", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Spacer(modifier = Modifier.weight(1f))
            HeaderCircle(
                icon = Icons.Filled.Check, contentDescription = "Save", enabled = isValid,
                onClick = { val e = buildExercise(existing, name, groups); close { onSave(e) } },
            )
        }
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            item {
                // Big prominent name field in a rounded card, matching iOS's 22pt name field.
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
                        cursorBrush = SolidColor(TrackstarAccent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        decorationBox = { inner ->
                            if (name.isEmpty()) {
                                Text("Exercise name", color = Color.White.copy(alpha = 0.4f), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                            }
                            inner()
                        },
                    )
                    if (name.isBlank()) {
                        Text("Name is required", fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f), modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }

            itemsIndexed(groups) { index, group ->
                SetGroupCard(
                    group = group,
                    canRemove = groups.size > 1,
                    onRemove = { groups.remove(group) },
                )
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(RowBackground)
                        .clickable {
                            val last = groups.lastOrNull()
                            groups.add(
                                if (last != null) {
                                    ExerciseSetGroupState(
                                        freqKind = last.freqKind, resistKind = last.resistKind, count = 1,
                                        reps = last.reps, repsMax = last.repsMax, durationText = last.durationText,
                                        distanceText = last.distanceText, weightText = last.weightText, bandText = last.bandText,
                                        restSeconds = last.restSeconds, setType = last.setType,
                                    )
                                } else defaultGroup()
                            )
                        }
                        .padding(vertical = 12.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.padding(start = 6.dp))
                    Text("Add a different set", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.7f))
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
      }
    }
}

// 44dp glass circle matching iOS's .glassCircle(); checkmark dims to 35% when disabled (invalid).
@Composable
private fun HeaderCircle(icon: ImageVector, contentDescription: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White.copy(alpha = if (enabled) 1f else 0.35f), modifier = Modifier.size(16.dp))
    }
}

// LazyListScope.items with index isn't imported by default under that name; small local helper
// to keep the call site above readable.
private fun LazyListScope.itemsIndexed(list: List<ExerciseSetGroupState>, content: @Composable (Int, ExerciseSetGroupState) -> Unit) {
    items(list.size) { index -> content(index, list[index]) }
}

@Composable
private fun SetGroupCard(group: ExerciseSetGroupState, canRemove: Boolean, onRemove: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(RowBackground).padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // 1. Set Type row — reps groups only (mirrors iOS: hidden for Duration/Distance,
        // where setType is meaningless and forced to NORMAL on save). "Set Type" label above a
        // horizontally-scrolling row of pills.
        if (group.freqKind == FreqKind.REPS) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text("Set Type", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.5f))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SetTypeOption.entries) { option ->
                        val selected = group.setType == option
                        Text(
                            option.shortLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            color = if (selected) Color.White else Color.White.copy(alpha = 0.4f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background((option.badgeColor ?: Color.White).copy(alpha = if (selected) 0.35f else 0.08f))
                                .clickable { group.setType = option }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            Divider()
        }

        // 2. Sets count — big count on the left (mirrors iOS), stepper on the right.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Sets", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.5f))
                Text("${group.count}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(modifier = Modifier.weight(1f))
            MiniStepper(value = group.count, onDecrement = { if (group.count > 1) group.count-- }, onIncrement = { group.count++ }, big = true)
        }
        Divider()

        // 3. Frequency type selector
        SegmentedRow(
            options = listOf("Reps", "Duration", "Distance"),
            selectedIndex = FreqKind.entries.indexOf(group.freqKind),
            onSelect = { index ->
                group.freqKind = FreqKind.entries[index]
                if (group.freqKind != FreqKind.REPS) {
                    group.setType = SetTypeOption.NORMAL
                    group.repsMax = null
                }
            },
        )
        Divider()

        // 4. Frequency value
        FrequencyValueRow(group)
        Divider()

        // 5. Resistance type selector
        SegmentedRow(
            options = listOf("Weight", "Band", "None"),
            selectedIndex = ResistKind.entries.indexOf(group.resistKind),
            onSelect = { group.resistKind = ResistKind.entries[it] },
        )

        // 6. Resistance value (hidden for None)
        if (group.resistKind != ResistKind.NONE) {
            Divider()
            when (group.resistKind) {
                ResistKind.WEIGHT -> ResistanceValueRow("Weight", group.weightText, { group.weightText = it }, placeholder = "0", unit = "kg", fieldWidth = 64.dp, keyboardType = KeyboardType.Decimal)
                ResistKind.BAND -> ResistanceValueRow("Band", group.bandText, { group.bandText = it }, placeholder = "e.g. Medium", unit = null, fieldWidth = 120.dp)
                ResistKind.NONE -> {}
            }
        }
        Divider()

        // 7. Rest — "Rest" label + formatted value, then a min/sec scroll-wheel (iOS RestTimePicker).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 2.dp)
        ) {
            Text("Rest", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.weight(1f))
            Text(formatRestTime(group.restSeconds), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.45f))
        }
        WheelPickerRow(
            columns = listOf(
                WheelColumn(
                    items = (0..10).map { it.toString() },
                    selectedIndex = (group.restSeconds / 60).coerceIn(0, 10),
                    onSelectedIndexChange = { group.restSeconds = it * 60 + group.restSeconds % 60 },
                    unit = "min",
                ),
                WheelColumn(
                    items = (0..59).map { it.toString() },
                    selectedIndex = group.restSeconds % 60,
                    onSelectedIndexChange = { group.restSeconds = (group.restSeconds / 60) * 60 + it },
                    unit = "sec",
                ),
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
        )

        // 8. Remove group
        if (canRemove) {
            Divider()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onRemove).padding(vertical = 12.dp)
            ) {
                Text("Remove", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFFF453A).copy(alpha = 0.85f))
            }
        }
    }
}

@Composable
private fun FrequencyValueRow(group: ExerciseSetGroupState) {
    when (group.freqKind) {
        FreqKind.REPS -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Toggle pill: single reps <-> range. Mirrors iOS's tap-to-toggle pill to the
                // left of the value control, fixed row height so toggling doesn't jump the card.
                Text(
                    if (group.repsMax != null) "Range ⇄" else "Reps ⇄",
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.1f))
                        .clickable {
                            if (group.repsMax != null) {
                                group.repsMax = null
                            } else {
                                group.reps = 1
                                group.repsMax = 2
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
                val max = group.repsMax
                if (max != null) {
                    RepsRangeControl(
                        min = group.reps,
                        max = max,
                        onMinChange = { newMin ->
                            group.reps = newMin
                            if (newMin >= group.repsMax!!) group.repsMax = newMin + 1
                        },
                        onMaxChange = { newMax -> group.repsMax = newMax },
                    )
                } else {
                    MiniStepper(value = group.reps, onDecrement = { if (group.reps > 0) group.reps-- }, onIncrement = { group.reps++ }, big = true)
                }
            }
        }
        FreqKind.DURATION -> {
            val (h, m, s) = parseDuration(group.durationText)
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 2.dp)
                ) {
                    Text("Duration", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.weight(1f))
                    Text(group.durationText.ifBlank { "0 sec" }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.45f))
                }
                WheelPickerRow(
                    columns = listOf(
                        WheelColumn((0..23).map { it.toString() }, h.coerceIn(0, 23), { group.durationText = formatDuration(it, m, s) }, unit = "hr", width = 48.dp),
                        WheelColumn((0..59).map { it.toString() }, m.coerceIn(0, 59), { group.durationText = formatDuration(h, it, s) }, unit = "min", width = 48.dp),
                        WheelColumn((0..59).map { it.toString() }, s.coerceIn(0, 59), { group.durationText = formatDuration(h, m, it) }, unit = "sec", width = 48.dp),
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }
        }
        FreqKind.DISTANCE -> {
            val (km, meters) = parseDistance(group.distanceText)
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 2.dp)
                ) {
                    Text("Distance", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.weight(1f))
                    Text(group.distanceText.ifBlank { "0 m" }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.45f))
                }
                WheelPickerRow(
                    columns = listOf(
                        WheelColumn((0..99).map { it.toString() }, km.coerceIn(0, 99), { group.distanceText = formatDistance(it, meters) }, unit = "km"),
                        WheelColumn((0..99).map { (it * 10).toString() }, (meters / 10).coerceIn(0, 99), { group.distanceText = formatDistance(km, it * 10) }, unit = "m"),
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }
        }
    }
}

// Duration/distance are stored as human strings ("2 minute 30 sec", "5 km 200 m") — parse them
// into wheel positions and re-format on change, mirroring iOS's formattedTime / formattedDistance.
private fun parseDuration(text: String): Triple<Int, Int, Int> {
    fun grab(unit: String) = Regex("(\\d+)\\s*$unit").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    return Triple(grab("hour"), grab("minute"), grab("sec"))
}

private fun formatDuration(h: Int, m: Int, s: Int): String {
    val parts = buildList {
        if (h > 0) add("$h hour")
        if (m > 0) add("$m minute")
        if (s > 0 || (h == 0 && m == 0)) add("$s sec")
    }
    return parts.joinToString(" ")
}

private fun parseDistance(text: String): Pair<Int, Int> {
    val kmMatch = Regex("(\\d+)\\s*km").find(text)
    val km = kmMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
    val rest = if (kmMatch != null) text.removeRange(kmMatch.range) else text
    val meters = Regex("(\\d+)\\s*m").find(rest)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    return km to meters
}

private fun formatDistance(km: Int, meters: Int): String {
    if (km <= 0 && meters <= 0) return "0 m"
    return buildList {
        if (km > 0) add("$km km")
        if (meters > 0) add("$meters m")
    }.joinToString(" ")
}

private fun formatRestTime(totalSeconds: Int): String {
    if (totalSeconds <= 0) return "None"
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return buildList {
        if (m > 0) add("$m min")
        if (s > 0) add("$s sec")
    }.joinToString(" ")
}

// Dual stepper: [- min +]  "min to max"  [- max +]. Mirrors iOS's RepsRangeControl exactly:
// min-increment drags max along to preserve min < max; max-decrement is blocked (not
// compensated) at max <= min+1.
@Composable
private fun RepsRangeControl(min: Int, max: Int, onMinChange: (Int) -> Unit, onMaxChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StepperButtons(
            onDecrement = { if (min > 1) onMinChange(min - 1) },
            onIncrement = { onMinChange(min + 1) },
        )
        Text(
            "$min to $max", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        StepperButtons(
            onDecrement = { if (max > min + 1) onMaxChange(max - 1) },
            onIncrement = { onMaxChange(max + 1) },
        )
    }
}

@Composable
private fun StepperButtons(onDecrement: () -> Unit, onIncrement: () -> Unit) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(Color.White.copy(alpha = 0.08f))
    ) {
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
private fun Divider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)).padding(horizontal = 16.dp))
}

// Boxed segmented control (translucent container, selected segment a filled rounded rect) —
// mirrors iOS's typeSelector rather than free-floating pills.
@Composable
private fun SegmentedRow(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(4.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Text(
                label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.4f),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) Color.White.copy(alpha = 0.2f) else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 7.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MiniStepper(value: Int, suffix: String = "", onDecrement: () -> Unit, onIncrement: () -> Unit, big: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (big) 16.dp else 8.dp)) {
        Box(
            modifier = Modifier.size(if (big) 32.dp else 26.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).clickable(onClick = onDecrement),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease", tint = Color.White, modifier = Modifier.size(if (big) 14.dp else 12.dp))
        }
        Text(
            "$value$suffix", fontSize = if (big) 18.sp else 14.sp, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace, color = Color.White,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
        Box(
            modifier = Modifier.size(if (big) 32.dp else 26.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).clickable(onClick = onIncrement),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Increase", tint = Color.White, modifier = Modifier.size(if (big) 14.dp else 12.dp))
        }
    }
}

// Resistance value: a label on the left, a trailing right-aligned entry field, and an optional
// unit caption ("kg") — mirrors iOS's resistanceValueRow.
@Composable
private fun ResistanceValueRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    unit: String?,
    fieldWidth: androidx.compose.ui.unit.Dp,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.weight(1f))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White, textAlign = TextAlign.End),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            cursorBrush = SolidColor(TrackstarAccent),
            modifier = Modifier.width(fieldWidth),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterEnd) {
                    if (value.isEmpty()) {
                        Text(placeholder, color = Color.White.copy(alpha = 0.3f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
                    }
                    inner()
                }
            },
        )
        if (unit != null) {
            Spacer(modifier = Modifier.padding(start = 6.dp))
            Text(unit, fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f))
        }
    }
}

private fun defaultGroup(): ExerciseSetGroupState = ExerciseSetGroupState(
    freqKind = FreqKind.REPS, resistKind = ResistKind.WEIGHT, count = 3, reps = 10, repsMax = null,
    durationText = "30 sec", distanceText = "0 m", weightText = "", bandText = "", restSeconds = 60,
    setType = SetTypeOption.NORMAL,
)

// Coalesces consecutive identical ExerciseSets into groups — mirrors ExerciseView.groupedSets /
// CreateExerciseBottomSheet.groupsFromSets on iOS (order-preserving coalescing, not full dedup).
private fun groupsFromSets(existing: ExerciseData?): List<ExerciseSetGroupState> {
    val sets = existing?.sets.orEmpty()
    if (sets.isEmpty()) return listOf(defaultGroup())

    data class Key(val reps: Int?, val repsMax: Int?, val duration: String?, val distance: String?, val weight: String?, val band: String?, val rest: Int, val setType: String?)

    val groups = mutableListOf<Pair<Key, Int>>()
    val sourceSets = mutableListOf<ExerciseSet>()
    sets.forEach { set ->
        val key = Key(
            set.frequencyValue?.reps, set.repsMax, set.frequencyValue?.duration, set.frequencyValue?.distance,
            set.resistanceValue?.weight, set.resistanceValue?.bandLevel, set.restSeconds ?: 0, set.setType,
        )
        if (groups.isNotEmpty() && groups.last().first == key) {
            groups[groups.size - 1] = key to (groups.last().second + 1)
        } else {
            groups.add(key to 1)
            sourceSets.add(set)
        }
    }
    return groups.mapIndexed { i, (key, count) ->
        val freqKind = when {
            key.duration != null -> FreqKind.DURATION
            key.distance != null -> FreqKind.DISTANCE
            else -> FreqKind.REPS
        }
        // A present weight — even an empty "" from a freshly-picked exercise's stub — selects the
        // Weight type, mirroring iOS's Resistance.Value where `.weight("")` implies Weight. A
        // genuinely absent resistance (null, e.g. an existing bodyweight exercise) stays None. So
        // only *new* exercises default to Weight; existing None/Band exercises are respected.
        val resistKind = when {
            key.weight != null -> ResistKind.WEIGHT
            key.band != null -> ResistKind.BAND
            else -> ResistKind.NONE
        }
        val setType = SetTypeOption.entries.find { it.backendValue == key.setType } ?: SetTypeOption.NORMAL
        ExerciseSetGroupState(
            freqKind = freqKind, resistKind = resistKind, count = count,
            reps = key.reps ?: 10, repsMax = key.repsMax,
            durationText = key.duration ?: "30 sec", distanceText = key.distance ?: "0 m",
            weightText = key.weight ?: "", bandText = key.band ?: "",
            restSeconds = key.rest, setType = if (freqKind == FreqKind.REPS) setType else SetTypeOption.NORMAL,
        )
    }
}

private fun buildExercise(existing: ExerciseData?, name: String, groups: List<ExerciseSetGroupState>): ExerciseData {
    val sets = groups.filter { it.count > 0 }.flatMap { group ->
        val freqValue = when (group.freqKind) {
            FreqKind.REPS -> FrequencyValue(reps = group.reps)
            FreqKind.DURATION -> FrequencyValue(duration = group.durationText.ifBlank { "30 sec" })
            FreqKind.DISTANCE -> FrequencyValue(distance = group.distanceText.ifBlank { "0 m" })
        }
        val resistValue = when (group.resistKind) {
            ResistKind.WEIGHT -> ResistanceValue(weight = group.weightText.ifBlank { "0" })
            ResistKind.BAND -> ResistanceValue(bandLevel = group.bandText.ifBlank { "Medium" })
            ResistKind.NONE -> ResistanceValue()
        }
        (0 until group.count).map {
            ExerciseSet(
                id = UUID.randomUUID().toString(),
                frequencyValue = freqValue,
                resistanceValue = resistValue,
                restSeconds = group.restSeconds,
                setType = if (group.freqKind == FreqKind.REPS) group.setType.backendValue else SetTypeOption.NORMAL.backendValue,
                repsMax = if (group.freqKind == FreqKind.REPS) group.repsMax else null,
            )
        }
    }
    // Exercise-level frequency/resistance metadata comes from the FIRST group only, matching
    // iOS's applyGroupsToExercise (if groups mix types, the first group's type "wins").
    val first = groups.firstOrNull { it.count > 0 } ?: groups.first()
    val resistUnit = when (first.resistKind) {
        ResistKind.WEIGHT -> ResistanceUnit(weight = "KG")
        ResistKind.BAND -> ResistanceUnit(bandLevel = true)
        ResistKind.NONE -> ResistanceUnit()
    }
    return ExerciseData(
        id = existing?.id ?: UUID.randomUUID().toString(),
        name = name.trim(),
        sets = sets,
        frequencyType = first.freqKind.backendValue,
        resistanceType = first.resistKind.backendValue,
        resistanceUnit = resistUnit,
        compoundGroupId = existing?.compoundGroupId,
    )
}
