package com.vasilisneo.trackstar.ui.screens.main.activity

// Activity groups on Android: my groups, create / join (QR or link), the group page (live map,
// today's leaderboard, members) and the invite screen (QR + link). Mirrors iOS's GroupsViews.swift.

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.vasilisneo.trackstar.data.activity.ActivityFormat
import com.vasilisneo.trackstar.data.activity.ActivityType
import com.vasilisneo.trackstar.data.activity.GroupStore
import com.vasilisneo.trackstar.data.api.GroupDetail
import com.vasilisneo.trackstar.data.api.GroupSummary
import com.vasilisneo.trackstar.data.api.LeaderboardEntry
import com.vasilisneo.trackstar.data.api.LiveMember
import com.vasilisneo.trackstar.ui.components.QrCameraScanner
import com.vasilisneo.trackstar.ui.i18n.Text
import com.vasilisneo.trackstar.ui.i18n.tr
import com.vasilisneo.trackstar.ui.i18n.trf
import com.vasilisneo.trackstar.ui.theme.TrackstarAccent
import com.vasilisneo.trackstar.ui.theme.trackstarBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val CardFill = Color.White.copy(alpha = 0.06f)
private val LiveGreen = Color(0xFF30D158)

private fun membersLabel(n: Int) = if (n == 1) tr("1 member") else trf("%s members", "$n")

// MARK: - Leaderboard categories & metrics

enum class LeaderboardMetric(val label: String) {
    TopSpeed("Top speed"), Vertical("Vertical"), Runs("Runs"), Distance("Distance"),
    Pace("Best pace"), Time("Time"), Elevation("Elevation");

    /** Sort value (higher is better; pace is inverted). */
    fun score(e: LeaderboardEntry): Double? = when (this) {
        TopSpeed -> e.topSpeedKmh.takeIf { it > 0 }
        Vertical -> e.verticalMeters.takeIf { it > 0 }
        Runs -> e.runs.toDouble().takeIf { it > 0 }
        Distance -> e.distanceMeters.takeIf { it > 0 }
        Pace -> e.bestPaceSecondsPerKm?.let { -it }
        Time -> e.movingSeconds.toDouble().takeIf { it > 0 }
        Elevation -> e.elevationGainMeters.takeIf { it > 0 }
    }

    fun format(e: LeaderboardEntry): Pair<String, String> = when (this) {
        TopSpeed -> "${e.topSpeedKmh.toInt()}" to "km/h"
        Vertical -> "${e.verticalMeters.toInt()}" to "m"
        Runs -> "${e.runs}" to ""
        Distance -> ActivityFormat.distance(e.distanceMeters) to "km"
        Pace -> ActivityFormat.pace(e.bestPaceSecondsPerKm) to "/km"
        Time -> ActivityFormat.duration(e.movingSeconds) to ""
        Elevation -> "${e.elevationGainMeters.toInt()}" to "m"
    }
}

enum class LeaderboardCategory(val raw: String, val label: String, val type: ActivityType, val metrics: List<LeaderboardMetric>) {
    Slopes("slopes", "Slopes", ActivityType.Ski, listOf(LeaderboardMetric.TopSpeed, LeaderboardMetric.Vertical, LeaderboardMetric.Runs, LeaderboardMetric.Distance)),
    Run("run", "Run", ActivityType.Run, listOf(LeaderboardMetric.Distance, LeaderboardMetric.Pace, LeaderboardMetric.Time)),
    Walk("walk", "Walk", ActivityType.Walk, listOf(LeaderboardMetric.Distance, LeaderboardMetric.Pace, LeaderboardMetric.Time)),
    Hike("hike", "Hike", ActivityType.Hike, listOf(LeaderboardMetric.Distance, LeaderboardMetric.Elevation, LeaderboardMetric.Time)),
    Ride("ride", "Ride", ActivityType.Ride, listOf(LeaderboardMetric.Distance, LeaderboardMetric.TopSpeed, LeaderboardMetric.Elevation)),
}

// MARK: - My groups

