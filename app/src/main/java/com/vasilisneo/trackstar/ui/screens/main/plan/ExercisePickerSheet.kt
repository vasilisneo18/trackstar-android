package com.vasilisneo.trackstar.ui.screens.main.plan

// Exercise library picker — ports iOS's ExercisePickerSheet.swift, including the real static
// exercise-name library (allGroups), copied verbatim (not invented). Category pills, search
// (substring match, deduplicated across categories in "All"), a "Add custom name" row when the
// search doesn't match anything, multi-select, and an "Add N Exercises" button. Emits stub
// ExerciseData per selected name (sets = one unconfigured set) — the caller (SessionEditScreen)
// is responsible for immediately opening ExerciseEditorSheet to configure each one, matching
// iOS's effective flow (the picker itself has no configuration step).

import com.vasilisneo.trackstar.ui.i18n.tr
import com.vasilisneo.trackstar.ui.i18n.trf
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import com.vasilisneo.trackstar.ui.i18n.Text
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.vasilisneo.trackstar.data.workout.CatalogExercise
import com.vasilisneo.trackstar.data.workout.ExerciseCatalog
import com.vasilisneo.trackstar.data.workout.MuscleGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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

// MARK: - Library mapping

/** Which library exercises appear under each picker category (same rules as iOS). */
private fun libraryIncludes(e: CatalogExercise, category: String): Boolean = when (category) {
    "All" -> true
    "Gym" -> e.category == "strength" && e.equipment != "body only"
    "Calisthenics" -> e.equipment == "body only" && (e.category == "strength" || e.category == "plyometrics")
    "Powerlifting" -> e.category == "powerlifting" || e.category == "strongman"
    "Olympic" -> e.category == "olympic weightlifting"
    "HIIT" -> e.category == "plyometrics" || e.category == "cardio"
    "Stretching" -> e.category == "stretching"
    else -> false
}

/** "muscle.Back" etc. — a namespaced key so "Back" the muscle isn't "Back" the button. */
private fun trPrefixed(prefix: String, s: String): String {
    val key = prefix + s
    val v = tr(key)
    return if (v == key) s else v
}

