package com.vasilisneo.trackstar.ui.screens.main

// Minimal stand-in for ProfileView on iOS — reachable via the avatar button on every tab.
// Real profile content (avatar, name, stats, coach card, settings) is a separate future
// piece of work; this just proves the navigation wiring out of the tab shell.

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vasilisneo.trackstar.ui.components.AuthScreenScaffold

@Composable
fun ProfileScreen(onBackClick: () -> Unit = {}) {
    AuthScreenScaffold(
        title = "Profile",
        subtitle = "Coming soon",
        showBackButton = true,
        onBackClick = onBackClick,
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
            Text(
                "Full profile (avatar, stats, coach card, settings) isn't built yet.",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.45f)
            )
        }
    }
}
