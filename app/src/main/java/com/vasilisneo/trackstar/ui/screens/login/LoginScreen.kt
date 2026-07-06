package com.vasilisneo.trackstar.ui.screens.login

// Replica of Trackstar/UI/View/Auth/LoginView.swift on iOS — same paddings, heights,
// corner radii, and font sizes. The iOS screen has a collapsing-nav-bar scroll animation
// (title fading in as you scroll) that isn't reproduced here (imePadding + a plain
// scrollable Column handles keyboard avoidance instead); every other visual detail
// (backgrounds, back button, wordmark, fields, buttons, spacing, error message) matches.
// See RegistrationHelpers.swift for the source values.

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vasilisneo.trackstar.ui.components.AuthBackground
import com.vasilisneo.trackstar.ui.components.AuthCapsuleButton
import com.vasilisneo.trackstar.ui.components.AuthSecureField
import com.vasilisneo.trackstar.ui.components.AuthTextField
import com.vasilisneo.trackstar.ui.components.AuthWordmark
import com.vasilisneo.trackstar.ui.components.GlassCircleIconButton
import com.vasilisneo.trackstar.ui.components.GoogleSignInButton
import com.vasilisneo.trackstar.ui.components.OrDivider

@Composable
fun LoginScreen(
    viewModel: LoginViewModel = viewModel(),
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {},
    onForgotPassword: () -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize()) {
        AuthBackground()

        // Fixed wordmark — part of the background, sits behind the scrollable content, never scrolls.
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            AuthWordmark()
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Nav bar — fixed, doesn't scroll with content
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showBackButton) {
                    GlassCircleIconButton(onClick = onBackClick, contentDescription = "Back")
                } else {
                    Spacer(modifier = Modifier.width(44.dp))
                }
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(44.dp))
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 20.dp)
            ) {
                // Title block
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 12.dp, bottom = 32.dp)
                ) {
                    Text(
                        text = "Welcome back",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Sign in to your Trackstar account",
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.45f)
                    )
                }

                // Email + password fields
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AuthTextField(
                        value = viewModel.email,
                        onValueChange = viewModel::onEmailChange,
                        placeholder = "Email"
                    )
                    AuthSecureField(
                        value = viewModel.password,
                        onValueChange = viewModel::onPasswordChange,
                        placeholder = "Password",
                        showPassword = viewModel.showPassword,
                        onToggleShowPassword = viewModel::toggleShowPassword
                    )
                }

                // Forgot password
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "Forgot Password?",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.clickable(onClick = onForgotPassword)
                    )
                }

                // Primary Log In button
                AuthCapsuleButton(
                    text = "Log In",
                    onClick = viewModel::login,
                    isLoading = viewModel.isLoading,
                    enabled = viewModel.isLoginEnabled,
                    modifier = Modifier.padding(top = 20.dp)
                )

                OrDivider(modifier = Modifier.padding(top = 20.dp, bottom = 16.dp))

                // Social sign-in — Google only; no Apple Sign In on Android
                GoogleSignInButton(
                    isLoading = viewModel.isGoogleLoading,
                    enabled = !viewModel.isLoading,
                    onClick = viewModel::loginWithGoogle
                )

                viewModel.errorMessage?.let { error ->
                    Text(
                        text = error,
                        fontSize = 13.sp,
                        color = Color.Red.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}
