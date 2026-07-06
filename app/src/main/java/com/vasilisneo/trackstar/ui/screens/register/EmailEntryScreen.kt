package com.vasilisneo.trackstar.ui.screens.register

// Replica of EmailEntryView (Trackstar/UI/View/Registration/CreateAccountView.swift) on
// iOS — first step of the registration flow, reached from Landing's "Create account".
// checkEmail() is simulated locally (see RegisterViewModel) — every email is treated as
// new, so the "Account found" sheet only shows if you wire that up later.

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.Text
import com.vasilisneo.trackstar.ui.components.AuthCapsuleButton
import com.vasilisneo.trackstar.ui.components.AuthErrorText
import com.vasilisneo.trackstar.ui.components.AuthScreenScaffold
import com.vasilisneo.trackstar.ui.components.AuthTextField
import com.vasilisneo.trackstar.ui.components.GlassCircleIconButton
import kotlin.math.roundToInt

// Local to this screen, matching iOS's own private `accent` redeclaration in
// EmailEntryView (same value as LandingScreen's, but iOS doesn't share it centrally either).
private val EmailEntryAccent = Color(0xFF5973FF)
private val SheetSurface = Color(0xFF1A1A24)

@Composable
fun EmailEntryScreen(
    viewModel: RegisterViewModel = viewModel(),
    onBackClick: () -> Unit = {},
    onNewEmail: () -> Unit = {},
    onExistingEmail: (String) -> Unit = {},
) {
    val showSheet = viewModel.emailCheckStatus == EmailCheckStatus.EXISTS

    LaunchedEffect(viewModel.emailCheckStatus) {
        if (viewModel.emailCheckStatus == EmailCheckStatus.AVAILABLE) {
            onNewEmail()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AuthScreenScaffold(
            title = "Create Account",
            subtitle = "Enter your email to get started",
            showBackButton = true,
            onBackClick = onBackClick,
        ) {
            AuthTextField(
                value = viewModel.email,
                onValueChange = viewModel::onEmailChange,
                placeholder = "Email"
            )

            viewModel.errorMessage?.let { error -> AuthErrorText(error) }

            AuthCapsuleButton(
                text = "Continue",
                onClick = viewModel::checkEmail,
                isLoading = viewModel.emailCheckStatus == EmailCheckStatus.CHECKING,
                enabled = viewModel.isValidEmail,
                modifier = Modifier.padding(top = 20.dp)
            )
        }

        AnimatedVisibility(
            visible = showSheet,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ExistingAccountSheet(
                email = viewModel.email,
                onDismiss = { viewModel.resetEmailCheckStatus() },
                onLogin = { onExistingEmail(viewModel.email.lowercase()) }
            )
        }
    }
}

@Composable
private fun ExistingAccountSheet(
    email: String,
    onDismiss: () -> Unit,
    onLogin: () -> Unit,
) {
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, dragOffset.roundToInt()) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragOffset > 150f) onDismiss()
                        dragOffset = 0f
                    },
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        dragOffset = (dragOffset + amount).coerceAtLeast(0f)
                    }
                )
            }
            .padding(horizontal = 15.dp)
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(SheetSurface)
            .padding(horizontal = 20.dp)
            .padding(top = 24.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Account found", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    "This email is already registered",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
            GlassCircleIconButton(onClick = onDismiss, icon = Icons.Filled.Close, contentDescription = "Close")
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp)
        ) {
            Box(
                modifier = Modifier.size(46.dp).clip(CircleShape).background(EmailEntryAccent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = email.take(1).uppercase(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmailEntryAccent
                )
            }
            Text(
                text = email.lowercase(),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
        }

        AuthCapsuleButton(text = "Log in", onClick = onLogin)
    }
}
