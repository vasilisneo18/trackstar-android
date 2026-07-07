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
import com.vasilisneo.trackstar.ui.screens.main.PersonalInfoScreen
import com.vasilisneo.trackstar.ui.screens.main.ProfileScreen
import com.vasilisneo.trackstar.ui.screens.main.SettingsScreen
import com.vasilisneo.trackstar.ui.screens.main.settings.AboutScreen
import com.vasilisneo.trackstar.ui.screens.main.settings.AppSettingsScreen
import com.vasilisneo.trackstar.ui.screens.main.settings.AppearanceScreen
import com.vasilisneo.trackstar.ui.screens.main.settings.CloseAccountScreen
import com.vasilisneo.trackstar.ui.screens.main.settings.NotificationsScreen
import com.vasilisneo.trackstar.ui.screens.subscription.SubscriptionScreen
import com.vasilisneo.trackstar.ui.theme.TrackstarTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrackstarTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // Mirrors AuthCoordinator on iOS: Landing is the real entry point
                    // (.resetStack root), Login/Create-Account are pushed from it with a
                    // back button.
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = "landing",
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
                                onLogin = { navController.navigate("login") }
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
                            MainAppScreen(onProfileClick = { navController.navigate("profile") })
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
                                onUpgrade = { navController.navigate("subscription") }
                            )
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
                                    // TODO: wire real POST /api/auth/register before navigating —
                                    // this just proves the destination exists for now.
                                    onContinue = {
                                        navController.navigate("main") {
                                            popUpTo("landing") { inclusive = true }
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
