package com.vasilisneo.trackstar.ui.screens.main.attendance

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.WrongLocation
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vasilisneo.trackstar.data.api.GymResponse
import com.vasilisneo.trackstar.data.api.VisitResponse
import com.vasilisneo.trackstar.ui.theme.TrackstarAccent
import com.vasilisneo.trackstar.ui.theme.trackstarBackground
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CardFill = Color.White.copy(alpha = 0.06f)

// MARK: - Hub

@Composable
fun CoachCheckInHubScreen(onBack: () -> Unit, onSessionCode: () -> Unit, onAttendance: () -> Unit, onGyms: () -> Unit) {
    CollapsingTitleScaffold(title = "Check-Ins", onBack = onBack) {
        item {
            Text(
                "Run a session and track who trains with you.",
                fontSize = 14.sp, color = Color.White.copy(alpha = 0.45f),
                modifier = Modifier.padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 16.dp)
            )
        }
        item { HubRow(Icons.Filled.QrCode2, "Session Code", "Show a code athletes scan to check in", onSessionCode) }
        item { HubRow(Icons.Filled.People, "Attendance", "See who checked in and for how long", onAttendance) }
        item { HubRow(Icons.Filled.LocationOn, "Gyms", "QR posters for gym check-ins", onGyms) }
    }
}

@Composable
private fun HubRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp)
            .clip(RoundedCornerShape(18.dp)).background(CardFill).clickable(onClick = onClick).padding(16.dp)
    ) {
        Box(modifier = Modifier.size(46.dp).background(Color.White.copy(alpha = 0.08f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = TrackstarAccent, modifier = Modifier.size(20.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(subtitle, fontSize = 13.sp, color = Color.White.copy(alpha = 0.45f))
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
    }
}

// MARK: - Session code

@Composable
fun SessionCodeScreen(onBack: () -> Unit) {
    val vm: CoachCheckInViewModel = viewModel()
    LaunchedEffect(Unit) { vm.runSessionCodeLoop() }

    Column(modifier = Modifier.fillMaxSize().trackstarBackground().statusBarsPadding()) {
        // Back-only nav row (no scroll here, so the title stays a large static header).
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp)) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Back", tint = Color.White, modifier = Modifier.size(22.dp)) }
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Session Code", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
            Text(
                "Ask your athletes to scan this to check in. It refreshes automatically.",
                fontSize = 14.sp, color = Color.White.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier.size(280.dp).clip(RoundedCornerShape(28.dp)).background(CardFill),
                contentAlignment = Alignment.Center
            ) {
                val payload = vm.sessionQrPayload
                val bmp = remember(payload) { payload?.let { attendanceQrBitmap(it, 600) } }
                if (bmp != null) {
                    Image(bitmap = bmp.asImageBitmap(), contentDescription = "Session QR", modifier = Modifier.size(224.dp))
                } else {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(27.dp))
                    .background(Color.White.copy(alpha = 0.12f)).clickable(enabled = !vm.isEnding) { vm.endSession() },
                contentAlignment = Alignment.Center
            ) {
                if (vm.isEnding) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                else Text("End Session", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    vm.endedMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.clearEnded(); onBack() },
            confirmButton = { TextButton(onClick = { vm.clearEnded(); onBack() }) { Text("Done") } },
            title = { Text("Session ended") }, text = { Text(msg) },
        )
    }
    vm.error?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.clearError() },
            confirmButton = { TextButton(onClick = { vm.clearError() }) { Text("OK") } },
            title = { Text("Couldn't update") }, text = { Text(msg) },
        )
    }
}

// MARK: - Gyms

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun GymsScreen(onBack: () -> Unit) {
    val vm: CoachCheckInViewModel = viewModel()
    LaunchedEffect(Unit) { vm.loadGyms() }
    var showAdd by remember { mutableStateOf(false) }
    var qrGym by remember { mutableStateOf<GymResponse?>(null) }

    CollapsingTitleScaffold(
        title = "My Gyms",
        onBack = onBack,
        actions = {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).clickable { showAdd = true },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Add, "Add gym", tint = Color.White, modifier = Modifier.size(20.dp)) }
        },
    ) {
        item {
            Text(
                "Locations athletes can scan into. Tap one to show its QR poster.",
                fontSize = 14.sp, color = Color.White.copy(alpha = 0.45f),
                modifier = Modifier.padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 20.dp)
            )
        }
        if (vm.gyms.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 80.dp, start = 32.dp, end = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Filled.WrongLocation, null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(40.dp))
                    Text("No gyms yet", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.6f))
                    Text(
                        "Add a location, then print its QR so athletes can check in.",
                        fontSize = 13.sp, color = Color.White.copy(alpha = 0.4f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            items(vm.gyms, key = { it.id ?: it.hashCode().toString() }) { gym ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(18.dp)).background(CardFill)
                        .combinedClickable(
                            onClick = { qrGym = gym },
                            onLongClick = { gym.id?.let { vm.deleteGym(it) } },
                        ).padding(14.dp)
                ) {
                    Icon(Icons.Filled.Place, null, tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(26.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                        Text(gym.name ?: "Gym", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Text("Tap to show QR poster", fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f))
                    }
                    Icon(Icons.Filled.QrCode2, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var saving by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add Gym") },
            text = {
                Column {
                    Text("Adds a gym at your current location. Athletes at this gym can scan its QR to check in.", fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        placeholder = { Text("Gym name (e.g. Gold's, Nicosia)") },
                        singleLine = true, keyboardOptions = KeyboardOptions.Default,
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && !saving, onClick = {
                    saving = true
                    vm.addGym(name.trim()) { ok -> saving = false; if (ok) showAdd = false }
                }) { Text(if (saving) "Adding…" else "Add at my location") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } },
        )
    }

    qrGym?.let { gym -> GymQrSheet(gym) { qrGym = null } }

    vm.error?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.clearError() },
            confirmButton = { TextButton(onClick = { vm.clearError() }) { Text("OK") } },
            title = { Text("Gyms") }, text = { Text(msg) },
        )
    }
}

