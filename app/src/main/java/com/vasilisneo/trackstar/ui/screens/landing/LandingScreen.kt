package com.vasilisneo.trackstar.ui.screens.landing

// Replica of LandingView (Trackstar/UI/View/InitialView.swift) on iOS — the real app
// entry point; Login (and eventually Create Account) are pushed from here, which is why
// LoginScreen has a back button. Skips the "Continue as" quick-login bottom sheet iOS
// shows when there's a cached email+password — that depends on local credential caching
// that doesn't exist on Android yet.

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vasilisneo.trackstar.ui.components.AuthBackground

// Local to this screen, distinct from the app's theme accent — matches iOS's
// `private let accent = Color(red: 0.35, green: 0.45, blue: 1.0)` in LandingView.
private val LandingAccent = Color(0xFF5973FF)

@Composable
fun LandingScreen(
    onCreateAccount: () -> Unit = {},
    onLogin: () -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize()) {
        AuthBackground(glowOffsetY = (-180).dp)

        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            Spacer(modifier = Modifier.weight(1f))
            BrandingSection()
            Spacer(modifier = Modifier.weight(1f))
            ButtonsSection(onCreateAccount = onCreateAccount, onLogin = onLogin)
        }
    }
}

@Composable
private fun BrandingSection() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .clip(CircleShape)
                    .background(LandingAccent.copy(alpha = 0.08f))
            )
            Box(
                modifier = Modifier
                    .size(94.dp)
                    .clip(CircleShape)
                    .background(LandingAccent.copy(alpha = 0.13f))
            )
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(LandingAccent.copy(alpha = 0.18f))
            )
            Icon(
                imageVector = Icons.Filled.FitnessCenter,
                contentDescription = null,
                tint = LandingAccent,
                modifier = Modifier.size(30.dp)
            )
        }

        Text(
            text = "Trackstar",
            fontSize = 42.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )

        Text(
            text = "Your personal training companion",
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.4f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ButtonsSection(onCreateAccount: () -> Unit, onLogin: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 48.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White)
                .clickable(onClick = onCreateAccount),
            contentAlignment = Alignment.Center
        ) {
            Text("Create account", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White.copy(alpha = 0.1f))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(28.dp))
                .clickable(onClick = onLogin),
            contentAlignment = Alignment.Center
        ) {
            Text("Log in", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
    }
}
