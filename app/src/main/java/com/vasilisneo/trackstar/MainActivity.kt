package com.vasilisneo.trackstar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.vasilisneo.trackstar.ui.screens.login.LoginScreen
import com.vasilisneo.trackstar.ui.theme.TrackstarTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrackstarTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // showBackButton matches iOS's real usage — AuthCoordinator.showLoginScreen()
                    // always sets showBackButton = true (Login is pushed from a Welcome screen).
                    // onBackClick is a no-op for now since there's no Welcome screen here yet.
                    LoginScreen(showBackButton = true)
                }
            }
        }
    }
}
