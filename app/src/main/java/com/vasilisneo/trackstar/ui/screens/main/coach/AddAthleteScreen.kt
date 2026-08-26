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
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
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
    // Email flow and QR scan funnel into a confirm popup that performs the add. Non-null = shown.
    var pendingEmail by remember { mutableStateOf<String?>(null) }
    // A connection flow waiting on the up-front Bronze decision (only when the coach has credits).
    var pendingProceed by remember { mutableStateOf<(() -> Unit)?>(null) }
    val ctx = LocalContext.current
    val coachName = remember {
        val ts = TokenStore(ctx)
        listOfNotNull(ts.firstName?.ifBlank { null }, ts.lastName?.ifBlank { null }).joinToString(" ").ifBlank { "Coach" }
    }

    fun shareInvite() {
        vm.inviteDeepLink?.let { link ->
            val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, link) }
            ctx.startActivity(Intent.createChooser(send, "Share invite"))
        }
    }

    // Tapping a connection option first asks the Bronze question (if credits remain), then proceeds.
    fun gate(proceed: () -> Unit) {
        if (vm.bronzeGrantsRemaining > 0) pendingProceed = proceed else proceed()
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
                onEmail = { gate { mode = AddMode.EMAIL } },
                onQr = { gate { mode = AddMode.QR } },
                onShare = { gate { shareInvite() } },
                onClose = onClose,
            )
            AddMode.EMAIL -> AddAthleteEmail(vm = vm, onBack = { mode = AddMode.MENU }, onSubmit = { pendingEmail = it })
            AddMode.QR -> QRConnectScreen(
                qrString = vm.inviteDeepLink ?: "",
                displayName = coachName,
                subtitle = "Athletes scan this to join your team",
                onBackClick = { mode = AddMode.MENU },
                backIcon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                showShareLink = false,
                // Coach scans an athlete's QR (encodes their email) — confirm before adding.
                onScan = { code ->
                    val email = code.removePrefix("mailto:").trim()
                    if (email.contains("@") && pendingEmail == null) pendingEmail = email
                },
            )
        }
    }

    pendingProceed?.let { proceed ->
        BronzeChoiceDialog(
            credits = vm.bronzeGrantsRemaining,
            onGrant = { vm.setGrantChoice(true); pendingProceed = null; proceed() },
            onSkip = { vm.setGrantChoice(false); pendingProceed = null; proceed() },
            onDismiss = { pendingProceed = null },
        )
    }

    pendingEmail?.let { email ->
        ConfirmConnectSheet(
            vm = vm,
            email = email,
            onDismiss = {
                pendingEmail = null
                vm.clearError()
            },
            onConnected = onAthleteAdded,
        )
    }
}

// Up-front Bronze decision shown when a connection option is tapped (and the coach has credits).
// Custom branded card rather than a stock AlertDialog.
@Composable
private fun BronzeChoiceDialog(credits: Int, onGrant: () -> Unit, onSkip: () -> Unit, onDismiss: () -> Unit) {
    val bronze = Color(0xFFCD7F32)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Lighten the default scrim so a bit more of the screen shows through.
        (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0.4f)
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(30.dp)).background(Color(0xFF15151F)).padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(66.dp).clip(CircleShape).background(bronze.copy(alpha = 0.15f))) {
                Icon(Icons.Filled.MilitaryTech, contentDescription = null, tint = bronze, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text("Give a free Bronze credit?", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(
                "This athlete will join on the Bronze plan for free, using 1 of your $credits " +
                    "credit${if (credits == 1) "" else "s"}. Skip to add them on the Free plan.",
                fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center, lineHeight = 20.sp,
            )
            Spacer(Modifier.height(24.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(Color.White).clickable(onClick = onGrant),
            ) {
                Text("Grant Bronze", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0D0D17))
            }
            Spacer(Modifier.height(6.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).clickable(onClick = onSkip),
            ) {
                Text("Skip this one", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.5f))
            }
        }

            Box(modifier = Modifier.align(Alignment.TopEnd).padding(top = 18.dp, end = 18.dp)) {
                GlassCircleIconButton(onClick = onDismiss, contentDescription = "Close", icon = Icons.Filled.Close)
            }
        }
    }
}

// Confirmation popup shown before an athlete is added — from the email flow or after a QR scan.
// Holds the Bronze-grant toggle and performs the add, matching iOS's scanConfirmPopup.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmConnectSheet(
    vm: AddAthleteViewModel,
    email: String,
    onDismiss: () -> Unit,
    onConnected: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val bronze = Color(0xFFCD7F32)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF15151F),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 36.dp)) {
            Text("Connect with Athlete", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text("They'll be added to your roster immediately.", fontSize = 14.sp, color = Color.White.copy(alpha = 0.5f))
            Spacer(Modifier.height(20.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.06f)).padding(14.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f))) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Text(email, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1, modifier = Modifier.weight(1f))
            }

            if (vm.useBronzeGrant) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.MilitaryTech, contentDescription = null, tint = bronze, modifier = Modifier.size(16.dp))
                    Text("Joining on the Bronze plan — 1 credit", fontSize = 13.sp, color = bronze)
                }
            }

            vm.addError?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, fontSize = 13.sp, color = Color(0xFFFF453A).copy(alpha = 0.85f))
            }

            Spacer(Modifier.height(20.dp))
            val enabled = !vm.isAdding
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp))
                    .background(if (enabled) Color.White else Color.White.copy(alpha = 0.4f))
                    .clickable(enabled = enabled) { vm.addAthlete(email) { onConnected() } },
            ) {
                Text(if (vm.isAdding) "Connecting…" else "Connect", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0D0D17))
            }
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
private fun AddAthleteEmail(vm: AddAthleteViewModel, onBack: () -> Unit, onSubmit: (String) -> Unit) {
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

                Spacer(modifier = Modifier.height(16.dp))
                val enabled = email.isNotBlank()
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(28.dp))
                        .background(if (enabled) Color.White else Color.White.copy(alpha = 0.4f))
                        .clickable(enabled = enabled) { vm.clearError(); onSubmit(email.trim()) },
                ) {
                    Text("Add Athlete", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0D0D17))
                }
            }
        }
    }
}
