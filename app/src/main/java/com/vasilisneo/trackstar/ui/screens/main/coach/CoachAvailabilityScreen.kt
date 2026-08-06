package com.vasilisneo.trackstar.ui.screens.main.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vasilisneo.trackstar.data.api.SlotResponse
import com.vasilisneo.trackstar.ui.components.GlassCircleIconButton
import com.vasilisneo.trackstar.ui.screens.main.plan.DayTabPill
import com.vasilisneo.trackstar.ui.screens.main.plan.WeekNavigationBar
import com.vasilisneo.trackstar.ui.screens.main.plan.formattedWeekRange
import com.vasilisneo.trackstar.ui.theme.TrackstarAccent
import com.vasilisneo.trackstar.ui.theme.trackstarBackground

private val CardFill = Color.White.copy(alpha = 0.06f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachAvailabilityScreen(
    onBack: () -> Unit = {},
    viewModel: CoachAvailabilityViewModel = viewModel(),
) {
    var showAdd by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().trackstarBackground()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                GlassCircleIconButton(onClick = onBack, icon = Icons.Filled.Close, contentDescription = "Close")
                Spacer(Modifier.weight(1f))
            }
            Text("Availability", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))

            // Day tabs — same expand/collapse pills as the weekly plan; a dot marks days with slots.
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 8.dp)) {
                val gap = 6.dp
                val inactiveW = 34.dp
                val activeW = (maxWidth - inactiveW * 6 - gap * 6).coerceAtLeast(72.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth()) {
                    viewModel.weekDays.forEach { day ->
                        DayTabPill(
                            day = day.dayOfWeek,
                            isActive = day.dayOfWeek == viewModel.selectedDay,
                            hasExercises = viewModel.hasSlots(day.dayOfWeek),
                            activeWidth = activeW,
                            inactiveWidth = inactiveW,
                            onClick = { viewModel.goToDay(day) },
                        )
                    }
                }
            }

            val daySlots = viewModel.slotsForSelectedDay
            when {
                viewModel.isLoading && viewModel.slots.isEmpty() ->
                    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = TrackstarAccent) }
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    if (daySlots.isEmpty()) {
                        item {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
                            ) {
                                Icon(Icons.Filled.EventBusy, contentDescription = null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(46.dp))
                                Text("No sessions", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f))
                                Text("Add availability for this day.", fontSize = 13.sp, color = Color.White.copy(alpha = 0.3f))
                            }
                        }
                    }
                    items(daySlots, key = { it.id }) { slot ->
                        CoachSlotCard(
                            time = "${slot.startTime} – ${slot.endTime}",
                            title = slot.title,
                            capacityLabel = "${slot.bookedByCount()}/${slot.capacity} booked",
                            attendees = slot.attendees?.mapNotNull { it.name } ?: emptyList(),
                            onDelete = { viewModel.deleteSlot(slot.id) },
                        )
                    }
                    item { AddSlotButton(onClick = { showAdd = true }) }
                }
            }

            WeekNavigationBar(
                weekRange = formattedWeekRange(viewModel.weekStart),
                onPrevious = viewModel::goToPreviousWeek,
                onNext = viewModel::goToNextWeek,
            )
        }
    }

    if (showAdd) {
        AddSlotSheet(
            dayLabel = viewModel.selectedDay.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH),
            onDismiss = { showAdd = false },
            onAdd = { start, end, capacity, title, repeatWeeks ->
                viewModel.addSlot(start, end, capacity, title, null, repeatWeeks) { ok -> if (ok) showAdd = false }
            },
        )
    }
}

private fun SlotResponse.bookedByCount(): Int = capacity - remaining

@Composable
private fun CoachSlotCard(time: String, title: String?, capacityLabel: String, attendees: List<String>, onDelete: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardFill).padding(16.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(time, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            title?.takeIf { it.isNotBlank() }?.let {
                Text(it, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
            }
            Text(capacityLabel, fontSize = 12.sp, color = TrackstarAccent, modifier = Modifier.padding(top = 4.dp))
            if (attendees.isNotEmpty()) {
                Text(attendees.joinToString(", "), fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f), modifier = Modifier.padding(top = 2.dp))
            }
        }
        Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete session", tint = Color(0xFFE5484D).copy(alpha = 0.8f),
            modifier = Modifier.size(22.dp).clickable(onClick = onDelete))
    }
}

@Composable
private fun AddSlotButton(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp)
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text("Add Session", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.7f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSlotSheet(
    dayLabel: String,
    onDismiss: () -> Unit,
    onAdd: (start: String, end: String, capacity: Int, title: String, repeatWeeks: Int) -> Unit,
) {
    var start by remember { mutableStateOf("09:00") }
    var end by remember { mutableStateOf("10:00") }
    var capacity by remember { mutableIntStateOf(1) }
    var title by remember { mutableStateOf("") }
    var recurring by remember { mutableStateOf(false) }
    var weeks by remember { mutableIntStateOf(8) }

    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = com.vasilisneo.trackstar.ui.theme.TrackstarSurface) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.75f).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 24.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("New session · $dayLabel", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)

            FieldRow(label = "Start", value = start, onClick = { pickStart = true })
            FieldRow(label = "End", value = end, onClick = { pickEnd = true })

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Capacity", color = Color.White.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
                StepperButton("−") { if (capacity > 1) capacity-- }
                Text("$capacity", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
                StepperButton("+") { capacity++ }
            }
            Text(if (capacity == 1) "One-on-one session" else "Group session ($capacity spots)",
                fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f))

            com.vasilisneo.trackstar.ui.components.AuthTextField(value = title, onValueChange = { title = it }, placeholder = "Title (optional) e.g. PT session")

            // Recurring: repeat this slot on the same weekday for N weeks.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Repeat weekly", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("Same day & time each week", fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f))
                }
                Switch(
                    checked = recurring, onCheckedChange = { recurring = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = TrackstarAccent),
                )
            }
            if (recurring) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(4, 8, 12).forEach { n ->
                        WeeksChip(label = "$n weeks", selected = weeks == n, modifier = Modifier.weight(1f)) { weeks = n }
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            com.vasilisneo.trackstar.ui.components.AuthCapsuleButton(
                text = if (recurring) "Add $weeks sessions" else "Add session",
                onClick = { onAdd(start, end, capacity, title, if (recurring) weeks else 1) },
                enabled = start < end,
            )
            if (start >= end) Text("End time must be after start time", fontSize = 12.sp, color = Color(0xFFE5484D))
        }
    }

    if (pickStart) TimePickerSheet(initial = start, onDismiss = { pickStart = false }, onPick = { start = it; pickStart = false })
    if (pickEnd) TimePickerSheet(initial = end, onDismiss = { pickEnd = false }, onPick = { end = it; pickEnd = false })
}

@Composable
private fun WeeksChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) TrackstarAccent else Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(label, color = if (selected) Color.White else Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerSheet(initial: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val parts = initial.split(":")
    val state = rememberTimePickerState(initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 9, initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0, is24Hour = true)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick("%02d:%02d".format(state.hour, state.minute)) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) },
    )
}

@Composable
private fun FieldRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardFill).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(label, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StepperButton(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.1f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(symbol, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
}
