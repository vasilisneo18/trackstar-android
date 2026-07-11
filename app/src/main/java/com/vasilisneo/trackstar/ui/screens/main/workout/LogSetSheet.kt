package com.vasilisneo.trackstar.ui.screens.main.workout

// Bottom sheet to log a set's actual performance. Mirrors iOS's LogSetSheet.swift closely: dark
// solid background, a big reps stepper (circular -/+ buttons either side of a large monospaced
// count), a labeled weight/duration/distance row, and a green "Log Set" pill + "Cancel" text
// button. Native Android bottom sheet container (ModalBottomSheet), not a literal SwiftUI sheet.

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
fun LogSetSheet(
    exerciseName: String,
    set: ExerciseSet,
    onLog: (reps: Int?, weight: String?, durationText: String?, distanceText: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val isDuration = set.frequencyValue?.duration != null
    val isDistance = set.frequencyValue?.distance != null

    var repCount by remember { mutableStateOf(set.frequencyValue?.reps ?: 0) }
    var weightText by remember { mutableStateOf(set.resistanceValue?.weight ?: "") }
    var durationText by remember { mutableStateOf(set.frequencyValue?.duration ?: "") }
    var distanceText by remember { mutableStateOf(set.frequencyValue?.distance ?: "") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetBackground,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
                Text("Set", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.padding(top = 4.dp))
                Text("Planned: ${set.repsOrDurationText()}", fontSize = 13.sp, color = Color.White.copy(alpha = 0.4f))
            }

            Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
                when {
                    isDuration -> LabeledTextField("Duration", durationText, { durationText = it }, "e.g. 30s")
                    isDistance -> LabeledTextField("Distance", distanceText, { distanceText = it }, "e.g. 400m")
                    else -> RepStepper(repCount, onDecrement = { if (repCount > 0) repCount-- }, onIncrement = { repCount++ })
                }

                Spacer(modifier = Modifier.padding(top = 12.dp))

                if (!isDuration && !isDistance) {
                    LabeledTextField("Weight (kg)", weightText, { weightText = it }, "0", KeyboardType.Decimal)
                }
            }

            Spacer(modifier = Modifier.padding(top = 24.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(50))
                    .background(CompletedGreen)
                    .clickable {
                        when {
                            isDuration -> onLog(null, null, durationText, null)
                            isDistance -> onLog(null, null, null, distanceText)
                            else -> onLog(repCount, weightText.ifBlank { null }, null, null)
                        }
                    }
            ) {
                Text("Log Set", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }

            Spacer(modifier = Modifier.padding(top = 12.dp))

            Text(
                "Cancel",
                fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth().clickable(onClick = onDismiss).padding(vertical = 4.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )

            Spacer(modifier = Modifier.padding(bottom = 24.dp))
        }
    }
}

@Composable
private fun RepStepper(count: Int, onDecrement: () -> Unit, onIncrement: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RowBackground)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text("Reps", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.55f))
        Spacer(modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).clickable(onClick = onDecrement),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "Decrease", tint = Color.White, modifier = Modifier.size(17.dp))
            }
            Text(
                "$count",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).clickable(onClick = onIncrement),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Increase", tint = Color.White, modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun LabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RowBackground)
            .padding(horizontal = 20.dp, vertical = 4.dp)
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.55f))
        Spacer(modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = Color.White.copy(alpha = 0.3f)) },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 20.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace,
                color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.End,
            ),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                cursorColor = TrackstarAccent,
            ),
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}
