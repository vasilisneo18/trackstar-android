package com.vasilisneo.trackstar.ui.screens.main.coach

// Ports iOS's AthleteDetailView: a coach drills into one athlete via a nav bar (back + name/email)
// and a segmented tab picker. This first pass wires the Sessions tab (the athlete's completed
// workouts, reusing the Stats history week list + SessionReportScreen); Plan and Diet tabs are
// placeholders until their athlete-scoped editing lands.

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import com.vasilisneo.trackstar.ui.screens.main.attendance.CollapsingTitleScaffold
import com.vasilisneo.trackstar.ui.theme.TrackstarAccent
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vasilisneo.trackstar.data.api.WorkoutSessionResponse
import com.vasilisneo.trackstar.ui.components.GlassCircleIconButton
import com.vasilisneo.trackstar.ui.screens.main.diet.DietViewModel
import com.vasilisneo.trackstar.ui.screens.main.plan.WeeklyPlanViewModel
import com.vasilisneo.trackstar.ui.screens.main.stats.SessionReportScreen
import com.vasilisneo.trackstar.ui.screens.main.stats.SessionsWeekList
import com.vasilisneo.trackstar.ui.theme.trackstarBackground

private enum class AthleteTab(val label: String, val icon: ImageVector) {
    PLAN("Plan", Icons.Filled.FitnessCenter),
    SESSIONS("Sessions", Icons.Filled.History),
    PROGRESS("Progress", Icons.AutoMirrored.Filled.TrendingUp),
    DIET("Diet", Icons.Filled.Restaurant),
    PROFILE("Profile", Icons.Filled.Person),
}

@Composable
fun AthleteDetailScreen(athleteId: String, onBack: () -> Unit) {
    val vm: AthleteDetailViewModel = viewModel(key = athleteId) { AthleteDetailViewModel(athleteId) }
    val name = vm.athlete?.fullName ?: "Athlete"
    val email = vm.athlete?.email

    // null = the athlete dashboard (cards); a value = that section open full-screen.
    var tab by remember { mutableStateOf<AthleteTab?>(null) }
    var reportSession by remember { mutableStateOf<WorkoutSessionResponse?>(null) }
    var showTemplatePicker by remember { mutableStateOf(false) }
    var pendingTemplate by remember { mutableStateOf<TemplateSummary?>(null) }

    // Hoisted so the nav-bar template button can drive it (applyTemplate) — not just the Plan tab —
    // and so the dashboard cards can summarise the plan/diet before a tab is opened.
    val planVm: WeeklyPlanViewModel = viewModel(key = "plan-$athleteId") {
        WeeklyPlanViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!, athleteId)
    }
    val dietVm: DietViewModel = viewModel(key = "diet-$athleteId") {
        DietViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!, athleteId)
    }

    Box(modifier = Modifier.fillMaxSize().trackstarBackground()) {
        val current = tab
        if (current == null) {
            AthleteDashboard(
                name = name, email = email, notes = vm.notes,
                planVm = planVm, dietVm = dietVm, sessions = vm.sessions,
                onBack = onBack, onOpen = { tab = it },
            )
        } else {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                // Inner section nav bar: back to dashboard, name on the left, plus (Plan) apply-template.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp)) {
                    GlassCircleIconButton(onClick = { tab = null }, contentDescription = "Back", icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft)
                    Text(name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, modifier = Modifier.padding(start = 10.dp).weight(1f))
                    if (current == AthleteTab.PLAN) {
                        GlassCircleIconButton(onClick = { showTemplatePicker = true }, contentDescription = "Apply template", icon = Icons.Filled.ContentCopy)
                    } else {
                        Spacer(modifier = Modifier.size(44.dp))
                    }
                }

                com.vasilisneo.trackstar.ui.components.OfflineBanner()

                Box(modifier = Modifier.fillMaxSize()) {
                    when (current) {
                        AthleteTab.SESSIONS -> SessionsWeekList(vm.sessions) { reportSession = it }
                        AthleteTab.PLAN -> AthletePlanTab(viewModel = planVm)
                        AthleteTab.PROGRESS -> AthleteProgressTab(sessions = vm.sessions)
                        AthleteTab.DIET -> AthleteDietTab(viewModel = dietVm)
                        AthleteTab.PROFILE -> AthleteProfileTab(viewModel = vm)
                    }
                }
            }
        }

        // Session report slides up over the detail (matches the Stats history overlay).
        AnimatedVisibility(
            visible = reportSession != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
        ) {
            reportSession?.let { SessionReportScreen(session = it, onClose = { reportSession = null }) }
        }
    }

    if (showTemplatePicker) {
        TemplatePickerSheet(
            onPick = { template -> pendingTemplate = template },
            onDismiss = { showTemplatePicker = false },
        )
    }

    pendingTemplate?.let { template ->
        val firstName = name.trim().substringBefore(' ').ifBlank { "this athlete" }
        AlertDialog(
            onDismissRequest = { pendingTemplate = null; showTemplatePicker = false },
            containerColor = Color(0xFF1A1A26),
            title = { Text("Apply Template?", color = Color.White) },
            text = { Text("\"${template.name}\" will replace this week's plan for $firstName. This cannot be undone.", color = Color.White.copy(alpha = 0.7f)) },
            confirmButton = {
                TextButton(onClick = {
                    planVm.applyTemplate(template.id)
                    pendingTemplate = null
                    showTemplatePicker = false
                }) { Text("Apply", color = Color(0xFFFF453A)) }
            },
            dismissButton = { TextButton(onClick = { pendingTemplate = null; showTemplatePicker = false }) { Text("Cancel", color = Color.White) } },
        )
    }
}

