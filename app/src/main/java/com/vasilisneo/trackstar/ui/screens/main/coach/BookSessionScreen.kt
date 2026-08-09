package com.vasilisneo.trackstar.ui.screens.main.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vasilisneo.trackstar.data.api.SlotResponse
import com.vasilisneo.trackstar.ui.components.GlassCircleIconButton
import com.vasilisneo.trackstar.ui.screens.main.plan.DayTabPill
import com.vasilisneo.trackstar.ui.screens.main.plan.WeekNavigationBar
import com.vasilisneo.trackstar.ui.screens.main.plan.formattedWeekRange
import com.vasilisneo.trackstar.ui.theme.TrackstarAccent
import com.vasilisneo.trackstar.ui.theme.trackstarBackground

private val CardFill = Color.White.copy(alpha = 0.06f)

@Composable
fun BookSessionScreen(
    onBack: () -> Unit = {},
    viewModel: BookSessionViewModel = viewModel(),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var withdrawing by remember { mutableStateOf<SlotResponse?>(null) }
    var calendarMode by remember { mutableStateOf(false) }
    var showDaySheet by remember { mutableStateOf(false) } // "show all" for the selected day in calendar mode
    LaunchedEffect(viewModel.errorMessage) {
        viewModel.errorMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }
    // Re-fetch when returning (e.g. after a booking elsewhere or a coach change) — skip the first
    // resume since init already fetched.
    val skipFirstResume = remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        if (skipFirstResume.value) skipFirstResume.value = false else viewModel.fetch()
        onPauseOrDispose { }
    }

    Box(modifier = Modifier.fillMaxSize().trackstarBackground()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                GlassCircleIconButton(onClick = onBack, icon = Icons.Filled.Close, contentDescription = "Close")
                Spacer(Modifier.weight(1f))
                GlassCircleIconButton(
                    onClick = { calendarMode = !calendarMode },
                    icon = if (calendarMode) Icons.Filled.ViewWeek else Icons.Filled.CalendarMonth,
                    contentDescription = if (calendarMode) "Week view" else "Calendar view",
                )
            }
            Text("Book a Session", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))

            val daySlots = viewModel.slotsForSelectedDay
            if (calendarMode) {
                MonthCalendar(
                    selectedDate = viewModel.selectedDate,
                    sessionCount = viewModel::sessionCountOn,
                    onSelectDate = viewModel::selectDate,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
                // The selected day's first session below the calendar; "show all" opens the rest in a sheet.
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (daySlots.isEmpty()) {
                        Text("No sessions on ${prettyBookDate(viewModel.selectedDate.toString())}.",
                            fontSize = 13.sp, color = Color.White.copy(alpha = 0.4f), modifier = Modifier.padding(top = 12.dp))
                    } else {
                        AthleteSessionRow(
                            slot = daySlots.first(),
                            busy = viewModel.busySlotId == daySlots.first().id,
                            onBook = { viewModel.book(daySlots.first().id) },
                            onWithdraw = { withdrawing = daySlots.first() },
                        )
                        if (daySlots.size > 1) {
                            ShowMoreBookButton("Show all ${daySlots.size} sessions") { showDaySheet = true }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
            } else {
                // Day tabs — same expand/collapse pills as the weekly plan; a dot marks days with sessions.
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 8.dp)) {
                    val gap = 6.dp
                    val inactiveW = 34.dp
                    val activeW = (maxWidth - inactiveW * 6 - gap * 6).coerceAtLeast(72.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth()) {
                        viewModel.weekDays.forEach { day ->
                            DayTabPill(
                                day = day.dayOfWeek,
                                isActive = day.dayOfWeek == viewModel.selectedDay,
                                hasExercises = viewModel.hasSlots(day.dayOfWeek),
                                activeWidth = activeW,
                                inactiveWidth = inactiveW,
                                onClick = { viewModel.goToDay(day) },
                            )
                        }
                    }
                }

                when {
                    viewModel.isLoading && viewModel.available.isEmpty() ->
                        Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) { CircularProgressIndicator(color = TrackstarAccent) }
                    else -> LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        if (daySlots.isEmpty()) {
                            item {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
                                ) {
                                    Icon(Icons.Filled.EventBusy, contentDescription = null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(46.dp))
                                    Text("No sessions", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f))
                                    Text("Your coach has no availability this day.", fontSize = 13.sp,
                                        color = Color.White.copy(alpha = 0.3f), textAlign = TextAlign.Center)
                                }
                            }
                        }
                        items(daySlots, key = { it.id }) { slot ->
                            AthleteSessionRow(
                                slot = slot,
                                busy = viewModel.busySlotId == slot.id,
                                onBook = { viewModel.book(slot.id) },
                                onWithdraw = { withdrawing = slot },
                            )
                        }
                    }
                }
            }

            if (!calendarMode) {
                WeekNavigationBar(
                    weekRange = formattedWeekRange(viewModel.weekStart),
                    onPrevious = viewModel::goToPreviousWeek,
                    onNext = viewModel::goToNextWeek,
                )
            }
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }

    if (showDaySheet) {
        AthleteDaySessionsSheet(
            title = prettyBookDate(viewModel.selectedDate.toString()),
            slots = viewModel.slotsForSelectedDay,
            busySlotId = viewModel.busySlotId,
            onBook = { viewModel.book(it.id) },
            onWithdraw = { showDaySheet = false; withdrawing = it },
            onDismiss = { showDaySheet = false },
        )
    }

    withdrawing?.let { slot ->
        AlertDialog(
            onDismissRequest = { withdrawing = null },
            containerColor = Color(0xFF1A1A26),
            title = { Text("Withdraw from session?", color = Color.White) },
            text = {
                Text(
                    "You'll give up your spot for ${displayTime(slot.startTime)} – ${displayTime(slot.endTime)}" +
                        (slot.coachName?.takeIf { it.isNotBlank() }?.let { " with $it" } ?: "") + ".",
                    color = Color.White.copy(alpha = 0.75f),
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.cancel(slot.id); withdrawing = null }) {
                    Text("Withdraw", color = Color(0xFFE5484D))
                }
            },
            dismissButton = {
                TextButton(onClick = { withdrawing = null }) { Text("Keep", color = Color.White.copy(alpha = 0.8f)) }
            },
        )
    }
}

