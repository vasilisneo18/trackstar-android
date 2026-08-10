package com.vasilisneo.trackstar.ui.screens.main.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vasilisneo.trackstar.data.api.SlotResponse
import com.vasilisneo.trackstar.ui.components.GlassCircleIconButton
import com.vasilisneo.trackstar.ui.screens.main.plan.DayTabPill
import com.vasilisneo.trackstar.ui.screens.main.plan.WeekNavigationBar
import com.vasilisneo.trackstar.ui.screens.main.plan.formattedWeekRange
import com.vasilisneo.trackstar.ui.theme.TrackstarAccent
import com.vasilisneo.trackstar.ui.theme.trackstarBackground

private val CardFill = Color.White.copy(alpha = 0.06f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachAvailabilityScreen(
    onBack: () -> Unit = {},
    viewModel: CoachAvailabilityViewModel = viewModel(),
) {
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SlotResponse?>(null) }
    var cancelling by remember { mutableStateOf<SlotResponse?>(null) }
    var deletingSeries by remember { mutableStateOf<SlotResponse?>(null) }
    var calendarMode by remember { mutableStateOf(false) }
    var showDaySheet by remember { mutableStateOf(false) } // "show all" for the selected day in calendar mode

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel.errorMessage) {
        viewModel.errorMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }
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
                Spacer(Modifier.width(10.dp))
                GlassCircleIconButton(onClick = { showAdd = true }, icon = Icons.Filled.Add, contentDescription = "Add session")
            }
            Text("Schedule", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))

            val daySlots = viewModel.slotsForSelectedDay
            if (calendarMode) {
                MonthCalendar(
                    selectedDate = viewModel.selectedDate,
                    sessionCount = viewModel::sessionCountOn,
                    onSelectDate = viewModel::selectDate,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
                // The selected day's first sessions below the calendar; scrolls if they don't all fit,
                // and "show all" opens the rest in a sheet.
                Column(
                    Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (daySlots.isEmpty()) {
                        Text("No sessions on ${prettyDate(viewModel.selectedDate.toString())}.",
                            fontSize = 13.sp, color = Color.White.copy(alpha = 0.4f), modifier = Modifier.padding(top = 12.dp))
                    } else {
                        daySlots.take(2).forEach { slot ->
                            CoachSessionRow(
                                slot = slot,
                                onEdit = { editing = slot },
                                onCancel = { cancelling = slot },
                                onDeletePermanently = { deletingSeries = slot },
                            )
                        }
                        if (daySlots.size > 2) {
                            ShowMoreButton("Show all ${daySlots.size} sessions") { showDaySheet = true }
                        }
                    }
                }
            } else {
                // Day tabs — same expand/collapse pills as the weekly plan; a dot marks days with slots.
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
                    viewModel.isLoading && viewModel.slots.isEmpty() ->
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
                                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                                ) {
                                    Icon(Icons.Filled.EventBusy, contentDescription = null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(46.dp))
                                    Text("No sessions", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f))
                                    Text("Add availability for this day.", fontSize = 13.sp, color = Color.White.copy(alpha = 0.3f))
                                }
                            }
                        }
                        items(daySlots, key = { it.id }) { slot ->
                            CoachSessionRow(
                                slot = slot,
                                onEdit = { editing = slot },
                                onCancel = { cancelling = slot },
                                onDeletePermanently = { deletingSeries = slot },
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

    if (showAdd) {
        AddSlotSheet(
            dayLabel = viewModel.selectedDay.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH),
            onDismiss = { showAdd = false },
            onAdd = { start, end, capacity, title, repeatWeeks ->
                viewModel.addSlot(start, end, capacity, title, null, repeatWeeks) { ok -> if (ok) showAdd = false }
            },
        )
    }

    editing?.let { slot ->
        EditSlotSheet(
            slot = slot,
            onDismiss = { editing = null },
            onSave = { start, end, capacity, title ->
                viewModel.editSlot(slot.id, slot.date, start, end, capacity, title) { ok -> if (ok) editing = null }
            },
        )
    }

    cancelling?.let { slot ->
        val hasBookings = slot.capacity - slot.remaining > 0
        ConfirmDialog(
            title = "Cancel this session?",
            message = "The session on ${prettyDate(slot.date)} at ${slot.startTime} will be removed" +
                if (hasBookings) " and booked athletes will be notified." else ".",
            confirmText = "Cancel session",
            onConfirm = { viewModel.deleteSlot(slot.id); cancelling = null },
            onDismiss = { cancelling = null },
        )
    }

    deletingSeries?.let { slot ->
        ConfirmDialog(
            title = "Delete permanently?",
            message = "This session and all future weeks in its series will be removed. Any booked athletes will be notified.",
            confirmText = "Delete all",
            onConfirm = { viewModel.deleteSlotSeries(slot.id); deletingSeries = null },
            onDismiss = { deletingSeries = null },
        )
    }

    if (showDaySheet) {
        val slots = viewModel.slotsForSelectedDay
        DaySessionsSheet(
            title = prettyDate(viewModel.selectedDate.toString()),
            slots = slots,
            onEdit = { showDaySheet = false; editing = it },
            onCancel = { showDaySheet = false; cancelling = it },
            onDeletePermanently = { showDaySheet = false; deletingSeries = it },
            onDismiss = { showDaySheet = false },
        )
    }
}

