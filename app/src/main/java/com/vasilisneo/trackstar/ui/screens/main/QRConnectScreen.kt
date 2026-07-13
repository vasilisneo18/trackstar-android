package com.vasilisneo.trackstar.ui.screens.main

// Replica of QRConnectSheet (Trackstar/UI/UIComponents/QRConnectSheet.swift) on iOS: a
// "My QR" tab showing the user's QR (real, generated with ZXing) with a figure overlay,
// name, subtitle, and a Share link button; and a "Scan" tab. The live camera scanner needs
// CameraX + a runtime-permission flow that isn't set up yet, so the Scan tab shows the
// scan-frame UI as a placeholder rather than a working camera.

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.vasilisneo.trackstar.ui.components.GlassCircleIconButton
import com.vasilisneo.trackstar.ui.theme.TrackstarBackground
import com.vasilisneo.trackstar.ui.theme.trackstarBackground

private enum class QRTab { MY_QR, SCAN }

@Composable
fun QRConnectScreen(
    qrString: String = "vasilis@example.com",
    displayName: String = "Vasilis Neophytou",
    subtitle: String = "Coach scans this to add you to their team",
    onBackClick: () -> Unit = {},
    // Standalone (from Profile) closes with an X; when pushed inside the add-athlete flow it's a back chevron.
    backIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Filled.Close,
    // The add-athlete flow already has a separate "Share Link" menu option, so it hides the in-card one.
    showShareLink: Boolean = true,
    // When provided, the Scan tab shows a live camera scanner and reports decoded QR strings here.
    // Null keeps the "coming soon" placeholder (e.g. a context where scanning has no meaning).
    onScan: ((String) -> Unit)? = null,
) {
    var tab by remember { mutableStateOf(QRTab.MY_QR) }

    Box(modifier = Modifier.fillMaxSize().trackstarBackground()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Nav bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                GlassCircleIconButton(onClick = onBackClick, icon = backIcon, contentDescription = "Back")
                Text("QR Code", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.width(44.dp))
            }

            // Tab picker
            Box(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
                TabPicker(tab = tab, onSelect = { tab = it })
            }

            when (tab) {
                QRTab.MY_QR -> MyQRContent(qrString, displayName, subtitle, showShareLink)
                QRTab.SCAN -> ScanContent(onScan)
            }
        }
    }
}

@Composable
private fun MyQRContent(qrString: String, displayName: String, subtitle: String, showShareLink: Boolean) {
    val context = LocalContext.current
    val qrBitmap = remember(qrString) { generateQrBitmap(qrString, 480) }

    // Card sits in the upper third (iOS: one top spacer, two bottom spacers).
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.weight(1f))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(28.dp))
                .padding(horizontal = 32.dp).padding(top = 48.dp, bottom = 40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                qrBitmap?.let {
                    Image(bitmap = it.asImageBitmap(), contentDescription = "Your QR code", modifier = Modifier.size(200.dp))
                }
                // Center figure overlay (concentric circles + running figure), matching iOS.
                Box(contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(64.dp).background(TrackstarBackground, CircleShape))
                    Box(modifier = Modifier.size(46.dp).background(Color.White.copy(alpha = 0.13f), CircleShape))
                    Box(modifier = Modifier.size(32.dp).background(Color.White.copy(alpha = 0.18f), CircleShape))
                    Icon(Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            Text(displayName, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(subtitle, fontSize = 13.sp, color = Color.White.copy(alpha = 0.45f), textAlign = TextAlign.Center)

            if (showShareLink) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier
                        .height(40.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable {
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, qrString)
                            }
                            runCatching { context.startActivity(Intent.createChooser(share, "Share")) }
                        }
                        .padding(horizontal = 28.dp)
                ) {
                    Icon(Icons.Filled.IosShare, contentDescription = null, tint = Color.Black, modifier = Modifier.size(15.dp))
                    Text("Share link", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
                }
            }
        }
        Spacer(modifier = Modifier.weight(2f))
    }
}

@Composable
private fun ScanContent(onScan: ((String) -> Unit)?) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Live camera fills behind the frame overlay when a scan handler is wired; otherwise the
        // tab keeps its static placeholder.
        if (onScan != null) {
            com.vasilisneo.trackstar.ui.components.QrCameraScanner(
                onCode = onScan,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Scan frame in the upper third (iOS: one top spacer, two bottom spacers).
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.weight(1f))
            Box(modifier = Modifier.size(230.dp).border(2.5.dp, Color.White, RoundedCornerShape(20.dp)))
            Spacer(modifier = Modifier.weight(2f))
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp)
        ) {
            Text("Scan to Connect", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(
                if (onScan != null) "Point at a Trackstar QR code" else "Camera scanning coming soon",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.45f),
            )
        }
    }
}

@Composable
private fun TabPicker(tab: QRTab, onSelect: (QRTab) -> Unit) {
    val width = 200.dp
    val thumbOffset by animateDpAsState(if (tab == QRTab.MY_QR) 3.dp else (width - 6.dp) / 2 + 3.dp, spring(dampingRatio = 0.7f, stiffness = 350f), label = "qrTab")
    Box(
        modifier = Modifier
            .width(width)
            .height(36.dp)
            .clip(CircleShape)
            .background(Color(0xFF10101A))
            .border(1.dp, Color.White.copy(alpha = 0.07f), CircleShape)
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = 3.dp)
                .offset(x = thumbOffset)
                .width((width - 6.dp) / 2)
                .height(30.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f))
        )
        Row(modifier = Modifier.fillMaxSize().padding(3.dp)) {
            TabPickerCell("My QR", Icons.Filled.QrCode2, tab == QRTab.MY_QR) { onSelect(QRTab.MY_QR) }
            TabPickerCell("Scan", Icons.Filled.QrCodeScanner, tab == QRTab.SCAN) { onSelect(QRTab.SCAN) }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TabPickerCell(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) Color.White else Color.White.copy(alpha = 0.38f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.weight(1f).fillMaxSize().clip(CircleShape).clickable(onClick = onClick),
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
        Spacer(modifier = Modifier.weight(1f))
    }
}

/** White-modules-on-transparent QR (so the dark background shows through), matching iOS. */
private fun generateQrBitmap(content: String, size: Int): Bitmap? {
    if (content.isBlank()) return null
    return runCatching {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
            EncodeHintType.MARGIN to 1,
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val white = android.graphics.Color.WHITE
        val transparent = android.graphics.Color.TRANSPARENT
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (matrix[x, y]) white else transparent)
            }
        }
        bmp
    }.getOrNull()
}
