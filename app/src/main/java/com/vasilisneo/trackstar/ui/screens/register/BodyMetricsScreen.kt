package com.vasilisneo.trackstar.ui.screens.register

// Replica of BodyMetricsView (Trackstar/UI/View/Registration/BodyMetricsView.swift) on
// iOS — step 3/5 of the registration flow. Height/weight are always stored on the shared
// RegisterViewModel in metric (cm/kg); the ft-in and stones/lb unit modes are local,
// screen-only UI state that convert into/out of metric on toggle or on Continue — same
// split iOS uses (@State locally, @Published metric fields on the view model).

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vasilisneo.trackstar.ui.components.AuthCapsuleButton
import com.vasilisneo.trackstar.ui.components.AuthErrorText
import com.vasilisneo.trackstar.ui.components.AuthFieldLabel
import com.vasilisneo.trackstar.ui.components.AuthMetricField
import com.vasilisneo.trackstar.ui.components.AuthScreenScaffold
import com.vasilisneo.trackstar.ui.components.AuthUnitStaticLabel
import com.vasilisneo.trackstar.ui.components.AuthUnitToggleButton

private enum class HeightMode { CM, FEET_INCHES }
private enum class WeightUnit(val label: String) { KG("kg"), STONES("st/lb"), LB("lb") }