private fun subtitle(e: CatalogExercise): String = listOfNotNull(
    e.equipment?.takeIf { it != "other" }?.let { tr(ExerciseCatalog.equipmentLabel(it)) },
    e.primary.firstOrNull()?.let { m -> trPrefixed("muscle.", m.split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }) },
).joinToString(" · ")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerSheet(onAdd: (List<ExerciseData>) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
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
    var muscle by remember { mutableStateOf<MuscleGroup?>(null) }
    var catalogReady by remember { mutableStateOf(ExerciseCatalog.exercises.isNotEmpty()) }
    var detail by remember { mutableStateOf<Pair<CatalogExercise, String>?>(null) }
    val selected = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    val customExercises = remember { androidx.compose.runtime.mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        if (!catalogReady) {
            withContext(Dispatchers.IO) { ExerciseCatalog.load(context.applicationContext) }
            catalogReady = true
        }
    }

    val query = searchText.trim().lowercase()
    val filtered = remember(query, selectedCategory, muscle, catalogReady) {
        filteredGroups(query, selectedCategory, muscle)
    }
    val library = remember(query, selectedCategory, muscle, catalogReady) {
        libraryGroups(query, selectedCategory, muscle)
    }
    val categoryHasLibrary = remember(selectedCategory, catalogReady) {
        ExerciseCatalog.exercises.any { libraryIncludes(it, selectedCategory) }
    }
    fun toggle(name: String) { if (selected.contains(name)) selected.remove(name) else selected.add(name) }

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
                // by the container's 11dp vertical padding — matching iOS's compact search bar.
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
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
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
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(cat.icon, contentDescription = null, tint = if (active) Color.White else Color.White.copy(alpha = 0.5f), modifier = Modifier.size(12.dp))
                        Text(cat.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (active) Color.White else Color.White.copy(alpha = 0.5f))
                    }
                }
            }

            // Secondary filter: muscle group (applies to familiar exercises and the library).
            if (categoryHasLibrary) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    val chips: List<MuscleGroup?> = listOf(null) + MuscleGroup.entries.filter { it != MuscleGroup.Other }
                    items(chips) { g ->
                        val active = muscle == g
                        Text(
                            if (g == null) tr("All muscles") else trPrefixed("muscle.", g.label),
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            color = if (active) Color.White else Color.White.copy(alpha = 0.5f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (active) TrackstarAccent.copy(alpha = 0.25f) else Color.Transparent)
                                .border(1.dp, if (active) TrackstarAccent else Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
                                .clickable { muscle = g }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(4.dp))
            }

            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (customExercises.isNotEmpty()) {
                    item {
                        Box(modifier = Modifier.padding(bottom = 20.dp)) {
                            ExerciseGroupSection(title = tr("Custom").uppercase(), names = customExercises, selected = selected,
                                onToggle = ::toggle, onInfo = null)
                        }
                    }
                }

                val trimmed = searchText.trim()
                if (trimmed.isNotEmpty() && !customExercises.contains(trimmed)) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(bottom = 20.dp)
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
                    Box(modifier = Modifier.padding(bottom = 20.dp)) {
                    ExerciseGroupSection(
                        title = trPrefixed("group.", group.name).uppercase(),
                        names = group.exercises, selected = selected, onToggle = ::toggle,
                        onInfo = { name -> ExerciseCatalog.exercise(name)?.let { detail = it to name } },
                    )
                    }
                }

                // Library — every other exercise in the bundled database, grouped by muscle. One lazy
                // item per row (the "All" library is ~750 rows), drawn as a rounded card per section.
                library.forEach { (group, list) ->
                    item(key = "lib-h-" + group.name) {
                        Text(
                            "${tr("Library")} · ${trPrefixed("muscle.", group.label)}".uppercase(),
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.45f),
                            modifier = Modifier.padding(horizontal = 4.dp).padding(bottom = 8.dp)
                        )
                    }
                    itemsIndexed(list, key = { _, e -> "lib-" + e.id }) { index, e ->
                        val top = if (index == 0) 18.dp else 0.dp
                        val bottom = if (index == list.lastIndex) 18.dp else 0.dp
                        Column(
                            modifier = Modifier
                                .padding(bottom = if (index == list.lastIndex) 20.dp else 0.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom))
                                .background(Color.White.copy(alpha = 0.07f))
                        ) {
                            PickerRow(name = e.name, exercise = e, isSelected = selected.contains(e.name),
                                onTap = { toggle(e.name) }, onInfo = { detail = e to e.name })
                            if (index != list.lastIndex) RowDivider()
                        }
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
                    // contentWindowInsets is zeroed above, so pad the nav bar here or the button
                    // sits behind the system nav bar on 3-button-nav devices.
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp, bottom = 16.dp)
                    .height(54.dp)
                    .clip(RoundedCornerShape(50))
                    .background(TrackstarAccent)
                    // Also dismiss the sheet (set showPicker=false) — hiding alone leaves it mounted
                    // and trapping input, which froze the screen after adding.
                    .clickable { close { onAdd(stubExercisesFromNames(selected.toList())); onDismiss() } }
            ) {
                Text(
                    if (selected.size == 1) tr("Add 1 Exercise") else trf("Add %s Exercises", selected.size),
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White
                )
            }
        }
        }
    }

    detail?.let { (exercise, name) ->
        ExerciseDetailSheet(
            exercise = exercise, displayName = name, isSelected = selected.contains(name),
            onToggle = { toggle(name); detail = null },
            onDismiss = { detail = null },
        )
    }
}

@Composable
private fun ExerciseGroupSection(
    title: String, names: List<String>, selected: List<String>,
    onToggle: (String) -> Unit, onInfo: ((String) -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.45f), modifier = Modifier.padding(horizontal = 4.dp))
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.07f))) {
            names.forEachIndexed { index, name ->
                val exercise = ExerciseCatalog.exercise(name)
                PickerRow(
                    name = name, exercise = exercise, isSelected = selected.contains(name),
                    onTap = { onToggle(name) },
                    onInfo = if (exercise != null && onInfo != null) ({ onInfo(name) }) else null,
                )
                if (index != names.lastIndex) RowDivider()
            }
        }
    }
}

