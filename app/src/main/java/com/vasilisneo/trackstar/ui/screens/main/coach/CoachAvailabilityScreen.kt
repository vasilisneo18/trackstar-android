package com.vasilisneo.trackstar.ui.screens.main.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vasilisneo.trackstar.ui.components.GlassCircleIconButton
import com.vasilisneo.trackstar.ui.theme.TrackstarAccent
import com.vasilisneo.trackstar.ui.theme.trackstarBackground
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val CardFill = Color.White.copy(alpha = 0.06f)
private val CardBorder = Color.White.copy(alpha = 0.09f)

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
                GlassCircleIconButton(onClick = { showAdd = true }, icon = Icons.Filled.Add, contentDescription = "Add slot")
            }
            Text("Availability", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))

            when {
                viewModel.isLoading && viewModel.slots.isEmpty() ->
                    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Color.White.copy(alpha = 0.6f)) }
                viewModel.slots.isEmpty() ->
                    Box(Modifier.fillMaxSize().padding(bottom = 80.dp), Alignment.Center) {
                        Text("No slots yet. Tap + to add availability.", color = Color.White.copy(alpha = 0.5f))
                    }
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    viewModel.slotsByDate.forEach { (date, slots) ->
                        item(key = "hdr-$date") {
                            Text(prettyDate(date), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                        }
                        items(slots, key = { it.id }) { slot ->
                            CoachSlotCard(
                                time = "${slot.startTime} – ${slot.endTime}",
                                title = slot.title,
                                capacityLabel = "${slot.bookedByCount()}/${slot.capacity} booked",
                                attendees = slot.attendees?.mapNotNull { it.name } ?: emptyList(),
                                onDelete = { viewModel.deleteSlot(slot.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddSlotSheet(
            onDismiss = { showAdd = false },
            onAdd = { date, start, end, capacity, title ->
                viewModel.addSlot(date, start, end, capacity, title, null) { ok -> if (ok) showAdd = false }
            },
        )
    }
}

private fun com.vasilisneo.trackstar.data.api.SlotResponse.bookedByCount(): Int = capacity - remaining

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
        Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete slot", tint = Color(0xFFE5484D).copy(alpha = 0.8f),
            modifier = Modifier.size(22.dp).clickable(onClick = onDelete))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSlotSheet(onDismiss: () -> Unit, onAdd: (date: String, start: String, end: String, capacity: Int, title: String) -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var start by remember { mutableStateOf("09:00") }
    var end by remember { mutableStateOf("10:00") }
    var capacity by remember { mutableStateOf(1) }
    var title by remember { mutableStateOf("") }

    var pickDate by remember { mutableStateOf(false) }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = com.vasilisneo.trackstar.ui.theme.TrackstarSurface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("New slot", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)

            FieldRow(label = "Date", value = prettyDate(date), onClick = { pickDate = true })
            FieldRow(label = "Start", value = start, onClick = { pickStart = true })
            FieldRow(label = "End", value = end, onClick = { pickEnd = true })

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Capacity", color = Color.White.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
                StepperButton("−") { if (capacity > 1) capacity-- }
                Text("$capacity", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(36.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                StepperButton("+") { capacity++ }
            }
            Text(if (capacity == 1) "One-on-one session" else "Group session ($capacity spots)",
                fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f))

            com.vasilisneo.trackstar.ui.components.AuthTextField(value = title, onValueChange = { title = it }, placeholder = "Title (optional) e.g. PT session")

            Spacer(Modifier.height(4.dp))
            com.vasilisneo.trackstar.ui.components.AuthCapsuleButton(
                text = "Add slot",
                onClick = { onAdd(date, start, end, capacity, title) },
                enabled = start < end,
            )
            if (start >= end) Text("End time must be after start time", fontSize = 12.sp, color = Color(0xFFE5484D))
        }
    }

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = LocalDate.parse(date).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate().toString() }
                    pickDate = false
                }) { Text("OK") }
            },
        ) { DatePicker(state = state) }
    }
    if (pickStart) TimePickerSheet(initial = start, onDismiss = { pickStart = false }, onPick = { start = it; pickStart = false })
    if (pickEnd) TimePickerSheet(initial = end, onDismiss = { pickEnd = false }, onPick = { end = it; pickEnd = false })
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

// "2026-08-11" -> "Mon, 11 Aug"
private fun prettyDate(iso: String): String = runCatching {
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("EEE, d MMM"))
}.getOrDefault(iso)
