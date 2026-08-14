package com.vasilisneo.trackstar.ui.screens.main.coach

// Ports iOS's AthletesView: the coach's roster (the MyTeam tab). Collapsing frosted header (same
// pattern as DietScreen — fixed nav bar above a list, frost fades in only once the large title has
// scrolled off, opaque so content doesn't bleed through), a large "MyTeam" title with an active
// count, and athlete cards showing a colored avatar, name/email and this week's planned/done pills.
// The + (add athlete) and templates buttons are wired to callbacks (Phase 2/4); tapping a card
// opens the athlete detail (Phase 3).

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vasilisneo.trackstar.data.api.ProfileResponse
import com.vasilisneo.trackstar.data.api.SlotResponse
import com.vasilisneo.trackstar.ui.components.GlassCircleIconButton
import com.vasilisneo.trackstar.ui.components.ProfileAvatarButton
import com.vasilisneo.trackstar.ui.theme.TrackstarAccent
import com.vasilisneo.trackstar.ui.theme.currentAppTheme
import com.vasilisneo.trackstar.ui.theme.trackstarBackground

private val HeaderTint = Color(0xFF3B3B46)
private val CardFill = Color.White.copy(alpha = 0.06f)
private val AvatarPalette = listOf(
    Color(0xFF0A84FF), Color(0xFFAF52DE), Color(0xFF34C759), Color(0xFFFF9F0A),
    Color(0xFFFF375F), Color(0xFF30B0C7), Color(0xFF5E5CE6), Color(0xFF64D2FF),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AthletesScreen(
    onProfileClick: () -> Unit = {},
    onAthleteClick: (ProfileResponse) -> Unit = {},
    onAddAthlete: () -> Unit = {},
    onShowTemplates: () -> Unit = {},
    onShowAvailability: () -> Unit = {},
    onSeeAll: () -> Unit = {},
    onOpenBookingSettings: () -> Unit = {},
    showAvailability: Boolean = false,
    viewModel: AthletesViewModel = viewModel(),
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val collapse by remember {
        derivedStateOf {
            val title = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }
                ?: return@derivedStateOf 1f
            val h = title.size.toFloat()
            if (h <= 0f) 0f else ((-title.offset).toFloat() / h).coerceIn(0f, 1f)
        }
    }
    val pinned = collapse > 0.9f
    val frostProgress by animateFloatAsState(targetValue = if (pinned) 1f else 0f, animationSpec = tween(200), label = "frost")
    val headerFill = HeaderTint.copy(alpha = 0.82f).compositeOver(currentAppTheme.gradientTop.compositeOver(Color.Black))

    val athletes = viewModel.athletes
    var athleteToRemove by remember { mutableStateOf<ProfileResponse?>(null) }
    val bookingBannerDismissed = com.vasilisneo.trackstar.ui.util.rememberBooleanPref("bookingBannerDismissed", false)
    // Quick info occupies a fixed slice of the screen height regardless of how little it contains.
    val quickInfoHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.28f).dp

    // Re-fetch when returning to the roster (e.g. after adding an athlete), so a new athlete shows.
    var skipFirstResume by remember { mutableStateOf(true) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        if (skipFirstResume) skipFirstResume = false else viewModel.fetch()
        onPauseOrDispose { }
    }

    Box(modifier = Modifier.fillMaxSize().trackstarBackground()) {
        Text(
            "Trackstar", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.05f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = com.vasilisneo.trackstar.ui.components.tabWatermarkBottomPadding())
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Fixed nav bar above the list; frost fades in once the title has scrolled off.
            Column(modifier = Modifier.fillMaxWidth().background(headerFill.copy(alpha = frostProgress)).statusBarsPadding()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp)) {
                    ProfileAvatarButton(initials = viewModel.userInitials, onClick = onProfileClick)
                    Spacer(modifier = Modifier.size(12.dp))
                    Text("Team", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.alpha(frostProgress))
                    Spacer(modifier = Modifier.weight(1f))
                }
            }

            CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = com.vasilisneo.trackstar.ui.components.tabBarContentBottomPadding()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    // Top block: the "coming up" booking card when booking is on, otherwise a two-line
                    // team-stats summary. Extra vertical space is baked into these composables.
                    item {
                        Box(modifier = Modifier.fillMaxWidth().height(quickInfoHeight), contentAlignment = Alignment.Center) {
                            if (athletes.isEmpty()) {
                                // No roster to page through — show the standalone block.
                                if (showAvailability) {
                                    CoachQuickInfoCard(
                                        next = viewModel.nextUpcomingSession,
                                        finishedCount = viewModel.finishedSessionsCount,
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                    )
                                } else {
                                    CoachTeamStatsBlock(
                                        athletesToday = viewModel.athletesWithSessionTodayCount,
                                        planned = viewModel.plannedSessionsCount,
                                        done = viewModel.finishedSessionsCount,
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                    )
                                }
                            } else {
                                // Most useful first: athletes with a finished session, then those with
                                // a session planned today, then everyone else (stable within groups).
                                val summaries = viewModel.weeklySummaries
                                val pagerAthletes = athletes.sortedBy { a ->
                                    val s = a.id?.let { summaries[it] }
                                    when {
                                        (s?.completedCount ?: 0) > 0 -> 0
                                        s?.hasSessionToday == true -> 1
                                        else -> 2
                                    }
                                }
                                CoachAthletePager(
                                    leadingComingUp = if (showAvailability) {
                                        {
                                            CoachQuickInfoCard(
                                                next = viewModel.nextUpcomingSession,
                                                finishedCount = viewModel.finishedSessionsCount,
                                            )
                                        }
                                    } else null,
                                    athletes = pagerAthletes,
                                    summaries = summaries,
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                )
                            }
                        }
                    }

                    // Action circles (moved out of the nav bar), kept close to the list below.
                    item {
                        CoachActionRow(
                            showSchedule = showAvailability,
                            onSchedule = onShowAvailability,
                            onTemplates = onShowTemplates,
                            onAdd = onAddAthlete,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }

                    // Booking promo below the buttons — only when booking is off and not dismissed.
                    if (!showAvailability && !bookingBannerDismissed.value) {
                        item {
                            BookingBanner(
                                onOpen = onOpenBookingSettings,
                                onDismiss = { bookingBannerDismissed.value = true },
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }

                    if (viewModel.isLoading && athletes.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = TrackstarAccent)
                            }
                        }
                    } else if (athletes.isEmpty()) {
                        item { com.vasilisneo.trackstar.ui.components.OfflineBanner() }
                        item { EmptyState() }
                    } else {
                        item {
                            Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 4.dp)) {
                                com.vasilisneo.trackstar.ui.components.OfflineBanner()
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Team", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("· ${athletes.size} active", fontSize = 16.sp, color = Color.White.copy(alpha = 0.5f))
                                }
                            }
                        }
                        item {
                            TeamCard(
                                athletes = athletes,
                                summaries = viewModel.weeklySummaries,
                                onSeeAll = onSeeAll,
                                onAthleteClick = onAthleteClick,
                                onAthleteLongClick = { athleteToRemove = it },
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                }
            }
        }

    }

    athleteToRemove?.let { athlete ->
        AlertDialog(
            onDismissRequest = { athleteToRemove = null },
            containerColor = Color(0xFF1A1A26),
            title = { Text("Remove Athlete?", color = Color.White) },
            text = { Text("${athlete.fullName} will be removed from your team.", color = Color.White.copy(alpha = 0.7f)) },
            confirmButton = {
                TextButton(onClick = { athlete.id?.let { viewModel.removeAthlete(it) }; athleteToRemove = null }) {
                    Text("Remove", color = Color(0xFFFF453A))
                }
            },
            dismissButton = { TextButton(onClick = { athleteToRemove = null }) { Text("Cancel", color = Color.White) } },
        )
    }
}

