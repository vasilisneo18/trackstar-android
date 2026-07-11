package com.vasilisneo.trackstar.ui.screens.main.plan

// AI Workout Planner — 5-step wizard (goal, schedule, training type, focus & split, injuries/
// notes) that calls the same backend Claude endpoint iOS uses, then a checklist review (default
// all-checked) before applying the selected exercises as new sessions for the displayed week.
// Deviations from iOS, both flagged in the approved plan: no equipment step (backend ignores
// it — zero effect on generation) and a checkbox review list instead of swipe-accept/reject cards.

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vasilisneo.trackstar.ui.theme.TrackstarAccent
import com.vasilisneo.trackstar.ui.theme.TrackstarBackground
import com.vasilisneo.trackstar.ui.theme.trackstarBackground

private val CompletedGreen = Color(0xFF34C759)
private val CardBackground = Color.White.copy(alpha = 0.06f)

@Composable
fun AIWorkoutPlannerScreen(
    viewModel: AIWorkoutPlannerViewModel,
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
                    Text("AI Workout Planner", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.size(38.dp))
            }

            when {
                viewModel.isCheckingUsage -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TrackstarAccent)
                }
                viewModel.limitReached -> LimitReachedView(used = viewModel.usageUsed, limit = viewModel.usageLimit)
                viewModel.hasGeneratedPlan -> ReviewSection(viewModel = viewModel, onApplied = onApplied)
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
                "You've used $used of $limit AI workout plans this month. Your limit resets next month.",
                fontSize = 14.sp, color = Color.White.copy(alpha = 0.5f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun GeneratingView() {
    Box(modifier = Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(color = TrackstarAccent)
            Text("Generating your plan…", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text("This can take up to a couple of minutes.", fontSize = 13.sp, color = Color.White.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun WizardSection(viewModel: AIWorkoutPlannerViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Step dots
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            repeat(viewModel.totalSteps) { i ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (i <= viewModel.step) TrackstarAccent else Color.White.copy(alpha = 0.1f))
                )
            }
        }

        Column(modifier = Modifier.weight(1f).padding(horizontal = 20.dp, vertical = 12.dp)) {
            when (viewModel.step) {
                0 -> GoalStep(viewModel)
                1 -> ScheduleStep(viewModel)
                2 -> TrainingTypeStep(viewModel)
                3 -> FocusSplitStep(viewModel)
                4 -> InjuriesStep(viewModel)
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
                    .height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(TrackstarAccent)
                    .clickable { if (isLast) viewModel.generate() else viewModel.goNext() }
            ) { Text(if (isLast) "Generate Plan" else "Next", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White) }
        }
    }
}

@Composable
private fun GoalStep(viewModel: AIWorkoutPlannerViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StepTitle("What's your goal?")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AiGoals.forEach { goal ->
                SelectableRow(label = goal, selected = viewModel.selectedGoal == goal) { viewModel.selectedGoal = goal }
            }
        }
    }
}

@Composable
private fun ScheduleStep(viewModel: AIWorkoutPlannerViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        StepTitle("Your schedule")
        LabeledSection("Training days per week") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardBackground).padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text("${viewModel.trainingDaysPerWeek} days", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.weight(1f))
                MiniStep(onDec = { if (viewModel.trainingDaysPerWeek > 1) viewModel.trainingDaysPerWeek-- }, onInc = { if (viewModel.trainingDaysPerWeek < 7) viewModel.trainingDaysPerWeek++ })
            }
        }
        LabeledSection("Session duration") {
            SegmentedChips(options = AiDurations.map { "$it min" }, selected = "${viewModel.sessionDuration} min") { label ->
                viewModel.sessionDuration = label.removeSuffix(" min").toInt()
            }
        }
        LabeledSection("Experience level") {
            SegmentedChips(options = AiExperienceLevels, selected = viewModel.experienceLevel) { viewModel.experienceLevel = it }
        }
    }
}

@Composable
private fun TrainingTypeStep(viewModel: AIWorkoutPlannerViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        StepTitle("Training type")
        LabeledSection("Primary (required)") {
            FlowChips(options = AiTrainingCategories, isSelected = { it == viewModel.selectedPrimaryCategory }) { viewModel.selectPrimary(it) }
        }
        LabeledSection("Secondary (up to 2, optional)") {
            FlowChips(
                options = AiTrainingCategories.filter { it != viewModel.selectedPrimaryCategory },
                isSelected = { viewModel.selectedSecondaryCategories.contains(it) },
            ) { viewModel.toggleSecondary(it) }
        }
    }
}

