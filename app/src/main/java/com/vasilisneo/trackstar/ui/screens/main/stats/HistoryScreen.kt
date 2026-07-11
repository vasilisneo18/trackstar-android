package com.vasilisneo.trackstar.ui.screens.main.stats

// Ports iOS's HistoryView: a month calendar (tap a day → that day's session summary) with a
// toggle into a week-by-week session list (iOS's AthleteHistoryTabView). Data is the completed-
// session list from StatsViewModel. Tapping a session opens the Session Report (Phase 3).

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Timer
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vasilisneo.trackstar.data.api.WorkoutSessionResponse
import com.vasilisneo.trackstar.ui.components.GlassCircleIconButton
import com.vasilisneo.trackstar.ui.theme.TrackstarAccent
import com.vasilisneo.trackstar.ui.theme.trackstarBackground
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CardFill = Color.White.copy(alpha = 0.06f)

@Composable
fun HistoryScreen(
    onBack: () -> Unit = {},
    viewModel: StatsViewModel = viewModel(),
) {
    var showingList by remember { mutableStateOf(false) }
    var reportSession by remember { mutableStateOf<WorkoutSessionResponse?>(null) }
    val sessionsByDate = remember(viewModel.sessions) {
        viewModel.sessions.mapNotNull { s -> s.localDate?.let { it to s } }.toMap()
    }

    Box(modifier = Modifier.fillMaxSize().trackstarBackground()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                GlassCircleIconButton(onClick = onBack, contentDescription = "Back", icon = Icons.Filled.ChevronLeft)
                Spacer(modifier = Modifier.weight(1f))
                Text(if (showingList) "Sessions" else "History", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Spacer(modifier = Modifier.weight(1f))
                GlassCircleIconButton(
                    onClick = { showingList = !showingList },
                    contentDescription = "Toggle view",
                    icon = if (showingList) Icons.Filled.CalendarMonth else Icons.AutoMirrored.Filled.List,
                )
            }

            if (showingList) {
                SessionsWeekList(viewModel) { reportSession = it }
            } else {
                CalendarPane(sessionsByDate) { reportSession = it }
            }
        }

        // Session Report — slides up over History (iOS's fullScreenCover).
        AnimatedVisibility(
            visible = reportSession != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
        ) {
            reportSession?.let { SessionReportScreen(session = it, onClose = { reportSession = null }) }
        }
    }
}

// MARK: - Calendar pane

@Composable
private fun CalendarPane(
    sessionsByDate: Map<LocalDate, WorkoutSessionResponse>,
    onOpenSession: (WorkoutSessionResponse) -> Unit,
) {
    var displayedMonth by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(LocalDate.now()) }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 32.dp)
    ) {
        CalendarCard(displayedMonth, selectedDate, sessionsByDate,
            onPrev = { displayedMonth = displayedMonth.minusMonths(1) },
            onNext = { displayedMonth = displayedMonth.plusMonths(1) },
            onSelect = { selectedDate = it })

        val date = selectedDate
        AnimatedVisibility(visible = date != null) {
            if (date != null) {
                val session = sessionsByDate[date]
                if (session != null) SessionSummaryCard(session, onClick = { onOpenSession(session) })
                else NoSessionCard(date)
            }
        }
    }
}

@Composable
private fun CalendarCard(
    displayedMonth: LocalDate,
    selectedDate: LocalDate?,
    sessionsByDate: Map<LocalDate, WorkoutSessionResponse>,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    val today = LocalDate.now()
    val canGoForward = displayedMonth.isBefore(today.withDayOfMonth(1))
    val monthFmt = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH) }

    // Cells: leading blanks (Monday-based) + each day of the month.
    val cells = remember(displayedMonth) {
        val firstOfMonth = displayedMonth.withDayOfMonth(1)
        val blanks = firstOfMonth.dayOfWeek.value - 1 // Monday=1 → 0 blanks
        val list = ArrayList<LocalDate?>()
        repeat(blanks) { list.add(null) }
        for (d in 1..displayedMonth.lengthOfMonth()) list.add(displayedMonth.withDayOfMonth(d))
        while (list.size % 7 != 0) list.add(null)
        list
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardFill).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month", tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onPrev).padding(4.dp))
            Spacer(modifier = Modifier.weight(1f))
            Text(displayedMonth.format(monthFmt), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ChevronRight, contentDescription = "Next month", tint = if (canGoForward) Color.White.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.2f),
                modifier = Modifier.size(32.dp).clip(CircleShape).clickable(enabled = canGoForward, onClick = onNext).padding(4.dp))
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { label ->
                Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.35f), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }

        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (date != null) {
                            HistoryDayCell(
                                date = date,
                                hasSession = sessionsByDate.containsKey(date),
                                isSelected = selectedDate == date,
                                isToday = date == today,
                                isFuture = date.isAfter(today),
                                onClick = { if (!date.isAfter(today)) onSelect(date) },
                            )
                        } else {
                            Spacer(modifier = Modifier.height(37.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryDayCell(date: LocalDate, hasSession: Boolean, isSelected: Boolean, isToday: Boolean, isFuture: Boolean, onClick: () -> Unit) {
    val textColor = when {
        isFuture -> Color.White.copy(alpha = 0.2f)
        isSelected -> Color.Black
        else -> Color.White.copy(alpha = if (isToday) 1f else 0.75f)
    }
    val bg = when {
        isSelected -> Color.White
        isToday -> Color.White.copy(alpha = 0.15f)
        else -> Color.Transparent
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
            Text("${date.dayOfMonth}", fontSize = 14.sp, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal, color = textColor)
        }
        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(if (hasSession) TrackstarAccent else Color.Transparent))
    }
}

// MARK: - Session summary (calendar-selected day)

@Composable
private fun SessionSummaryCard(session: WorkoutSessionResponse, onClick: () -> Unit) {
    val date = session.localDate
    val fullFmt = remember { DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH) }
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardFill).clickable(onClick = onClick).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF34C759), modifier = Modifier.size(18.dp))
            Text(date?.format(fullFmt) ?: "", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(16.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatPill(Icons.Filled.Timer, formatDuration(session.durationSecondsOrData))
            StatPill(Icons.Filled.CheckCircle, "${session.completedSets}/${session.totalSets} sets")
            StatPill(Icons.Filled.FitnessCenter, "${session.exerciseSummaries.size} exercises")
        }
        Text(session.exerciseSummaries.joinToString(" · ") { it.name }, fontSize = 13.sp, color = Color.White.copy(alpha = 0.45f), maxLines = 2)
    }
}

