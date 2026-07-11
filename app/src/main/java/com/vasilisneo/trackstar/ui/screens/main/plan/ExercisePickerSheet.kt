package com.vasilisneo.trackstar.ui.screens.main.plan

// Exercise library picker — ports iOS's ExercisePickerSheet.swift, including the real static
// exercise-name library (allGroups), copied verbatim (not invented). Category pills, search
// (substring match, deduplicated across categories in "All"), a "Add custom name" row when the
// search doesn't match anything, multi-select, and an "Add N Exercises" button. Emits stub
// ExerciseData per selected name (sets = one unconfigured set) — the caller (SessionEditScreen)
// is responsible for immediately opening ExerciseEditorSheet to configure each one, matching
// iOS's effective flow (the picker itself has no configuration step).

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vasilisneo.trackstar.data.api.ExerciseData
import com.vasilisneo.trackstar.data.api.ExerciseSet
import com.vasilisneo.trackstar.data.api.FrequencyValue
import com.vasilisneo.trackstar.data.api.ResistanceUnit
import com.vasilisneo.trackstar.data.api.ResistanceValue
import com.vasilisneo.trackstar.ui.theme.TrackstarAccent
import com.vasilisneo.trackstar.ui.theme.trackstarBackground
import kotlinx.coroutines.launch
import java.util.UUID

// MARK: - Data (ported verbatim from ExercisePickerSheet.swift's `allGroups`)

private data class FitnessCategory(val name: String, val icon: ImageVector)

private val categories = listOf(
    FitnessCategory("All", Icons.Filled.GridView),
    FitnessCategory("Gym", Icons.Filled.FitnessCenter),
    FitnessCategory("Calisthenics", Icons.Filled.Accessibility),
    FitnessCategory("Powerlifting", Icons.Filled.FitnessCenter),
    FitnessCategory("Olympic", Icons.Filled.EmojiEvents),
    FitnessCategory("Running", Icons.AutoMirrored.Filled.DirectionsRun),
    FitnessCategory("Cycling", Icons.AutoMirrored.Filled.DirectionsBike),
    FitnessCategory("Track & Field", Icons.Filled.Timer),
    FitnessCategory("Swimming", Icons.Filled.Pool),
    FitnessCategory("HIIT", Icons.Filled.LocalFireDepartment),
    FitnessCategory("Yoga", Icons.Filled.SelfImprovement),
    FitnessCategory("Stretching", Icons.Filled.Accessibility),
)

private data class ExerciseGroup(val name: String, val category: String, val exercises: List<String>) {
    val id: String get() = "$category-$name"
}