// Full roster, pushed from the MyTeam "Show all" button — every athlete in one scrolling card.
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AllAthletesScreen(
    onBack: () -> Unit = {},
    onAthleteClick: (ProfileResponse) -> Unit = {},
    viewModel: AthletesViewModel = viewModel(),
) {
    val athletes = viewModel.athletes
    var athleteToRemove by remember { mutableStateOf<ProfileResponse?>(null) }

    Box(modifier = Modifier.fillMaxSize().trackstarBackground()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp)) {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Back", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.size(4.dp))
                    Text("Team", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.weight(1f))
                }
            }

            CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardFill)) {
                            athletes.forEach { athlete ->
                                AthleteRow(
                                    athlete = athlete,
                                    summary = athlete.id?.let { viewModel.weeklySummaries[it] },
                                    onClick = { onAthleteClick(athlete) },
                                    onLongClick = { athleteToRemove = athlete },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    athleteToRemove?.let { athlete ->
        AlertDialog(
            onDismissRequest = { athleteToRemove = null },
            containerColor = Color(0xFF1A1A26),
            title = { Text("Remove athlete?", color = Color.White) },
            text = { Text("${athlete.fullName} will be removed from your team.", color = Color.White.copy(alpha = 0.7f)) },
            confirmButton = {
                TextButton(onClick = { athlete.id?.let { viewModel.removeAthlete(it) }; athleteToRemove = null }) {
                    Text("Remove", color = Color(0xFFFF453A))
                }
            },
            dismissButton = { TextButton(onClick = { athleteToRemove = null }) { Text("Cancel", color = Color.White) } },
        )
    }
}

// Coach "at a glance": next booked session + finished-sessions count.
// Card-less, centred "at a glance": the next booked session on top, finished-sessions count below.
@Composable
private fun CoachQuickInfoCard(next: SlotResponse?, finishedCount: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("COMING UP", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = Color.White.copy(alpha = 0.45f))
        if (next != null) {
            Text(
                "${relativeDay(next.date)} · ${displayTime(next.startTime)}",
                fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center,
            )
            val who = next.attendees?.mapNotNull { it.name?.ifBlank { null } }?.firstOrNull()
            Text(
                listOfNotNull(next.title?.ifBlank { null } ?: "Session", who?.let { "with $it" }).joinToString(" · "),
                fontSize = 14.sp, color = Color.White.copy(alpha = 0.55f), textAlign = TextAlign.Center,
            )
        } else {
            Text("No upcoming booked sessions", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
            Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF34C759), modifier = Modifier.size(15.dp))
            Text(
                "$finishedCount finished session${if (finishedCount == 1) "" else "s"} this week",
                fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

// Shown instead of the "coming up" card when the coach has booking disabled — two centred lines: how
// many athletes train today, then the week's planned/done totals.
@Composable
private fun CoachTeamStatsBlock(athletesToday: Int, planned: Int, done: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (athletesToday == 1) "1 athlete has a session today" else "$athletesToday athletes have sessions today",
            fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Planned $planned", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.6f))
            Text("|", fontSize = 14.sp, color = Color.White.copy(alpha = 0.25f))
            Text("Done $done", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.6f))
        }
    }
}

// Swipeable carousel in the quick-info area: an optional leading "coming up" page (shown when booking
// is on) followed by one page per athlete (avatar, name, today indicator, week's planned/done), with
// a dot indicator below.
@Composable
private fun CoachAthletePager(
    leadingComingUp: (@Composable () -> Unit)?,
    athletes: List<ProfileResponse>,
    summaries: Map<String, AthleteWeeklySummary>,
    modifier: Modifier = Modifier,
) {
    val hasLead = leadingComingUp != null
    val leadCount = if (hasLead) 1 else 0
    val realCount = athletes.size + leadCount
    // Loop past the last page back to the first: with >1 page, run a huge virtual count and map each
    // virtual page to a real one by modulo, starting in the middle so it wraps both directions.
    val infinite = realCount > 1
    val startPage = remember(realCount) { if (infinite) (Int.MAX_VALUE / 2).let { it - it % realCount } else 0 }
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = startPage,
        pageCount = { if (infinite) Int.MAX_VALUE else realCount },
    )
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) { page ->
            val real = page % realCount
            if (hasLead && real == 0) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { leadingComingUp!!() }
            } else {
                val athlete = athletes[real - leadCount]
                AthletePagerPage(athlete, athlete.id?.let { summaries[it] })
            }
        }
        Spacer(Modifier.height(10.dp))
        val current = pagerState.currentPage % realCount
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 14.dp)) {
            repeat(realCount) { i ->
                val selected = i == current
                Box(
                    Modifier.size(if (selected) 7.dp else 6.dp).clip(CircleShape)
                        .background(if (selected) Color.White else Color.White.copy(alpha = 0.3f))
                )
            }
        }
    }
}

