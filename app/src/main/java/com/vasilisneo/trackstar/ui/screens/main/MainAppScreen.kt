package com.vasilisneo.trackstar.ui.screens.main

// Replica of the bottom tab bar built in MainAppCoordinator.buildTabs() on iOS: Workout,
// Stats, MyTeam (coach-only on iOS — shown unconditionally here since there's no
// role/subscription system on Android yet to gate it), Diet. Android's Material3
// NavigationBar is the native equivalent of iOS's UITabBarController — using it instead of
// hand-rolling iOS's liquid-glass tab bar is intentional per the "match iOS visuals, use
// native interaction mechanics" project convention; only the color styling matches iOS
// (dark, white selected / dimmed unselected).

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vasilisneo.trackstar.ui.screens.main.workout.WorkoutScreen
import com.vasilisneo.trackstar.ui.theme.TrackstarBackground

private data class MainTab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val MainTabs = listOf(
    MainTab("workout", "Workout", Icons.Filled.FitnessCenter),
    MainTab("stats", "Stats", Icons.Filled.BarChart),
    MainTab("myteam", "MyTeam", Icons.Filled.Groups),
    MainTab("diet", "Diet", Icons.Filled.Restaurant),
)

@Composable
fun MainAppScreen(onProfileClick: () -> Unit = {}) {
    val tabNavController = rememberNavController()

    Scaffold(
        containerColor = TrackstarBackground,
        bottomBar = {
            val backStackEntry by tabNavController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination

            NavigationBar(containerColor = TrackstarBackground) {
                MainTabs.forEach { tab ->
                    val selected = currentRoute?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            tabNavController.navigate(tab.route) {
                                popUpTo(tabNavController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { androidx.compose.material3.Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Color.White,
                            unselectedIconColor = Color.White.copy(alpha = 0.5f),
                            unselectedTextColor = Color.White.copy(alpha = 0.5f),
                            indicatorColor = Color.White.copy(alpha = 0.12f),
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = tabNavController,
            startDestination = "workout",
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            composable("workout") { WorkoutScreen(onProfileClick = onProfileClick) }
            composable("stats") { PlaceholderTabScreen(title = "Stats", onProfileClick = onProfileClick) }
            composable("myteam") { PlaceholderTabScreen(title = "MyTeam", onProfileClick = onProfileClick) }
            composable("diet") { PlaceholderTabScreen(title = "Diet", onProfileClick = onProfileClick) }
        }
    }
}