@Composable
private fun NoSessionCard(date: LocalDate) {
    val fmt = remember { DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardFill).padding(16.dp)
    ) {
        Icon(Icons.Filled.NightsStay, contentDescription = null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
        Text("No session on ${date.format(fmt)}", fontSize = 14.sp, color = Color.White.copy(alpha = 0.4f))
    }
}

@Composable
private fun StatPill(icon: ImageVector, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.1f)).padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.65f), modifier = Modifier.size(11.dp))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.65f))
    }
}

// MARK: - Week list (iOS AthleteHistoryTabView)

@Composable
private fun SessionsWeekList(viewModel: StatsViewModel, onOpenSession: (WorkoutSessionResponse) -> Unit) {
    val today = LocalDate.now()
    val thisWeekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
    var weekStart by remember { mutableStateOf(thisWeekStart) }
    val isCurrentWeek = weekStart == thisWeekStart
    val fmt = remember { DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH) }
    val byDate = remember(viewModel.sessions) { viewModel.sessions.mapNotNull { s -> s.localDate?.let { it to s } }.toMap() }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 16.dp)
        ) {
            for (i in 0..6) {
                val date = weekStart.plusDays(i.toLong())
                val session = byDate[date]
                if (session != null) WeekSessionCard(date, session, onClick = { onOpenSession(session) })
                else RestDayRow(date, isFuture = date.isAfter(today))
            }
        }
        // Week nav bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 16.dp).height(56.dp)
                .clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.08f))
        ) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous week", tint = Color.White,
                modifier = Modifier.fillMaxHeight().clickable { weekStart = weekStart.minusWeeks(1) }.padding(horizontal = 24.dp))
            Spacer(modifier = Modifier.weight(1f))
            Text("${weekStart.format(fmt)} – ${weekStart.plusDays(6).format(fmt)}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ChevronRight, contentDescription = "Next week", tint = if (isCurrentWeek) Color.White.copy(alpha = 0.2f) else Color.White,
                modifier = Modifier.fillMaxHeight().clickable(enabled = !isCurrentWeek) { weekStart = weekStart.plusWeeks(1) }.padding(horizontal = 24.dp))
        }
    }
}

@Composable
private fun WeekSessionCard(date: LocalDate, session: WorkoutSessionResponse, onClick: () -> Unit) {
    val color = dayColor(date.dayOfWeek)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardFill).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.width(40.dp)) {
            Text(date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH).uppercase(Locale.ENGLISH), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
            Text("${date.dayOfMonth}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Box(modifier = Modifier.width(2.dp).height(38.dp).clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.5f)))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(session.sessionData?.title?.ifBlank { "Workout" } ?: "Workout", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallChip(Icons.Filled.Timer, formatDuration(session.durationSecondsOrData))
                SmallChip(Icons.Filled.CheckCircle, "${session.completedSets}/${session.totalSets} sets")
                SmallChip(Icons.Filled.FitnessCenter, "${session.exerciseSummaries.size} ex")
            }
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.25f), modifier = Modifier.size(12.dp))
    }
}

@Composable
private fun RestDayRow(date: LocalDate, isFuture: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.04f)).padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.width(40.dp)) {
            Text(date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH).uppercase(Locale.ENGLISH), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.25f))
            Text("${date.dayOfMonth}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.25f))
        }
        Box(modifier = Modifier.width(2.dp).height(30.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)))
        Text(if (isFuture) "Upcoming" else "Rest day", fontSize = 14.sp, color = Color.White.copy(alpha = 0.25f))
    }
}

@Composable
private fun SmallChip(icon: ImageVector, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.size(10.dp))
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.55f))
    }
}

// MARK: - Helpers

private fun dayColor(day: DayOfWeek): Color = when (day) {
    DayOfWeek.MONDAY -> Color(0xFF0A84FF)
    DayOfWeek.TUESDAY -> Color(0xFFFF9F0A)
    DayOfWeek.WEDNESDAY -> Color(0xFFFFD60A)
    DayOfWeek.THURSDAY -> Color(0xFF34C759)
    DayOfWeek.FRIDAY -> Color(0xFF64D2FF)
    DayOfWeek.SATURDAY -> Color(0xFFAF52DE)
    DayOfWeek.SUNDAY -> Color(0xFFFF375F)
}

private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600; val m = (seconds % 3600) / 60; val s = seconds % 60
    return if (h > 0) String.format(Locale.ENGLISH, "%d:%02d:%02d", h, m, s) else String.format(Locale.ENGLISH, "%d:%02d", m, s)
}

private val WorkoutSessionResponse.durationSecondsOrData: Int
    get() = durationSeconds ?: sessionData?.durationSeconds ?: 0
private val WorkoutSessionResponse.exerciseSummaries
    get() = sessionData?.exercises.orEmpty()
