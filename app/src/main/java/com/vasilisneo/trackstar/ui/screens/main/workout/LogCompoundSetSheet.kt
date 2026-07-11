package com.vasilisneo.trackstar.ui.screens.main.workout

// Logs one round of a superset — both exercises' sets at once, in a single sheet. Ports iOS's
// LogCompoundSetSheet.swift (reps+weight only, matching CompoundExercisePairSheet's own scope).
// Styled like LogSetSheet (dark solid background, big reps stepper) but with two independent
// exercise blocks stacked in one sheet instead of one.

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.vasilisneo.trackstar.data.api.ExerciseSet
import com.vasilisneo.trackstar.ui.theme.TrackstarAccent

private val SheetBackground = Color(0xFF0D0D1F)
private val RowBackground = Color.White.copy(alpha = 0.07f)
private val CompletedGreen = Color(0xFF34C759)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogCompoundSetSheet(
    round: Int,
    nameA: String, setA: ExerciseSet,
    nameB: String, setB: ExerciseSet,
    onLog: (repsA: Int, weightA: String?, repsB: Int, weightB: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var repsA by remember { mutableStateOf(setA.frequencyValue?.reps ?: 0) }
    var weightA by remember { mutableStateOf(setA.resistanceValue?.weight ?: "") }
    var repsB by remember { mutableStateOf(setB.frequencyValue?.reps ?: 0) }
    var weightB by remember { mutableStateOf(setB.resistanceValue?.weight ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = SheetBackground) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text("Round $round", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(bottom = 20.dp))

            CompoundExerciseBlock(name = nameA, reps = repsA, onRepsChange = { repsA = it }, weight = weightA, onWeightChange = { weightA = it })
            Spacer(modifier = Modifier.padding(top = 14.dp))
            CompoundExerciseBlock(name = nameB, reps = repsB, onRepsChange = { repsB = it }, weight = weightB, onWeightChange = { weightB = it })

            Spacer(modifier = Modifier.padding(top = 24.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(50))
                    .background(CompletedGreen)
                    .clickable { onLog(repsA, weightA.ifBlank { null }, repsB, weightB.ifBlank { null }) }
            ) {
                Text("Log Round", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }

            Spacer(modifier = Modifier.padding(top = 12.dp))
            Text(
                "Cancel", fontSize = 15.sp, color = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth().clickable(onClick = onDismiss).padding(vertical = 4.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(modifier = Modifier.padding(bottom = 24.dp))
        }
    }
}

@Composable
private fun CompoundExerciseBlock(name: String, reps: Int, onRepsChange: (Int) -> Unit, weight: String, onWeightChange: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(RowBackground).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.75f))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Reps", fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier.size(26.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).clickable { if (reps > 0) onRepsChange(reps - 1) },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Remove, contentDescription = "Decrease", tint = Color.White, modifier = Modifier.size(12.dp)) }
            Text("$reps", fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color.White, modifier = Modifier.padding(horizontal = 10.dp))
            Box(
                modifier = Modifier.size(26.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).clickable { onRepsChange(reps + 1) },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Add, contentDescription = "Increase", tint = Color.White, modifier = Modifier.size(12.dp)) }
            Spacer(modifier = Modifier.padding(start = 14.dp))
            OutlinedTextField(
                value = weight,
                onValueChange = onWeightChange,
                placeholder = { Text("kg", color = Color.White.copy(alpha = 0.3f)) },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.End),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent, cursorColor = TrackstarAccent),
                modifier = Modifier.width(70.dp),
            )
        }
    }
}
