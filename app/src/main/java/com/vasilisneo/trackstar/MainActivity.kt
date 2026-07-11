package com.vasilisneo.trackstar

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vasilisneo.trackstar.ui.screens.landing.LandingScreen
import com.vasilisneo.trackstar.ui.screens.login.ForgotPasswordScreen
import com.vasilisneo.trackstar.ui.screens.login.LoginScreen
import com.vasilisneo.trackstar.ui.screens.register.BodyMetricsScreen
import com.vasilisneo.trackstar.ui.screens.register.CreatePasswordScreen
import com.vasilisneo.trackstar.ui.screens.register.EmailEntryScreen
import com.vasilisneo.trackstar.ui.screens.register.FitnessProfileScreen
import com.vasilisneo.trackstar.ui.screens.register.GoalsScreen
import com.vasilisneo.trackstar.ui.screens.register.PersonalDetailsScreen
import com.vasilisneo.trackstar.ui.screens.register.RegisterViewModel
import com.vasilisneo.trackstar.ui.screens.main.MainAppScreen
import com.vasilisneo.trackstar.ui.screens.main.plan.SessionEditScreen
import com.vasilisneo.trackstar.ui.screens.main.plan.WeeklyPlanScreen
import com.vasilisneo.trackstar.ui.screens.main.PersonalInfoScreen
import com.vasilisneo.trackstar.ui.screens.main.ProfileScreen
import com.vasilisneo.trackstar.ui.screens.main.QRConnectScreen
import com.vasilisneo.trackstar.ui.screens.main.SettingsScreen
import com.vasilisneo.trackstar.ui.screens.main.stats.ExerciseProgressScreen
import com.vasilisneo.trackstar.ui.screens.main.stats.HistoryScreen
import com.vasilisneo.trackstar.ui.screens.main.settings.AboutScreen
import com.vasilisneo.trackstar.ui.screens.main.settings.AppSettingsScreen
import com.vasilisneo.trackstar.ui.screens.main.settings.AppearanceScreen
import com.vasilisneo.trackstar.ui.screens.main.settings.CloseAccountScreen
import com.vasilisneo.trackstar.ui.screens.main.settings.NotificationsScreen
import com.vasilisneo.trackstar.ui.screens.subscription.SubscriptionScreen
import com.vasilisneo.trackstar.data.auth.TokenStore
import com.vasilisneo.trackstar.ui.theme.TrackstarTheme
import com.vasilisneo.trackstar.ui.theme.loadSavedTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Apply the saved Appearance theme before the first frame so there's no midnight→theme flash.
        loadSavedTheme(this)
        // Auto-login: if a session token is already persisted, open straight into the main
        // app (mirrors MasterCoordinator.start() on iOS), otherwise start at Landing.
        val startDestination = if (TokenStore(this).isLoggedIn) "main" else "landing"
        setContent {
            TrackstarTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // Mirrors AuthCoordinator on iOS: Landing is the real entry point
                    // (.resetStack root), Login/Create-Account are pushed from it with a
                    // back button.
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = startDestination,
                        // NavHost has no slide animation by default — this replicates
                        // iOS/UIKit's push/pop: new screen slides in from the right over
                        // the current one (which parallax-shifts left slightly), and pop
                        // reverses it.
                        enterTransition = { slideInHorizontally(initialOffsetX = { fullWidth -> fullWidth }) },
                        exitTransition = { slideOutHorizontally(targetOffsetX = { fullWidth -> -fullWidth / 3 }) },
                        popEnterTransition = { slideInHorizontally(initialOffsetX = { fullWidth -> -fullWidth / 3 }) },
                        popExitTransition = { slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth }) },
                    ) {
                        composable("landing") {
                            LandingScreen(
                                onCreateAccount = { navController.navigate("register") },
                                onLogin = { navController.navigate("login") },
                                onQuickLoginSuccess = {
                                    navController.navigate("main") {
                                        popUpTo("landing") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable(
                            route = "login?email={email}",
                            arguments = listOf(navArgument("email") { type = NavType.StringType; defaultValue = "" })
                        ) { backStackEntry ->
                            val initialEmail = backStackEntry.arguments?.getString("email") ?: ""
                            LoginScreen(
                                showBackButton = true,
                                onBackClick = { navController.popBackStack() },
                                initialEmail = initialEmail,
                                onForgotPassword = { navController.navigate("forgot_password") },
                                onLoginSuccess = {
                                    navController.navigate("main") {
                                        popUpTo("landing") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("forgot_password") {
                            ForgotPasswordScreen(onBackClick = { navController.popBackStack() })
                        }
                        composable(
                            "main",
                            // Hold still while Profile zooms in/out on top instead of
                            // parallax-sliding like the auth push flow does.
                            exitTransition = { ExitTransition.None },
                            popEnterTransition = { EnterTransition.None },
                        ) {
                            MainAppScreen(
                                onProfileClick = { navController.navigate("profile") },
                                onScheduleWorkout = { navController.navigate("weekly_plan") },
                                onOpenHistory = { navController.navigate("history") },
                                onOpenProgress = { navController.navigate("progress") },
                            )
                        }
                        composable(
                            "weekly_plan",
                            // iOS pushes this via UIKit (WorkoutCoordinator.showWeeklyPlan) —
                            // a horizontal push, not a modal cover — so this uses the NavHost's
                            // default push transitions (same as "profile" et al.) rather than
                            // the vertical slide used for subscription/active_session.
                            exitTransition = { ExitTransition.None },
                            popEnterTransition = { EnterTransition.None },
                        ) {
                            WeeklyPlanScreen(
                                onBackClick = { navController.popBackStack() },
                                onOpenSession = { weekIdentifier, day, sessionId ->
                                    val sessionSegment = sessionId ?: "new"
                                    navController.navigate(
                                        "session_edit/${Uri.encode(weekIdentifier)}/${Uri.encode(day)}/${Uri.encode(sessionSegment)}"
                                    )
                                },
                            )
                        }
                        composable(
                            route = "session_edit/{weekIdentifier}/{day}/{sessionId}",
                            arguments = listOf(
                                navArgument("weekIdentifier") { type = NavType.StringType },
                                navArgument("day") { type = NavType.StringType },
                                navArgument("sessionId") { type = NavType.StringType },
                            ),
                            // Full-screen modal that slides up to present and down to dismiss, like
                            // iOS's fullScreenCover for SessionEditView (not a horizontal push).
                            enterTransition = { slideInVertically(initialOffsetY = { it }) },
                            exitTransition = { ExitTransition.None },
                            popEnterTransition = { EnterTransition.None },
                            popExitTransition = { slideOutVertically(targetOffsetY = { it }) },
                        ) { backStackEntry ->
                            val weekIdentifier = backStackEntry.arguments?.getString("weekIdentifier") ?: ""
                            val day = backStackEntry.arguments?.getString("day") ?: ""
                            val sessionSegment = backStackEntry.arguments?.getString("sessionId") ?: "new"
                            SessionEditScreen(
                                weekIdentifier = weekIdentifier,
                                day = day,
                                sessionId = sessionSegment.takeUnless { it == "new" },
                                onClose = { navController.popBackStack() },
                                onSaved = { navController.popBackStack() },
                            )
                        }
                        composable(
                            "profile",
                            // iOS presents Profile with a zoom transition (from the avatar),
                            // not a horizontal push — approximate it with a fade + slight
                            // scale so it grows in over the stationary main screen.
                            enterTransition = { fadeIn() + scaleIn(initialScale = 0.92f) },
                            popExitTransition = { fadeOut() + scaleOut(targetScale = 0.92f) },
                            // Hold still when a child (Subscription modal, Personal Info,
                            // Settings) is pushed over it, instead of parallax-sliding left.
                            exitTransition = { ExitTransition.None },
                            popEnterTransition = { EnterTransition.None },
                        ) {
                            ProfileScreen(
                                onBackClick = { navController.popBackStack() },
                                onLogout = {
                                    navController.navigate("landing") {
                                        popUpTo("landing") { inclusive = true }
                                    }
                                },
                                onPersonalInfo = { navController.navigate("personal_info") },
                                onSettings = { navController.navigate("settings") },
                                onUpgrade = { navController.navigate("subscription") },
                                onQrCode = { navController.navigate("qr") }
                            )
                        }
                        composable(
                            "qr",
                            enterTransition = { slideInVertically(initialOffsetY = { it }) },
                            exitTransition = { ExitTransition.None },
                            popEnterTransition = { EnterTransition.None },
                            popExitTransition = { slideOutVertically(targetOffsetY = { it }) },
                        ) {
                            QRConnectScreen(onBackClick = { navController.popBackStack() })
                        }
                        composable(
                            "subscription",
                            // iOS presents this as a fullScreenCover — slide up from the
                            // bottom like a modal rather than the horizontal auth push.
                            enterTransition = { slideInVertically(initialOffsetY = { it }) },
                            exitTransition = { ExitTransition.None },
                            popEnterTransition = { EnterTransition.None },
                            popExitTransition = { slideOutVertically(targetOffsetY = { it }) },
                        ) {
                            SubscriptionScreen(onDismiss = { navController.popBackStack() })
                        }
                        composable(
                            // iOS pushes History (horizontal) and hides the tab bar; full-screen route here.
                            "history",
                            exitTransition = { ExitTransition.None },
                            popEnterTransition = { EnterTransition.None },
                        ) {
                            HistoryScreen(onBack = { navController.popBackStack() })
                        }
                        composable(
                            "progress",
                            exitTransition = { ExitTransition.None },
                            popEnterTransition = { EnterTransition.None },
                        ) {
                            ExerciseProgressScreen(onBack = { navController.popBackStack() })
                        }
                        composable("personal_info") {
                            PersonalInfoScreen(onBackClick = { navController.popBackStack() })
                        }
                        composable("settings") {
                            SettingsScreen(
                                onBackClick = { navController.popBackStack() },
                                onOpenDetail = { route -> navController.navigate("settings_$route") }
                            )
                        }
                        composable("settings_notifications") {
                            NotificationsScreen(onBackClick = { navController.popBackStack() })
                        }
                        composable(
                            "settings_appearance",
                            // Hold still while the Subscription modal slides up over it.
                            exitTransition = { ExitTransition.None },
                            popEnterTransition = { EnterTransition.None },
                        ) {
                            AppearanceScreen(
                                onBackClick = { navController.popBackStack() },
                                onUpgrade = { navController.navigate("subscription") }
                            )
                        }
                        composable("settings_app_settings") {
                            AppSettingsScreen(onBackClick = { navController.popBackStack() })
                        }
                        composable("settings_about") {
                            AboutScreen(onBackClick = { navController.popBackStack() })
                        }
                        composable("settings_close_account") {
                            CloseAccountScreen(
                                onDismiss = { navController.popBackStack() },
                                onClosed = {
                                    // Account deleted — full wipe including cached credentials
                                    // so "Continue as" won't offer the dead account.
                                    TokenStore(this@MainActivity).clearAll()
                                    navController.navigate("landing") {
                                        popUpTo("landing") { inclusive = true }
                                    }
                                }
                            )
                        }

                        // Registration flow — nested graph so every step shares one
                        // RegisterViewModel instance (same shape as iOS's shared
                        // RegisterViewModel passed down the whole NavigationStack).
                        navigation(startDestination = "email_entry", route = "register") {
                            composable("email_entry") { backStackEntry ->
                                val parentEntry = remember(backStackEntry) { navController.getBackStackEntry("register") }
                                val registerViewModel: RegisterViewModel = viewModel(parentEntry)
                                EmailEntryScreen(
                                    viewModel = registerViewModel,
                                    onBackClick = { navController.popBackStack() },
                                    onNewEmail = { navController.navigate("create_password") },
                                    onExistingEmail = { email ->
                                        navController.navigate("login?email=${Uri.encode(email)}")
                                    }
                                )
                            }
                            composable("create_password") { backStackEntry ->
                                val parentEntry = remember(backStackEntry) { navController.getBackStackEntry("register") }
                                val registerViewModel: RegisterViewModel = viewModel(parentEntry)
                                CreatePasswordScreen(
                                    viewModel = registerViewModel,
                                    onBackClick = { navController.popBackStack() },
                                    onContinue = { navController.navigate("personal_details") }
                                )
                            }
                            composable("personal_details") { backStackEntry ->
                                val parentEntry = remember(backStackEntry) { navController.getBackStackEntry("register") }
                                val registerViewModel: RegisterViewModel = viewModel(parentEntry)
                                PersonalDetailsScreen(
                                    viewModel = registerViewModel,
                                    onBackClick = { navController.popBackStack() },
                                    onContinue = { navController.navigate("body_metrics") }
                                )
                            }
                            composable("body_metrics") { backStackEntry ->
                                val parentEntry = remember(backStackEntry) { navController.getBackStackEntry("register") }
                                val registerViewModel: RegisterViewModel = viewModel(parentEntry)
                                BodyMetricsScreen(
                                    viewModel = registerViewModel,
                                    onBackClick = { navController.popBackStack() },
                                    onContinue = { navController.navigate("fitness_profile") }
                                )
                            }
                            composable("fitness_profile") { backStackEntry ->
                                val parentEntry = remember(backStackEntry) { navController.getBackStackEntry("register") }
                                val registerViewModel: RegisterViewModel = viewModel(parentEntry)
                                FitnessProfileScreen(
                                    viewModel = registerViewModel,
                                    onBackClick = { navController.popBackStack() },
                                    onContinue = { navController.navigate("goals") }
                                )
                            }
                            composable("goals") { backStackEntry ->
                                val parentEntry = remember(backStackEntry) { navController.getBackStackEntry("register") }
                                val registerViewModel: RegisterViewModel = viewModel(parentEntry)
                                GoalsScreen(
                                    viewModel = registerViewModel,
                                    onBackClick = { navController.popBackStack() },
                                    onContinue = {
                                        registerViewModel.register {
                                            navController.navigate("main") {
                                                popUpTo("landing") { inclusive = true }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