private val allGroups = listOf(
    // Gym
    ExerciseGroup("Chest", "Gym", listOf("Bench Press", "Incline Bench Press", "Decline Bench Press", "Dumbbell Fly", "Cable Fly", "Pec Deck", "Chest Dip")),
    ExerciseGroup("Back", "Gym", listOf("Deadlift", "Bent-Over Row", "Lat Pulldown", "Seated Cable Row", "Face Pull", "T-Bar Row", "Single-Arm Row", "Good Morning")),
    ExerciseGroup("Shoulders", "Gym", listOf("Overhead Press", "Dumbbell Lateral Raise", "Front Raise", "Arnold Press", "Rear Delt Fly", "Upright Row", "Cable Lateral Raise", "Shrugs")),
    ExerciseGroup("Arms", "Gym", listOf("Barbell Curl", "Dumbbell Curl", "Hammer Curl", "Preacher Curl", "Concentration Curl", "Tricep Pushdown", "Skull Crusher", "Overhead Tricep Extension", "Close-Grip Bench Press")),
    ExerciseGroup("Legs", "Gym", listOf("Squat", "Front Squat", "Romanian Deadlift", "Leg Press", "Leg Curl", "Leg Extension", "Calf Raise", "Hip Thrust", "Bulgarian Split Squat", "Walking Lunge", "Hack Squat")),
    ExerciseGroup("Core", "Gym", listOf("Cable Crunch", "Ab Wheel Rollout", "Hanging Leg Raise", "Decline Sit-Up", "Pallof Press", "Woodchop")),
    // Calisthenics
    ExerciseGroup("Push", "Calisthenics", listOf("Push-Up", "Diamond Push-Up", "Wide Push-Up", "Pike Push-Up", "Archer Push-Up", "Pseudo Planche Push-Up", "Handstand Push-Up", "Chest Dip", "Korean Dip")),
    ExerciseGroup("Pull", "Calisthenics", listOf("Pull-Up", "Chin-Up", "Wide-Grip Pull-Up", "Archer Pull-Up", "Muscle-Up", "Australian Row", "Typewriter Pull-Up")),
    ExerciseGroup("Core", "Calisthenics", listOf("Plank", "Side Plank", "Hollow Body Hold", "L-Sit", "Dragon Flag", "Dead Bug", "Mountain Climber", "Crunches", "Russian Twist", "V-Up")),
    ExerciseGroup("Legs", "Calisthenics", listOf("Pistol Squat", "Jump Squat", "Box Jump", "Walking Lunge", "Step-Up", "Glute Bridge", "Nordic Curl")),
    ExerciseGroup("Skills", "Calisthenics", listOf("Handstand Hold", "Planche Lean", "Front Lever Tuck", "Back Lever", "Human Flag", "Ring Support Hold")),
    // Powerlifting
    ExerciseGroup("Competition Lifts", "Powerlifting", listOf("Squat", "Bench Press", "Deadlift")),
    ExerciseGroup("Squat Variations", "Powerlifting", listOf("Low Bar Squat", "High Bar Squat", "Box Squat", "Pause Squat", "Safety Bar Squat", "Front Squat", "Belt Squat")),
    ExerciseGroup("Bench Variations", "Powerlifting", listOf("Pause Bench Press", "Close-Grip Bench Press", "Spoto Press", "Board Press", "Floor Press", "Wide-Grip Bench Press")),
    ExerciseGroup("Deadlift Variations", "Powerlifting", listOf("Sumo Deadlift", "Conventional Deadlift", "Deficit Deadlift", "Rack Pull", "Block Pull", "Romanian Deadlift", "Stiff-Leg Deadlift")),
    ExerciseGroup("Accessories", "Powerlifting", listOf("Good Morning", "Hip Thrust", "Barbell Row", "Lat Pulldown", "Overhead Press", "Face Pull", "Tricep Pushdown", "Bicep Curl")),
    // Olympic
    ExerciseGroup("Clean", "Olympic", listOf("Power Clean", "Clean", "Hang Power Clean", "Hang Clean", "Clean Pull", "Clean Deadlift")),
    ExerciseGroup("Snatch", "Olympic", listOf("Power Snatch", "Snatch", "Hang Power Snatch", "Hang Snatch", "Snatch Pull", "Snatch Deadlift", "Muscle Snatch")),
    ExerciseGroup("Jerk", "Olympic", listOf("Split Jerk", "Push Jerk", "Power Jerk", "Clean & Jerk")),
    ExerciseGroup("Accessories", "Olympic", listOf("Front Squat", "Overhead Squat", "Push Press", "Snatch Balance", "Drop Snatch", "Romanian Deadlift", "Good Morning")),
    // Running
    ExerciseGroup("Easy", "Running", listOf("Easy Run", "Recovery Run", "Long Run", "Base Run")),
    ExerciseGroup("Intervals", "Running", listOf("Interval Sprints", "Hill Repeats", "Fartlek", "Tempo Run", "Lactate Threshold Run", "Cruise Intervals")),
    ExerciseGroup("Distance", "Running", listOf("5K Run", "10K Run", "Half Marathon Pace Run", "Marathon Pace Run")),
    // Cycling
    ExerciseGroup("Outdoor", "Cycling", listOf("Road Cycling", "Hill Climb", "Endurance Ride", "Group Ride")),
    ExerciseGroup("Indoor", "Cycling", listOf("Indoor Cycling", "Interval Cycling", "Spin Class", "Tempo Ride", "VO2 Max Intervals")),
    // Track & Field
    ExerciseGroup("Sprints", "Track & Field", listOf("100m Sprint", "200m Sprint", "400m Sprint", "60m Sprint", "Flying 30s", "Acceleration Runs")),
    ExerciseGroup("Middle Distance", "Track & Field", listOf("800m Run", "1000m Run", "1500m Run", "Mile Run")),
    ExerciseGroup("Long Distance", "Track & Field", listOf("3000m Run", "5000m Run", "10000m Run", "Steeplechase")),
    ExerciseGroup("Jumps", "Track & Field", listOf("Long Jump", "Triple Jump", "High Jump", "Pole Vault", "Box Jump")),
    ExerciseGroup("Throws", "Track & Field", listOf("Shot Put", "Discus", "Javelin", "Hammer Throw", "Medicine Ball Throw")),
    // Swimming
    ExerciseGroup("Strokes", "Swimming", listOf("Freestyle", "Backstroke", "Breaststroke", "Butterfly", "Individual Medley")),
    ExerciseGroup("Training", "Swimming", listOf("Interval Swim", "Endurance Swim", "Kick Sets", "Pull Sets", "Drill Work", "Open Water Swim")),
    // HIIT
    ExerciseGroup("Metabolic", "HIIT", listOf("Burpees", "Jump Rope", "Battle Ropes", "Kettlebell Swing", "Box Jump", "Sled Push", "Assault Bike", "Rowing Sprints")),
    ExerciseGroup("Circuits", "HIIT", listOf("Tabata", "AMRAP", "EMOM", "Death By...", "Ladder Circuit")),
    // Yoga
    ExerciseGroup("Flow", "Yoga", listOf("Sun Salutation", "Vinyasa Flow", "Power Yoga", "Ashtanga")),
    ExerciseGroup("Holds", "Yoga", listOf("Warrior I", "Warrior II", "Downward Dog", "Chair Pose", "Tree Pose", "Crow Pose", "Pigeon Pose")),
    // Stretching
    ExerciseGroup("Static", "Stretching", listOf("Hamstring Stretch", "Hip Flexor Stretch", "Quad Stretch", "Chest Opener", "Shoulder Cross Stretch", "Calf Stretch", "Seated Forward Fold", "Butterfly Stretch")),
    ExerciseGroup("Dynamic", "Stretching", listOf("Leg Swings", "Arm Circles", "Hip Circles", "Inchworm", "World's Greatest Stretch", "Lateral Lunge", "Walking Knee Hug")),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerSheet(onAdd: (List<ExerciseData>) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    // Locked + animated like the superset sheet: reject user swipe/scrim (confirmValueChange), but
    // allow our own hide so the X / "Add N" buttons slide the sheet down before it's removed.
    var allowHide by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || allowHide },
    )
    fun close(after: () -> Unit) {
        allowHide = true
        scope.launch {
            sheetState.hide()
            after()
        }
    }
    var searchText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    val selected = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    val customExercises = remember { androidx.compose.runtime.mutableStateListOf<String>() }

    val filtered = remember(searchText, selectedCategory, customExercises.size) {
        filteredGroups(searchText, selectedCategory)
    }

    ModalBottomSheet(
        onDismissRequest = {},
        sheetState = sheetState,
        containerColor = Color.Transparent,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        // 93%-height sheet (stops below the status bar), rounded top + theme gradient — matching
        // the superset sheet.
        Box(
            modifier = Modifier
                .fillMaxHeight(0.93f)
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .trackstarBackground()
        ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable { close(onDismiss) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.weight(1f))
                Text("Add Exercises", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.size(44.dp))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.1f))
                    .padding(horizontal = 14.dp, vertical = 11.dp)
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                // BasicTextField (not Material3 TextField) so the search bar's height is set purely
                // by the container's 11dp vertical padding — matching iOS's compact search bar
                // (h14/v11, corner 16, white@10%) instead of Material's ~56dp min height.
                BasicTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (searchText.isEmpty()) {
                            Text("Search or type a custom name…", color = Color.White.copy(alpha = 0.4f), fontSize = 16.sp)
                        }
                        inner()
                    },
                )
                if (searchText.isNotEmpty()) {
                    Icon(
                        Icons.Filled.Clear, contentDescription = "Clear", tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp).clickable { searchText = "" }
                    )
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                items(categories) { cat ->
                    val active = selectedCategory == cat.name
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (active) TrackstarAccent else Color.White.copy(alpha = 0.08f))
                            .clickable { selectedCategory = cat.name }
                            // iOS chip is h14/v8 with a 12pt icon; Compose's text line-box runs a
                            // few dp taller, so v6 + a 12dp icon lands the chip at iOS's height.
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(cat.icon, contentDescription = null, tint = if (active) Color.White else Color.White.copy(alpha = 0.5f), modifier = Modifier.size(12.dp))
                        Text(cat.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (active) Color.White else Color.White.copy(alpha = 0.5f))
                    }
                }
            }

            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (customExercises.isNotEmpty()) {
                    item {
                        ExerciseGroupSection(title = "CUSTOM", names = customExercises, selected = selected) { name ->
                            if (selected.contains(name)) selected.remove(name) else selected.add(name)
                        }
                    }
                }

                val trimmed = searchText.trim()
                if (trimmed.isNotEmpty() && !customExercises.contains(trimmed)) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(TrackstarAccent.copy(alpha = 0.15f))
                                .clickable {
                                    customExercises.add(trimmed)
                                    selected.add(trimmed)
                                    searchText = ""
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Box(modifier = Modifier.size(34.dp).clip(CircleShape).background(TrackstarAccent), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                            Spacer(modifier = Modifier.padding(start = 12.dp))
                            Column {
                                Text("Add \"$trimmed\"", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                Text("Custom exercise", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                            }
                        }
                    }
                }

                items(filtered, key = { it.id }) { group ->
                    ExerciseGroupSection(title = group.name.uppercase(), names = group.exercises, selected = selected) { name ->
                        if (selected.contains(name)) selected.remove(name) else selected.add(name)
                    }
                }

                item { Spacer(modifier = Modifier.height(if (selected.isEmpty()) 20.dp else 90.dp)) }
            }
        }

        if (selected.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp, bottom = 16.dp)
                    .height(54.dp)
                    .clip(RoundedCornerShape(50))
                    .background(TrackstarAccent)
                    .clickable { close { onAdd(stubExercisesFromNames(selected.toList())) } }
            ) {
                Text(
                    "Add ${selected.size} Exercise${if (selected.size == 1) "" else "s"}",
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White
                )
            }
        }
        }
    }
}