@Composable
fun GroupsDialog(onClose: () -> Unit) {
    val groups by GroupStore.groups.collectAsState()
    val hasLoaded by GroupStore.hasLoaded.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var joining by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf<GroupSummary?>(null) }
    LaunchedEffect(Unit) { GroupStore.refresh() }

    AppSheet(onDismissRequest = onClose) {
        Box(Modifier.fillMaxSize().trackstarBackground()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(top = LocalBarInsets.current.top + 72.dp, bottom = LocalBarInsets.current.bottom + 40.dp)
                    .padding(horizontal = 16.dp)
            ) {
                Text("Groups", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("See each other live while you record and compare today's stats.", fontSize = 14.sp, color = Color.White.copy(alpha = 0.55f))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    PillButton("Create group", Icons.Filled.Add, filled = true, Modifier.weight(1f)) { creating = true }
                    PillButton("Join", Icons.Filled.QrCodeScanner, filled = false, Modifier.weight(1f)) { joining = true }
                }
                if (groups.isEmpty() && hasLoaded) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp)) {
                        Icon(Icons.Filled.Groups, null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(38.dp))
                        Text("No groups yet", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.6f))
                        Text("Create one and share its QR code or link with your friends.", fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.4f), textAlign = TextAlign.Center)
                    }
                }
                groups.forEach { g -> GroupRow(g) { opened = g } }
            }
            Row(Modifier.fillMaxWidth().padding(top = LocalBarInsets.current.top).padding(horizontal = 20.dp, vertical = 14.dp)) {
                RoundButton(Icons.Filled.Close, tr("Close"), onClose)
            }
        }
        if (creating) CreateGroupDialog(onCreated = { g -> creating = false; opened = g }, onCancel = { creating = false })
        if (joining) JoinGroupDialog(onJoined = { g -> joining = false; opened = g }, onCancel = { joining = false })
        opened?.let { g -> GroupDetailDialog(g.id, g.name, onClose = { opened = null }) }
    }
}

@Composable
private fun PillButton(title: String, icon: ImageVector, filled: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.height(50.dp).clip(RoundedCornerShape(50))
            .background(if (filled) TrackstarAccent.copy(alpha = if (enabled) 1f else 0.4f) else Color.White.copy(alpha = 0.1f))
            .clickable(enabled = enabled, onClick = onClick)) {
        Icon(icon, null, tint = if (filled) Color.White else Color.White.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (filled) Color.White else Color.White.copy(alpha = 0.85f))
    }
}

@Composable
private fun GroupRow(g: GroupSummary, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardFill).clickable(onClick = onClick).padding(14.dp)) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(TrackstarAccent.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Groups, null, tint = TrackstarAccent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            androidx.compose.material3.Text(g.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            androidx.compose.material3.Text(membersLabel(g.memberCount), fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f))
        }
        if (g.liveCount > 0) LiveBadge(trf("%s live", "${g.liveCount}"))
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(12.dp))
    }
}

@Composable
private fun LiveBadge(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(LiveGreen.copy(alpha = 0.12f)).padding(horizontal = 9.dp, vertical = 5.dp)) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(LiveGreen))
        Spacer(Modifier.width(5.dp))
        androidx.compose.material3.Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = LiveGreen)
    }
}

// MARK: - Create / join

@Composable
private fun CreateGroupDialog(onCreated: (GroupSummary) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { delay(200); runCatching { focus.requestFocus() } }
    fun create() {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || busy) return
        busy = true
        scope.launch {
            runCatching { GroupStore.create(trimmed) }
                .onSuccess(onCreated)
                .onFailure { error = tr("Couldn't create the group. Check your connection and try again.") }
            busy = false
        }
    }
    AppSheet(onDismissRequest = onCancel) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().trackstarBackground().padding(top = LocalBarInsets.current.top, bottom = LocalBarInsets.current.bottom).padding(20.dp)) {
            RoundButton(Icons.Filled.Close, tr("Close"), onCancel)
            Text("New group", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Field(name, { name = it }, tr("e.g. Troodos crew"), Modifier.focusRequester(focus), onDone = ::create)
            error?.let { androidx.compose.material3.Text(it, fontSize = 13.sp, color = Color(0xFFFF453A)) }
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(50))
                .background(TrackstarAccent.copy(alpha = if (name.isBlank()) 0.4f else 1f))
                .clickable(enabled = !busy && name.isNotBlank()) { create() }, contentAlignment = Alignment.Center) {
                if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                else Text("Create group", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, onDone: () -> Unit = {}) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.1f)).padding(14.dp)) {
        if (value.isEmpty()) androidx.compose.material3.Text(placeholder, fontSize = 17.sp, color = Color.White.copy(alpha = 0.4f))
        BasicTextField(value = value, onValueChange = onChange, singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 17.sp), cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = modifier.fillMaxWidth())
    }
}

