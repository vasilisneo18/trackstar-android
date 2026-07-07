package com.vasilisneo.trackstar.ui.screens.register

// Replica of GoalsView (Trackstar/UI/View/Registration/GoalsView.swift) on iOS — step 5/5,
// the final step of the registration flow. 2x3 grid of multi-select goal cards plus a
// full-width "Monitor Athletes" (coach) card below. Grid built with two Rows per the same
// nested-scrollable-parent reasoning as FitnessProfileScreen's experience-level grid.

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vasilisneo.trackstar.ui.components.AuthCapsuleButton
import com.vasilisneo.trackstar.ui.components.AuthErrorText
import com.vasilisneo.trackstar.ui.components.AuthScreenScaffold

private fun goalIcon(goal: UserGoal): ImageVector = when (goal) {
    UserGoal.LOSE_FAT -> Icons.Filled.LocalFireDepartment
    UserGoal.BUILD_MUSCLE -> Icons.Filled.FitnessCenter
    UserGoal.TRACK_TRAINING -> Icons.Filled.BarChart
    UserGoal.PERFORMANCE -> Icons.Filled.Bolt
    UserGoal.EAT_BETTER -> Icons.Filled.Eco
    UserGoal.COACHING -> Icons.Filled.HowToReg
    UserGoal.MONITOR_ATHLETES -> Icons.Filled.Groups
}

private fun goalColor(goal: UserGoal): Color = when (goal) {
    UserGoal.LOSE_FAT -> Color(0xFFFF7333)
    UserGoal.BUILD_MUSCLE -> Color(0xFF5A8CFF)
    UserGoal.TRACK_TRAINING -> Color(0xFF40D199)
    UserGoal.PERFORMANCE -> Color(0xFFB359FF)
    UserGoal.EAT_BETTER -> Color(0xFF40D180)
    UserGoal.COACHING -> Color(0xFFF2B740)
    UserGoal.MONITOR_ATHLETES -> Color(0xFFF2B740)
}

private val GridGoals = listOf(
    UserGoal.LOSE_FAT, UserGoal.BUILD_MUSCLE,
    UserGoal.TRACK_TRAINING, UserGoal.PERFORMANCE,
    UserGoal.EAT_BETTER, UserGoal.COACHING,
)

@Composable
fun GoalsScreen(
    viewModel: RegisterViewModel = viewModel(),
    onBackClick: () -> Unit = {},
    onContinue: () -> Unit = {},
) {
    AuthScreenScaffold(
        title = "What's your goal?",
        subtitle = "Pick everything that applies",
        showBackButton = true,
        onBackClick = onBackClick,
        navBarTrailing = {
            Text(
                text = "5 / 5",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.45f)
            )
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (row in GridGoals.chunked(2)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (goal in row) {
                        GoalCard(
                            goal = goal,
                            selected = viewModel.goals.contains(goal),
                            onClick = { viewModel.toggleGoal(goal) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            MonitorAthletesCard(
                selected = viewModel.goals.contains(UserGoal.MONITOR_ATHLETES),
                onClick = { viewModel.toggleGoal(UserGoal.MONITOR_ATHLETES) },
            )
        }

        viewModel.errorMessage?.let { error -> AuthErrorText(error) }

        AuthCapsuleButton(
            text = "Continue",
            onClick = onContinue,
            isLoading = viewModel.isRegistering,
            enabled = viewModel.isGoalsValid,
            modifier = Modifier.padding(top = 20.dp)
        )
    }
}

@Composable
private fun GoalCard(
    goal: UserGoal,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = goalColor(goal)
    Column(
        modifier = modifier
            .height(118.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (selected) color.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.05f),
                RoundedCornerShape(18.dp)
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) color.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier.size(40.dp).background(color.copy(alpha = if (selected) 0.22f else 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = goalIcon(goal), contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.Circle,
                contentDescription = null,
                tint = if (selected) color else Color.White.copy(alpha = 0.2f),
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(goal.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(3.dp))
        Text(goal.subtitle, fontSize = 11.sp, color = Color.White.copy(alpha = 0.45f), maxLines = 2)
    }
}

@Composable
private fun MonitorAthletesCard(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val goal = UserGoal.MONITOR_ATHLETES
    val color = goalColor(goal)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (selected) color.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.05f),
                RoundedCornerShape(18.dp)
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) color.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp)
    ) {
        Box(
            modifier = Modifier.size(44.dp).background(color.copy(alpha = if (selected) 0.22f else 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = goalIcon(goal), contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(goal.label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Box(
                    modifier = Modifier
                        .background(color.copy(alpha = 0.15f), RoundedCornerShape(50))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text("FOR COACHES", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = color)
                }
            }
            Text(goal.subtitle, fontSize = 13.sp, color = Color.White.copy(alpha = 0.45f))
        }
        Icon(
            imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.Circle,
            contentDescription = null,
            tint = if (selected) color else Color.White.copy(alpha = 0.2f),
            modifier = Modifier.size(20.dp)
        )
    }
}
