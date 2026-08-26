package com.vasilisneo.trackstar.ui.screens.main.coach

// The coach's "Bronze Grants" screen — the athletes they've spent a Bronze credit on. Opened by
// tapping the Bronze Credits card in Profile. Swipe a row to revoke a grant (athlete → free,
// credit refunded). Mirrors iOS's BronzeGrantsView.

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vasilisneo.trackstar.data.api.BronzeGrantResponse
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.workout.AthleteRepository
import com.vasilisneo.trackstar.ui.components.GlassCircleIconButton
import com.vasilisneo.trackstar.ui.components.initialsFrom
import com.vasilisneo.trackstar.ui.theme.trackstarBackground
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val Bronze = Color(0xFFCD7F32)

private fun grantName(grant: BronzeGrantResponse): String =
    listOfNotNull(grant.firstName?.ifBlank { null }, grant.lastName?.ifBlank { null })
        .joinToString(" ").ifBlank { grant.email ?: "Athlete" }

@Composable
fun BronzeGrantsScreen(onBack: () -> Unit) {
    val repo = remember { AthleteRepository() }
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    val grants = remember { mutableStateListOf<BronzeGrantResponse>() }
    var pendingRevoke by remember { mutableStateOf<BronzeGrantResponse?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val result = (repo.getBronzeGrants() as? ApiResult.Success)?.data ?: emptyList()
        grants.clear(); grants.addAll(result); loading = false
    }

    Box(modifier = Modifier.fillMaxSize().trackstarBackground()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp)) {
                GlassCircleIconButton(onClick = onBack, contentDescription = "Back", icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft)
                Spacer(Modifier.weight(1f))
            }
            Column(modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 20.dp)) {
                Text("Bronze Grants", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(6.dp))
                Text("Athletes you've given a free Bronze plan. Swipe a row to revoke.", fontSize = 14.sp, color = Color.White.copy(alpha = 0.45f))
            }

            when {
                loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Bronze) }
                grants.isEmpty() -> EmptyGrants()
                else -> LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(grants, key = { it.athleteId ?: it.hashCode().toString() }) { grant ->
                        SwipeableGrantRow(grant) { pendingRevoke = grant }
                    }
                }
            }
        }
    }

    pendingRevoke?.let { grant ->
        AlertDialog(
            onDismissRequest = { pendingRevoke = null },
            title = { Text("Revoke Bronze grant?") },
            text = { Text("${grantName(grant)} will return to the Free plan and the credit will be refunded to you.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRevoke = null
                    val id = grant.athleteId ?: return@TextButton
                    scope.launch {
                        when (repo.revokeBronzeGrant(id)) {
                            is ApiResult.Success -> grants.removeAll { it.athleteId == id }
                            else -> errorMsg = "Couldn't revoke — please try again."
                        }
                    }
                }) { Text("Revoke", color = Color(0xFFE5484D)) }
            },
            dismissButton = { TextButton(onClick = { pendingRevoke = null }) { Text("Cancel") } },
        )
    }

    errorMsg?.let { msg ->
        AlertDialog(
            onDismissRequest = { errorMsg = null },
            title = { Text("Couldn't revoke") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { errorMsg = null }) { Text("OK") } },
        )
    }
}

// iOS-style swipe: dragging left settles open to an anchor (no spring-back) and reveals a circular
// Revoke button that fades in with the swipe. Tapping it opens the confirm dialog and closes the row.
@Composable
private fun SwipeableGrantRow(grant: BronzeGrantResponse, onRevoke: () -> Unit) {
    val density = LocalDensity.current
    val revealPx = with(density) { 88.dp.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val progress = (-offsetX.value / revealPx).coerceIn(0f, 1f)

    Box(modifier = Modifier.fillMaxWidth()) {
        // Circular action floating in the revealed strip, padded off the card edge, fading in on swipe.
        Box(
            modifier = Modifier.matchParentSize().padding(end = 18.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                modifier = Modifier.size(52.dp).alpha(progress).clip(CircleShape).background(Color(0xFFE5484D))
                    .clickable(enabled = progress > 0.5f) {
                        scope.launch { offsetX.animateTo(0f) }
                        onRevoke()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Revoke", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch { offsetX.snapTo((offsetX.value + delta).coerceIn(-revealPx, 0f)) }
                    },
                    onDragStopped = {
                        scope.launch { offsetX.animateTo(if (offsetX.value < -revealPx / 2f) -revealPx else 0f) }
                    },
                ),
        ) {
            GrantRow(grant)
        }
    }
}

@Composable
private fun EmptyGrants() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 80.dp, start = 32.dp, end = 32.dp),
    ) {
        Icon(Icons.Filled.MilitaryTech, contentDescription = null, tint = Bronze.copy(alpha = 0.4f), modifier = Modifier.size(46.dp))
        Text("No Bronze grants yet", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.6f))
        Text(
            "When you add an athlete and spend a Bronze credit, they'll show up here.",
            fontSize = 13.sp, color = Color.White.copy(alpha = 0.4f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun GrantRow(grant: BronzeGrantResponse) {
    val name = grantName(grant)
    val active = grant.plan.equals("bronze", ignoreCase = true)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.06f)).padding(14.dp),
    ) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(Bronze.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Text(initialsFrom(name), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Bronze)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
            grant.email?.takeIf { it.isNotBlank() && it != name }?.let {
                Text(it, fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f), maxLines = 1)
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            if (active) expiryLabel(grant.grantExpiresAt) else "Expired",
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = if (active) Bronze else Color.White.copy(alpha = 0.4f),
            modifier = Modifier.clip(RoundedCornerShape(50))
                .background((if (active) Bronze else Color.White).copy(alpha = if (active) 0.2f else 0.08f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

private fun expiryLabel(epochMs: Long?): String {
    if (epochMs == null) return "Active"
    return runCatching {
        val d = java.time.Instant.ofEpochMilli(epochMs).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        "Until " + d.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy"))
    }.getOrDefault("Active")
}
