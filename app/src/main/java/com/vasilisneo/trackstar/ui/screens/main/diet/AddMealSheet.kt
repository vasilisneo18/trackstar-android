package com.vasilisneo.trackstar.ui.screens.main.diet

// Add/edit a single meal — ports iOS's AddMealSheet. A meal type picker (hidden when the type is
// locked, e.g. editing an existing meal), a meal name, an expandable food list with an inline
// add-food form (name/amount + kcal/P/C/F), and notes. Locked + slide-down-animated like the
// exercise/superset sheets (iOS .interactiveDismissDisabled + .large detent).

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

private val FieldFill = Color.White.copy(alpha = 0.08f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMealSheet(
    existing: DietMeal?,
    lockedType: MealType?,
    onSave: (DietMeal) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // Locked + animated like the exercise/superset sheets (iOS .interactiveDismissDisabled): reject
    // a user swipe/scrim, but allow our own hide so X / checkmark slide the sheet down.
    var allowHide by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || allowHide },
    )
    fun close(after: () -> Unit) {
        allowHide = true
        scope.launch { sheetState.hide(); after() }
    }

    var type by remember { mutableStateOf(lockedType ?: existing?.let { MealType.from(it.type) } ?: MealType.BREAKFAST) }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    val foods = remember { mutableStateListOf<FoodItem>().apply { existing?.foods?.let { addAll(it) } } }
    var showFoodForm by remember { mutableStateOf(false) }

    val isValid = name.isNotBlank()

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
        // Header: X discards, checkmark commits (dimmed until a name is entered).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
        ) {
            HeaderCircle(icon = Icons.Filled.Close, contentDescription = "Discard", onClick = { close(onDismiss) })
            Spacer(modifier = Modifier.weight(1f))
            Text(if (existing == null) "Add Meal" else "Edit Meal", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Spacer(modifier = Modifier.weight(1f))
            HeaderCircle(
                icon = Icons.Filled.Check, contentDescription = "Save", enabled = isValid, tint = TrackstarAccent,
                onClick = {
                    val meal = (existing ?: DietMeal(id = UUID.randomUUID().toString(), type = type.label)).copy(
                        type = type.label, name = name.trim(), foods = foods.toList(), notes = notes.trim(),
                    )
                    close { onSave(meal) }
                },
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            // Meal type picker — hidden when the type is locked (editing an existing meal).
            if (lockedType == null) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        MealType.entries.forEach { t ->
                            val selected = t == type
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                                    .background(if (selected) t.color.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f))
                                    .then(if (selected) Modifier.border(1.dp, t.color.copy(alpha = 0.5f), RoundedCornerShape(14.dp)) else Modifier)
                                    .clickable { type = t }.padding(vertical = 10.dp)
                            ) {
                                Icon(t.icon, contentDescription = null, tint = if (selected) t.color else Color.White.copy(alpha = 0.35f), modifier = Modifier.size(16.dp))
                                Text(t.label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else Color.White.copy(alpha = 0.35f))
                            }
                        }
                    }
                }
            }

            // Meal name.
            item {
                FieldLabel("Meal Name")
                Spacer(modifier = Modifier.height(6.dp))
                InputField(value = name, onValueChange = { name = it }, placeholder = "e.g. Chicken & Rice", fontSize = 15.sp)
            }

            // Foods.
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    FieldLabel("Foods", modifier = Modifier.weight(1f))
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(24.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable { showFoodForm = !showFoodForm }
                    ) {
                        Icon(if (showFoodForm) Icons.Filled.Remove else Icons.Filled.Add, contentDescription = "Toggle add food", tint = Color.White, modifier = Modifier.size(13.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    foods.forEach { food ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.06f)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(food.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White)
                                if (food.amount.isNotBlank()) Text(food.amount, fontSize = 11.sp, color = Color.White.copy(alpha = 0.45f))
                            }
                            Text("${food.calories} kcal", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.6f))
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(20.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).clickable { foods.remove(food) }
                            ) { Icon(Icons.Filled.Close, contentDescription = "Remove food", tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(10.dp)) }
                        }
                    }
                    AnimatedVisibility(visible = showFoodForm) {
                        AddFoodForm(onAdd = { foods.add(it) })
                    }
                }
            }

            // Notes.
            item {
                FieldLabel("Notes")
                Spacer(modifier = Modifier.height(6.dp))
                InputField(value = notes, onValueChange = { notes = it }, placeholder = "Optional notes...", fontSize = 14.sp, singleLine = false)
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
      }
    }
}

