package com.vasilisneo.trackstar.ui.screens.landing

// Replica of LandingView (Trackstar/UI/View/InitialView.swift) on iOS — the real app
// entry point; Login (and eventually Create Account) are pushed from here, which is why
// LoginScreen has a back button. When there's a cached signed-out user, shows iOS's
// "Continue as" quick-login card for one-tap re-login.

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vasilisneo.trackstar.R
import com.vasilisneo.trackstar.ui.components.AuthBackground
import com.vasilisneo.trackstar.ui.components.AuthErrorText
import com.vasilisneo.trackstar.ui.screens.login.LoginViewModel

// Local to this screen, distinct from the app's theme accent — matches iOS's
// `private let accent = Color(red: 0.35, green: 0.45, blue: 1.0)` in LandingView.
private val LandingAccent = Color(0xFF5973FF)

@Composable
fun LandingScreen(
    onCreateAccount: () -> Unit = {},
    onLogin: () -> Unit = {},
    onQuickLoginSuccess: () -> Unit = {},
    loginViewModel: LoginViewModel = viewModel(),
) {
    val cachedEmail = loginViewModel.cachedEmail

    Box(modifier = Modifier.fillMaxSize()) {
        AuthBackground(glowOffsetY = (-180).dp)

        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            Spacer(modifier = Modifier.weight(1f))
            BrandingSection()
            Spacer(modifier = Modifier.weight(1f))
            if (cachedEmail != null) {
                ContinueAsSection(
                    email = cachedEmail,
                    isLoading = loginViewModel.isLoading,
                    errorMessage = loginViewModel.errorMessage,
                    onContinue = { loginViewModel.quickLogin(onQuickLoginSuccess) },
                    onNotYou = { loginViewModel.forgetCachedUser() },
                )
            } else {
                ButtonsSection(onCreateAccount = onCreateAccount, onLogin = onLogin)
            }
        }
    }
}

// Mirrors iOS's `.lineLimit(1).truncationMode(.middle)` on the cached-email row — Compose's
// Text has no built-in middle-ellipsis, so measure the full string and binary-search the
// widest head+tail split that still fits the available width.
@Composable
private fun MiddleEllipsisText(
    text: String,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontSize = fontSize, fontWeight = fontWeight, color = color)
    BoxWithConstraints(modifier = modifier) {
        val maxWidthPx = constraints.maxWidth
        val fullWidth = measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
        val display = if (fullWidth <= maxWidthPx || text.length <= 3) {
            text
        } else {
            var lo = 0
            var hi = text.length
            var best = "…"
            while (lo <= hi) {
                val mid = (lo + hi) / 2
                val headLen = (mid + 1) / 2
                val tailLen = mid - headLen
                val candidate = if (mid <= 0) "…" else text.take(headLen) + "…" + text.takeLast(tailLen)
                val width = measurer.measure(candidate, style, maxLines = 1, softWrap = false).size.width
                if (width <= maxWidthPx) {
                    best = candidate
                    lo = mid + 1
                } else {
                    hi = mid - 1
                }
            }
            best
        }
        Text(display, fontSize = fontSize, fontWeight = fontWeight, color = color, maxLines = 1)
    }
}

@Composable
private fun ContinueAsSection(
    email: String,
    isLoading: Boolean,
    errorMessage: String?,
    onContinue: () -> Unit,
    onNotYou: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 48.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Continue as", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
            Text("Not you?", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = LandingAccent, modifier = Modifier.clickable(onClick = onNotYou))
        }

        errorMessage?.let { AuthErrorText(it) }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                .clickable(enabled = !isLoading, onClick = onContinue)
                .padding(horizontal = 16.dp)
        ) {
            Box(
                modifier = Modifier.size(46.dp).clip(CircleShape).background(LandingAccent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(email.take(1).uppercase(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LandingAccent)
            }
            MiddleEllipsisText(
                text = email,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            if (isLoading) {
                CircularProgressIndicator(color = Color.White.copy(alpha = 0.5f), strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(20.dp))
            }
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
                painter = painterResource(R.drawable.ic_workout_figure),
                contentDescription = null,
                tint = LandingAccent,
                modifier = Modifier.size(38.dp)
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