/** Join by scanning a group QR code or pasting its link. */
@Composable
private fun JoinGroupDialog(onJoined: (GroupSummary) -> Unit, onCancel: () -> Unit) {
    var link by remember { mutableStateOf("") }
    var token by remember { mutableStateOf<String?>(null) }
    var invalid by remember { mutableStateOf(false) }
    AppSheet(onDismissRequest = onCancel) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().trackstarBackground().padding(top = LocalBarInsets.current.top, bottom = LocalBarInsets.current.bottom).padding(20.dp)) {
            RoundButton(Icons.Filled.Close, tr("Close"), onCancel)
            Text("Join a group", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Scan the group's QR code, or paste the link someone sent you.", fontSize = 14.sp, color = Color.White.copy(alpha = 0.55f))
            Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(22.dp))) {
                if (token == null) QrCameraScanner(onCode = { code -> GroupStore.token(code)?.let { token = it } })
            }
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.1f)).padding(12.dp)) {
                Box(Modifier.weight(1f)) {
                    if (link.isEmpty()) Text("Paste group link", fontSize = 15.sp, color = Color.White.copy(alpha = 0.4f))
                    BasicTextField(value = link, onValueChange = { link = it; invalid = false }, singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp), cursorBrush = SolidColor(Color.White), modifier = Modifier.fillMaxWidth())
                }
                Text("Go", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.Black,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = if (link.isBlank()) 0.5f else 1f))
                        .clickable(enabled = link.isNotBlank()) { GroupStore.token(link)?.let { token = it } ?: run { invalid = true } }
                        .padding(horizontal = 14.dp, vertical = 8.dp))
            }
            if (invalid) Text("This group link is no longer valid.", fontSize = 13.sp, color = Color(0xFFFF453A))
        }
        token?.let { t -> JoinGroupConfirmDialog(t, onJoined = { token = null; onJoined(it) }, onCancel = { token = null }) }
    }
}