@Composable
private fun ExerciseGroupSection(title: String, names: List<String>, selected: List<String>, onToggle: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.45f), modifier = Modifier.padding(horizontal = 4.dp))
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.07f))) {
            names.forEachIndexed { index, name ->
                val isSelected = selected.contains(name)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth().clickable { onToggle(name) }.padding(horizontal = 14.dp, vertical = 13.dp)
                ) {
                    Box(
                        modifier = Modifier.size(28.dp).clip(CircleShape).background(if (isSelected) TrackstarAccent else Color.White.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                    }
                    Text(name, fontSize = 16.sp, color = Color.White, modifier = Modifier.weight(1f))
                }
                if (index != names.lastIndex) {
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)).padding(start = 52.dp))
                }
            }
        }
    }
}

private fun deduplicated(groups: List<ExerciseGroup>): List<ExerciseGroup> {
    val seen = mutableSetOf<String>()
    return groups.mapNotNull { group ->
        val unique = group.exercises.filter { seen.add(it) }
        if (unique.isEmpty()) null else group.copy(exercises = unique)
    }
}

private fun filteredGroups(searchText: String, selectedCategory: String): List<ExerciseGroup> {
    val categoryFiltered = if (selectedCategory == "All") allGroups else allGroups.filter { it.category == selectedCategory }
    val inAllMode = selectedCategory == "All"
    if (searchText.isEmpty()) {
        return if (inAllMode) deduplicated(categoryFiltered) else categoryFiltered
    }
    val query = searchText.lowercase()
    val result = categoryFiltered.mapNotNull { group ->
        val matches = group.exercises.filter { it.lowercase().contains(query) }
        if (matches.isEmpty()) null else group.copy(exercises = matches)
    }
    return if (inAllMode) deduplicated(result) else result
}

// Builds the stub ExerciseData list from selected names — matches iOS's addButton action
// exactly (default set: reps(0), weight(""), .weight resistance unit).
fun stubExercisesFromNames(names: List<String>): List<ExerciseData> = names.map { name ->
    ExerciseData(
        id = UUID.randomUUID().toString(),
        name = name,
        sets = listOf(
            ExerciseSet(
                id = UUID.randomUUID().toString(),
                frequencyValue = FrequencyValue(reps = 0),
                resistanceValue = ResistanceValue(weight = ""),
                restSeconds = 0,
                setType = "Normal",
                repsMax = null,
            )
        ),
        frequencyType = "Repetitions",
        resistanceType = "Weight",
        resistanceUnit = ResistanceUnit(weight = "KG"),
        compoundGroupId = null,
    )
}