@Composable
private fun LibrarySection(
    title: String, exercises: List<CatalogExercise>, selected: List<String>,
    onToggle: (String) -> Unit, onInfo: (CatalogExercise) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.45f), modifier = Modifier.padding(horizontal = 4.dp))
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.07f))) {
            exercises.forEachIndexed { index, e ->
                PickerRow(name = e.name, exercise = e, isSelected = selected.contains(e.name),
                    onTap = { onToggle(e.name) }, onInfo = { onInfo(e) })
                if (index != exercises.lastIndex) RowDivider()
            }
        }
    }
}

@Composable
private fun RowDivider() {
    Box(modifier = Modifier.fillMaxWidth().padding(start = 72.dp).height(1.dp).background(Color.White.copy(alpha = 0.08f)))
}

/** Thumbnail (with a selection badge), name and "Barbell · Chest", plus ⓘ for the details. */
@Composable
private fun PickerRow(name: String, exercise: CatalogExercise?, isSelected: Boolean, onTap: () -> Unit, onInfo: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f).clickable(onClick = onTap).padding(start = 12.dp, top = 9.dp, bottom = 9.dp, end = 8.dp)
        ) {
            Box {
                ExerciseThumbnail(exercise = exercise, size = 46.dp)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 5.dp, y = 5.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) TrackstarAccent else Color.Black.copy(alpha = 0.55f))
                        .border(1.2.dp, if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontSize = 15.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal, color = Color.White, maxLines = 2)
                if (exercise != null) {
                    val sub = subtitle(exercise)
                    if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f), maxLines = 1)
                }
            }
        }
        if (onInfo != null) {
            Box(modifier = Modifier.size(width = 44.dp, height = 48.dp).clickable(onClick = onInfo), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Info, contentDescription = tr("Exercise details"), tint = Color.White.copy(alpha = 0.45f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Rounded-square exercise photo, or a neutral glyph when there's no photo. */
@Composable
fun ExerciseThumbnail(exercise: CatalogExercise?, size: androidx.compose.ui.unit.Dp) {
    val shape = RoundedCornerShape(size * 0.24f)
    Box(modifier = Modifier.size(size).clip(shape).background(Color.White.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
        if (exercise != null) {
            Icon(Icons.Filled.FitnessCenter, contentDescription = null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(size * 0.36f))
            AsyncImage(model = exercise.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Filled.FitnessCenter, contentDescription = null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(size * 0.4f))
        }
    }
}

/** Library exercise details: the two photos alternate like a slow animation, then muscles,
 *  equipment, level and steps. Mirrors ExerciseDetailSheet.swift. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseDetailSheet(exercise: CatalogExercise, displayName: String, isSelected: Boolean, onToggle: () -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var frame by remember { mutableStateOf(0) }
    LaunchedEffect(exercise.id) {
        if (exercise.images > 1) while (true) { delay(1100); frame = (frame + 1) % exercise.images }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Color.Transparent,
        dragHandle = null, contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Box(modifier = Modifier.fillMaxHeight(0.93f).clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)).trackstarBackground()) {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 72.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().aspectRatio(850f / 567f).clip(RoundedCornerShape(22.dp)).background(Color.White)
                    ) {
                        for (i in 0 until maxOf(exercise.images, 1)) {
                            val alpha by animateFloatAsState(if (frame == i) 1f else 0f, label = "photo")
                            AsyncImage(model = exercise.imageUrl(i), contentDescription = null, contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().alpha(alpha))
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(displayName, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        if (!displayName.equals(exercise.name, ignoreCase = true)) {
                            Text(exercise.name, fontSize = 14.sp, color = Color.White.copy(alpha = 0.45f))
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOfNotNull(
                            exercise.equipment?.takeIf { it != "other" }?.let { tr(ExerciseCatalog.equipmentLabel(it)) },
                            exercise.level?.let { tr(it.replaceFirstChar { c -> c.uppercase() }) },
                            exercise.mechanic?.let { tr(it.replaceFirstChar { c -> c.uppercase() }) },
                        ).forEach { fact ->
                            Text(fact, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.75f),
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.1f)).padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    }
                }
                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.07f)).padding(16.dp)
                    ) {
                        MuscleLine(tr("Primary"), exercise.primary, strong = true)
                        if (exercise.secondary.isNotEmpty()) MuscleLine(tr("Secondary"), exercise.secondary, strong = false)
                    }
                }
                if (exercise.instructions.isNotEmpty()) {
                    item {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.07f)).padding(16.dp)
                        ) {
                            Text("How to do it", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            exercise.instructions.forEachIndexed { i, step ->
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Box(modifier = Modifier.size(22.dp).clip(CircleShape).background(TrackstarAccent.copy(alpha = 0.8f)), contentAlignment = Alignment.Center) {
                                        Text("${i + 1}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                    Text(step, fontSize = 15.sp, color = Color.White.copy(alpha = 0.8f))
                                }
                            }
                        }
                    }
                }
            }
            Box(
                modifier = Modifier.padding(16.dp).size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp, bottom = 16.dp)
                    .height(54.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) Color.White.copy(alpha = 0.15f) else Color.White)
                    .clickable(onClick = onToggle)
            ) {
                Text(
                    if (isSelected) tr("Remove from selection") else tr("Select exercise"),
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (isSelected) Color.White else Color.Black
                )
            }
        }
    }
}

@Composable
private fun MuscleLine(title: String, names: List<String>, strong: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.45f))
        Text(
            names.joinToString(", ") { m -> trPrefixed("muscle.", m.split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }) },
            fontSize = 15.sp, fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
            color = Color.White.copy(alpha = if (strong) 0.95f else 0.7f)
        )
    }
}

private fun deduplicated(groups: List<ExerciseGroup>): List<ExerciseGroup> {
    val seen = mutableSetOf<String>()
    return groups.mapNotNull { group ->
        val unique = group.exercises.filter { seen.add(it) }
        if (unique.isEmpty()) null else group.copy(exercises = unique)
    }
}

private fun filteredGroups(query: String, selectedCategory: String, muscle: MuscleGroup?): List<ExerciseGroup> {
    val categoryFiltered = if (selectedCategory == "All") allGroups else allGroups.filter { it.category == selectedCategory }
    val inAllMode = selectedCategory == "All"
    if (query.isEmpty() && muscle == null) {
        return if (inAllMode) deduplicated(categoryFiltered) else categoryFiltered
    }
    val result = categoryFiltered.mapNotNull { group ->
        val matches = group.exercises.filter {
            (query.isEmpty() || it.lowercase().contains(query)) &&
                (muscle == null || ExerciseCatalog.exercise(it)?.muscleGroup == muscle)
        }
        if (matches.isEmpty()) null else group.copy(exercises = matches)
    }
    return if (inAllMode) deduplicated(result) else result
}

/** Library exercises for the current category / muscle / search, grouped by muscle. */
private fun libraryGroups(query: String, category: String, muscle: MuscleGroup?): List<Pair<MuscleGroup, List<CatalogExercise>>> {
    val items = ExerciseCatalog.exercises.filter { e ->
        e.id !in ExerciseCatalog.aliasedIds &&
            libraryIncludes(e, category) &&
            (muscle == null || e.muscleGroup == muscle) &&
            (query.isEmpty() || e.name.lowercase().contains(query) ||
                e.primary.any { it.contains(query) } || (e.equipment?.contains(query) == true))
    }
    val grouped = items.groupBy { it.muscleGroup }
    return MuscleGroup.entries.mapNotNull { g -> grouped[g]?.takeIf { it.isNotEmpty() }?.let { g to it } }
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
