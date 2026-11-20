package com.vasilisneo.trackstar.data.auth

// Before a log-in that would log out the account's oldest device (an account keeps up to three
// logged in), ask first. Asked from AuthRepository, so every way of logging in (email, Google)
// gets it; DeviceLimitPromptHost shows the dialog above everything. iOS: DeviceLimitPrompt.

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.vasilisneo.trackstar.ui.i18n.tr
import kotlinx.coroutines.CompletableDeferred

object DeviceLimitPrompt {
    private var answer by mutableStateOf<CompletableDeferred<Boolean>?>(null)

    /** True when the user chooses to log in anyway. */
    suspend fun confirm(): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        answer = deferred
        return try { deferred.await() } finally { answer = null }
    }

    @Composable
    fun Host() {
        val pending = answer ?: return
        AlertDialog(
            onDismissRequest = { pending.complete(false) },
            containerColor = Color(0xFF1A1A26),
            title = { Text(tr("Log out another device?"), color = Color.White) },
            text = {
                Text(tr("You're logged in on 3 devices. Logging in here logs out the one you logged in on longest ago."),
                    color = Color.White.copy(alpha = 0.7f))
            },
            confirmButton = { TextButton(onClick = { pending.complete(true) }) { Text(tr("Log In"), color = Color.White) } },
            dismissButton = { TextButton(onClick = { pending.complete(false) }) { Text(tr("Cancel"), color = Color.White.copy(alpha = 0.7f)) } },
        )
    }
}
