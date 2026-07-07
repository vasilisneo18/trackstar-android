package com.vasilisneo.trackstar

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import com.vasilisneo.trackstar.ui.screens.main.ProfileScreen
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
                        composable("main") {
                            MainAppScreen(onProfileClick = { navController.navigate("profile") })
                        }
                        composable("profile") {
                            ProfileScreen(
                                onBackClick = { navController.popBackStack() },
                                onLogout = {
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
