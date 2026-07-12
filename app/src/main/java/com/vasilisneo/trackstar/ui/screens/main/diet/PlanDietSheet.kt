package com.vasilisneo.trackstar.ui.screens.main.diet

// Bulk day editor — ports iOS's PlanDietSheet (the ✎ button on the Diet tab). Add Breakfast/Lunch/
// Dinner (each type only once), then inline-edit every meal on the day: name, optional time (wheel
// picker), foods, notes, and interleaved "Add Snack" buttons after each meal group. Saving replaces
// the whole day's meal list. Locked + slide-down-animated like the other sheets (iOS .large detent).

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vasilisneo.trackstar.data.api.DietMeal
import com.vasilisneo.trackstar.data.api.FoodItem
import com.vasilisneo.trackstar.ui.components.WheelColumn
import com.vasilisneo.trackstar.ui.components.WheelPickerRow
import com.vasilisneo.trackstar.ui.theme.TrackstarAccent
import com.vasilisneo.trackstar.ui.theme.trackstarBackground
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

private val CardFill = Color.White.copy(alpha = 0.06f)
private val FieldFill = Color.White.copy(alpha = 0.07f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanDietSheet(
    initialMeals: List<DietMeal>,
    onSave: (List<DietMeal>) -> Unit,
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

    val meals = remember { mutableStateListOf<DietMeal>().apply { addAll(initialMeals) } }

    fun replace(id: String, updated: DietMeal) {
        val idx = meals.indexOfFirst { it.id == id }
        if (idx >= 0) meals[idx] = updated
    }

    ModalBottomSheet(
        onDismissRequest = {},
        sheetState = sheetState,
        containerColor = Color.Transparent,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
      Column(
          modifier = Modifier.fillMaxHeight(0.93f).clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)).trackstarBackground()
      ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)
        ) {
            HeaderCircle(icon = Icons.Filled.Close, contentDescription = "Discard", onClick = { close(onDismiss) })
            Spacer(modifier = Modifier.weight(1f))
            Text("Plan Your Diet", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Spacer(modifier = Modifier.weight(1f))
            HeaderCircle(icon = Icons.Filled.Check, contentDescription = "Save", tint = TrackstarAccent, onClick = { close { onSave(meals.toList()) } })
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            // Main meal-type buttons — each type addable once.
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER).forEach { type ->
                        val disabled = meals.any { it.type == type.label }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                                .background(if (disabled) Color.White.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.07f))
                                .clickable(enabled = !disabled) {
                                    meals.add(DietMeal(id = UUID.randomUUID().toString(), type = type.label, time = defaultTimeEpoch(type)))
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            Icon(type.icon, contentDescription = null, tint = if (disabled) Color.White.copy(alpha = 0.18f) else type.color, modifier = Modifier.size(18.dp))
                            Text(type.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (disabled) Color.White.copy(alpha = 0.18f) else Color.White)
                        }
                    }
                }
            }

            // Meals interleaved with "Add Snack" buttons after each meal group (matching iOS displayItems).
            meals.forEachIndexed { i, meal ->
                item(key = meal.id) {
                    InlineMealCard(meal = meal, onChange = { replace(meal.id, it) }, onRemove = { meals.removeAll { m -> m.id == meal.id } })
                }
                val isLastInGroup = i == meals.size - 1 || meals[i + 1].type != MealType.SNACK.label
                if (isLastInGroup) {
                    item(key = "snack-btn-${meal.id}") {
                        AddSnackButton {
                            val insertAt = meals.indexOfFirst { it.id == meal.id } + 1
                            meals.add(insertAt, DietMeal(id = UUID.randomUUID().toString(), type = MealType.SNACK.label))
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(28.dp)) }
        }
      }
    }
}

