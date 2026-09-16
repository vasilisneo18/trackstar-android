package com.vasilisneo.trackstar.ui.screens.main.plan

// Configure two exercises performed back-to-back as a superset. Each exercise uses the same
// SetGroupCard as a single exercise (frequency reps/duration/distance, resistance weight/band/none,
// set type), while Sets and Rest are shared across both. Writes both with a matching compoundGroupId
// so they render as one paired unit (see data/workout/ExerciseGrouping.kt).

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vasilisneo.trackstar.data.api.ExerciseData
import com.vasilisneo.trackstar.ui.components.WheelColumn
import com.vasilisneo.trackstar.ui.components.WheelPickerRow
import com.vasilisneo.trackstar.ui.theme.TrackstarAccent
import com.vasilisneo.trackstar.ui.theme.trackstarBackground
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompoundExercisePairSheet(
    initialExerciseA: ExerciseData?,
    initialExerciseB: ExerciseData?,
    onSave: (ExerciseData, ExerciseData) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var allowHide by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || allowHide },
    )
    fun close(after: () -> Unit) {
        allowHide = true
        scope.launch { sheetState.hide(); after() }
    }

    val editKey = initialExerciseA?.id to initialExerciseB?.id
    var nameA by remember(editKey) { mutableStateOf(initialExerciseA?.name ?: "") }
    var nameB by remember(editKey) { mutableStateOf(initialExerciseB?.name ?: "") }
    // Each half is a full single-exercise group; Sets and Rest are shared and hidden inside them.
    val groupA = remember(editKey) { firstGroupState(initialExerciseA) }
    val groupB = remember(editKey) { firstGroupState(initialExerciseB) }
    var rounds by remember(editKey) { mutableStateOf(initialExerciseA?.sets?.size?.takeIf { it > 0 } ?: 3) }
    val initialRest = initialExerciseA?.sets?.firstOrNull()?.restSeconds ?: 60
    var restMinutes by remember(editKey) { mutableStateOf(initialRest / 60) }
    var restSeconds by remember(editKey) { mutableStateOf(initialRest % 60) }

    val isValid = nameA.isNotBlank() && nameB.isNotBlank() && rounds > 0

    ModalBottomSheet(
        onDismissRequest = {},
        sheetState = sheetState,
        containerColor = Color.Transparent,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
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
                ) { Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = Color.White, modifier = Modifier.size(16.dp)) }
                Spacer(modifier = Modifier.weight(1f))
                Text("Superset", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f))
                        .clickable(enabled = isValid) {
                            val restTotal = restMinutes * 60 + restSeconds
                            val groupId = initialExerciseA?.compoundGroupId ?: UUID.randomUUID().toString()
                            groupA.count = rounds; groupA.restSeconds = restTotal
                            groupB.count = rounds; groupB.restSeconds = restTotal
                            val exA = buildExercise(initialExerciseA, nameA, listOf(groupA)).copy(compoundGroupId = groupId)
                            val exB = buildExercise(initialExerciseB, nameB, listOf(groupB)).copy(compoundGroupId = groupId)
                            close { onSave(exA, exB) }
                        },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Check, contentDescription = "Save", tint = Color.White.copy(alpha = if (isValid) 1f else 0.35f), modifier = Modifier.size(16.dp)) }
            }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                item {
                    Text("Two exercises performed back-to-back with no rest between them, sharing the same sets and rest.",
                        fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f))
                }

                item { SectionLabel("Exercise 1") }
                item { NameCard(nameA) { nameA = it } }
                item { SetGroupCard(group = groupA, showSets = false, showRest = false) }

                item {
                    Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.ArrowDownward, contentDescription = null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(14.dp))
                    }
                }

                item { SectionLabel("Exercise 2") }
                item { NameCard(nameB) { nameB = it } }
                item { SetGroupCard(group = groupB, showSets = false, showRest = false) }

                // Shared Sets
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.06f)).padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Sets", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.5f))
                            Text("$rounds", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        SharedStepper(value = rounds, onDecrement = { if (rounds > 1) rounds-- }, onIncrement = { rounds++ })
                    }
                }
                // Shared Rest
                item {
                    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.06f))) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                            Text("Rest between rounds", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.weight(1f))
                            Text(restLabel(restMinutes * 60 + restSeconds), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.45f))
                        }
                        WheelPickerRow(
                            columns = listOf(
                                WheelColumn((0..10).map { it.toString() }, restMinutes.coerceIn(0, 10), { restMinutes = it }, unit = "min"),
                                WheelColumn((0..59).map { it.toString() }, restSeconds.coerceIn(0, 59), { restSeconds = it }, unit = "sec"),
                            ),
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
private fun SectionLabel(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(start = 4.dp))
}

@Composable
private fun NameCard(name: String, onNameChange: (String) -> Unit) {
    BasicTextField(
        value = name,
        onValueChange = onNameChange,
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
        cursorBrush = SolidColor(TrackstarAccent),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.06f)).padding(horizontal = 16.dp, vertical = 14.dp),
        decorationBox = { inner ->
            if (name.isEmpty()) {
                Text("Exercise name", color = Color.White.copy(alpha = 0.4f), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            inner()
        },
    )
}

@Composable
private fun SharedStepper(value: Int, onDecrement: () -> Unit, onIncrement: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).clickable(onClick = onDecrement), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease", tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Text("$value", fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color.White)
        Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).clickable(onClick = onIncrement), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Add, contentDescription = "Increase", tint = Color.White, modifier = Modifier.size(14.dp))
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