// Time label + coach session card — one session block, reused in the week list, the calendar summary,
// and the "show all" day sheet.
@Composable
private fun CoachSessionRow(slot: SlotResponse, onEdit: () -> Unit, onCancel: () -> Unit, onDeletePermanently: () -> Unit) {
    Column {
        Text(
            "${displayTime(slot.startTime)} – ${displayTime(slot.endTime)}",
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        CoachSlotCard(
            title = slot.title,
            booked = slot.bookedByCount(),
            capacity = slot.capacity,
            attendees = slot.attendees?.mapNotNull { it.name?.ifBlank { null } } ?: emptyList(),
            onEdit = onEdit,
            onCancel = onCancel,
            onDeletePermanently = onDeletePermanently,
        )
    }
}

@Composable
private fun ShowMoreButton(text: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TrackstarAccent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DaySessionsSheet(
    title: String,
    slots: List<SlotResponse>,
    onEdit: (SlotResponse) -> Unit,
    onCancel: (SlotResponse) -> Unit,
    onDeletePermanently: (SlotResponse) -> Unit,
    onDismiss: () -> Unit,
) {
    // Locked: no swipe/scrim dismiss (confirmValueChange blocks Hidden, no drag handle) — the close
    // button is the only way out.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { it != SheetValue.Hidden })
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, dragHandle = null, containerColor = com.vasilisneo.trackstar.ui.theme.TrackstarSurface) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(horizontal = 16.dp).padding(top = 14.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f).padding(start = 4.dp))
                GlassCircleIconButton(onClick = onDismiss, icon = Icons.Filled.Close, contentDescription = "Close")
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                items(slots, key = { it.id }) { slot ->
                    CoachSessionRow(
                        slot = slot,
                        onEdit = { onEdit(slot) },
                        onCancel = { onCancel(slot) },
                        onDeletePermanently = { onDeletePermanently(slot) },
                    )
                }
            }
        }
    }
}

// "2026-08-11" -> "Tue, 11 Aug"
private fun prettyDate(iso: String): String = runCatching {
    java.time.LocalDate.parse(iso).format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM"))
}.getOrDefault(iso)

@Composable
private fun ConfirmDialog(title: String, message: String, confirmText: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A26),
        title = { Text(title, color = Color.White) },
        text = { Text(message, color = Color.White.copy(alpha = 0.75f)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText, color = Color(0xFFE5484D)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep", color = Color.White.copy(alpha = 0.8f)) } },
    )
}

private fun SlotResponse.bookedByCount(): Int = capacity - remaining

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSlotSheet(
    slot: SlotResponse,
    onDismiss: () -> Unit,
    onSave: (start: String, end: String, capacity: Int, title: String) -> Unit,
) {
    var start by remember { mutableStateOf(slot.startTime) }
    var end by remember { mutableStateOf(slot.endTime) }
    var endEdited by remember { mutableStateOf(true) } // existing session — keep the end unless it changes it
    var capacity by remember { mutableIntStateOf(slot.capacity) }
    var title by remember { mutableStateOf(slot.title ?: "") }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    // Capacity can't drop below the number of athletes already booked on this session.
    val minCapacity = maxOf(1, slot.bookedByCount())

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = com.vasilisneo.trackstar.ui.theme.TrackstarSurface) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.75f).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 24.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Edit session · ${prettyDate(slot.date)}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)

            com.vasilisneo.trackstar.ui.components.AuthTextField(value = title, onValueChange = { title = it }, placeholder = "Session title, e.g. PT session")

            FieldRow(label = "Start", value = displayTime(start), onClick = { pickStart = true })
            FieldRow(label = "End", value = displayTime(end), onClick = { pickEnd = true })

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Capacity", color = Color.White.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
                StepperButton("−") { if (capacity > minCapacity) capacity-- }
                Text("$capacity", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
                StepperButton("+") { capacity++ }
            }
            Text(
                if (minCapacity > 1) "$minCapacity already booked" else if (capacity == 1) "One-on-one session" else "Group session ($capacity spots)",
                fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f),
            )

            Spacer(Modifier.weight(1f))
            com.vasilisneo.trackstar.ui.components.AuthCapsuleButton(
                text = "Save changes",
                onClick = { onSave(start, end, capacity, title) },
                enabled = start < end,
            )
            if (start >= end) Text("End time must be after start time", fontSize = 12.sp, color = Color(0xFFE5484D))
        }
    }

    if (pickStart) TimePickerSheet(initial = start, onDismiss = { pickStart = false },
        onPick = { start = it; end = nextEnd(it, end, endEdited); pickStart = false })
    if (pickEnd) TimePickerSheet(initial = end, minExclusive = start, onDismiss = { pickEnd = false },
        onPick = { end = it; endEdited = true; pickEnd = false })
}

