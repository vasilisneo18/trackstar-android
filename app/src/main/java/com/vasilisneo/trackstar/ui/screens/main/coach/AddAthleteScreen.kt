package com.vasilisneo.trackstar.ui.screens.main.coach

// Ports iOS's AddAthleteSheet: a menu to add an athlete by Email, share/scan a QR invite, or share
// an invite link. The QR deep link is created on open (createInvite). Reuses the existing
// QRConnectScreen for the QR tab (My QR = the invite QR + a scan placeholder).

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.QrCode2
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vasilisneo.trackstar.data.auth.TokenStore
import com.vasilisneo.trackstar.ui.components.GlassCircleIconButton
import com.vasilisneo.trackstar.ui.screens.main.QRConnectScreen
import com.vasilisneo.trackstar.ui.theme.trackstarBackground

private enum class AddMode { MENU, EMAIL, QR }

@Composable
fun AddAthleteScreen(onClose: () -> Unit, onAthleteAdded: () -> Unit) {
    val vm: AddAthleteViewModel = viewModel()
    var mode by remember { mutableStateOf(AddMode.MENU) }
    val ctx = LocalContext.current
    val coachName = remember {
        val ts = TokenStore(ctx)
        listOfNotNull(ts.firstName?.ifBlank { null }, ts.lastName?.ifBlank { null }).joinToString(" ").ifBlank { "Coach" }
    }

    // Horizontal push like iOS's NavigationStack: MENU is the root; EMAIL/QR push in from the right,
    // going back pops them off (incoming from the left with a slight parallax).
    AnimatedContent(
        targetState = mode,
        transitionSpec = {
            val forward = targetState != AddMode.MENU
            val spec = tween<IntOffset>(300)
            if (forward) {
                slideInHorizontally(spec) { it } togetherWith slideOutHorizontally(spec) { -it / 3 }
            } else {
                slideInHorizontally(spec) { -it / 3 } togetherWith slideOutHorizontally(spec) { it }
            }
        },
        label = "addAthleteMode",
    ) { m ->
        when (m) {
            AddMode.MENU -> AddAthleteMenu(
                onEmail = { mode = AddMode.EMAIL },
                onQr = { mode = AddMode.QR },
                onShare = {
                    vm.inviteDeepLink?.let { link ->
                        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, link) }
                        ctx.startActivity(Intent.createChooser(send, "Share invite"))
                    }
                },
                onClose = onClose,
            )
            AddMode.EMAIL -> AddAthleteEmail(vm = vm, onBack = { mode = AddMode.MENU }, onAdded = onAthleteAdded)
            AddMode.QR -> QRConnectScreen(
                qrString = vm.inviteDeepLink ?: "",
                displayName = coachName,
                subtitle = "Athletes scan this to join your team",
                onBackClick = { mode = AddMode.MENU },
                backIcon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                showShareLink = false,
                // Coach scans an athlete's QR, which encodes their email — add them by it (mirrors
                // iOS's handleScan). vm.addAthlete guards against double-adds while in flight.
                onScan = { code ->
                    val email = code.removePrefix("mailto:").trim()
                    if (email.contains("@")) vm.addAthlete(email) { onAthleteAdded() }
                },
            )
        }
    }
}

@Composable
private fun AddAthleteMenu(onEmail: () -> Unit, onQr: () -> Unit, onShare: () -> Unit, onClose: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().trackstarBackground()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp)) {
                GlassCircleIconButton(onClick = onClose, contentDescription = "Close", icon = Icons.Filled.Close)
                Spacer(modifier = Modifier.weight(1f))
            }
            Text(
                "Add Trackstar\nAthlete", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White,
                lineHeight = 36.sp,
                modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 28.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                OptionRow(Icons.Filled.Email, "Email", "Add using their email address", onEmail)
                OptionRow(Icons.Filled.QrCode2, "QR Code", "Share or scan a QR code", onQr)
                OptionRow(Icons.Filled.IosShare, "Share Link", "Send an invite link", onShare)
            }
        }
    }
}

@Composable
private fun OptionRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.07f)).clickable(onClick = onClick).padding(16.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f))) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(subtitle, fontSize = 13.sp, color = Color.White.copy(alpha = 0.45f))
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun AddAthleteEmail(vm: AddAthleteViewModel, onBack: () -> Unit, onAdded: () -> Unit) {
    var email by remember { mutableStateOf("") }
    Box(modifier = Modifier.fillMaxSize().trackstarBackground()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp)) {
                GlassCircleIconButton(onClick = onBack, contentDescription = "Back", icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft)
                Spacer(modifier = Modifier.weight(1f))
            }
            Column(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp)) {
                Text("Add New Athlete", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "They'll be added to your roster immediately. You can start managing their plan right away.",
                    fontSize = 14.sp, color = Color.White.copy(alpha = 0.45f),
                )
                Spacer(modifier = Modifier.height(24.dp))

                BasicTextField(
                    value = email,
                    onValueChange = { email = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 14.dp, vertical = 14.dp),
                    decorationBox = { inner ->
                        if (email.isEmpty()) Text("athlete@email.com", color = Color.White.copy(alpha = 0.4f), fontSize = 15.sp)
                        inner()
                    },
                )

                vm.addError?.let {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(it, fontSize = 13.sp, color = Color(0xFFFF453A).copy(alpha = 0.85f))
                }

                Spacer(modifier = Modifier.height(16.dp))
                val enabled = email.isNotBlank() && !vm.isAdding
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(28.dp))
                        .background(if (enabled) Color.White else Color.White.copy(alpha = 0.4f))
                        .clickable(enabled = enabled) { vm.addAthlete(email) { onAdded() } },
                ) {
                    Text(if (vm.isAdding) "Adding…" else "Add Athlete", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0D0D17))
                }
            }
        }
    }
}
