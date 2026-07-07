package com.vasilisneo.trackstar.ui.screens.main

// Visual replica of PersonalInfoView (Trackstar/UI/View/MainApp/Profile/PersonalInfoView.swift)
// on iOS: an ACCOUNT group (email static, country editable) and a BODY group (gender, age,
// height, weight, target weight — each opening an edit sheet). Edits are held in local state
// (not persisted / not synced back to the Profile screen) since there's no auth/profile
// networking yet. Height/weight edit sheets reuse the same metric-field + unit-toggle
// components as the Body Metrics registration step.

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vasilisneo.trackstar.ui.components.AuthMetricField
import com.vasilisneo.trackstar.ui.components.AuthSelectorButton
import com.vasilisneo.trackstar.ui.components.AuthUnitToggleButton
import com.vasilisneo.trackstar.ui.components.CountryPickerSheet
import com.vasilisneo.trackstar.ui.components.GlassCircleIconButton
import com.vasilisneo.trackstar.ui.theme.TrackstarAccent
import com.vasilisneo.trackstar.ui.theme.TrackstarBackground
import com.vasilisneo.trackstar.ui.theme.TrackstarSurface
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneOffset

private enum class EditField { GENDER, AGE, HEIGHT, WEIGHT, TARGET_WEIGHT }
private val GroupSurface = Color.White.copy(alpha = 0.07f)

@Composable
fun PersonalInfoScreen(onBackClick: () -> Unit = {}) {
    // Local edit state, seeded from the same placeholder as the Profile screen.
    var country by remember { mutableStateOf("Cyprus") }
    var gender by remember { mutableStateOf("Male") }
    var age by remember { mutableStateOf("25") }
    var heightCm by remember { mutableStateOf("178") }
    var weightKg by remember { mutableStateOf("82.0") }
    var targetWeightKg by remember { mutableStateOf("75.0") }
    val email = "vasilis@example.com"

    var editing by remember { mutableStateOf<EditField?>(null) }
    var showCountryPicker by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(TrackstarBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Nav bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                GlassCircleIconButton(onClick = onBackClick, contentDescription = "Back")
                Spacer(modifier = Modifier.weight(1f))
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Personal Info", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))

                InfoGroup(title = "ACCOUNT") {
                    StaticInfoRow("Email", email)
                    InfoRow("Country", country.ifEmpty { "—" }) { showCountryPicker = true }
                }

                InfoGroup(title = "BODY") {
                    InfoRow("Gender", gender.ifEmpty { "—" }) { editing = EditField.GENDER }
                    InfoRow("Age", if (age.isEmpty()) "—" else "$age yrs") { editing = EditField.AGE }
                    InfoRow("Height", if (heightCm.isEmpty()) "—" else "$heightCm cm") { editing = EditField.HEIGHT }
                    InfoRow("Weight", if (weightKg.isEmpty()) "—" else "$weightKg kg") { editing = EditField.WEIGHT }
                    InfoRow("Target Weight", if (targetWeightKg.isEmpty()) "—" else "$targetWeightKg kg") { editing = EditField.TARGET_WEIGHT }
                }
            }
        }
    }

    when (editing) {
        EditField.GENDER -> GenderSheet(current = gender, onSelect = { gender = it }, onDismiss = { editing = null })
        EditField.AGE -> AgeSheet(currentAge = age, onSave = { age = it }, onDismiss = { editing = null })
        EditField.HEIGHT -> HeightSheet(currentCm = heightCm, onSave = { heightCm = it }, onDismiss = { editing = null })
        EditField.WEIGHT -> WeightSheet(title = "Weight", currentKg = weightKg, onSave = { weightKg = it }, onDismiss = { editing = null })
        EditField.TARGET_WEIGHT -> WeightSheet(title = "Target Weight", currentKg = targetWeightKg, onSave = { targetWeightKg = it }, onDismiss = { editing = null })
        null -> Unit
    }

    if (showCountryPicker) {
        CountryPickerSheet(
            selectedCountry = country,
            onSelect = { country = it },
            onDismiss = { showCountryPicker = false }
        )
    }
}

@Composable
private fun InfoGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.4f), letterSpacing = 0.5.sp, modifier = Modifier.padding(start = 4.dp))
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GroupSurface)) {
            content()
        }
    }
}

