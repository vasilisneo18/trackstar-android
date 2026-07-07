package com.vasilisneo.trackstar.ui.screens.main.workout

// Replica of MyWorkoutView's rest-day empty state (Trackstar/UI/View/MainApp/Athlete/
// Workout/Today/MyWorkoutView.swift) on iOS. Scope for this pass: the empty state only —
// there's no session/plan data model on Android yet, so displaySessions is always empty
// and this screen never shows anything else. Skipped vs. iOS: scroll-collapsing day strip,
// swipe-to-change-day gesture, floating "Today" pill, active-session mini-bar, weekly
// session-limit gating — all deferred until there's real plan data to drive them.

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.ui.res.painterResource
import com.vasilisneo.trackstar.R
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vasilisneo.trackstar.ui.components.AuthWordmark
import com.vasilisneo.trackstar.ui.components.ProfileAvatarButton
import com.vasilisneo.trackstar.ui.components.initialsFrom
import com.vasilisneo.trackstar.ui.theme.TrackstarBackground
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@Composable
fun WorkoutScreen(
    onProfileClick: () -> Unit = {},
    onScheduleWorkout: () -> Unit = {},
) {
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val weekStart = remember(selectedDate) { selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
    val weekDays = remember(weekStart) { (0..6).map { weekStart.plusDays(it.toLong()) } }
    val today = LocalDate.now()

    Box(modifier = Modifier.fillMaxSize().background(TrackstarBackground)) {
        // Lift the wordmark above the floating tab bar (which overlays the bottom of this
        // full-screen tab content) instead of letting it sit hidden behind it.
        Box(
            modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 76.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            AuthWordmark()
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Top row: profile avatar + schedule (calendar) button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                ProfileAvatarButton(initials = initialsFrom(null), onClick = onProfileClick)
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable(onClick = onScheduleWorkout),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = "Schedule workout", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }

            // Day strip
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                for (day in weekDays) {
                    val selected = day == selectedDate
                    val isToday = day == today
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) Color.White.copy(alpha = 0.18f) else Color.Transparent)
                            .clickable { selectedDate = day }
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                        Text(
                            text = day.dayOfMonth.toString(),
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = Color.White
                        )
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .background(if (isToday) Color.White else Color.Transparent, CircleShape)
                        )
                    }
                }
            }

            // Rest-day empty state
            Box(modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 100.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.size(120.dp).background(Color.White.copy(alpha = 0.05f), CircleShape))
                        Box(modifier = Modifier.size(86.dp).background(Color.White.copy(alpha = 0.07f), CircleShape))
                        Box(modifier = Modifier.size(58.dp).background(Color.White.copy(alpha = 0.10f), CircleShape))
                        Icon(
                            painter = painterResource(R.drawable.ic_workout_figure),
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(50))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "REST DAY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.5.sp,
                            color = Color.White.copy(alpha = 0.35f)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Nothing planned.", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            "Enjoy the recovery, or\nschedule something.",
                            fontSize = 15.sp,
                            color = Color.White.copy(alpha = 0.4f),
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable(onClick = onScheduleWorkout)
                            .padding(vertical = 14.dp),
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text("Schedule Workout", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
