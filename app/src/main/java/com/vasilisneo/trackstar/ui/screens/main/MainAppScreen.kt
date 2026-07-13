package com.vasilisneo.trackstar.ui.screens.main

// Replica of the bottom tab bar built in MainAppCoordinator.buildTabs() on iOS: Workout,
// Stats, MyTeam (coach-only on iOS — shown unconditionally here since there's no
// role/subscription system on Android yet to gate it), Diet.
//
// iOS's tab bar is a floating rounded pill inset from the screen edges with a blurred
// (.ultraThinMaterial) background and a pill-shaped highlight behind the selected item —
// visually distinct enough from Android's default edge-to-edge NavigationBar that it's
// hand-rolled here instead, per the "match iOS visuals" side of the project's design
// principle (the tap/selection mechanics underneath are still plain Compose clickables,
// nothing iOS-gesture-specific). True backdrop blur isn't used anywhere else in this app
// either (see AuthComponents' "glass" buttons), so a solid translucent dark fill
// approximates .ultraThinMaterial the same way those do.

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Brush
import com.vasilisneo.trackstar.ui.theme.TrackstarBackground
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vasilisneo.trackstar.R
import com.vasilisneo.trackstar.ui.screens.main.workout.ActiveSessionMiniBar
import com.vasilisneo.trackstar.ui.screens.main.workout.ActiveSessionScreen
import com.vasilisneo.trackstar.ui.screens.main.workout.ActiveSessionViewModel
import com.vasilisneo.trackstar.ui.screens.main.workout.QuickLogSheet
import com.vasilisneo.trackstar.ui.screens.main.workout.QuickLogViewModel
import com.vasilisneo.trackstar.ui.screens.main.workout.WorkoutScreen

// Workout uses a custom vector drawable (ic_workout_figure) recreating iOS's overhead-press
// figure; the other three use Material icons. Exactly one of icon / iconRes is set per tab.
private data class MainTab(
    val route: String,
    val label: String,
    val icon: ImageVector? = null,
    @DrawableRes val iconRes: Int? = null,
)

private val MainTabs = listOf(
    MainTab("workout", "Workout", iconRes = R.drawable.ic_workout_figure),
    MainTab("stats", "Stats", icon = Icons.Filled.BarChart),
    MainTab("myteam", "MyTeam", icon = Icons.Filled.Groups),
    MainTab("diet", "Diet", icon = Icons.Filled.Restaurant),
)

// Lighter, translucent "liquid glass" capsule (was a near-black #17171F that vanished on the dark
// background) with a faint white rim for the glass edge.
private val TabBarSurface = Color(0xFF3A3A46).copy(alpha = 0.82f)
private val TabBarRim = Color.White.copy(alpha = 0.14f)