@Composable
private fun InfoRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.45f))
            Text(value, fontSize = 16.sp, color = Color.White)
        }
        Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = TrackstarAccent, modifier = Modifier.padding(2.dp))
    }
}

@Composable
private fun StaticInfoRow(title: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.45f))
        Text(value, fontSize = 16.sp, color = Color.White)
    }
}

// MARK: - Edit sheets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSheet(title: String, onSave: () -> Unit, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = TrackstarSurface) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                Text("Save", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.clickable { onSave(); onDismiss() })
            }
            content()
        }
    }
}

@Composable
private fun GenderSheet(current: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    EditSheet(title = "Gender", onSave = {}, onDismiss = onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AuthSelectorButton("Female", selected = current == "Female", onClick = { onSelect("Female"); onDismiss() }, modifier = Modifier.weight(1f))
            AuthSelectorButton("Male", selected = current == "Male", onClick = { onSelect("Male"); onDismiss() }, modifier = Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgeSheet(currentAge: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val today = LocalDate.now()
    val initialDob = remember {
        val yrs = currentAge.toIntOrNull() ?: 20
        today.minusYears(yrs.toLong())
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDob.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        yearRange = (today.year - 100)..(today.year - 13),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    val dob = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    onSave(Period.between(dob, today).years.toString())
                }
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DatePicker(state = state, title = { Text("Date of Birth", modifier = Modifier.padding(16.dp)) })
    }
}

@Composable
private fun HeightSheet(currentCm: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var cm by remember { mutableStateOf(currentCm) }
    var feetInches by remember { mutableStateOf(false) }
    var feet by remember { mutableStateOf("") }
    var inches by remember { mutableStateOf("") }

    fun toggle() {
        if (!feetInches) {
            cm.toDoubleOrNull()?.let { c ->
                val totalIn = c / 2.54
                feet = (totalIn.toInt() / 12).toString()
                inches = (totalIn.toInt() % 12).toString()
            }
            feetInches = true
        } else {
            val f = feet.toDoubleOrNull(); val i = inches.toDoubleOrNull()
            if (f != null && i != null) cm = "%.0f".format((f * 12 + i) * 2.54)
            feetInches = false
        }
    }

    EditSheet(title = "Height", onSave = {
        if (feetInches) {
            val f = feet.toDoubleOrNull(); val i = inches.toDoubleOrNull()
            if (f != null && i != null) onSave("%.0f".format((f * 12 + i) * 2.54))
        } else onSave(cm)
    }, onDismiss = onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (feetInches) {
                AuthMetricField(value = feet, onValueChange = { feet = it }, placeholder = "ft", modifier = Modifier.weight(1f))
                AuthMetricField(value = inches, onValueChange = { inches = it }, placeholder = "in", modifier = Modifier.weight(1f))
            } else {
                AuthMetricField(value = cm, onValueChange = { cm = it }, placeholder = "cm", modifier = Modifier.weight(1f))
            }
            AuthUnitToggleButton(label = if (feetInches) "ft / in" else "cm", onClick = ::toggle)
        }
    }
}

@Composable
private fun WeightSheet(title: String, currentKg: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    // Simplified vs iOS (kg <-> lb only, no stones) — same metric-field styling.
    var kg by remember { mutableStateOf(currentKg) }
    var lb by remember { mutableStateOf(false) }
    var lbValue by remember { mutableStateOf("") }
    EditSheet(title = title, onSave = {
        if (lb) lbValue.toDoubleOrNull()?.let { onSave("%.1f".format(it * 0.453592)) }
        else onSave(kg)
    }, onDismiss = onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (lb) {
                AuthMetricField(value = lbValue, onValueChange = { lbValue = it }, placeholder = "lb", modifier = Modifier.weight(1f))
            } else {
                AuthMetricField(value = kg, onValueChange = { kg = it }, placeholder = "kg", modifier = Modifier.weight(1f))
            }
            AuthUnitToggleButton(label = if (lb) "lb" else "kg", onClick = {
                if (!lb) { kg.toDoubleOrNull()?.let { lbValue = "%.1f".format(it / 0.453592) }; lb = true }
                else { lbValue.toDoubleOrNull()?.let { kg = "%.1f".format(it * 0.453592) }; lb = false }
            })
        }
    }
}