// MARK: - Dashboard (cards instead of tabs)

@Composable
private fun AthleteDashboard(
    name: String,
    email: String?,
    notes: com.vasilisneo.trackstar.data.api.AthleteNotesDto,
    planVm: WeeklyPlanViewModel,
    dietVm: DietViewModel,
    sessions: List<WorkoutSessionResponse>,
    onBack: () -> Unit,
    onOpen: (AthleteTab) -> Unit,
) {
    val weekSessions = planVm.weekPlannedSessions
    val planCount = weekSessions.size
    val exCount = weekSessions.sumOf { it.exercises?.size ?: 0 }
    val planSub = if (planCount == 0) "No plan yet — tap to build one"
        else "$planCount session${if (planCount == 1) "" else "s"} · $exCount exercise${if (exCount == 1) "" else "s"} this week"

    val last = sessions.maxByOrNull { it.sessionData?.completedAt ?: it.sessionData?.date ?: 0.0 }
    val sessionsSub = if (sessions.isEmpty()) "No sessions logged yet"
        else "${sessions.size} logged" + (last?.sessionData?.completedAt?.let { " · last " + epochSecDate(it) } ?: "")

    val meals = dietVm.weeklyPlan.values.sumOf { it.size }
    val dietSub = if (meals == 0) "No diet plan yet" else "$meals meal${if (meals == 1) "" else "s"} planned across the week"

    val profileSub = buildList {
        add(notes.fitnessLevel)
        if (notes.trainingDaysPerWeek > 0) add("${notes.trainingDaysPerWeek} days/week")
        if (notes.goals.isNotBlank()) add(notes.goals)
    }.joinToString(" · ")

    // Detail footers.
    val planDetail = last?.let { s ->
        val t = s.sessionData?.title?.ifBlank { "Workout" } ?: "Workout"
        "Last done: $t" + (s.sessionData?.completedAt?.let { " · " + epochSecDate(it) } ?: "")
    }
    val sessionsDetail = last?.let { s ->
        val t = s.sessionData?.title?.ifBlank { "Workout" } ?: "Workout"
        val sets = s.sessionData?.exercises?.sumOf { it.sets.size } ?: 0
        "$t · ${durationLabel(s.sessionData?.durationSeconds ?: 0)} · $sets sets"
    }
    val trackedCount = sessions.flatMap { it.sessionData?.exercises?.map { e -> e.name.lowercase() } ?: emptyList() }.toSet().size
    val progressDetail = if (trackedCount == 0) null else "$trackedCount exercise${if (trackedCount == 1) "" else "s"} tracked"
    val profileDetail = notes.injuries.trim().takeIf { it.isNotEmpty() }?.let { "Injuries: $it" }

    CollapsingTitleScaffold(title = name, onBack = onBack) {
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                email?.takeIf { it.isNotBlank() }?.let {
                    Text(it, fontSize = 14.sp, color = Color.White.copy(alpha = 0.55f))
                }
                Text(coachingSinceLine(notes), fontSize = 12.sp, color = Color.White.copy(alpha = 0.4f), modifier = Modifier.padding(top = 2.dp))
            }
        }
        item { AthleteDashCard(AthleteTab.PLAN.icon, "Weekly Plan", planSub, Icons.Filled.CheckCircle, planDetail) { onOpen(AthleteTab.PLAN) } }
        item { AthleteDashCard(AthleteTab.SESSIONS.icon, "Sessions", sessionsSub, Icons.Filled.Schedule, sessionsDetail) { onOpen(AthleteTab.SESSIONS) } }
        item { AthleteDashCard(AthleteTab.PROGRESS.icon, "Progress", "Strength trends per exercise", Icons.Filled.BarChart, progressDetail) { onOpen(AthleteTab.PROGRESS) } }
        item { AthleteDashCard(AthleteTab.DIET.icon, "Diet Plan", dietSub, null, null) { onOpen(AthleteTab.DIET) } }
        item { AthleteDashCard(AthleteTab.PROFILE.icon, "Profile", profileSub, Icons.Filled.HealthAndSafety, profileDetail) { onOpen(AthleteTab.PROFILE) } }
    }
}

