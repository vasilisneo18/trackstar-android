package com.vasilisneo.trackstar.ui.screens.main.diet

// Add/edit a single meal — mirrors iOS's AddMealSheet. One simple form with everything visible:
// meal type, name, calories and protein/carbs/fat. Meals from the AI planner keep their food list
// unless the numbers are changed, in which case they collapse to a single entry. Locked +
// slide-down-animated like the exercise/superset sheets (iOS .interactiveDismissDisabled).

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import com.vasilisneo.trackstar.ui.i18n.Text
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vasilisneo.trackstar.data.api.DietMeal
import com.vasilisneo.trackstar.data.api.FoodItem
import com.vasilisneo.trackstar.ui.theme.TrackstarAccent
import com.vasilisneo.trackstar.ui.theme.trackstarBackground
import kotlinx.coroutines.launch
import java.util.UUID
import java.time.LocalTime
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding

private val FieldFill = Color.White.copy(alpha = 0.08f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMealSheet(
    existing: DietMeal?,
    // Initial type only — the picker is always shown so a meal can be moved between types.
    lockedType: MealType? = null,
    onSave: (DietMeal) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // Locked + animated like the exercise/superset sheets (iOS .interactiveDismissDisabled): reject
    // a user swipe/scrim, but allow our own hide so X / Save slide the sheet down.
    var allowHide by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || allowHide },
    )
    fun close(after: () -> Unit) {
        allowHide = true
        scope.launch { sheetState.hide(); after() }
    }

    val hasFoods = !existing?.foods.isNullOrEmpty()
    var type by remember { mutableStateOf(lockedType ?: existing?.let { MealType.from(it.type) } ?: suggestedType()) }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var kcal by remember { mutableStateOf(if (hasFoods) "${existing!!.totalCalories}" else "") }
    var protein by remember { mutableStateOf(if (hasFoods) fmt(existing!!.totalProtein) else "") }
    var carbs by remember { mutableStateOf(if (hasFoods) fmt(existing!!.totalCarbs) else "") }
    var fat by remember { mutableStateOf(if (hasFoods) fmt(existing!!.totalFat) else "") }
    val isValid = name.isNotBlank()
    val title = if (existing == null) "Add meal" else "Edit meal"

    fun save() {
        val k = kcal.toIntOrNull() ?: 0
        val p = num(protein); val c = num(carbs); val f = num(fat)
        val base = existing ?: DietMeal(id = UUID.randomUUID().toString(), type = type.label)
        val unchanged = existing != null && hasFoods && k == existing.totalCalories &&
            fmt(p) == fmt(existing.totalProtein) && fmt(c) == fmt(existing.totalCarbs) && fmt(f) == fmt(existing.totalFat)
        val foods = if (unchanged) base.foods
            else listOf(FoodItem(id = UUID.randomUUID().toString(), name = name.trim(), calories = k, protein = p, carbs = c, fat = f))
        val meal = base.copy(type = type.label, name = name.trim(), foods = foods)
        close { onSave(meal) }
    }

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
        // Header: X discards; title centered (44dp spacer balances the X).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp)
        ) {
            HeaderCircle(icon = Icons.Filled.Close, contentDescription = "Close", onClick = { close(onDismiss) })
            Spacer(modifier = Modifier.weight(1f))
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.size(44.dp))
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MealType.entries.forEach { t ->
                    val selected = t == type
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                            .background(if (selected) t.color.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f))
                            .then(if (selected) Modifier.border(1.dp, t.color.copy(alpha = 0.5f), RoundedCornerShape(14.dp)) else Modifier)
                            .clickable { type = t }.padding(vertical = 12.dp)
                    ) {
                        Icon(t.icon, contentDescription = null, tint = if (selected) t.color else Color.White.copy(alpha = 0.35f), modifier = Modifier.size(16.dp))
                        Text(t.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else Color.White.copy(alpha = 0.4f))
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("Meal name")
                InputField(value = name, onValueChange = { name = it }, placeholder = "e.g. Chicken & rice")
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("Calories")
                InputField(value = kcal, onValueChange = { kcal = it.filter(Char::isDigit) }, placeholder = "0", suffix = "kcal", keyboard = KeyboardType.Number)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MacroField("Protein", protein, { protein = it }, Color(0xFF0A84FF), Modifier.weight(1f))
                MacroField("Carbs", carbs, { carbs = it }, Color(0xFF64D2FF), Modifier.weight(1f))
                MacroField("Fat", fat, { fat = it }, Color(0xFFFF9500), Modifier.weight(1f))
            }
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth().clip(CircleShape)
                .background(if (isValid) TrackstarAccent else Color.White.copy(alpha = 0.12f))
                .clickable(enabled = isValid) { save() }
                .padding(vertical = 16.dp)
        ) {
            Text(if (existing == null) "Add meal" else "Save", fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = if (isValid) 1f else 0.5f))
        }
      }
    }
}

private fun num(s: String): Double = s.replace(',', '.').toDoubleOrNull() ?: 0.0
private fun fmt(v: Double): String = if (v == Math.floor(v)) "${v.toInt()}" else String.format(java.util.Locale.US, "%.1f", v)

// Breakfast before 11, lunch until 16, dinner after 18, snack otherwise (matches iOS).
private fun suggestedType(): MealType {
    val h = LocalTime.now().hour
    return when {
        h < 11 -> MealType.BREAKFAST
        h < 16 -> MealType.LUNCH
        h >= 18 -> MealType.DINNER
        else -> MealType.SNACK
    }
}

// MARK: - Field pieces

@Composable
private fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.5f), modifier = modifier)
}

@Composable
private fun InputField(
    value: String, onValueChange: (String) -> Unit, placeholder: String,
    modifier: Modifier = Modifier, suffix: String? = null, keyboard: KeyboardType = KeyboardType.Text,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(FieldFill).padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        BasicTextField(
            value = value, onValueChange = onValueChange, singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(placeholder, color = Color.White.copy(alpha = 0.4f), fontSize = 17.sp)
                inner()
            },
        )
        if (suffix != null) Text(suffix, fontSize = 17.sp, color = Color.White.copy(alpha = 0.4f))
    }
}

@Composable
private fun MacroField(label: String, value: String, onValueChange: (String) -> Unit, color: Color, modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(color))
            FieldLabel(label)
        }
        InputField(
            value = value, onValueChange = { v -> onValueChange(v.filter { it.isDigit() || it == '.' || it == ',' }) },
            placeholder = "0", suffix = "g", keyboard = KeyboardType.Decimal,
        )
    }
}

@Composable
private fun HeaderCircle(icon: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(16.dp))
    }
}
