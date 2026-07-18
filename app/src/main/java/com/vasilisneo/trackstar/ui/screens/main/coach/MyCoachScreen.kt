package com.vasilisneo.trackstar.ui.screens.main.coach

// Athlete-side "My Coach" screen, pushed from Profile (athletes have no MyTeam tab — that tab is
// Gold-coach-only, matching iOS, which surfaces the coach relationship inside ProfileView). When
// linked: a card with the coach's avatar, name and "coaching since"; when unlinked: an empty state
// pointing the athlete at their QR code (a coach scans it) — accepting an invite link is the other
// path, handled by AcceptInviteSheet via the trackstar://invite/{token} deep link.

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vasilisneo.trackstar.data.api.ProfileResponse
import com.vasilisneo.trackstar.ui.components.GlassCircleIconButton
import com.vasilisneo.trackstar.ui.components.initialsFrom
import com.vasilisneo.trackstar.ui.theme.TrackstarAccent
import com.vasilisneo.trackstar.ui.theme.trackstarBackground
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CardFill = Color.White.copy(alpha = 0.06f)
private val CoachAvatar = Color(0xFF5E5CE6)

@Composable
fun MyCoachScreen(
    onBack: () -> Unit = {},
    onShowQr: () -> Unit = {},
    viewModel: MyCoachViewModel = viewModel(),
) {
    // Re-fetch on return (e.g. after accepting an invite elsewhere) so a freshly linked coach shows.
    var skipFirstResume = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        if (skipFirstResume.value) skipFirstResume.value = false else viewModel.fetch()
        onPauseOrDispose { }
    }

    Box(modifier = Modifier.fillMaxSize().trackstarBackground()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Nav bar with a back button — this is a pushed detail from Profile, not a tab.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                GlassCircleIconButton(onClick = onBack, icon = Icons.Filled.Close, contentDescription = "Close")
                Spacer(modifier = Modifier.weight(1f))
            }

            Text(
                "My Coach",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            Box(modifier = Modifier.fillMaxSize().padding(bottom = 80.dp), contentAlignment = Alignment.Center) {
                val coach = viewModel.coach
                when {
                    !viewModel.loaded -> CircularProgressIndicator(color = Color.White.copy(alpha = 0.6f))
                    coach != null -> CoachCard(coach)
                    else -> EmptyState(onShowQr)
                }
            }
        }
    }
}

@Composable
private fun CoachCard(coach: ProfileResponse) {
    val name = listOfNotNull(coach.firstName?.ifBlank { null }, coach.lastName?.ifBlank { null })
        .joinToString(" ").ifBlank { "Your Coach" }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(CardFill)
            .padding(vertical = 32.dp, horizontal = 20.dp)
    ) {
        Box(
            modifier = Modifier.size(88.dp).clip(CircleShape).background(CoachAvatar),
            contentAlignment = Alignment.Center
        ) {
            Text(initialsFrom(name), fontSize = 32.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
        Spacer(Modifier.height(16.dp))
        Text(name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
        coachingSinceLabel(coach.coachingSince)?.let { since ->
            Spacer(Modifier.height(4.dp))
            Text(since, fontSize = 14.sp, color = Color.White.copy(alpha = 0.55f))
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Your coach manages your workout plan.",
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.45f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EmptyState(onShowQr: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)
    ) {
        Box(
            modifier = Modifier.size(80.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.QrCode2, contentDescription = null, tint = Color.White, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("No coach yet", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Text(
            "Connect with a coach by having them scan your QR code, or open the invite link they send you.",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(TrackstarAccent)
                .clickable(onClick = onShowQr)
                .padding(horizontal = 28.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.QrCode2, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Text("Show My QR Code", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}

// Formats the coach's "coachingSince" (a "yyyy-MM-dd" ISO date) as "Coaching since June 2025",
// mirroring iOS's formattedCoachingSince. Returns null if the date is missing/unparseable.
private fun coachingSinceLabel(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    val date = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return null
    val month = date.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
    return "Coaching since $month"
}