@Composable
fun BodyMetricsScreen(
    viewModel: RegisterViewModel = viewModel(),
    onBackClick: () -> Unit = {},
    onContinue: () -> Unit = {},
) {
    var heightMode by remember { mutableStateOf(HeightMode.CM) }
    var feet by remember { mutableStateOf("") }
    var inches by remember { mutableStateOf("") }

    var weightUnit by remember { mutableStateOf(WeightUnit.KG) }
    var weightStones by remember { mutableStateOf("") }
    var weightPounds by remember { mutableStateOf("") }
    var targetStones by remember { mutableStateOf("") }
    var targetPounds by remember { mutableStateOf("") }

    fun toggleHeight() {
        if (heightMode == HeightMode.CM) {
            viewModel.heightCm.toDoubleOrNull()?.let { cm ->
                val totalIn = cm / 2.54
                feet = (totalIn.toInt() / 12).toString()
                inches = (totalIn.toInt() % 12).toString()
            }
            heightMode = HeightMode.FEET_INCHES
        } else {
            val f = feet.toDoubleOrNull()
            val i = inches.toDoubleOrNull()
            if (f != null && i != null) {
                viewModel.onHeightCmChange("%.0f".format((f * 12 + i) * 2.54))
            }
            heightMode = HeightMode.CM
        }
    }

    fun toggleWeight() {
        when (weightUnit) {
            WeightUnit.KG -> {
                viewModel.weightKg.toDoubleOrNull()?.let { kg ->
                    val lbs = kg / 0.453592
                    weightStones = (lbs.toInt() / 14).toString()
                    weightPounds = "%.1f".format(lbs.mod(14.0))
                }
                viewModel.targetWeightKg.toDoubleOrNull()?.let { kg ->
                    val lbs = kg / 0.453592
                    targetStones = (lbs.toInt() / 14).toString()
                    targetPounds = "%.1f".format(lbs.mod(14.0))
                }
                weightUnit = WeightUnit.STONES
            }
            WeightUnit.STONES -> {
                val s = weightStones.toDoubleOrNull()
                val p = weightPounds.toDoubleOrNull()
                if (s != null && p != null) weightPounds = "%.1f".format(s * 14 + p)
                val ts = targetStones.toDoubleOrNull()
                val tp = targetPounds.toDoubleOrNull()
                if (ts != null && tp != null) targetPounds = "%.1f".format(ts * 14 + tp)
                weightUnit = WeightUnit.LB
            }
            WeightUnit.LB -> {
                weightPounds.toDoubleOrNull()?.let { lb ->
                    viewModel.onWeightKgChange("%.1f".format(lb * 0.453592))
                }
                targetPounds.toDoubleOrNull()?.let { lb ->
                    viewModel.onTargetWeightKgChange("%.1f".format(lb * 0.453592))
                }
                weightUnit = WeightUnit.KG
            }
        }
    }

    fun syncUnitsToViewModel() {
        when (weightUnit) {
            WeightUnit.STONES -> {
                val s = weightStones.toDoubleOrNull()
                val p = weightPounds.toDoubleOrNull()
                if (s != null && p != null) viewModel.onWeightKgChange("%.1f".format((s * 14 + p) * 0.453592))
                val ts = targetStones.toDoubleOrNull()
                val tp = targetPounds.toDoubleOrNull()
                if (ts != null && tp != null) viewModel.onTargetWeightKgChange("%.1f".format((ts * 14 + tp) * 0.453592))
            }
            WeightUnit.LB -> {
                weightPounds.toDoubleOrNull()?.let { viewModel.onWeightKgChange("%.1f".format(it * 0.453592)) }
                targetPounds.toDoubleOrNull()?.let { viewModel.onTargetWeightKgChange("%.1f".format(it * 0.453592)) }
            }
            WeightUnit.KG -> Unit
        }
        if (heightMode == HeightMode.FEET_INCHES) {
            val f = feet.toDoubleOrNull()
            val i = inches.toDoubleOrNull()
            if (f != null && i != null) viewModel.onHeightCmChange("%.0f".format((f * 12 + i) * 2.54))
        }
    }

    AuthScreenScaffold(
        title = "Body Metrics",
        subtitle = "Help us personalise your programme",
        showBackButton = true,
        onBackClick = onBackClick,
        navBarTrailing = {
            Text(
                text = "3 / 5",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.45f)
            )
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
            // Height
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AuthFieldLabel("HEIGHT")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (heightMode == HeightMode.FEET_INCHES) {
                        AuthMetricField(value = feet, onValueChange = { feet = it }, placeholder = "ft", modifier = Modifier.weight(1f))
                        AuthMetricField(value = inches, onValueChange = { inches = it }, placeholder = "in", modifier = Modifier.weight(1f))
                    } else {
                        AuthMetricField(value = viewModel.heightCm, onValueChange = viewModel::onHeightCmChange, placeholder = "cm", modifier = Modifier.weight(1f))
                    }
                    AuthUnitToggleButton(label = if (heightMode == HeightMode.CM) "cm" else "ft / in", onClick = ::toggleHeight)
                }
            }

            // Current weight
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AuthFieldLabel("CURRENT WEIGHT")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (weightUnit) {
                        WeightUnit.STONES -> {
                            AuthMetricField(value = weightStones, onValueChange = { weightStones = it }, placeholder = "st", modifier = Modifier.weight(1f))
                            AuthMetricField(value = weightPounds, onValueChange = { weightPounds = it }, placeholder = "lb", modifier = Modifier.weight(1f))
                        }
                        WeightUnit.LB -> {
                            AuthMetricField(value = weightPounds, onValueChange = { weightPounds = it }, placeholder = "lb", modifier = Modifier.weight(1f))
                        }
                        WeightUnit.KG -> {
                            AuthMetricField(value = viewModel.weightKg, onValueChange = viewModel::onWeightKgChange, placeholder = "kg", modifier = Modifier.weight(1f))
                        }
                    }
                    AuthUnitToggleButton(label = weightUnit.label, onClick = ::toggleWeight)
                }
            }

            // Target weight
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AuthFieldLabel("TARGET WEIGHT")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (weightUnit) {
                        WeightUnit.STONES -> {
                            AuthMetricField(value = targetStones, onValueChange = { targetStones = it }, placeholder = "st", modifier = Modifier.weight(1f))
                            AuthMetricField(value = targetPounds, onValueChange = { targetPounds = it }, placeholder = "lb", modifier = Modifier.weight(1f))
                        }
                        WeightUnit.LB -> {
                            AuthMetricField(value = targetPounds, onValueChange = { targetPounds = it }, placeholder = "lb", modifier = Modifier.weight(1f))
                        }
                        WeightUnit.KG -> {
                            AuthMetricField(value = viewModel.targetWeightKg, onValueChange = viewModel::onTargetWeightKgChange, placeholder = "kg", modifier = Modifier.weight(1f))
                        }
                    }
                    AuthUnitStaticLabel(label = weightUnit.label)
                }
            }
        }

        viewModel.errorMessage?.let { error -> AuthErrorText(error) }

        AuthCapsuleButton(
            text = "Continue",
            onClick = {
                syncUnitsToViewModel()
                onContinue()
            },
            modifier = Modifier.padding(top = 20.dp)
        )
    }
}
