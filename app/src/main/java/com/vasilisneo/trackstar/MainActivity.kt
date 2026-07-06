package com.vasilisneo.trackstar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vasilisneo.trackstar.ui.screens.landing.LandingScreen
import com.vasilisneo.trackstar.ui.screens.login.LoginScreen
import com.vasilisneo.trackstar.ui.theme.TrackstarTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrackstarTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // Mirrors AuthCoordinator on iOS: Landing is the real entry point
                    // (.resetStack root), Login is pushed from it with a back button.
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
                                onCreateAccount = { /* TODO: Create Account flow not built yet */ },
                                onLogin = { navController.navigate("login") }
                            )
                        }
                        composable("login") {
                            LoginScreen(
                                showBackButton = true,
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