/** "Join Troodos crew? · 5 members" — for a scanned QR, a pasted link or a tapped link. */
@Composable
fun JoinGroupConfirmDialog(token: String, onJoined: (GroupSummary) -> Unit, onCancel: () -> Unit) {
    var preview by remember { mutableStateOf<com.vasilisneo.trackstar.data.api.GroupPreview?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(token) {
        runCatching { GroupStore.preview(token) }.onSuccess { preview = it }.onFailure { error = tr("This group link is no longer valid.") }
    }
    AppSheet(onDismissRequest = onCancel) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().trackstarBackground().padding(top = LocalBarInsets.current.top + 40.dp, bottom = LocalBarInsets.current.bottom).padding(20.dp)) {
            Box(Modifier.size(72.dp).clip(CircleShape).background(TrackstarAccent.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Groups, null, tint = TrackstarAccent, modifier = Modifier.size(32.dp))
            }
            val p = preview
            when {
                p != null -> {
                    androidx.compose.material3.Text(p.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
                    androidx.compose.material3.Text(membersLabel(p.memberCount), fontSize = 14.sp, color = Color.White.copy(alpha = 0.55f))
                    Text("Members see you on the map only while you record with live sharing on, and compare today's stats.",
                        fontSize = 13.sp, color = Color.White.copy(alpha = 0.45f), textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 12.dp))
                }
                error != null -> androidx.compose.material3.Text(error!!, fontSize = 15.sp, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                else -> CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.padding(vertical = 20.dp))
            }
            Spacer(Modifier.weight(1f))
            if (p != null) {
                Box(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(50)).background(TrackstarAccent).clickable(enabled = !busy) {
                    busy = true
                    scope.launch {
                        runCatching { GroupStore.join(token) }.onSuccess(onJoined)
                            .onFailure { error = tr("Couldn't join the group. The link may have been reset."); preview = null }
                        busy = false
                    }
                }, contentAlignment = Alignment.Center) {
                    if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    else Text(if (p.alreadyMember) "Open group" else "Join group", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
            Text("Not now", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.clickable(onClick = onCancel).padding(8.dp))
        }
    }
}

// MARK: - Group page

@Composable
fun GroupDetailDialog(groupId: String, initialName: String, onClose: () -> Unit) {
    var detail by remember { mutableStateOf<GroupDetail?>(null) }
    var category by remember { mutableStateOf(LeaderboardCategory.Slopes) }
    var metric by remember { mutableStateOf(LeaderboardMetric.TopSpeed) }
    var categoryChosen by remember { mutableStateOf(false) }
    var showInvite by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var removing by remember { mutableStateOf<com.vasilisneo.trackstar.data.api.GroupMember?>(null) }
    var reload by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val name = detail?.name ?: initialName

    suspend fun load() {
        val d = runCatching { GroupStore.detail(groupId) }.getOrNull() ?: return
        detail = d
        if (!categoryChosen) {
            val counts = d.leaderboard.groupingBy { it.category }.eachCount()
            LeaderboardCategory.entries.maxByOrNull { counts[it.raw] ?: 0 }?.takeIf { (counts[it.raw] ?: 0) > 0 }?.let {
                category = it; metric = it.metrics[0]
            }
        }
    }
    // Live: refresh every 10 s while the page is open.
    LaunchedEffect(reload) { while (true) { load(); delay(10_000) } }

    fun memberName(id: String) = detail?.members?.firstOrNull { it.userId == id }?.let { if (it.isMe) tr("You") else it.name } ?: tr("Member")
    fun initials(id: String) = detail?.members?.firstOrNull { it.userId == id }?.initials ?: "?"

    AppSheet(onDismissRequest = onClose) {
        Box(Modifier.fillMaxSize().trackstarBackground()) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(top = LocalBarInsets.current.top + 72.dp, bottom = LocalBarInsets.current.bottom + 40.dp).padding(horizontal = 16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    androidx.compose.material3.Text(name, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    detail?.let { d ->
                        val m = membersLabel(d.members.size)
                        androidx.compose.material3.Text(if (d.live.isEmpty()) m else "$m · ${trf("%s live", "${d.live.size}")}",
                            fontSize = 14.sp, color = Color.White.copy(alpha = 0.55f))
                    }
                }
                LiveMap(detail?.live.orEmpty(), ::initials, ::memberName)
                Leaderboard(detail, category, metric, ::initials, ::memberName,
                    onCategory = { categoryChosen = true; category = it; metric = it.metrics[0] }, onMetric = { metric = it })
                Members(detail) { m -> removing = m }
                Text("Leave group", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFFF453A),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardFill).clickable { confirmLeave = true }.padding(vertical = 14.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(top = LocalBarInsets.current.top).padding(horizontal = 20.dp, vertical = 14.dp)) {
                RoundButton(Icons.Filled.ExpandMore, tr("Close"), onClose)
                Spacer(Modifier.weight(1f))
                if (detail?.isOwner == true) RoundButton(Icons.Filled.Edit, tr("Rename")) { newName = name; renaming = true }
                if (detail != null) RoundButton(Icons.Filled.PersonAdd, tr("Invite")) { showInvite = true }
            }
        }
        val d = detail
        if (showInvite && d != null) GroupInviteDialog(d.id, d.name, d.inviteLink, d.isOwner) { showInvite = false }
        if (confirmLeave) AlertDialog(
            onDismissRequest = { confirmLeave = false }, containerColor = Color(0xFF1A1A26),
            title = { Text("Leave this group?", color = Color.White) },
            text = { Text("You'll stop seeing the group's live map and leaderboard. You can join again with its link.", color = Color.White.copy(alpha = 0.7f)) },
            confirmButton = { TextButton(onClick = { confirmLeave = false; scope.launch { runCatching { GroupStore.leave(groupId) }; onClose() } }) { Text("Leave", color = Color(0xFFFF453A)) } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Cancel", color = Color.White) } },
        )
        if (renaming) AlertDialog(
            onDismissRequest = { renaming = false }, containerColor = Color(0xFF1A1A26),
            title = { Text("Rename group", color = Color.White) },
            text = { Field(newName, { newName = it }, tr("Name")) },
            confirmButton = { TextButton(onClick = {
                renaming = false
                val n = newName.trim()
                if (n.isNotEmpty()) scope.launch { runCatching { GroupStore.rename(groupId, n) }; reload++ }
            }) { Text("Save", color = TrackstarAccent) } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel", color = Color.White) } },
        )
        removing?.let { m ->
            AlertDialog(
                onDismissRequest = { removing = null }, containerColor = Color(0xFF1A1A26),
                title = { Text("Remove from group", color = Color.White) },
                text = { androidx.compose.material3.Text(m.name, color = Color.White.copy(alpha = 0.7f)) },
                confirmButton = { TextButton(onClick = {
                    removing = null
                    scope.launch { runCatching { GroupStore.removeMember(groupId, m.userId) }; reload++ }
                }) { Text("Remove", color = Color(0xFFFF453A)) } },
                dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel", color = Color.White) } },
            )
        }
    }
}

@Composable
private fun LiveMap(live: List<LiveMember>, initials: (String) -> String, name: (String) -> String) {
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(LatLng(35.17, 33.36), 9f) }
    val ids = live.map { it.userId }.sorted()
    LaunchedEffect(ids) {
        val b = boundsOf(live.map { LatLng(it.latitude, it.longitude) }) ?: return@LaunchedEffect
        runCatching {
            if (live.size == 1) camera.animate(CameraUpdateFactory.newLatLngZoom(b.center, 14f), 500)
            else camera.animate(CameraUpdateFactory.newLatLngBounds(b, 120), 500)
        }
    }
    Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(24.dp))) {
        GoogleMap(modifier = Modifier.fillMaxSize(), cameraPositionState = camera, properties = darkMapProperties,
            uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false, myLocationButtonEnabled = false)) {
            LiveMemberMarkers(live, initials, name)
        }
        if (live.isEmpty()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.align(Alignment.Center).padding(24.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(14.dp)) {
                Icon(Icons.Filled.Sensors, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
                Text("No one is live right now", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text("Members appear here while they record with live sharing on.", fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.65f), textAlign = TextAlign.Center)
            }
        }
    }
}