@Composable
private fun AthleteDashCard(
    icon: ImageVector, title: String, subtitle: String,
    detailIcon: ImageVector?, detailText: String?, onClick: () -> Unit,
) {
    val accent = TrackstarAccent
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 14.dp)
            .clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.06f)).clickable(onClick = onClick).padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(modifier = Modifier.size(50.dp).background(Color.White.copy(alpha = 0.08f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text(subtitle, fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f), maxLines = 2)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
        }
        if (detailText != null) {
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)).padding(vertical = 0.dp))
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (detailIcon != null) Icon(detailIcon, null, tint = accent, modifier = Modifier.size(13.dp))
                Text(detailText, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
            }
        }
    }
}

private fun epochSecDate(sec: Double): String =
    java.text.SimpleDateFormat("EEE, MMM d", java.util.Locale.ENGLISH).format(java.util.Date((sec * 1000).toLong()))

private fun durationLabel(seconds: Int): String {
    val m = (if (seconds < 0) 0 else seconds) / 60
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
}

private fun coachingSinceLine(notes: com.vasilisneo.trackstar.data.api.AthleteNotesDto): String {
    val started = runCatching {
        val d = java.time.LocalDate.parse(notes.startDate)
        d.format(java.time.format.DateTimeFormatter.ofPattern("MMM yyyy", java.util.Locale.ENGLISH))
    }.getOrNull()
    return if (started != null) "Coaching since $started · ${notes.fitnessLevel}" else notes.fitnessLevel
}

@Composable
private fun TabPicker(selected: AthleteTab, onSelect: (AthleteTab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.08f)).padding(4.dp)
    ) {
        AthleteTab.entries.forEach { t ->
            val active = t == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (active) Color.White.copy(alpha = 0.2f) else Color.Transparent)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(t) },
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                    modifier = Modifier.padding(vertical = 9.dp),
                ) {
                    Icon(t.icon, contentDescription = null, tint = if (active) Color.White else Color.White.copy(alpha = 0.45f), modifier = Modifier.size(18.dp))
                    Text(t.label, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = if (active) Color.White else Color.White.copy(alpha = 0.45f))
                }
            }
        }
    }
}

@Composable
private fun Placeholder(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 15.sp, color = Color.White.copy(alpha = 0.4f), textAlign = TextAlign.Center)
    }
}