@Composable
private fun AddSnackButton(onClick: () -> Unit) {
    val green = MealType.SNACK.color
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .dashedBorder(green.copy(alpha = 0.3f), 10.dp)
            .clickable(onClick = onClick).padding(vertical = 8.dp)
    ) {
        Icon(MealType.SNACK.icon, contentDescription = null, tint = green.copy(alpha = 0.7f), modifier = Modifier.size(11.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Add Snack", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = green.copy(alpha = 0.7f))
    }
}

// MARK: - Inline meal card

@Composable
private fun InlineMealCard(meal: DietMeal, onChange: (DietMeal) -> Unit, onRemove: () -> Unit) {
    val type = MealType.from(meal.type)
    var showFoodForm by remember(meal.id) { mutableStateOf(false) }
    var showTimePicker by remember(meal.id) { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardFill).padding(14.dp)
    ) {
        // Header: type badge · name · kcal · remove.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(28.dp).clip(CircleShape).background(type.color.copy(alpha = 0.15f))) {
                Icon(type.icon, contentDescription = null, tint = type.color, modifier = Modifier.size(13.dp))
            }
            Text(type.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            if (meal.totalCalories > 0) Text("· ${meal.totalCalories} kcal", fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f))
            Spacer(modifier = Modifier.weight(1f))
            SmallCircleButton(icon = Icons.Filled.Close, size = 26.dp, iconSize = 11.dp, onClick = onRemove)
        }

        // Meal name.
        CardField(value = meal.name, onValueChange = { onChange(meal.copy(name = it)) }, placeholder = "Meal name", fontSize = 14.sp)

        // Optional time.
        TimeField(meal = meal, showPicker = showTimePicker, onShowPicker = { showTimePicker = it }, onChange = onChange)

        // Foods.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Foods", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.weight(1f))
                SmallCircleButton(icon = if (showFoodForm) Icons.Filled.Remove else Icons.Filled.Add, size = 22.dp, iconSize = 11.dp, fill = Color.White.copy(alpha = 0.12f), tint = Color.White) { showFoodForm = !showFoodForm }
            }
            if (meal.foods.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    meal.foods.forEach { food ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.05f)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(food.name, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White)
                                if (food.amount.isNotBlank()) Text(food.amount, fontSize = 10.sp, color = Color.White.copy(alpha = 0.4f))
                            }
                            Text("${food.calories} kcal", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.width(8.dp))
                            SmallCircleButton(icon = Icons.Filled.Close, size = 18.dp, iconSize = 9.dp, fill = Color.White.copy(alpha = 0.07f)) {
                                onChange(meal.copy(foods = meal.foods.filterNot { it.id == food.id }))
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(visible = showFoodForm) {
                InlineAddFoodForm { onChange(meal.copy(foods = meal.foods + it)); showFoodForm = false }
            }
        }

        // Notes.
        CardField(value = meal.notes, onValueChange = { onChange(meal.copy(notes = it)) }, placeholder = "Notes (optional)", fontSize = 13.sp, singleLine = false)
    }
}

@Composable
private fun TimeField(meal: DietMeal, showPicker: Boolean, onShowPicker: (Boolean) -> Unit, onChange: (DietMeal) -> Unit) {
    when {
        showPicker -> {
            val current = meal.time?.let { epochToLocalTime(it) } ?: defaultTimeFor(MealType.from(meal.type))
            var hour by remember(meal.id) { mutableStateOf(current.hour) }
            var minute by remember(meal.id) { mutableStateOf(current.minute) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(FieldFill).padding(12.dp)) {
                WheelPickerRow(
                    columns = listOf(
                        WheelColumn(items = (0..23).map { it.toString().padStart(2, '0') }, selectedIndex = hour, onSelectedIndexChange = { hour = it; onChange(meal.copy(time = epochForTime(hour, minute))) }, width = 56.dp),
                        WheelColumn(items = (0..59).map { it.toString().padStart(2, '0') }, selectedIndex = minute, onSelectedIndexChange = { minute = it; onChange(meal.copy(time = epochForTime(hour, minute))) }, width = 56.dp),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                PillButton(text = "Done", fill = Color.White.copy(alpha = 0.15f)) { onShowPicker(false) }
            }
        }
        meal.time != null -> {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(FieldFill).padding(horizontal = 12.dp, vertical = 10.dp)) {
                Icon(Icons.Filled.Schedule, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(12.dp))
                Text(epochToLocalTime(meal.time).format(DateTimeFormatter.ofPattern("h:mm a")), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                Spacer(modifier = Modifier.weight(1f))
                SmallCircleButton(icon = Icons.Filled.Edit, size = 26.dp, iconSize = 11.dp) { onShowPicker(true) }
                SmallCircleButton(icon = Icons.Filled.Close, size = 26.dp, iconSize = 10.dp, fill = Color.White.copy(alpha = 0.07f)) { onChange(meal.copy(time = null)); onShowPicker(false) }
            }
        }
        else -> {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(FieldFill)
                    .clickable { onChange(meal.copy(time = defaultTimeEpoch(MealType.from(meal.type)))); onShowPicker(true) }
                    .padding(horizontal = 12.dp, vertical = 10.dp)) {
                Icon(Icons.Filled.Schedule, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(13.dp))
                Text("Set time", fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.weight(1f))
                Text("Optional", fontSize = 11.sp, color = Color.White.copy(alpha = 0.25f))
            }
        }
    }
}

@Composable
private fun InlineAddFoodForm(onAdd: (FoodItem) -> Unit) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.04f)).padding(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MiniField(value = name, onValueChange = { name = it }, placeholder = "Food name", modifier = Modifier.weight(1f))
            MiniField(value = amount, onValueChange = { amount = it }, placeholder = "Amount", modifier = Modifier.width(80.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MiniField(value = kcal, onValueChange = { kcal = it }, placeholder = "Kcal", numeric = true, center = true, modifier = Modifier.weight(1f))
            MiniField(value = protein, onValueChange = { protein = it }, placeholder = "P (g)", numeric = true, center = true, modifier = Modifier.weight(1f))
            MiniField(value = carbs, onValueChange = { carbs = it }, placeholder = "C (g)", numeric = true, center = true, modifier = Modifier.weight(1f))
            MiniField(value = fat, onValueChange = { fat = it }, placeholder = "F (g)", numeric = true, center = true, modifier = Modifier.weight(1f))
        }
        val canAdd = name.isNotBlank()
        PillButton(text = "Add Food", fill = Color.White.copy(alpha = 0.1f), enabled = canAdd) {
            onAdd(FoodItem(id = UUID.randomUUID().toString(), name = name.trim(), amount = amount.trim(),
                calories = kcal.toIntOrNull() ?: 0, protein = protein.toDoubleOrNull() ?: 0.0, carbs = carbs.toDoubleOrNull() ?: 0.0, fat = fat.toDoubleOrNull() ?: 0.0))
        }
    }
}