@Composable
fun MainAppScreen(
    onProfileClick: () -> Unit = {},
    onScheduleWorkout: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenProgress: () -> Unit = {},
    onOpenAthlete: (String) -> Unit = {},
    onOpenAddAthlete: () -> Unit = {},
    onOpenTemplates: () -> Unit = {},
    onOpenQr: () -> Unit = {},
) {
    val tabNavController = rememberNavController()

    // Active session is owned here (not inside the Workout tab or a nav route) so it survives
    // being minimized: the full-screen UI dismisses to a mini-bar while the session keeps
    // running, mirroring iOS where ActiveSessionViewModel outlives its fullScreenCover. The
    // full-screen surfaces render above the floating tab bar, like iOS's fullScreenCover.
    var activeSession by remember { mutableStateOf<ActiveSessionViewModel?>(null) }
    var showActiveFull by remember { mutableStateOf(false) }
    var quickLog by remember { mutableStateOf<QuickLogViewModel?>(null) }
    // Bumped whenever a session is saved, so the Workout tab re-fetches its completed sessions.
    var workoutRefreshKey by remember { mutableIntStateOf(0) }

    fun startSession(date: java.time.LocalDate, sessionId: String) {
        if (activeSession != null) { showActiveFull = true; return }
        activeSession = ActiveSessionViewModel(date, sessionId)
        showActiveFull = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = tabNavController,
            startDestination = "workout",
            modifier = Modifier.fillMaxSize()
        ) {
            composable("workout") {
                WorkoutScreen(
                    onProfileClick = onProfileClick,
                    onScheduleWorkout = onScheduleWorkout,
                    onStartSession = { date, sessionId -> startSession(date, sessionId) },
                    onQuickLog = { date, sessionId -> quickLog = QuickLogViewModel(date, sessionId) },
                    activeSession = activeSession,
                    onResumeSession = { showActiveFull = true },
                    refreshKey = workoutRefreshKey,
                )
            }
            composable("stats") {
                com.vasilisneo.trackstar.ui.screens.main.stats.StatsScreen(
                    onProfileClick = onProfileClick,
                    onHistoryClick = onOpenHistory,
                    onOpenProgress = onOpenProgress,
                )
            }
            composable("myteam") {
                // Coach-only roster (matches iOS, where MyTeam is gated by the coach subscription).
                // Athletes get a "My Coach" screen instead (Phase 4). Role comes from the JWT login.
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val role = remember { com.vasilisneo.trackstar.data.auth.TokenStore(ctx).role }
                if (role == "coach") {
                    com.vasilisneo.trackstar.ui.screens.main.coach.AthletesScreen(
                        onProfileClick = onProfileClick,
                        onAthleteClick = { athlete -> athlete.id?.let(onOpenAthlete) },
                        onAddAthlete = onOpenAddAthlete,
                        onShowTemplates = onOpenTemplates,
                    )
                } else {
                    com.vasilisneo.trackstar.ui.screens.main.coach.MyCoachScreen(
                        onProfileClick = onProfileClick,
                        onShowQr = onOpenQr,
                    )
                }
            }
            composable("diet") {
                com.vasilisneo.trackstar.ui.screens.main.diet.DietScreen(onProfileClick = onProfileClick)
            }
        }

        // Scroll-edge fade behind the floating tab bar: content scrolling toward the bar softly
        // dissolves into the background instead of sharply cutting off — approximates the way
        // iOS's liquid-glass tab bar blurs/hides content passing beneath it.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(130.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to TrackstarBackground.copy(alpha = 0.55f),
                        1f to TrackstarBackground,
                    )
                )
        )

        // Mini-bar (when minimized) sits directly above the floating tab bar. When present, the
        // mini-bar + the whole bottom region (behind/around the floating tab pill, down to the
        // screen edge) share one continuous translucent surface, matching iOS.
        val session = activeSession
        if (session != null && !showActiveFull) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(Color(0xFF14141C).copy(alpha = 0.95f))
            ) {
                ActiveSessionMiniBar(viewModel = session, onExpand = { showActiveFull = true })
                FloatingTabBar(tabNavController = tabNavController)
            }
        } else {
            FloatingTabBar(
                tabNavController = tabNavController,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // Full-screen active session — slides up over everything (including the tab bar).
        AnimatedVisibility(
            visible = showActiveFull && activeSession != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
        ) {
            activeSession?.let { session ->
                ActiveSessionScreen(
                    viewModel = session,
                    onMinimize = { showActiveFull = false },
                    onDiscard = {
                        showActiveFull = false
                        session.dispose()
                        activeSession = null
                    },
                    onFinish = {
                        showActiveFull = false
                        session.dispose()
                        activeSession = null
                        workoutRefreshKey++
                    },
                )
            }
        }

        // Full-screen quick-log — same modal slide-up treatment.
        AnimatedVisibility(
            visible = quickLog != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
        ) {
            quickLog?.let { vm ->
                QuickLogSheet(
                    viewModel = vm,
                    onClose = { quickLog = null },
                    onFinished = {
                        quickLog = null
                        workoutRefreshKey++
                    },
                )
            }
        }
    }
}

@Composable
private fun FloatingTabBar(
    tabNavController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val backStackEntry by tabNavController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination

    Row(
        verticalAlignment = Alignment.CenterVertically,
        // Inter-pill gaps come from spacedBy (not per-item side padding) so the first/last
        // pill sits an equal 5dp from the bar's edge on every side — per-item side padding
        // would have added on top of the row inset, making the outer horizontal gap larger
        // than the top/bottom gap.
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp) // outer margin from the screen edges
            // background(color, shape) draws an antialiased rounded fill; using clip() here
            // instead would give hard, jagged corners because hardware clipPath isn't
            // antialiased on Android. No clip is needed since the pills are inset 5dp and
            // stay inside the bar's rounded corners.
            .background(TabBarSurface, RoundedCornerShape(percent = 50))
            .border(1.dp, TabBarRim, RoundedCornerShape(percent = 50))
            .padding(4.dp) // inner inset between the capsule's edge and the tab row itself, equal on every side
    ) {
        MainTabs.forEach { tab ->
            val selected = currentRoute?.hierarchy?.any { it.route == tab.route } == true
            // weight(1f) gives every tab an equal-width slot; the pill fills that slot
            // (minus a small gap to its neighbours), so the selected pill is a wide stadium
            // like iOS rather than a narrow shape floating in the middle of its slot.
            TabBarItem(
                tab = tab,
                selected = selected,
                onClick = {
                    tabNavController.navigate(tab.route) {
                        popUpTo(tabNavController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TabBarItem(
    tab: MainTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (selected) Color.White else Color.White.copy(alpha = 0.5f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
        modifier = modifier
            // percent = 50 makes a true stadium here because the pill is now wide: the corner
            // radius resolves to half the *shorter* (vertical) side, giving fully-rounded ends.
            // background(color, shape) draws the antialiased fill; clip() is applied only
            // afterward to bound the tap ripple (hardware clip is not antialiased, so it must
            // NOT be what shapes the visible fill or the corners come out jagged).
            .background(
                if (selected) Color.White.copy(alpha = 0.15f) else Color.Transparent,
                RoundedCornerShape(percent = 50)
            )
            .clip(RoundedCornerShape(percent = 50))
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp)
    ) {
        if (tab.iconRes != null) {
            Icon(
                painter = painterResource(tab.iconRes),
                contentDescription = tab.label,
                tint = contentColor,
                modifier = Modifier.size(30.dp)
            )
        } else {
            Icon(tab.icon!!, contentDescription = tab.label, tint = contentColor, modifier = Modifier.size(30.dp))
        }
        Text(tab.label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = contentColor)
    }
}
