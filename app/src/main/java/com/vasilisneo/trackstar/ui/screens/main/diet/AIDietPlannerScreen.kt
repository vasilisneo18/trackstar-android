package com.vasilisneo.trackstar.ui.screens.main.diet

// AI Diet Planner — 5-step wizard (body goal, training, food preferences, restrictions & cuisine,
// notes) calling the same backend Claude diet endpoint iOS uses, then a 7-day preview before
// applying the plan to the user's weekly diet. Mirrors AIWorkoutPlannerScreen's structure/visuals.

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vasilisneo.trackstar.data.api.DietPlanMeal
import com.vasilisneo.trackstar.ui.theme.TrackstarAccent
import com.vasilisneo.trackstar.ui.theme.trackstarBackground

private val CompletedGreen = Color(0xFF34C759)
private val CardBackground = Color.White.copy(alpha = 0.06f)
private val DayOrder = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")

@Composable
fun AIDietPlannerScreen(
    viewModel: AIDietPlannerViewModel,
    onClose: () -> Unit,
    onApplied: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().trackstarBackground()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Box(
                    modifier = Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = TrackstarAccent, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.padding(start = 6.dp))
                    Text("AI Diet Planner", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.size(38.dp))
            }

            when {
                viewModel.isCheckingUsage -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TrackstarAccent)
                }
                viewModel.limitReached -> LimitReachedView(used = viewModel.usageUsed, limit = viewModel.usageLimit)
                viewModel.hasGeneratedPlan -> ResultSection(viewModel = viewModel, onApplied = onApplied)
                viewModel.isGenerating -> GeneratingView()
                else -> WizardSection(viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun LimitReachedView(used: Int, limit: Int) {
    Box(modifier = Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(36.dp))
            Text("Monthly limit reached", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                "You've used $used of $limit AI diet plans this month. Your limit resets next month.",
                fontSize = 14.sp, color = Color.White.copy(alpha = 0.5f), textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun GeneratingView() {
    Box(modifier = Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(color = TrackstarAccent)
            Text("Generating your diet…", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text("This can take up to a couple of minutes.", fontSize = 13.sp, color = Color.White.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun WizardSection(viewModel: AIDietPlannerViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            repeat(viewModel.totalSteps) { i ->
                Box(
                    modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(50))
                        .background(if (i <= viewModel.step) TrackstarAccent else Color.White.copy(alpha = 0.1f))
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
        ) {
            when (viewModel.step) {
                0 -> BodyGoalStep(viewModel)
                1 -> TrainingStep(viewModel)
                2 -> FoodPrefsStep(viewModel)
                3 -> RestrictionsStep(viewModel)
                4 -> NotesStep(viewModel)
            }
        }

        viewModel.errorMessage?.let { msg ->
            Text(msg, fontSize = 13.sp, color = Color(0xFFFF453A), modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            if (viewModel.step > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.1f)).clickable { viewModel.goBack() }
                ) { Text("Back", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White) }
            }
            val isLast = viewModel.step == viewModel.totalSteps - 1
            Row(
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(if (viewModel.step > 0) 2f else 1f)
                    .height(52.dp).clip(RoundedCornerShape(16.dp)).background(TrackstarAccent)
                    .clickable { if (isLast) viewModel.generate() else viewModel.goNext() }
            ) { Text(if (isLast) "Generate Plan" else "Next", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White) }
        }
    }
}

@Composable
private fun BodyGoalStep(viewModel: AIDietPlannerViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        StepTitle("Your goal")
        StepperRow("Current weight", "${viewModel.currentWeightKg} kg",
            onDec = { if (viewModel.currentWeightKg > 40) viewModel.currentWeightKg-- },
            onInc = { if (viewModel.currentWeightKg < 200) viewModel.currentWeightKg++ })
        StepperRow("Target weight", "${viewModel.targetWeightKg} kg",
            onDec = { if (viewModel.targetWeightKg > 40) viewModel.targetWeightKg-- },
            onInc = { if (viewModel.targetWeightKg < 200) viewModel.targetWeightKg++ })
        StepperRow("Timeline", "${viewModel.timelineWeeks} weeks",
            onDec = { if (viewModel.timelineWeeks > 1) viewModel.timelineWeeks-- },
            onInc = { if (viewModel.timelineWeeks < 52) viewModel.timelineWeeks++ })
    }
}

@Composable
private fun TrainingStep(viewModel: AIDietPlannerViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        StepTitle("Training")
        StepperRow("Training days per week", "${viewModel.trainingDaysPerWeek} days",
            onDec = { if (viewModel.trainingDaysPerWeek > 0) viewModel.trainingDaysPerWeek-- },
            onInc = { if (viewModel.trainingDaysPerWeek < 7) viewModel.trainingDaysPerWeek++ })
        LabeledSection("Training type") {
            SegmentedChips(options = AiTrainingTypes, selected = viewModel.trainingType) { viewModel.trainingType = it }
        }
        StepperRow("Meals per day", "${viewModel.mealsPerDay} meals",
            onDec = { if (viewModel.mealsPerDay > 3) viewModel.mealsPerDay-- },
            onInc = { if (viewModel.mealsPerDay < 6) viewModel.mealsPerDay++ })
    }
}

@Composable
private fun FoodPrefsStep(viewModel: AIDietPlannerViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        StepTitle("Food preferences")
        Text("Optional — pick foods you'd like to see more of.", fontSize = 13.sp, color = Color.White.copy(alpha = 0.4f))
        LabeledSection("Breakfast") {
            FlowChips(AiBreakfastFoods, { viewModel.breakfastFoods.contains(it) }) { viewModel.toggleBreakfast(it) }
        }
        LabeledSection("Lunch") {
            FlowChips(AiLunchDinnerFoods, { viewModel.lunchFoods.contains(it) }) { viewModel.toggleLunch(it) }
        }
        LabeledSection("Dinner") {
            FlowChips(AiLunchDinnerFoods, { viewModel.dinnerFoods.contains(it) }) { viewModel.toggleDinner(it) }
        }
        LabeledSection("Snacks") {
            FlowChips(AiSnackFoods, { viewModel.snackFoods.contains(it) }) { viewModel.toggleSnack(it) }
        }
    }
}

@Composable
private fun RestrictionsStep(viewModel: AIDietPlannerViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        StepTitle("Restrictions & cuisine")
        LabeledSection("Dietary restrictions") {
            FlowChips(AiRestrictions, { viewModel.restrictions.contains(it) }) { viewModel.toggleRestriction(it) }
        }
        LabeledSection("Cuisine preference") {
            SegmentedChips(options = AiCuisines, selected = viewModel.cuisine) { viewModel.cuisine = it }
        }
    }
}

