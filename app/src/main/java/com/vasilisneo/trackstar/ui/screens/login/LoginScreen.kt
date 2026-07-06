package com.vasilisneo.trackstar.ui.screens.login

// Replica of Trackstar/UI/View/Auth/LoginView.swift on iOS — same paddings, heights,
// corner radii, and font sizes. The iOS screen has a collapsing-nav-bar scroll animation
// (title fading in as you scroll) that isn't reproduced here (imePadding + a plain
// scrollable Column handles keyboard avoidance instead); every other visual detail
// (backgrounds, back button, wordmark, fields, buttons, spacing, error message) matches.
// See RegistrationHelpers.swift for the source values. Uses AuthScreenScaffold, the shell
// shared by every scrollable auth screen.

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vasilisneo.trackstar.ui.components.AuthCapsuleButton
import com.vasilisneo.trackstar.ui.components.AuthErrorText
import com.vasilisneo.trackstar.ui.components.AuthScreenScaffold
import com.vasilisneo.trackstar.ui.components.AuthSecureField
import com.vasilisneo.trackstar.ui.components.AuthTextField
import com.vasilisneo.trackstar.ui.components.GoogleSignInButton
import com.vasilisneo.trackstar.ui.components.OrDivider

@Composable
fun LoginScreen(
    viewModel: LoginViewModel = viewModel(),
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {},
    onForgotPassword: () -> Unit = {},
    initialEmail: String = "",
) {
    // Matches iOS's navigateToLogin(email:) — pre-fills the email field when arriving
    // here from the "this email is already registered" sheet in Email Entry.
    LaunchedEffect(Unit) {
        if (initialEmail.isNotBlank()) viewModel.onEmailChange(initialEmail)
    }

    AuthScreenScaffold(
        title = "Welcome back",
        subtitle = "Sign in to your Trackstar account",
        showBackButton = showBackButton,
        onBackClick = onBackClick,
    ) {
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

        viewModel.errorMessage?.let { error -> AuthErrorText(error) }
    }
}
