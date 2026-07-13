package com.vasilisneo.trackstar.ui.screens.main.coach

// Ports iOS's AcceptInviteSheet: shown when an athlete opens a trackstar://invite/{token} deep
// link. Validates the token (without consuming it), then walks four states — validating /
// invalid(reason) / pending / accepted — matching the iOS copy for each reason. Accepting POSTs
// to /coach/invite/accept/{token}, which links the athlete to the coach.

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.workout.AthleteRepository
import kotlinx.coroutines.launch

private val SheetBackground = Color(0xFF1A1A24)

private sealed interface InviteState {
    data object Validating : InviteState
    data class Invalid(val reason: String) : InviteState
    data object Pending : InviteState
    data object Accepted : InviteState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcceptInviteSheet(
    token: String,
    coachName: String?,
    onDismiss: () -> Unit,
    // Called once after a successful accept so the host can refresh (e.g. re-fetch My Coach).
    onAccepted: () -> Unit = {},
) {
    val repo = remember { AthleteRepository() }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var state by remember { mutableStateOf<InviteState>(InviteState.Validating) }
    var isAccepting by remember { mutableStateOf(false) }
    var acceptError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(token) {
        state = when (val r = repo.validateInvite(token)) {
            is ApiResult.Success -> if (r.data.valid) InviteState.Pending else InviteState.Invalid(r.data.reason)
            // Network/parse failure — treat as a generic invalid rather than hanging on the spinner.
            is ApiResult.Error -> InviteState.Invalid("")
        }
    }

    fun accept() {
        isAccepting = true
        acceptError = null
        scope.launch {
            when (repo.acceptInvite(token)) {
                is ApiResult.Success -> {
                    isAccepting = false
                    state = InviteState.Accepted
                    onAccepted()
                }
                is ApiResult.Error -> {
                    isAccepting = false
                    acceptError = "Something went wrong. Please try again."
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetBackground,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 20.dp),
        ) {
            when (val s = state) {
                is InviteState.Validating -> Box(Modifier.height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
                is InviteState.Invalid -> InvalidContent(s.reason, onDismiss)
                is InviteState.Pending -> PendingContent(
                    coachName = coachName,
                    isAccepting = isAccepting,
                    error = acceptError,
                    onAccept = ::accept,
                    onDecline = onDismiss,
                )
                is InviteState.Accepted -> AcceptedContent(onDismiss)
            }
        }
    }
}

@Composable
private fun InvalidContent(reason: String, onDismiss: () -> Unit) {
    IconBadge(Icons.Filled.Close, Color(0xFFFF453A))
    Spacer(Modifier.height(24.dp))
    Text(invalidTitle(reason), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(invalidMessage(reason), fontSize = 14.sp, color = Color.White.copy(alpha = 0.55f), textAlign = TextAlign.Center)
    Spacer(Modifier.height(24.dp))
    SecondaryButton("Close", onDismiss)
}

@Composable
private fun PendingContent(
    coachName: String?,
    isAccepting: Boolean,
    error: String?,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    IconBadge(Icons.Filled.PersonAddAlt1, Color.White.copy(alpha = 0.9f), tint = Color.White)
    Spacer(Modifier.height(24.dp))
    if (!coachName.isNullOrBlank()) {
        Text(coachName, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("has invited you to join their team on Trackstar.", fontSize = 14.sp, color = Color.White.copy(alpha = 0.55f), textAlign = TextAlign.Center)
    } else {
        Text("Coach Invite", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("A coach has invited you to join their team on Trackstar.", fontSize = 14.sp, color = Color.White.copy(alpha = 0.55f), textAlign = TextAlign.Center)
    }
    if (error != null) {
        Spacer(Modifier.height(12.dp))
        Text(error, fontSize = 13.sp, color = Color(0xFFFF453A).copy(alpha = 0.85f), textAlign = TextAlign.Center)
    }
    Spacer(Modifier.height(24.dp))
    PrimaryButton(if (isAccepting) null else "Accept", enabled = !isAccepting, onClick = onAccept)
    Spacer(Modifier.height(10.dp))
    SecondaryButton("Decline", onDecline)
}

@Composable
private fun AcceptedContent(onDismiss: () -> Unit) {
    IconBadge(Icons.Filled.CheckCircle, Color(0xFF34C759).copy(alpha = 0.15f), tint = Color(0xFF34C759))
    Spacer(Modifier.height(24.dp))
    Text("You're on the team!", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
    Spacer(Modifier.height(8.dp))
    Text("Your coach can now manage your workout plan.", fontSize = 14.sp, color = Color.White.copy(alpha = 0.55f), textAlign = TextAlign.Center)
    Spacer(Modifier.height(24.dp))
    PrimaryButton("Done", onClick = onDismiss)
}

@Composable
private fun IconBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, fill: Color, tint: Color = fill) {
    Box(
        modifier = Modifier.size(72.dp).clip(CircleShape).background(if (tint == fill) fill.copy(alpha = 0.15f) else fill.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(34.dp))
    }
}

@Composable
private fun PrimaryButton(label: String?, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(Color.White)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (label == null) {
            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        } else {
            Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
        }
    }
}

@Composable
private fun SecondaryButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(Color.White.copy(alpha = 0.07f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.5f))
    }
}

private fun invalidTitle(reason: String): String = when (reason) {
    "used" -> "Invite Already Used"
    "expired" -> "Invite Expired"
    "already_linked" -> "Already Connected"
    "own_invite" -> "Waiting for Recipient"
    else -> "Invalid Invite"
}

private fun invalidMessage(reason: String): String = when (reason) {
    "used" -> "This invite has already been used. Ask your coach to send a new one."
    "expired" -> "This invite has expired. Ask your coach to generate a new one."
    "already_linked" -> "You're already connected to a coach. Leave your current team first."
    "own_invite" -> "This invite hasn't been used by your athlete yet. Share the link and wait for them to accept."
    else -> "This invite is no longer valid. Ask your coach to send a new one."
}