@Composable
private fun NotesStep(viewModel: AIDietPlannerViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StepTitle("Anything to know?")
        LabeledSection("Additional notes") {
            OutlinedTextField(
                value = viewModel.additionalNotes, onValueChange = { viewModel.additionalNotes = it },
                placeholder = { Text("e.g. intermittent fasting, high protein", color = Color.White.copy(alpha = 0.3f)) },
                modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
            )
        }
    }
}

@Composable
private fun ResultSection(viewModel: AIDietPlannerViewModel, onApplied: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.weight(1f)
        ) {
            item { Text("Your 7-day plan", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White) }
            DayOrder.forEach { day ->
                val dayPlan = viewModel.generatedDays[day] ?: return@forEach
                val meals = dayPlan.meals.orEmpty()
                if (meals.isEmpty()) return@forEach
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(day.replaceFirstChar { it.uppercase() }, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(modifier = Modifier.weight(1f))
                            Text("${dayCalories(meals)} kcal", fontSize = 12.sp, color = Color.White.copy(alpha = 0.4f))
                        }
                        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardBackground)) {
                            meals.forEachIndexed { index, meal ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            meal.name?.takeIf { it.isNotBlank() } ?: (meal.type ?: "Meal").replaceFirstChar { it.uppercase() },
                                            fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White,
                                        )
                                        Text(mealSummary(meal), fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f))
                                    }
                                    Text((meal.type ?: "").replaceFirstChar { it.uppercase() }, fontSize = 11.sp, color = Color.White.copy(alpha = 0.35f))
                                }
                                if (index != meals.lastIndex) Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)
                .height(54.dp).clip(RoundedCornerShape(50)).background(CompletedGreen.copy(alpha = 0.85f))
                .clickable(enabled = !viewModel.isApplying) { viewModel.apply { success -> if (success) onApplied() } }
        ) {
            if (viewModel.isApplying) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
            else {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.padding(start = 8.dp))
                Text("Apply to My Diet", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}

private fun dayCalories(meals: List<DietPlanMeal>): Int =
    meals.sumOf { m -> m.foods.orEmpty().sumOf { it.calories ?: 0 } }

private fun mealSummary(meal: DietPlanMeal): String {
    val kcal = meal.foods.orEmpty().sumOf { it.calories ?: 0 }
    val protein = meal.foods.orEmpty().sumOf { it.protein ?: 0 }
    return "$kcal kcal · ${protein}g protein"
}

// MARK: - Shared step chrome (kept local so this screen is self-contained, matching the codebase's
// per-screen convention; visually identical to the workout planner's helpers).

@Composable
private fun StepTitle(text: String) {
    Text(text, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun LabeledSection(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.4f))
        content()
    }
}

@Composable
private fun StepperRow(label: String, valueText: String, onDec: () -> Unit, onInc: () -> Unit) {
    LabeledSection(label) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardBackground).padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(valueText, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.size(30.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).clickable(onClick = onDec), contentAlignment = Alignment.Center) {
                    Text("−", fontSize = 18.sp, color = Color.White)
                }
                Box(modifier = Modifier.size(30.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).clickable(onClick = onInc), contentAlignment = Alignment.Center) {
                    Text("+", fontSize = 18.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun SegmentedChips(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { option ->
            val isSelected = option == selected
            Text(
                option, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (isSelected) TrackstarAccent else CardBackground)
                    .clickable { onSelect(option) }.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FlowChips(options: List<String>, isSelected: (String) -> Boolean, onToggle: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            val selected = isSelected(option)
            Text(
                option, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.5f),
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (selected) TrackstarAccent else CardBackground)
                    .clickable { onToggle(option) }.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
    focusedBorderColor = TrackstarAccent, unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
    cursorColor = TrackstarAccent,
)