@Composable
private fun CoachSlotCard(
    title: String?,
    booked: Int,
    capacity: Int,
    attendees: List<String>,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
    onDeletePermanently: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showAttendees by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardFill).padding(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title?.takeIf { it.isNotBlank() } ?: "Session",
                fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White,
            )
            Text(
                if (capacity == 1) (if (booked > 0) "Booked" else "Open") else "$booked/$capacity booked",
                fontSize = 12.sp, color = TrackstarAccent, modifier = Modifier.padding(top = 4.dp),
            )

            // Who booked — a single summary line ("Kostas and 3 others"); tap to open the full list.
            Spacer(Modifier.height(12.dp))
            if (attendees.isEmpty()) {
                Text("No bookings yet", fontSize = 12.sp, color = Color.White.copy(alpha = 0.35f))
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { showAttendees = true }
                        .padding(vertical = 2.dp),
                ) {
                    Box(
                        modifier = Modifier.size(24.dp).clip(CircleShape).background(TrackstarAccent.copy(alpha = 0.9f)),
                        contentAlignment = Alignment.Center,
                    ) { Text(initials(attendees.first()), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                    Spacer(Modifier.width(8.dp))
                    Text(attendeesSummary(attendees), fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f))
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(Modifier.width(8.dp))
        Box {
            Icon(Icons.Filled.MoreVert, contentDescription = "Session options", tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp).clip(CircleShape).clickable { menuOpen = true })
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = Color(0xFF1F1F2B),
                shape = RoundedCornerShape(16.dp),
            ) {
                DropdownMenuItem(
                    text = { Text("Edit session", color = Color.White) },
                    leadingIcon = { Icon(Icons.Filled.Edit, null, tint = Color.White.copy(alpha = 0.8f)) },
                    onClick = { menuOpen = false; onEdit() },
                )
                DropdownMenuItem(
                    text = { Text("Cancel this session", color = Color.White) },
                    leadingIcon = { Icon(Icons.Filled.EventBusy, null, tint = Color.White.copy(alpha = 0.8f)) },
                    onClick = { menuOpen = false; onCancel() },
                )
                DropdownMenuItem(
                    text = { Text("Delete permanently", color = Color(0xFFE5484D)) },
                    leadingIcon = { Icon(Icons.Filled.DeleteOutline, null, tint = Color(0xFFE5484D)) },
                    onClick = { menuOpen = false; onDeletePermanently() },
                )
            }
        }
    }

    if (showAttendees) AttendeesSheet(names = attendees, onDismiss = { showAttendees = false })
}

// ["Jane Doe", "Bob Fox", "Al Roy"] -> "Jane and 2 others"; single -> "Jane".
private fun attendeesSummary(names: List<String>): String {
    val first = names.first().trim().substringBefore(' ').ifBlank { names.first() }
    val rest = names.size - 1
    return if (rest <= 0) first else "$first and $rest other${if (rest == 1) "" else "s"}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttendeesSheet(names: List<String>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = com.vasilisneo.trackstar.ui.theme.TrackstarSurface) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Booked · ${names.size}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
            ) {
                items(names) { name ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(30.dp).clip(CircleShape).background(TrackstarAccent.copy(alpha = 0.9f)),
                            contentAlignment = Alignment.Center,
                        ) { Text(initials(name), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                        Spacer(Modifier.width(12.dp))
                        Text(name, fontSize = 15.sp, color = Color.White.copy(alpha = 0.9f))
                    }
                }
            }
        }
    }
}