@Composable
private fun AddFoodForm(onAdd: (FoodItem) -> Unit) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.05f)).padding(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallField(value = name, onValueChange = { name = it }, placeholder = "Food name", modifier = Modifier.weight(1f))
            SmallField(value = amount, onValueChange = { amount = it }, placeholder = "Amount", modifier = Modifier.width(90.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallField(value = kcal, onValueChange = { kcal = it }, placeholder = "Kcal", numeric = true, center = true, modifier = Modifier.weight(1f))
            SmallField(value = protein, onValueChange = { protein = it }, placeholder = "P (g)", numeric = true, center = true, modifier = Modifier.weight(1f))
            SmallField(value = carbs, onValueChange = { carbs = it }, placeholder = "C (g)", numeric = true, center = true, modifier = Modifier.weight(1f))
            SmallField(value = fat, onValueChange = { fat = it }, placeholder = "F (g)", numeric = true, center = true, modifier = Modifier.weight(1f))
        }
        val canAdd = name.isNotBlank()
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.12f))
                .clickable(enabled = canAdd) {
                    onAdd(FoodItem(
                        id = UUID.randomUUID().toString(), name = name.trim(), amount = amount.trim(),
                        calories = kcal.toIntOrNull() ?: 0, protein = protein.toDoubleOrNull() ?: 0.0,
                        carbs = carbs.toDoubleOrNull() ?: 0.0, fat = fat.toDoubleOrNull() ?: 0.0,
                    ))
                    name = ""; amount = ""; kcal = ""; protein = ""; carbs = ""; fat = ""
                }
                .padding(vertical = 10.dp)
        ) {
            Text("Add Food", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = if (canAdd) 1f else 0.4f))
        }
    }
}

// MARK: - Small shared field pieces

@Composable
private fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.5f), modifier = modifier)
}

@Composable
private fun InputField(value: String, onValueChange: (String) -> Unit, placeholder: String, fontSize: androidx.compose.ui.unit.TextUnit, singleLine: Boolean = true) {
    BasicTextField(
        value = value, onValueChange = onValueChange, singleLine = singleLine,
        textStyle = TextStyle(color = Color.White, fontSize = fontSize),
        cursorBrush = SolidColor(Color.White),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(FieldFill).padding(horizontal = 14.dp, vertical = 12.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) Text(placeholder, color = Color.White.copy(alpha = 0.4f), fontSize = fontSize)
            inner()
        },
    )
}

@Composable
private fun SmallField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, numeric: Boolean = false, center: Boolean = false) {
    BasicTextField(
        value = value, onValueChange = onValueChange, singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = if (numeric) 12.sp else 13.sp, textAlign = if (center) TextAlign.Center else TextAlign.Start),
        cursorBrush = SolidColor(Color.White),
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(FieldFill).padding(horizontal = if (numeric) 6.dp else 10.dp, vertical = 9.dp),
        decorationBox = { inner ->
            Box(contentAlignment = if (center) Alignment.Center else Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, color = Color.White.copy(alpha = 0.4f), fontSize = if (numeric) 12.sp else 13.sp, modifier = if (center) Modifier.fillMaxWidth() else Modifier, textAlign = if (center) TextAlign.Center else TextAlign.Start)
                inner()
            }
        },
    )
}

@Composable
private fun HeaderCircle(icon: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String, enabled: Boolean = true, tint: Color? = null, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(44.dp).clip(CircleShape).background((tint ?: Color.White).copy(alpha = if (tint != null) 0.2f else 0.12f)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = (tint ?: Color.White).copy(alpha = if (enabled) 1f else 0.35f), modifier = Modifier.size(16.dp))
    }
}
