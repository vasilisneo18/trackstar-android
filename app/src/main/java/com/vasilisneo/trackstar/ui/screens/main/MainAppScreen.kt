package com.vasilisneo.trackstar.ui.screens.main

// Replica of the bottom tab bar built in MainAppCoordinator.buildTabs() on iOS: Workout,
// Stats, MyTeam, Diet. The MyTeam tab always shows, but its coach roster is gated: it appears
// only for a Gold-plan coach (FeatureGate.canCoach), matching iOS — everyone else gets the
// athlete "My Coach" screen instead.
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
private val TabBarSurface = Color(0xFF2A2A32).copy(alpha = 0.94f)
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
    onOpenAiDietPlanner: () -> Unit = {},
    onOpenSubscription: () -> Unit = {},
) {
    val tabNavController = rememberNavController()

    // MyTeam tab is shown only for a Gold coach, matching iOS (MainAppCoordinator.buildTabs adds
    // the athletes tab only when FeatureGate.canCoach). Athletes/free/non-Gold users get the other
    // three tabs; their coach relationship lives in Profile → My Coach instead.
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val role = remember { com.vasilisneo.trackstar.data.auth.TokenStore(ctx).role }
    val plan by com.vasilisneo.trackstar.data.billing.BillingManager.currentPlan.collectAsState()
    val isCoach = role == "coach" && com.vasilisneo.trackstar.data.billing.FeatureGate.canCoach(plan)
    val visibleTabs = remember(isCoach) { MainTabs.filter { it.route != "myteam" || isCoach } }

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
                    onUpgrade = onOpenSubscription,
                )
            }
            composable("stats") {
                com.vasilisneo.trackstar.ui.screens.main.stats.StatsScreen(
                    onProfileClick = onProfileClick,
                    onHistoryClick = onOpenHistory,
                    onOpenProgress = onOpenProgress,
                    onUpgrade = onOpenSubscription,
                )
            }
            composable("myteam") {
                // Only reachable when the MyTeam tab is shown, i.e. for a Gold coach (see
                // visibleTabs above) — so this renders the roster directly. Athletes have no
                // MyTeam tab; their "My Coach" view lives in Profile.
                com.vasilisneo.trackstar.ui.screens.main.coach.AthletesScreen(
                    onProfileClick = onProfileClick,
                    onAthleteClick = { athlete -> athlete.id?.let(onOpenAthlete) },
                    onAddAthlete = onOpenAddAthlete,
                    onShowTemplates = onOpenTemplates,
                )
            }
            composable("diet") {
                com.vasilisneo.trackstar.ui.screens.main.diet.DietScreen(
                    onProfileClick = onProfileClick,
                    onOpenAiPlanner = onOpenAiDietPlanner,
                    onUpgrade = onOpenSubscription,
                )
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
                FloatingTabBar(tabNavController = tabNavController, tabs = visibleTabs)
            }
        } else {
            FloatingTabBar(
                tabNavController = tabNavController,
                tabs = visibleTabs,
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

        // Quick-log as a locked bottom sheet — it owns its own slide-up/scrim animation.
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

@Composable
private fun FloatingTabBar(
    tabNavController: NavHostController,
    tabs: List<MainTab>,
    modifier: Modifier = Modifier,
) {
    val backStackEntry by tabNavController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination

    // The pill hugs its tabs and centers, so it narrows with fewer tabs (3 for athletes, 4 for a
    // Gold coach), matching iOS's content-sized floating tab bar rather than stretching edge to
    // edge. The full-width Box just provides the centering + bottom inset.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(vertical = 5.dp), // gap above the system nav bar / screen edge
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                // background(color, shape) draws an antialiased rounded fill; using clip() here
                // instead would give hard, jagged corners because hardware clipPath isn't
                // antialiased on Android.
                .background(TabBarSurface, RoundedCornerShape(percent = 50))
                .border(1.dp, TabBarRim, RoundedCornerShape(percent = 50))
                .padding(4.dp) // inner inset between the capsule's edge and the tab row
        ) {
            tabs.forEach { tab ->
                val selected = currentRoute?.hierarchy?.any { it.route == tab.route } == true
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
                )
            }
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
            // Every tab is the same fixed width, so the bar sizes to (tab count × width) and all
            // slots (and the selected stadium filling one) match — narrower for 3 tabs, wider for
            // 4, always centered (see FloatingTabBar). background(color, shape) draws the
            // antialiased fill; clip() is applied only afterward to bound the tap ripple (hardware
            // clip is not antialiased, so it must NOT be what shapes the visible fill).
            .width(96.dp)
            .background(
                if (selected) Color.White.copy(alpha = 0.15f) else Color.Transparent,
                RoundedCornerShape(percent = 50)
            )
            .clip(RoundedCornerShape(percent = 50))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        if (tab.iconRes != null) {
            Icon(
                painter = painterResource(tab.iconRes),
                contentDescription = tab.label,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Icon(tab.icon!!, contentDescription = tab.label, tint = contentColor, modifier = Modifier.size(24.dp))
        }
        Text(tab.label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = contentColor)
    }
}