// MARK: - Small shared pieces

@Composable
private fun CardField(value: String, onValueChange: (String) -> Unit, placeholder: String, fontSize: androidx.compose.ui.unit.TextUnit, singleLine: Boolean = true) {
    BasicTextField(
        value = value, onValueChange = onValueChange, singleLine = singleLine,
        textStyle = TextStyle(color = Color.White, fontSize = fontSize),
        cursorBrush = SolidColor(Color.White),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(FieldFill).padding(horizontal = 12.dp, vertical = 10.dp),
        decorationBox = { inner -> if (value.isEmpty()) Text(placeholder, color = Color.White.copy(alpha = 0.4f), fontSize = fontSize); inner() },
    )
}

@Composable
private fun MiniField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, numeric: Boolean = false, center: Boolean = false) {
    val fs = if (numeric) 12.sp else 13.sp
    BasicTextField(
        value = value, onValueChange = onValueChange, singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = fs, textAlign = if (center) TextAlign.Center else TextAlign.Start),
        cursorBrush = SolidColor(Color.White),
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = if (numeric) 6.dp else 10.dp, vertical = 8.dp),
        decorationBox = { inner ->
            Box(contentAlignment = if (center) Alignment.Center else Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, color = Color.White.copy(alpha = 0.4f), fontSize = fs, modifier = if (center) Modifier.fillMaxWidth() else Modifier, textAlign = if (center) TextAlign.Center else TextAlign.Start)
                inner()
            }
        },
    )
}

@Composable
private fun PillButton(text: String, fill: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(fill).clickable(enabled = enabled, onClick = onClick).padding(vertical = 9.dp)
    ) { Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = if (enabled) 1f else 0.4f)) }
}

@Composable
private fun SmallCircleButton(icon: ImageVector, size: androidx.compose.ui.unit.Dp, iconSize: androidx.compose.ui.unit.Dp, fill: Color = Color.White.copy(alpha = 0.08f), tint: Color = Color.White.copy(alpha = 0.5f), onClick: () -> Unit) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size).clip(CircleShape).background(fill).clickable(onClick = onClick)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun HeaderCircle(icon: ImageVector, contentDescription: String, enabled: Boolean = true, tint: Color? = null, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(44.dp).clip(CircleShape).background((tint ?: Color.White).copy(alpha = if (tint != null) 0.2f else 0.12f)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = contentDescription, tint = (tint ?: Color.White).copy(alpha = if (enabled) 1f else 0.35f), modifier = Modifier.size(16.dp)) }
}

// Dashed rounded border, matching iOS's dashed strokeBorder on the Add Snack button.
private fun Modifier.dashedBorder(color: Color, cornerRadius: androidx.compose.ui.unit.Dp): Modifier = this.drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(cornerRadius.toPx()),
        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)),
    )
}

// MARK: - Time helpers
// `time` is epoch seconds (matching iOS's Date encoding). Only hour:minute matters here; the offset
// between iOS's reference date (2001) and the Unix epoch is a whole number of days, so time-of-day
// round-trips correctly across platforms.

private fun defaultTimeFor(type: MealType): LocalTime = when (type) {
    MealType.BREAKFAST -> LocalTime.of(9, 0)
    MealType.LUNCH -> LocalTime.of(13, 0)
    MealType.DINNER -> LocalTime.of(19, 0)
    MealType.SNACK -> LocalTime.of(11, 0)
}

private fun defaultTimeEpoch(type: MealType): Double = epochForTime(defaultTimeFor(type).hour, defaultTimeFor(type).minute)

private fun epochForTime(hour: Int, minute: Int): Double =
    LocalDate.now().atTime(hour, minute).atZone(ZoneId.systemDefault()).toEpochSecond().toDouble()

private fun epochToLocalTime(epoch: Double): LocalTime =
    Instant.ofEpochSecond(epoch.toLong()).atZone(ZoneId.systemDefault()).toLocalTime()
