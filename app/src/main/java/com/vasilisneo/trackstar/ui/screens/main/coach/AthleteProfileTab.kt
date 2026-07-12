package com.vasilisneo.trackstar.ui.screens.main.coach

// The coach's athlete-detail Profile tab — ports iOS's profileContent: the athlete's bio (from
// their profile), an editable training profile + goals / injuries / coach notes (persisted to
// /coach/athletes/{id}/notes), and a Remove Athlete action.

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vasilisneo.trackstar.data.api.AthleteNotesDto
import com.vasilisneo.trackstar.data.api.ProfileResponse
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FitnessLevels = listOf("Beginner", "Intermediate", "Advanced", "Elite")

@Composable
fun AthleteProfileTab(viewModel: AthleteDetailViewModel, modifier: Modifier = Modifier) {
    val athlete = viewModel.athlete
    val notes = viewModel.notes

    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 40.dp),
    ) {
        Section("Athlete") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BioCell("Age", athlete?.age?.toString() ?: "—", Modifier.weight(1f))
                BioCell("Gender", athlete?.gender?.replaceFirstChar { it.uppercase() } ?: "—", Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BioCell("Height", athlete?.height?.let { "${it.toInt()} cm" } ?: "—", Modifier.weight(1f))
                BioCell("Weight", athlete?.weight?.let { "${it.toInt()} kg" } ?: "—", Modifier.weight(1f))
            }
        }

        Section("Training") {
            StartDateRow(notes.startDate) { viewModel.updateNotes(notes.copy(startDate = it)) }
            Divider()
            FitnessLevelRow(notes.fitnessLevel) { viewModel.updateNotes(notes.copy(fitnessLevel = it)) }
            Divider()
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Text("Sessions / Week", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White)
                Spacer(modifier = Modifier.weight(1f))
                Stepper(
                    value = notes.trainingDaysPerWeek,
                    onDecrement = { if (notes.trainingDaysPerWeek > 1) viewModel.updateNotes(notes.copy(trainingDaysPerWeek = notes.trainingDaysPerWeek - 1)) },
                    onIncrement = { if (notes.trainingDaysPerWeek < 7) viewModel.updateNotes(notes.copy(trainingDaysPerWeek = notes.trainingDaysPerWeek + 1)) },
                )
            }
        }

        Section("Goals") { NotesField(notes.goals, "Training goals...", minLines = 3, maxLines = 6) { viewModel.updateNotes(notes.copy(goals = it)) } }
        Section("Injuries & Limitations") { NotesField(notes.injuries, "Any injuries or limitations to consider...", minLines = 2, maxLines = 4) { viewModel.updateNotes(notes.copy(injuries = it)) } }
        Section("Coach Notes") { NotesField(notes.notes, "General notes about this athlete...", minLines = 3, maxLines = 6) { viewModel.updateNotes(notes.copy(notes = it)) } }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(Locale.ENGLISH), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(start = 4.dp))
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.06f)).padding(16.dp), content = content)
    }
}

private typealias ColumnScope = androidx.compose.foundation.layout.ColumnScope

@Composable
private fun BioCell(title: String, value: String, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.08f)).padding(vertical = 16.dp)) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.5f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartDateRow(dateStr: String, onChange: (String) -> Unit) {
    val date = remember(dateStr) { runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: LocalDate.now() }
    var showPicker by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text("Start Date", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White)
        Spacer(modifier = Modifier.weight(1f))
        Text(
            date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.1f)).clickable { showPicker = true }.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
    if (showPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate().toString()) }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun FitnessLevelRow(current: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text("Fitness Level", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White)
        Spacer(modifier = Modifier.weight(1f))
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.1f)).clickable { expanded = true }.padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(current.ifBlank { "Beginner" }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Icon(Icons.Filled.UnfoldMore, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(15.dp))
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                shape = RoundedCornerShape(14.dp),
                containerColor = Color(0xFF20202C),
                shadowElevation = 14.dp,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            ) {
                FitnessLevels.forEach { level ->
                    val selected = level.equals(current.ifBlank { "Beginner" }, ignoreCase = true)
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(150.dp)) {
                                Text(level, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = Color.White)
                                Spacer(modifier = Modifier.weight(1f))
                                if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF34C759), modifier = Modifier.size(16.dp))
                            }
                        },
                        onClick = { onSelect(level); expanded = false },
                    )
                }
            }
        }
    }
}

// iOS-style segmented stepper: the value, then a joined − | + control with a hairline divider.
@Composable
private fun Stepper(value: Int, onDecrement: () -> Unit, onIncrement: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("$value", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(32.dp).clip(RoundedCornerShape(9.dp)).background(Color.White.copy(alpha = 0.12f)),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxHeight().clickable(onClick = onDecrement).padding(horizontal = 14.dp)) {
                Icon(Icons.Filled.Remove, contentDescription = "Decrease", tint = Color.White, modifier = Modifier.size(15.dp))
            }
            Box(modifier = Modifier.width(1.dp).height(18.dp).background(Color.White.copy(alpha = 0.18f)))
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxHeight().clickable(onClick = onIncrement).padding(horizontal = 14.dp)) {
                Icon(Icons.Filled.Add, contentDescription = "Increase", tint = Color.White, modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
private fun Divider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
}

// Commits on focus loss (avoids a POST per keystroke), like the plan's session-title field.
// minLines gives the multi-line min height iOS uses (goals/notes 3, injuries 2).
@Composable
private fun NotesField(initial: String, placeholder: String, minLines: Int, maxLines: Int, onCommit: (String) -> Unit) {
    var text by remember(initial) { mutableStateOf(initial) }
    BasicTextField(
        value = text,
        onValueChange = { text = it },
        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
        cursorBrush = SolidColor(Color.White),
        minLines = minLines,
        maxLines = maxLines,
        modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused && text != initial) onCommit(text) },
        decorationBox = { inner ->
            Box {
                if (text.isEmpty()) Text(placeholder, color = Color.White.copy(alpha = 0.35f), fontSize = 14.sp)
                inner()
            }
        },
    )
}
