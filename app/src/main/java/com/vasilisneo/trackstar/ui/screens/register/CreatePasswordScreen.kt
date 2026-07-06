package com.vasilisneo.trackstar.ui.screens.register

// Replica of CreatePasswordView (step 1/5 in Trackstar/UI/View/Registration/CreateAccountView.swift)
// on iOS — second step of the registration flow, reached after Email Entry confirms a new email.

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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

@Composable
fun CreatePasswordScreen(
    viewModel: RegisterViewModel = viewModel(),
    onBackClick: () -> Unit = {},
    onContinue: () -> Unit = {},
) {
    val canContinue = viewModel.isPasswordValid && viewModel.passwordsMatch

    AuthScreenScaffold(
        title = "Create Password",
        subtitle = "Choose a secure password for your account",
        showBackButton = true,
        onBackClick = onBackClick,
        navBarTrailing = {
            Text(
                text = "1 / 5",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.45f)
            )
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AuthSecureField(
                value = viewModel.password,
                onValueChange = viewModel::onPasswordChange,
                placeholder = "Password",
                showPassword = viewModel.showPassword,
                onToggleShowPassword = viewModel::toggleShowPassword
            )
            AuthSecureField(
                value = viewModel.confirmPassword,
                onValueChange = viewModel::onConfirmPasswordChange,
                placeholder = "Confirm Password",
                showPassword = viewModel.showConfirmPassword,
                onToggleShowPassword = viewModel::toggleShowConfirmPassword
            )

            if (viewModel.password.isNotEmpty()) {
                PasswordHints(viewModel = viewModel, modifier = Modifier.padding(top = 4.dp))
            }
        }

        viewModel.errorMessage?.let { error -> AuthErrorText(error) }

        AuthCapsuleButton(
            text = "Continue",
            onClick = onContinue,
            enabled = canContinue,
            modifier = Modifier.padding(top = 20.dp)
        )
    }
}

@Composable
private fun PasswordHints(viewModel: RegisterViewModel, modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        PasswordHint("At least 8 characters", met = viewModel.isPasswordValid)
        if (viewModel.confirmPassword.isNotEmpty()) {
            PasswordHint("Passwords match", met = viewModel.passwordsMatch)
        }
    }
}

@Composable
private fun PasswordHint(label: String, met: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (met) Icons.Filled.CheckCircle else Icons.Filled.Circle,
            contentDescription = null,
            tint = if (met) Color.Green else Color.White.copy(alpha = 0.25f),
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = if (met) Color.White.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.3f)
        )
    }
}
