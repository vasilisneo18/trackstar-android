package com.vasilisneo.trackstar.ui.screens.main.coach

// Ports iOS's AthleteDetailView: a coach drills into one athlete via a nav bar (back + name/email)
// and a segmented tab picker. This first pass wires the Sessions tab (the athlete's completed
// workouts, reusing the Stats history week list + SessionReportScreen); Plan and Diet tabs are
// placeholders until their athlete-scoped editing lands.

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
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
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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

    var tab by remember { mutableStateOf(AthleteTab.PLAN) }
    var reportSession by remember { mutableStateOf<WorkoutSessionResponse?>(null) }

    Box(modifier = Modifier.fillMaxSize().trackstarBackground()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // Nav bar: back + athlete name/email.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp)) {
                GlassCircleIconButton(onClick = onBack, contentDescription = "Back", icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                    email?.takeIf { it.isNotBlank() }?.let {
                        Text(it, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 1)
                    }
                }
                Spacer(modifier = Modifier.size(44.dp))
            }

            TabPicker(selected = tab, onSelect = { tab = it }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))

            Box(modifier = Modifier.fillMaxSize()) {
                when (tab) {
                    AthleteTab.SESSIONS -> SessionsWeekList(vm.sessions) { reportSession = it }
                    AthleteTab.PLAN -> {
                        val planVm: WeeklyPlanViewModel = viewModel(key = "plan-$athleteId") {
                            WeeklyPlanViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!, athleteId)
                        }
                        AthletePlanTab(viewModel = planVm)
                    }
                    AthleteTab.PROGRESS -> AthleteProgressTab(sessions = vm.sessions)
                    AthleteTab.DIET -> {
                        val dietVm: DietViewModel = viewModel(key = "diet-$athleteId") {
                            DietViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!, athleteId)
                        }
                        AthleteDietTab(viewModel = dietVm)
                    }
                    AthleteTab.PROFILE -> AthleteProfileTab(viewModel = vm)
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