@Composable
private fun AthletePagerPage(athlete: ProfileResponse, summary: AthleteWeeklySummary?) {
    val name = athlete.fullName
    val color = remember(name) { AvatarPalette[(name.sumOf { it.code }).mod(AvatarPalette.size)] }
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(color.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
            Text(athlete.athleteInitials, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(Modifier.height(10.dp))
        Text(name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
        if (summary?.hasSessionToday == true) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Filled.EventAvailable, null, tint = Color(0xFF34C759), modifier = Modifier.size(13.dp))
                Text("Session today", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF34C759))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "${summary?.plannedCount ?: 0} planned · ${summary?.completedCount ?: 0} done",
            fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f),
        )
    }
}

// Booking promo — shown to a coach who has Session Booking off. Tapping opens Settings; the X dismisses.
@Composable
private fun BookingBanner(onOpen: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.06f)).clickable(onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Session Booking", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text(
                    "Let your athletes book sessions with you. Turn it on in Settings.",
                    fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f), lineHeight = 17.sp,
                )
            }
            Spacer(Modifier.size(12.dp))
            Icon(Icons.Filled.CalendarMonth, null, tint = TrackstarAccent, modifier = Modifier.size(34.dp))
        }
        Box(
            modifier = Modifier.align(Alignment.TopEnd).clip(CircleShape).clickable(onClick = onDismiss).padding(10.dp),
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.size(14.dp))
        }
    }
}

