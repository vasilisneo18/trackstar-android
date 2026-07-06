package com.vasilisneo.trackstar.ui.screens.login

// Replica of Trackstar/UI/View/ForgotPasswordView.swift on iOS — reached from Login's
// "Forgot Password?" link. Two states: the email-entry form, then a confirmation message
// after "sending" the reset link (simulated — see ForgotPasswordViewModel).

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vasilisneo.trackstar.ui.components.AuthCapsuleButton
import com.vasilisneo.trackstar.ui.components.AuthErrorText
import com.vasilisneo.trackstar.ui.components.AuthScreenScaffold
import com.vasilisneo.trackstar.ui.components.AuthTextField

@Composable
fun ForgotPasswordScreen(
    viewModel: ForgotPasswordViewModel = viewModel(),
    onBackClick: () -> Unit = {},
) {
    AuthScreenScaffold(
        title = if (viewModel.didSubmit) "Check your email" else "Forgot password?",
        subtitle = if (viewModel.didSubmit) {
            "We've sent a reset link to ${viewModel.email}. Tap it to set a new password."
        } else {
            "Enter your email and we'll send you a reset link."
        },
        showBackButton = true,
        onBackClick = onBackClick,
    ) {
        if (viewModel.didSubmit) {
            AuthCapsuleButton(text = "Back to Login", onClick = onBackClick)
        } else {
            Column {
                AuthTextField(
                    value = viewModel.email,
                    onValueChange = viewModel::onEmailChange,
                    placeholder = "Email"
                )

                viewModel.errorMessage?.let { error -> AuthErrorText(error) }

                AuthCapsuleButton(
                    text = "Send Reset Link",
                    onClick = viewModel::submit,
                    isLoading = viewModel.isLoading,
                    enabled = viewModel.email.isNotBlank(),
                    modifier = Modifier.padding(top = 20.dp)
                )
            }
        }
    }
}
