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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vasilisneo.trackstar.data.api.ProfileResponse
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

    // Re-fetch when returning to the roster (e.g. after adding an athlete), so a new athlete shows.
    var skipFirstResume by remember { mutableStateOf(true) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        if (skipFirstResume) skipFirstResume = false else viewModel.fetch()
        onPauseOrDispose { }
    }

    Box(modifier = Modifier.fillMaxSize().trackstarBackground()) {
        Text(
            "Trackstar", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.08f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Fixed nav bar above the list; frost fades in once the title has scrolled off.
            Column(modifier = Modifier.fillMaxWidth().background(headerFill.copy(alpha = frostProgress)).statusBarsPadding()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp)) {
                    ProfileAvatarButton(initials = viewModel.userInitials, onClick = onProfileClick)
                    Spacer(modifier = Modifier.size(12.dp))
                    Text("MyTeam", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.alpha(frostProgress))
                    Spacer(modifier = Modifier.weight(1f))
                    GlassCircleIconButton(onClick = onShowTemplates, contentDescription = "Templates", icon = Icons.Filled.ContentCopy)
                    Spacer(modifier = Modifier.size(10.dp))
                    GlassCircleIconButton(onClick = onAddAthlete, contentDescription = "Add athlete", icon = Icons.Filled.Add)
                }
            }

            CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = com.vasilisneo.trackstar.ui.components.tabBarContentBottomPadding()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 16.dp).alpha(1f - collapse)) {
                            Text(
                                if (viewModel.isLoading && athletes.isEmpty()) "" else "${athletes.size} active",
                                fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f),
                            )
                            Text("MyTeam", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    if (viewModel.isLoading && athletes.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = TrackstarAccent)
                            }
                        }
                    } else if (athletes.isEmpty()) {
                        item { EmptyState() }
                    } else {
                        items(athletes, key = { it.id ?: it.hashCode().toString() }) { athlete ->
                            SwipeRevealAthleteRow(
                                athlete = athlete,
                                summary = athlete.id?.let { viewModel.weeklySummaries[it] },
                                onOpen = { onAthleteClick(athlete) },
                                onRemoveTap = { athleteToRemove = athlete },
                                modifier = Modifier.padding(horizontal = 16.dp).animateItem(),
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

@Composable
private fun AthleteCard(athlete: ProfileResponse, summary: AthleteWeeklySummary?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val name = athlete.fullName
    val color = remember(name) { AvatarPalette[(name.sumOf { it.code }).mod(AvatarPalette.size)] }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardFill)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(46.dp).clip(CircleShape).background(color.copy(alpha = 0.25f))) {
            Text(athlete.athleteInitials, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
            athlete.email?.takeIf { it.isNotBlank() }?.let {
                Text(it, fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f), maxLines = 1)
            }
            if (summary != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryPill(Icons.Filled.CalendarMonth, "${summary.plannedCount} planned", Color.White.copy(alpha = 0.6f))
                    SummaryPill(
                        Icons.Filled.CheckCircle, "${summary.completedCount} done",
                        if (summary.completedCount > 0) Color(0xFF34C759) else Color.White.copy(alpha = 0.35f),
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.size(width = 72.dp, height = 18.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.07f)))
                    Box(modifier = Modifier.size(width = 56.dp, height = 18.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.07f)))
                }
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.White.copy(alpha = 0.25f), modifier = Modifier.size(18.dp))
    }
}

// iOS-style swipe-to-reveal: swipe the card left to expose a red Remove button, tap it to confirm.
// The card snaps open/closed; tapping an open card closes it, tapping a closed card opens the detail.
@Composable
private fun SwipeRevealAthleteRow(
    athlete: ProfileResponse,
    summary: AthleteWeeklySummary?,
    onOpen: () -> Unit,
    onRemoveTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // Matches iOS SwipeAction: the action reserves 80pt of slide, the button is a 68×68 rounded
    // square (corner 20) centred in it (≈6pt gap each side).
    val actionWidth = 80.dp
    val buttonSize = 68.dp
    val actionWidthPx = with(density) { actionWidth.toPx() }
    var offsetX by remember(athlete.id) { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    // draggable updates offsetX SYNCHRONOUSLY on each delta (unlike a launched snapTo, whose value is
    // stale at drag-end on a fast fling), so the open/closed snap decision is always correct.
    val draggableState = rememberDraggableState { delta -> offsetX = (offsetX + delta).coerceIn(-actionWidthPx, 0f) }
    fun settle(target: Float) { scope.launch { animate(offsetX, target, animationSpec = spring(dampingRatio = 0.85f)) { v, _ -> offsetX = v } } }

    Box(modifier = modifier.fillMaxWidth()) {
        // Remove button — a floating rounded-square action revealed on the right as the card slides
        // left (matches iOS's swipe action: a gap from the card + slight vertical inset). matchParentSize
        // gives it the card's height to inset from. Hidden when fully closed so it can't bleed through
        // the translucent card at rest.
        if (offsetX < 0f) {
            Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(end = 6.dp).size(buttonSize)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFEF4A40))
                        .clickable { settle(0f); onRemoveTap() },
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(Icons.Filled.PersonRemove, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(20.dp))
                        Text("Remove", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Horizontal,
                    onDragStopped = { settle(if (offsetX < -actionWidthPx * 0.4f) -actionWidthPx else 0f) },
                )
        ) {
            AthleteCard(
                athlete = athlete,
                summary = summary,
                onClick = { if (offsetX != 0f) settle(0f) else onOpen() },
            )
        }
    }
}

@Composable
private fun SummaryPill(icon: ImageVector, value: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.07f)).padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(10.dp))
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = color)
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