@Composable
private fun FocusSplitStep(viewModel: AIWorkoutPlannerViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        StepTitle("Focus & split")
        if (viewModel.selectedCategories.isEmpty()) {
            Text("Pick a training type first.", fontSize = 14.sp, color = Color.White.copy(alpha = 0.4f))
        } else {
            viewModel.selectedCategories.forEach { category ->
                val focus = AiCategoryFocusOptions[category] ?: return@forEach
                LabeledSection(focus.label) {
                    FlowChips(
                        options = focus.options,
                        isSelected = { (viewModel.focusByCategory[category] ?: emptySet()).contains(it) },
                    ) { viewModel.toggleFocus(category, it) }
                }
            }
        }
        LabeledSection("Workout split") {
            SegmentedChips(options = AiWorkoutSplits, selected = viewModel.selectedWorkoutSplit) { viewModel.selectedWorkoutSplit = it }
        }
    }
}

@Composable
private fun InjuriesStep(viewModel: AIWorkoutPlannerViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StepTitle("Anything to know?")
        LabeledSection("Injuries or limitations") {
            OutlinedTextField(
                value = viewModel.injuriesText, onValueChange = { viewModel.injuriesText = it },
                placeholder = { Text("e.g. lower back sensitivity", color = Color.White.copy(alpha = 0.3f)) },
                modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
            )
        }
        LabeledSection("Additional notes") {
            OutlinedTextField(
                value = viewModel.additionalNotes, onValueChange = { viewModel.additionalNotes = it },
                placeholder = { Text("Optional", color = Color.White.copy(alpha = 0.3f)) },
                modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
            )
        }
    }
}

@Composable
private fun ReviewSection(viewModel: AIWorkoutPlannerViewModel, onApplied: () -> Unit) {
    val dayOrder = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.weight(1f)
        ) {
            item {
                Text("Review your plan", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            dayOrder.forEach { day ->
                val dayPlan = viewModel.generatedDays[day] ?: return@forEach
                val exercises = dayPlan.exercises.orEmpty()
                if (exercises.isEmpty()) return@forEach
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(day.replaceFirstChar { it.uppercase() }, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(modifier = Modifier.padding(start = 8.dp))
                            Text(dayPlan.title ?: "", fontSize = 13.sp, color = Color.White.copy(alpha = 0.4f))
                            Spacer(modifier = Modifier.weight(1f))
                            Text("${viewModel.checkedCount(day)}/${exercises.size}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.4f))
                        }
                        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardBackground)) {
                            exercises.forEachIndexed { index, exercise ->
                                val key = "$day::$index"
                                val checked = viewModel.checkedKeys.contains(key)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().clickable { viewModel.toggleExercise(key) }.padding(horizontal = 14.dp, vertical = 12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(if (checked) CompletedGreen else Color.White.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) { if (checked) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp)) }
                                    Spacer(modifier = Modifier.padding(start = 12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(exercise.name ?: "Exercise", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = if (checked) 1f else 0.4f))
                                        Text(exerciseGenSummary(exercise), fontSize = 12.sp, color = Color.White.copy(alpha = if (checked) 0.5f else 0.25f))
                                    }
                                }
                                if (index != exercises.lastIndex) Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)).padding(start = 14.dp))
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
                .clickable(enabled = !viewModel.isApplying && viewModel.checkedKeys.isNotEmpty()) { viewModel.apply { success -> if (success) onApplied() } }
        ) {
            if (viewModel.isApplying) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
            else {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.padding(start = 8.dp))
                Text("Apply to This Week", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}

private fun exerciseGenSummary(exercise: com.vasilisneo.trackstar.data.api.WorkoutPlanExercise): String {
    val setCount = exercise.sets ?: 1
    val valuePart = when {
        exercise.durationSeconds != null -> "${exercise.durationSeconds}s"
        exercise.distanceMeters != null -> "${exercise.distanceMeters}m"
        else -> "${exercise.reps ?: 10} reps"
    }
    return "$setCount sets · $valuePart"
}

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
private fun SelectableRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) TrackstarAccent.copy(alpha = 0.18f) else CardBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = TrackstarAccent, modifier = Modifier.size(18.dp))
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
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) TrackstarAccent else CardBackground)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
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
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) TrackstarAccent else CardBackground)
                    .clickable { onToggle(option) }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun MiniStep(onDec: () -> Unit, onInc: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(modifier = Modifier.size(30.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).clickable(onClick = onDec), contentAlignment = Alignment.Center) {
            Text("−", fontSize = 18.sp, color = Color.White)
        }
        Box(modifier = Modifier.size(30.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).clickable(onClick = onInc), contentAlignment = Alignment.Center) {
            Text("+", fontSize = 18.sp, color = Color.White)
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
    focusedBorderColor = TrackstarAccent, unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
    cursorColor = TrackstarAccent,
)