// "Jane Doe" -> "JD", "madonna" -> "M".
private fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSlotSheet(
    dayLabel: String,
    onDismiss: () -> Unit,
    onAdd: (start: String, end: String, capacity: Int, title: String, repeatWeeks: Int) -> Unit,
) {
    var start by remember { mutableStateOf("09:00") }
    var end by remember { mutableStateOf("10:00") }
    var endEdited by remember { mutableStateOf(false) }
    var capacity by remember { mutableIntStateOf(1) }
    var title by remember { mutableStateOf("") }
    var recurring by remember { mutableStateOf(false) }
    var weeks by remember { mutableIntStateOf(8) }

    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = com.vasilisneo.trackstar.ui.theme.TrackstarSurface) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.75f).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 24.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("New session · $dayLabel", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)

            com.vasilisneo.trackstar.ui.components.AuthTextField(value = title, onValueChange = { title = it }, placeholder = "Session title, e.g. PT session")

            FieldRow(label = "Start", value = displayTime(start), onClick = { pickStart = true })
            FieldRow(label = "End", value = displayTime(end), onClick = { pickEnd = true })

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Capacity", color = Color.White.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
                StepperButton("−") { if (capacity > 1) capacity-- }
                Text("$capacity", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
                StepperButton("+") { capacity++ }
            }
            Text(if (capacity == 1) "One-on-one session" else "Group session ($capacity spots)",
                fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f))

            // Recurring: repeat this slot on the same weekday for N weeks.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Repeat weekly", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("Same day & time each week", fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f))
                }
                Switch(
                    checked = recurring, onCheckedChange = { recurring = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = TrackstarAccent),
                )
            }
            if (recurring) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(4, 8, 12).forEach { n ->
                        WeeksChip(label = "$n weeks", selected = weeks == n, modifier = Modifier.weight(1f)) { weeks = n }
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            com.vasilisneo.trackstar.ui.components.AuthCapsuleButton(
                text = if (recurring) "Add $weeks sessions" else "Add session",
                onClick = { onAdd(start, end, capacity, title, if (recurring) weeks else 1) },
                enabled = start < end,
            )
            if (start >= end) Text("End time must be after start time", fontSize = 12.sp, color = Color(0xFFE5484D))
        }
    }

    if (pickStart) TimePickerSheet(initial = start, onDismiss = { pickStart = false },
        onPick = { start = it; end = nextEnd(it, end, endEdited); pickStart = false })
    if (pickEnd) TimePickerSheet(initial = end, minExclusive = start, onDismiss = { pickEnd = false },
        onPick = { end = it; endEdited = true; pickEnd = false })
}

@Composable
private fun WeeksChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) TrackstarAccent else Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(label, color = if (selected) Color.White else Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerSheet(initial: String, minExclusive: String? = null, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val parts = initial.split(":")
    // 12-hour dial with an AM/PM toggle; state.hour is still 0–23 so we store "HH:mm".
    val state = rememberTimePickerState(initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 9, initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0, is24Hour = false)
    val selected = "%02d:%02d".format(state.hour, state.minute)
    // "Disable times before the start" — the dial can't grey out slots, so OK is disabled until the
    // selection is after the start time, which prevents committing an invalid end.
    val valid = minExclusive == null || selected > minExclusive
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(enabled = valid, onClick = { onPick(selected) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Column {
                TimePicker(state = state)
                if (!valid && minExclusive != null) {
                    Text("Must be after ${displayTime(minExclusive)}", color = Color(0xFFE5484D), fontSize = 12.sp)
                }
            }
        },
    )
}

// "09:00" -> "9:00 AM" for display; storage stays "HH:mm". Shared with the athlete booking screen.
internal fun displayTime(hhmm: String): String = runCatching {
    java.time.LocalTime.parse(hhmm).format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
}.getOrDefault(hhmm)

// "09:30" -> "10:30" (keeps minutes; no midnight-crossing sessions expected).
private fun plusOneHour(hhmm: String): String = runCatching {
    java.time.LocalTime.parse(hhmm).plusHours(1).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
}.getOrDefault(hhmm)

// End time when the start changes: default to start+1h until the coach edits the end themselves,
// and always bump it back to start+1h if a start change would leave the end at/before start.
private fun nextEnd(newStart: String, end: String, endEdited: Boolean): String =
    if (!endEdited || end <= newStart) plusOneHour(newStart) else end

@Composable
private fun FieldRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardFill).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(label, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StepperButton(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.1f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(symbol, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
}