private fun relativeDay(iso: String): String {
    val d = runCatching { java.time.LocalDate.parse(iso) }.getOrNull() ?: return iso
    val today = java.time.LocalDate.now()
    return when (d) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> d.format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM"))
    }
}

@Composable
private fun CoachActionRow(showSchedule: Boolean, onSchedule: () -> Unit, onTemplates: () -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircleAction(Icons.Filled.PersonAddAlt1, "Add", onAdd, Modifier.weight(1f))
        CircleAction(Icons.Filled.ContentCopy, "Templates", onTemplates, Modifier.weight(1f))
        if (showSchedule) CircleAction(Icons.Filled.CalendarMonth, "Schedule", onSchedule, Modifier.weight(1f))
    }
}

@Composable
private fun CircleAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // clickable lives on the clipped circle so the press ripple is round (clip before clickable),
    // not a square flash spanning the whole icon+label column.
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.size(8.dp))
        Text(label, fontSize = 13.sp, color = Color.White.copy(alpha = 0.9f))
    }
}

// The whole roster in one rounded card (Revolut-style list): up to 3 rows visible, the rest behind
// a "Show all" toggle at the bottom. Tap a row to open the athlete; long-press to remove.
// "Show all" toggle at the bottom. Tap a row to open the athlete; long-press to remove.
@Composable
private fun TeamCard(
    athletes: List<ProfileResponse>,
    summaries: Map<String, AthleteWeeklySummary>,
    onSeeAll: () -> Unit,
    onAthleteClick: (ProfileResponse) -> Unit,
    onAthleteLongClick: (ProfileResponse) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardFill)) {
        athletes.take(3).forEach { athlete ->
            AthleteRow(
                athlete = athlete,
                summary = athlete.id?.let { summaries[it] },
                onClick = { onAthleteClick(athlete) },
                onLongClick = { onAthleteLongClick(athlete) },
            )
        }
        if (athletes.size > 3) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onSeeAll).padding(vertical = 15.dp),
            ) {
                Text(
                    "Show all",
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TrackstarAccent,
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun AthleteRow(
    athlete: ProfileResponse,
    summary: AthleteWeeklySummary?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val name = athlete.fullName
    val color = remember(name) { AvatarPalette[(name.sumOf { it.code }).mod(AvatarPalette.size)] }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(46.dp).clip(CircleShape).background(color.copy(alpha = 0.25f))) {
            Text(athlete.athleteInitials, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
            if (summary != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryStat(Icons.Filled.CalendarMonth, "${summary.plannedCount} planned", Color.White.copy(alpha = 0.55f))
                    SummaryStat(
                        Icons.Filled.CheckCircle, "${summary.completedCount} done",
                        if (summary.completedCount > 0) Color(0xFF34C759) else Color.White.copy(alpha = 0.35f),
                    )
                }
            } else {
                // Reserve the pills' height while summaries load, so the row doesn't grow (and shove
                // everything below it) when the counts arrive.
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(width = 64.dp, height = 15.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.06f)))
                    Box(Modifier.size(width = 48.dp, height = 15.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.06f)))
                }
            }
        }
    }
}

// Plain inline stat (icon + text) — no chip background.
@Composable
private fun SummaryStat(icon: ImageVector, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = color)
    }
}


@Composable
private fun EmptyState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 60.dp, start = 40.dp, end = 40.dp)
    ) {
        Icon(Icons.Filled.Groups, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(60.dp))
        Text("No Athletes Yet", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(
            "Tap the + button to add an athlete by their email address.",
            fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
