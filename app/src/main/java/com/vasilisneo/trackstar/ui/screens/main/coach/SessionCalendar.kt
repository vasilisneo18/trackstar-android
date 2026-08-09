package com.vasilisneo.trackstar.ui.screens.main.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
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
import com.vasilisneo.trackstar.ui.theme.TrackstarAccent
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

// A month grid that shows each day's session count and highlights the selected day. Month navigation
// is self-contained; tapping a day calls onSelectDate (which opens that day). Shared by the coach and
// athlete booking screens.
@Composable
internal fun MonthCalendar(
    selectedDate: LocalDate,
    sessionCount: (LocalDate) -> Int,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Re-initialised from the selected date each time the calendar appears (it's only composed in
    // calendar mode); within that session, the arrows drive the visible month.
    var month by remember { mutableStateOf(YearMonth.from(selectedDate)) }
    val today = LocalDate.now()

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        // Month header — chevrons sit in the first/last of 7 equal columns so they line up under the
        // Monday and Sunday day-number columns; the title fills the middle five.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.size(34.dp).clip(CircleShape).clickable { month = month.minusMonths(1) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
            Text(
                "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
                fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                textAlign = TextAlign.Center, modifier = Modifier.weight(5f),
            )
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.size(34.dp).clip(CircleShape).clickable { month = month.plusMonths(1) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Next month", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }

        // Weekday labels (Monday-first, matching the day tabs).
        Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp)) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { d ->
                Text(d, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }

        // Leading blanks so day 1 lands under its weekday, then the days, padded to full weeks.
        val lead = month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value // 0..6
        val cells = buildList<LocalDate?> {
            repeat(lead) { add(null) }
            for (d in 1..month.lengthOfMonth()) add(month.atDay(d))
            while (size % 7 != 0) add(null)
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (date != null) DayCell(
                            date = date,
                            selected = date == selectedDate,
                            isToday = date == today,
                            count = sessionCount(date),
                            onClick = { onSelectDate(date) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, selected: Boolean, isToday: Boolean, count: Int, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(CircleShape).clickable(onClick = onClick).padding(vertical = 2.dp),
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(CircleShape)
                .background(if (selected) TrackstarAccent else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${date.dayOfMonth}",
                fontSize = 15.sp,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    selected -> Color.White
                    isToday -> TrackstarAccent
                    else -> Color.White.copy(alpha = 0.85f)
                },
            )
        }
        // Session dot — only on unselected days (the selected day's circle already stands out). Row
        // height is reserved either way so weeks stay aligned.
        Box(modifier = Modifier.height(10.dp), contentAlignment = Alignment.Center) {
            if (count > 0 && !selected) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(TrackstarAccent))
            }
        }
    }
}
