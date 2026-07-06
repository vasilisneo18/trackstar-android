package com.vasilisneo.trackstar.ui.screens.main

// Temporary stand-in for the Stats, MyTeam, and Diet tabs until those screens are built —
// each is a real, separate feature on iOS (WorkoutStatsView, AthletesView, DietView) that's
// out of scope for this pass (Workout tab + tab shell only).

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vasilisneo.trackstar.ui.components.ProfileAvatarButton
import com.vasilisneo.trackstar.ui.components.initialsFrom
import com.vasilisneo.trackstar.ui.theme.TrackstarBackground

@Composable
fun PlaceholderTabScreen(
    title: String,
    onProfileClick: () -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize().background(TrackstarBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                ProfileAvatarButton(initials = initialsFrom(null), onClick = onProfileClick)
                Spacer(modifier = Modifier.weight(1f))
            }

            Box(modifier = Modifier.fillMaxSize().padding(bottom = 80.dp), contentAlignment = Alignment.Center) {
                Column {
                    Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Coming soon", fontSize = 14.sp, color = Color.White.copy(alpha = 0.45f))
                }
            }
        }
    }
}
