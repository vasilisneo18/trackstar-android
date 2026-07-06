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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.vasilisneo.trackstar.ui.screens.main.workout.WorkoutScreen

// No Material icon matches iOS's overhead-barbell-press glyph exactly, and a hand-drawn
// substitute didn't read clearly at this size (looked like an hourglass) — FitnessCenter
// is a standard, unambiguous "workout" icon even though it's a dumbbell object rather than
// a person doing that exact pose.
private data class MainTab(val route: String, val label: String, val icon: ImageVector)

private val MainTabs = listOf(
    MainTab("workout", "Workout", Icons.Filled.FitnessCenter),
    MainTab("stats", "Stats", Icons.Filled.BarChart),
    MainTab("myteam", "MyTeam", Icons.Filled.Groups),
    MainTab("diet", "Diet", Icons.Filled.Restaurant),
)

private val TabBarSurface = Color(0xFF17171F).copy(alpha = 0.92f)

@Composable
fun MainAppScreen(onProfileClick: () -> Unit = {}) {
    val tabNavController = rememberNavController()

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = tabNavController,
            startDestination = "workout",
            modifier = Modifier.fillMaxSize()
        ) {
            composable("workout") { WorkoutScreen(onProfileClick = onProfileClick) }
            composable("stats") { PlaceholderTabScreen(title = "Stats", onProfileClick = onProfileClick) }
            composable("myteam") { PlaceholderTabScreen(title = "MyTeam", onProfileClick = onProfileClick) }
            composable("diet") { PlaceholderTabScreen(title = "Diet", onProfileClick = onProfileClick) }
        }

        FloatingTabBar(
            tabNavController = tabNavController,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
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
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(TabBarSurface)
    ) {
        MainTabs.forEach { tab ->
            val selected = currentRoute?.hierarchy?.any { it.route == tab.route } == true
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                TabBarItem(
                    tab = tab,
                    selected = selected,
                    onClick = {
                        tabNavController.navigate(tab.route) {
                            popUpTo(tabNavController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    }
}

// Fixed min width so the selected pill is the same size on every tab regardless of label
// length ("Diet" vs "MyTeam") — sizing the pill to each label's own text width made the
// capsules visibly different sizes across tabs.
private val TabPillMinWidth = 72.dp

// A percent-based RoundedCornerShape only reads as a true capsule when width is much
// larger than height; here the two are close enough that percent=50 rendered as an
// egg/ellipse instead of a stadium. A fixed dp radius set to roughly half the pill's own
// height (icon 20dp + 1dp spacing + ~13dp text + 8dp top/bottom padding ≈ 50dp tall) gives
// a real flat-sided capsule — the same "shape language" as the outer bar's own fixed-radius
// RoundedCornerShape(26.dp), just scaled to the smaller pill.
private val TabPillCornerRadius = 24.dp

@Composable
private fun TabBarItem(
    tab: MainTab,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val contentColor = if (selected) Color.White else Color.White.copy(alpha = 0.5f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
        modifier = Modifier
            .defaultMinSize(minWidth = TabPillMinWidth)
            .clip(RoundedCornerShape(TabPillCornerRadius))
            .background(if (selected) Color.White.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Icon(tab.icon, contentDescription = tab.label, tint = contentColor, modifier = Modifier.size(20.dp))
        Text(tab.label, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = contentColor)
    }
}