// "2026-08-13" -> "Thu, 13 Aug"
private fun prettyBookDate(iso: String): String = runCatching {
    java.time.LocalDate.parse(iso).format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM"))
}.getOrDefault(iso)

// Time label + athlete session card — one session block, reused in the week list, the calendar
// summary, and the "show all" day sheet.
@Composable
private fun AthleteSessionRow(slot: SlotResponse, busy: Boolean, onBook: () -> Unit, onWithdraw: () -> Unit) {
    Column {
        Text(
            "${displayTime(slot.startTime)} – ${displayTime(slot.endTime)}",
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        AthleteSlotCard(slot = slot, busy = busy, onBook = onBook, onWithdraw = onWithdraw)
    }
}

@Composable
private fun ShowMoreBookButton(text: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TrackstarAccent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AthleteDaySessionsSheet(
    title: String,
    slots: List<SlotResponse>,
    busySlotId: String?,
    onBook: (SlotResponse) -> Unit,
    onWithdraw: (SlotResponse) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = com.vasilisneo.trackstar.ui.theme.TrackstarSurface) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(start = 4.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
            ) {
                items(slots, key = { it.id }) { slot ->
                    AthleteSessionRow(
                        slot = slot,
                        busy = busySlotId == slot.id,
                        onBook = { onBook(slot) },
                        onWithdraw = { onWithdraw(slot) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AthleteSlotCard(slot: SlotResponse, busy: Boolean, onBook: () -> Unit, onWithdraw: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardFill).padding(16.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(slot.title?.takeIf { it.isNotBlank() } ?: "Session", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White)
            slot.coachName?.takeIf { it.isNotBlank() }?.let { Text("with $it", fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f), modifier = Modifier.padding(top = 2.dp)) }
            if (slot.capacity > 1) {
                Text("${slot.remaining} spot${if (slot.remaining == 1) "" else "s"} left", fontSize = 12.sp, color = TrackstarAccent, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Spacer(Modifier.size(12.dp))
        when {
            busy -> CircularProgressIndicator(color = Color.White.copy(alpha = 0.7f), strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            // Booked: a status pill (not a hidden toggle) + a ⋮ menu with an explicit "Withdraw".
            slot.bookedByMe -> Row(verticalAlignment = Alignment.CenterVertically) {
                BookedPill()
                BookedMenu(onWithdraw = onWithdraw)
            }
            slot.full -> Text("Full", color = Color.White.copy(alpha = 0.4f), fontWeight = FontWeight.SemiBold)
            else -> BookingChip(text = "Book", onClick = onBook)
        }
    }
}

@Composable
private fun BookedPill() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
        Spacer(Modifier.size(6.dp))
        Text("Booked", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun BookedMenu(onWithdraw: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Icon(
            Icons.Filled.MoreVert, contentDescription = "Booking options", tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 4.dp).size(24.dp).clip(RoundedCornerShape(50)).clickable { open = true },
        )
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = Color(0xFF1F1F2B),
            shape = RoundedCornerShape(16.dp),
        ) {
            DropdownMenuItem(
                text = { Text("Withdraw from session", color = Color(0xFFE5484D)) },
                leadingIcon = { Icon(Icons.Filled.EventBusy, null, tint = Color(0xFFE5484D)) },
                onClick = { open = false; onWithdraw() },
            )
        }
    }
}

@Composable
private fun BookingChip(text: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(TrackstarAccent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}