/** Group members on a map: initials bubble with activity badge and "Name · 32 km/h" underneath. */
@Composable
@GoogleMapComposable
fun LiveMemberMarkers(live: List<LiveMember>, initials: (String) -> String, name: (String) -> String) {
    for (m in live) {
        val pos = LatLng(m.latitude, m.longitude)
        val state = rememberMarkerState(key = m.userId, position = pos)
        LaunchedEffect(pos) { state.position = pos }
        val first = name(m.userId).split(" ").firstOrNull().orEmpty()
        val type = ActivityType.entries.firstOrNull { it.raw == m.activityType } ?: ActivityType.Run
        MarkerComposable(m.userId, first, m.speedKmh.toInt(), type, state = state, anchor = Offset(0.5f, 1f)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box {
                    InitialsAvatar(initials(m.userId), 34.dp, Modifier.border(2.dp, Color.White, CircleShape))
                    Box(Modifier.align(Alignment.BottomEnd).offset(x = 3.dp, y = 3.dp).size(15.dp).clip(CircleShape).background(Color.White),
                        contentAlignment = Alignment.Center) { Icon(type.icon(), null, tint = Color.Black, modifier = Modifier.size(10.dp)) }
                }
                Spacer(Modifier.height(2.dp))
                androidx.compose.material3.Text("$first · ${m.speedKmh.toInt()} km/h", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
    }
}

@Composable
fun InitialsAvatar(initials: String, size: Dp = 36.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(TrackstarAccent.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
        androidx.compose.material3.Text(initials, fontSize = (size.value * 0.38f).sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun Leaderboard(
    detail: GroupDetail?, category: LeaderboardCategory, metric: LeaderboardMetric,
    initials: (String) -> String, name: (String) -> String,
    onCategory: (LeaderboardCategory) -> Unit, onMetric: (LeaderboardMetric) -> Unit,
) {
    val ranked = detail?.leaderboard.orEmpty().filter { it.category == category.raw }
        .mapNotNull { e -> metric.score(e)?.let { e to it } }.sortedByDescending { it.second }.map { it.first }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardFill).padding(16.dp)) {
        Text("Today", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            LeaderboardCategory.entries.forEach { c ->
                val sel = c == category
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (sel) TrackstarAccent else Color.White.copy(alpha = 0.08f))
                        .clickable { onCategory(c) }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Icon(c.type.icon(), null, tint = if (sel) Color.White else Color.White.copy(alpha = 0.55f), modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(c.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (sel) Color.White else Color.White.copy(alpha = 0.55f))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            category.metrics.forEach { m ->
                val sel = m == metric
                Text(m.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (sel) Color.White else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (sel) TrackstarAccent.copy(alpha = 0.25f) else Color.Transparent)
                        .border(1.dp, if (sel) TrackstarAccent else Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
                        .clickable { onMetric(m) }.padding(horizontal = 11.dp, vertical = 6.dp))
            }
        }
        if (ranked.isEmpty()) {
            Text("No one has recorded this today yet.", fontSize = 13.sp, color = Color.White.copy(alpha = 0.45f),
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp))
        }
        ranked.forEachIndexed { i, e ->
            val rank = i + 1
            val medal = when (rank) { 1 -> Color(0xFFFFC733); 2 -> Color(0xFFC7C7C7); 3 -> Color(0xFFCC8040); else -> null }
            val (value, unit) = metric.format(e)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(medal ?: Color.White.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Text("$rank", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (medal == null) Color.White.copy(alpha = 0.6f) else Color.Black)
                }
                Spacer(Modifier.width(12.dp))
                InitialsAvatar(initials(e.userId))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    androidx.compose.material3.Text(name(e.userId), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
                    if (e.live) Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(LiveGreen))
                        Spacer(Modifier.width(4.dp))
                        Text("Live", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = LiveGreen)
                    }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    androidx.compose.material3.Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    if (unit.isNotEmpty()) {
                        Spacer(Modifier.width(3.dp))
                        androidx.compose.material3.Text(unit, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.45f), modifier = Modifier.padding(bottom = 3.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Members(detail: GroupDetail?, onRemove: (com.vasilisneo.trackstar.data.api.GroupMember) -> Unit) {
    val list = detail?.members.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardFill).padding(16.dp)) {
        Text("Members", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
        list.forEach { m ->
            val canRemove = detail?.isOwner == true && !m.isMe
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().then(if (canRemove) Modifier.clickable { onRemove(m) } else Modifier)) {
                InitialsAvatar(m.initials)
                Spacer(Modifier.width(12.dp))
                androidx.compose.material3.Text(if (m.isMe) "${m.name} (${tr("you")})" else m.name, fontSize = 15.sp, color = Color.White)
                if (m.isOwner) {
                    Spacer(Modifier.width(8.dp))
                    Text("Owner", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.1f)).padding(horizontal = 8.dp, vertical = 3.dp))
                }
                Spacer(Modifier.weight(1f))
                if (detail?.live?.any { it.userId == m.userId } == true) Box(Modifier.size(8.dp).clip(CircleShape).background(LiveGreen))
            }
        }
    }
}

// MARK: - Invite

@Composable
private fun GroupInviteDialog(groupId: String, name: String, initialLink: String, isOwner: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    var link by remember { mutableStateOf(initialLink) }
    var confirmReset by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val qr = remember(link) { com.vasilisneo.trackstar.ui.screens.main.attendance.printableQrBitmap(link, 720)?.asImageBitmap() }
    AppSheet(onDismissRequest = onClose) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize().trackstarBackground().padding(top = LocalBarInsets.current.top, bottom = LocalBarInsets.current.bottom).padding(20.dp)) {
            Row(Modifier.fillMaxWidth()) { RoundButton(Icons.Filled.Close, tr("Close"), onClose) }
            androidx.compose.material3.Text(trf("Invite to %s", name), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
            Text("Friends scan this with their camera or the Trackstar app.", fontSize = 14.sp, color = Color.White.copy(alpha = 0.55f), textAlign = TextAlign.Center)
            if (qr != null) {
                Image(qr, contentDescription = null, modifier = Modifier.widthIn(max = 260.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp)).background(Color.White).padding(18.dp))
            }
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(50)).background(Color.White).clickable {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, trf("Join my group \"%s\" on Trackstar", name) + "\n" + link)
                    context.startActivity(Intent.createChooser(send, null))
                }) {
                Icon(Icons.Filled.Share, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Share link", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                cm?.setPrimaryClip(ClipData.newPlainText("Trackstar group", link))
                copied = true
            }.padding(8.dp)) {
                Icon(Icons.Filled.ContentCopy, null, tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (copied) "Copied" else "Copy link", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.75f))
            }
            if (isOwner) Text("Reset link", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.45f),
                modifier = Modifier.clickable { confirmReset = true }.padding(8.dp))
        }
        if (confirmReset) AlertDialog(
            onDismissRequest = { confirmReset = false }, containerColor = Color(0xFF1A1A26),
            title = { Text("Reset the invite link?", color = Color.White) },
            text = { Text("The current QR code and link stop working. Members stay in the group.", color = Color.White.copy(alpha = 0.7f)) },
            confirmButton = { TextButton(onClick = {
                confirmReset = false
                scope.launch { runCatching { GroupStore.resetInvite(groupId) }.getOrNull()?.let { link = it.inviteLink; copied = false } }
            }) { Text("Reset", color = Color(0xFFFF453A)) } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel", color = Color.White) } },
        )
    }
}
