package com.vasilisneo.trackstar.ui.screens.register

// Replica of FitnessProfileView (Trackstar/UI/View/Registration/FitnessProfileView.swift)
// on iOS — step 4/5 of the registration flow: experience level (2x2 card grid) and
// training days per week (7-day pill row). The 2x2 grid is built with two Rows rather
// than a LazyVerticalGrid since it sits inside AuthScreenScaffold's already-scrollable
// column and there are only ever 4 fixed cards — a nested lazy grid would need its own
// unbounded-height workaround for no benefit.

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vasilisneo.trackstar.ui.components.AuthCapsuleButton
import com.vasilisneo.trackstar.ui.components.AuthFieldLabel
import com.vasilisneo.trackstar.ui.components.AuthScreenScaffold

private val FitnessAccent = Color(0xFF5A8CFF)

private fun levelIcon(level: FitnessLevel): ImageVector = when (level) {
    FitnessLevel.BEGINNER -> Icons.AutoMirrored.Filled.DirectionsWalk
    FitnessLevel.INTERMEDIATE -> Icons.AutoMirrored.Filled.DirectionsRun
    FitnessLevel.ADVANCED -> Icons.Filled.FitnessCenter
    FitnessLevel.ELITE -> Icons.Filled.Bolt
}

private fun levelSubtitle(level: FitnessLevel): String = when (level) {
    FitnessLevel.BEGINNER -> "New to training"
    FitnessLevel.INTERMEDIATE -> "1–3 years of training"
    FitnessLevel.ADVANCED -> "3+ years, structured"
    FitnessLevel.ELITE -> "Competitive level"
}

@Composable
fun FitnessProfileScreen(
    viewModel: RegisterViewModel = viewModel(),
    onBackClick: () -> Unit = {},
    onContinue: () -> Unit = {},
) {
    AuthScreenScaffold(
        title = "Fitness Profile",
        subtitle = "Helps us tailor your plans",
        showBackButton = true,
        onBackClick = onBackClick,
        navBarTrailing = {
            Text(
                text = "4 / 5",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.45f)
            )
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(32.dp)) {
            // Experience level
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AuthFieldLabel("EXPERIENCE LEVEL")
                val levels = FitnessLevel.entries
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (row in levels.chunked(2)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (level in row) {
                                LevelCard(
                                    level = level,
                                    selected = viewModel.fitnessLevel == level,
                                    onClick = { viewModel.onFitnessLevelChange(level) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // Training days per week
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AuthFieldLabel("TRAINING DAYS / WEEK")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (day in 1..7) {
                        DayPill(
                            day = day,
                            selected = viewModel.trainingDaysPerWeek == day,
                            onClick = { viewModel.onTrainingDaysPerWeekChange(day) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        AuthCapsuleButton(
            text = "Continue",
            onClick = onContinue,
            modifier = Modifier.padding(top = 28.dp)
        )
    }
}

@Composable
private fun LevelCard(
    level: FitnessLevel,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .background(
                if (selected) FitnessAccent.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.05f),
                RoundedCornerShape(16.dp)
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) FitnessAccent.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = levelIcon(level),
                contentDescription = null,
                tint = if (selected) FitnessAccent else Color.White.copy(alpha = 0.4f),
                modifier = Modifier.height(18.dp)
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.Circle,
                contentDescription = null,
                tint = if (selected) FitnessAccent else Color.White.copy(alpha = 0.2f),
                modifier = Modifier.height(18.dp)
            )
        }
        Text(level.label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(levelSubtitle(level), fontSize = 12.sp, color = Color.White.copy(alpha = 0.4f))
    }
}

@Composable
private fun DayPill(
    day: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .height(48.dp)
            .background(
                if (selected) FitnessAccent.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
                RoundedCornerShape(12.dp)
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) FitnessAccent.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = day.toString(),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.4f)
        )
    }
}