// Gym QR poster sheet (matches iOS's GymQRSheet): name + hint + QR + Share/Print.
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun GymQrSheet(gym: GymResponse, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val payload = gym.id?.let { "trackstar://checkin/gym/$it" }
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenH = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Color(0xFF14141F)) {
        Column(
            modifier = Modifier.fillMaxWidth().height(screenH * 0.86f).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(gym.name ?: "Gym", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(6.dp))
            Text("Print this and put it at the entrance", fontSize = 14.sp, color = Color.White.copy(alpha = 0.5f))
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier.size(260.dp).clip(RoundedCornerShape(24.dp)).background(CardFill),
                contentAlignment = Alignment.Center
            ) {
                val bmp = remember(payload) { payload?.let { attendanceQrBitmap(it, 600) } }
                if (bmp != null) Image(bitmap = bmp.asImageBitmap(), contentDescription = "Gym QR", modifier = Modifier.size(212.dp))
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(27.dp)).background(Color.White)
                    .clickable { payload?.let { sharePrintableQr(context, it, gym.name) } },
                contentAlignment = Alignment.Center
            ) { Text("Share / Print", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.Black) }
        }
    }
}

private fun sharePrintableQr(context: android.content.Context, content: String, name: String?) {
    val bmp = printableQrBitmap(content, 800) ?: return
    val file = java.io.File(context.cacheDir, "gym-qr-${System.currentTimeMillis()}.png")
    java.io.FileOutputStream(file).use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(android.content.Intent.createChooser(intent, name ?: "Gym QR"))
}

// MARK: - Attendance roster

@Composable
fun CoachAttendanceScreen(onBack: () -> Unit) {
    val vm: CoachCheckInViewModel = viewModel()
    LaunchedEffect(Unit) { vm.loadAttendance() }
    var showExport by remember { mutableStateOf(false) }
    var previewFile by remember { mutableStateOf<java.io.File?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        CollapsingTitleScaffold(title = "Attendance", onBack = onBack) {
            if (vm.attendance.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 80.dp, start = 32.dp, end = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Filled.People, null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(34.dp))
                        Text("No check-ins yet", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.7f))
                        Text("When athletes scan in, their visits show up here.", fontSize = 13.sp, color = Color.White.copy(alpha = 0.4f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            } else {
                // Group check-ins by day: a date section header, then that day's athletes below it.
                val grouped = vm.attendance.groupBy { startOfDayMs(it.checkInAt) }
                grouped.keys.sortedDescending().forEach { day ->
                    val rows = grouped[day]!!.sortedByDescending { it.checkInAt ?: 0.0 }
                    item(key = "hdr-$day") {
                        Text(
                            attendanceSectionTitle(day),
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 8.dp)
                        )
                    }
                    items(rows, key = { it.id ?: it.hashCode().toString() }) {
                        Box(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp)) { RosterRow(it) }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) } // clear the export button
            }
        }
        if (vm.attendance.isNotEmpty()) {
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp)
                    .height(52.dp).clip(RoundedCornerShape(26.dp)).background(Color.White).clickable { showExport = true },
                contentAlignment = Alignment.Center
            ) { Text("Export Report", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.Black) }
        }
    }

    if (showExport) {
        AttendanceExportSheet(
            reportTitle = "Team Attendance",
            subjectName = null,
            visits = vm.attendance,
            groupByAthlete = true,
            onGenerated = { previewFile = it },
            onDismiss = { showExport = false },
        )
    }
    previewFile?.let { file -> PdfPreviewScreen(file = file, onClose = { previewFile = null }) }
}

@Composable
private fun RosterRow(visit: VisitResponse) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardFill).padding(14.dp)
    ) {
        Box(modifier = Modifier.size(38.dp).background(Color.White.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
            Text(initials(visit.athleteName), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(visit.athleteName ?: "Athlete", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
            visit.checkInAt?.let {
                Text("Checked in " + SimpleDateFormat("HH:mm", Locale.ENGLISH).format(Date(it.toLong())), fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f))
            }
        }
        Text(
            if (visit.isOpen) "In gym" else (visit.durationMin?.let { if (it >= 60) "${it / 60}h ${it % 60}m" else "${it}m" } ?: "—"),
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = if (visit.isOpen) Color(0xFF34C759) else Color.White.copy(alpha = 0.6f)
        )
    }
}

private fun startOfDayMs(ms: Double?): Long {
    val cal = java.util.Calendar.getInstance().apply {
        timeInMillis = ms?.toLong() ?: 0L
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

private fun attendanceSectionTitle(dayMs: Long): String {
    val today = startOfDayMs(System.currentTimeMillis().toDouble())
    return when (dayMs) {
        today -> "Today"
        today - 86_400_000L -> "Yesterday"
        else -> SimpleDateFormat("EEEE, MMM d", Locale.ENGLISH).format(Date(dayMs))
    }
}

private fun initials(name: String?): String {
    val parts = name?.trim()?.split(" ")?.filter { it.isNotEmpty() } ?: return "?"
    if (parts.isEmpty()) return "?"
    val first = parts.first().first()
    val last = if (parts.size > 1) parts.last().first().toString() else ""
    return "$first$last".uppercase()
}
