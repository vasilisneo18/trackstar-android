package com.vasilisneo.trackstar.ui.screens.main.attendance

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vasilisneo.trackstar.data.api.VisitResponse
import com.vasilisneo.trackstar.ui.theme.TrackstarAccent
import com.vasilisneo.trackstar.ui.theme.trackstarBackground
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CardFill = Color.White.copy(alpha = 0.06f)
private val CheckedInGreen = Color(0xFF34C759)

@Composable
fun CheckInScreen(onBack: () -> Unit) {
    val vm: CheckInViewModel = viewModel()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showScanner by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        vm.load()
        // Ask for location up front so a gym check-in isn't blocked when the athlete scans.
        locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    Box(modifier = Modifier.fillMaxSize().trackstarBackground()) {
        if (showScanner) {
            ScannerOverlay(
                onCode = { code -> showScanner = false; vm.handleScan(code) },
                onClose = { showScanner = false },
            )
        } else {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f)).clickable(onClick = onBack),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Back", tint = Color.White, modifier = Modifier.size(22.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Text("Check In", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.weight(1f))
                    if (vm.history.isNotEmpty()) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.1f)).clickable { showExport = true },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Filled.IosShare, "Export report", tint = Color.White, modifier = Modifier.size(18.dp)) }
                    }
                }

                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        val active = vm.activeVisit
                        if (active != null) {
                            ActiveVisitCard(visit = active, busy = vm.isBusy, onCheckOut = vm::checkOut)
                        } else {
                            ScanButton(busy = vm.isBusy, onScan = { showScanner = true })
                        }
                    }
                    if (vm.history.isNotEmpty()) {
                        item {
                            Text(
                                "HISTORY", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.4f), modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        items(vm.history, key = { it.id ?: it.hashCode().toString() }) { HistoryRow(it) }
                    }
                }
            }
        }

        if (showExport) {
            val store = remember { com.vasilisneo.trackstar.data.auth.TokenStore(context) }
            val name = listOfNotNull(store.firstName?.ifBlank { null }, store.lastName?.ifBlank { null })
                .joinToString(" ").ifBlank { null }
            AttendanceExportSheet(
                reportTitle = "Check-in history",
                subjectName = name,
                visits = vm.history,
                onDismiss = { showExport = false },
            )
        }

        vm.error?.let { message ->
            AlertDialog(
                onDismissRequest = { vm.clearError() },
                confirmButton = { TextButton(onClick = { vm.clearError() }) { Text("OK") } },
                title = { Text("Check-in") },
                text = { Text(message) },
            )
        }
    }
}

@Composable
private fun ActiveVisitCard(visit: VisitResponse, busy: Boolean, onCheckOut: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(CardFill)
            .border(1.dp, CheckedInGreen.copy(alpha = 0.35f), RoundedCornerShape(24.dp)).padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(modifier = Modifier.size(8.dp).background(CheckedInGreen, CircleShape))
            Text("CHECKED IN", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CheckedInGreen)
        }
        Text(visit.locationLabel, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        visit.checkInAt?.let {
            Text("Checked in at ${fmtTime(it)}", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.6f))
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(27.dp))
                .background(Color.White).clickable(enabled = !busy, onClick = onCheckOut),
            contentAlignment = Alignment.Center
        ) {
            if (busy) CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            else Text("Check Out", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
        }
    }
}

@Composable
private fun ScanButton(busy: Boolean, onScan: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(CardFill)
            .clickable(enabled = !busy, onClick = onScan).padding(vertical = 40.dp, horizontal = 16.dp)
    ) {
        if (busy) {
            CircularProgressIndicator(color = TrackstarAccent, strokeWidth = 2.dp, modifier = Modifier.size(44.dp))
        } else {
            Icon(Icons.Filled.QrCodeScanner, null, tint = Color.White, modifier = Modifier.size(44.dp))
        }
        Text("Scan to check in", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        Text(
            "Scan a gym QR or your coach's session code",
            fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f), textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun HistoryRow(visit: VisitResponse) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.05f)).padding(14.dp)
    ) {
        Icon(Icons.Filled.FitnessCenter, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(visit.locationLabel, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
            visit.checkInAt?.let {
                Text(fmtDate(it), fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f))
            }
        }
        Text(fmtDuration(visit), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.6f))
    }
}

@Composable
private fun ScannerOverlay(onCode: (String) -> Unit, onClose: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        com.vasilisneo.trackstar.ui.components.QrCameraScanner(onCode = onCode, modifier = Modifier.fillMaxSize())
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Box(modifier = Modifier.size(230.dp).border(2.5.dp, Color.White, RoundedCornerShape(20.dp)))
            Spacer(Modifier.weight(2f))
        }
        Box(
            modifier = Modifier.statusBarsPadding().padding(16.dp).size(40.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.4f)).clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.Close, "Close", tint = Color.White, modifier = Modifier.size(20.dp)) }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 48.dp)
        ) {
            Text("Scan to Check In", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text("Point at a gym QR or your coach's code", fontSize = 13.sp, color = Color.White.copy(alpha = 0.45f))
        }
    }
}

private fun fmtTime(epochMs: Double): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epochMs.toLong()))

private fun fmtDate(epochMs: Double): String =
    SimpleDateFormat("EEE, MMM d", Locale.ENGLISH).format(Date(epochMs.toLong()))

private fun fmtDuration(visit: VisitResponse): String {
    if (visit.isOpen) return "In progress"
    val min = visit.durationMin ?: return "—"
    val h = min / 60
    val m = min % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
