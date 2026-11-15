package com.vasilisneo.trackstar.ui.screens.main.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.vasilisneo.trackstar.data.api.ExerciseData
import com.vasilisneo.trackstar.ui.screens.main.plan.ExerciseEditorSheet
import com.vasilisneo.trackstar.ui.screens.main.plan.ExercisePickerSheet

/** "Add exercise" during a live session or Quick Log: pick from the exercise library, set each one
 *  up (sets, reps, weight) in the usual editor, then hand it back — the session logs it and adds it
 *  to the planned session. iOS: AddExerciseFlow. */
@Composable
fun AddExerciseFlow(picking: Boolean, onPickerClosed: () -> Unit, onAdd: (ExerciseData) -> Unit) {
    // Picked exercises still to set up, one editor at a time.
    var queue by remember { mutableStateOf<List<ExerciseData>>(emptyList()) }
    if (picking) {
        ExercisePickerSheet(onAdd = { queue = it }, onDismiss = onPickerClosed)
    }
    val next = queue.firstOrNull()
    if (!picking && next != null) {
        key(next.id) {
            ExerciseEditorSheet(
                existing = next,
                onSave = { onAdd(it); queue = queue.drop(1) },
                onDelete = null,
                onDismiss = { queue = queue.drop(1) },
            )
        }
    }
}

/** The "Add Exercise" row under the session's exercises. */
@Composable
fun AddExerciseRowButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick).padding(vertical = 14.dp),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Add Exercise", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.8f))
    }
}
