package com.vasilisneo.trackstar.ui.screens.main.settings

// Replicas of the four simpler Settings sub-screens on iOS: NotificationsView,
// AppSettingsView, AboutView, CloseAccountView. Toggles persist via SharedPreferences
// (rememberBooleanPref), mirroring iOS's @AppStorage. Appearance lives in its own file.

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vasilisneo.trackstar.ui.components.GlassCircleIconButton
import com.vasilisneo.trackstar.ui.components.SettingsActionRow
import com.vasilisneo.trackstar.ui.components.SettingsGroup
import com.vasilisneo.trackstar.ui.components.SettingsRowDivider
import com.vasilisneo.trackstar.ui.components.SettingsScaffold
import com.vasilisneo.trackstar.ui.components.SettingsSectionHeader
import com.vasilisneo.trackstar.ui.components.SettingsToggleRow
import com.vasilisneo.trackstar.ui.theme.TrackstarAccent
import com.vasilisneo.trackstar.ui.theme.trackstarBackground
import com.vasilisneo.trackstar.ui.util.rememberBooleanPref

@Composable
fun NotificationsScreen(onBackClick: () -> Unit = {}) {
    var planUpdates by rememberBooleanPref("notifyPlanUpdates", true)
    var comments by rememberBooleanPref("notifyComments", true)
    var completions by rememberBooleanPref("notifySessionCompletions", true)

    SettingsScaffold(title = "Notifications", onBack = onBackClick) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsSectionHeader("Push Notifications")
            SettingsGroup {
                SettingsToggleRow(Icons.Filled.CalendarMonth, "Plan Updates", planUpdates) { planUpdates = it }
                SettingsRowDivider()
                SettingsToggleRow(Icons.Filled.ChatBubble, "Comments", comments) { comments = it }
                SettingsRowDivider()
                SettingsToggleRow(Icons.Filled.CheckCircle, "Session Completions", completions) { completions = it }
            }
        }
    }
}

@Composable
fun AppSettingsScreen(onBackClick: () -> Unit = {}) {
    var showDietTab by rememberBooleanPref("showDietTab", true)
    var restTimerSound by rememberBooleanPref("restTimerSound", true)
    var useMetric by rememberBooleanPref("useMetricUnits", true)

    SettingsScaffold(title = "App Settings", onBack = onBackClick) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsSectionHeader("General")
            SettingsGroup {
                SettingsToggleRow(Icons.Filled.Restaurant, "Show Diet Tab", showDietTab) { showDietTab = it }
                SettingsRowDivider()
                // Units row: two-line label + toggles Metric/Imperial on tap
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth().clickable { useMetric = !useMetric }.padding(horizontal = 16.dp, vertical = 18.dp)
                ) {
                    Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.CenterStart) {
                        Icon(Icons.Filled.Straighten, contentDescription = null, tint = Color.White.copy(alpha = 0.7f))
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Units", fontSize = 16.sp, color = Color.White)
                        Text(if (useMetric) "Metric  ·  cm, kg" else "Imperial  ·  ft, lbs", fontSize = 13.sp, color = Color.White.copy(alpha = 0.45f))
                    }
                    Text(if (useMetric) "Metric" else "Imperial", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.35f))
                    Icon(Icons.Filled.UnfoldMore, contentDescription = null, tint = Color.White.copy(alpha = 0.25f), modifier = Modifier.size(16.dp))
                }
                SettingsRowDivider()
                SettingsToggleRow(Icons.Filled.VolumeUp, "Rest Timer Sound", restTimerSound) { restTimerSound = it }
            }
        }
    }
}

@Composable
fun AboutScreen(onBackClick: () -> Unit = {}) {
    val context = LocalContext.current
    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    SettingsScaffold(title = "About", onBack = onBackClick) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp)
        ) {
            Box(
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)).background(TrackstarAccent),
                contentAlignment = Alignment.Center
            ) {
                Text("T", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Text("Trackstar", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Version 1.0.0", fontSize = 14.sp, color = Color.White.copy(alpha = 0.4f))
        }

        SettingsGroup {
            AboutLinkRow(Icons.Filled.Shield, "Privacy Policy") { open("https://trackstar.fitness/privacy.html") }
            SettingsRowDivider()
            AboutLinkRow(Icons.Filled.Description, "Terms of Use") { open("https://trackstar.fitness/terms.html") }
            SettingsRowDivider()
            AboutLinkRow(Icons.Filled.Language, "Website") { open("https://trackstar.fitness") }
            SettingsRowDivider()
            AboutLinkRow(Icons.Filled.Email, "Contact Us") { open("mailto:support@trackstar.fitness") }
        }
    }
}

@Composable
private fun AboutLinkRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    SettingsActionRow(
        icon = icon,
        label = label,
        trailing = {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(15.dp))
        },
        onClick = onClick,
    )
}

/** Full-screen Close Account warning (iOS presents it as a fullScreenCover). onClosed
 *  simulates deletion and should route back to Landing. */
@Composable
fun CloseAccountScreen(onDismiss: () -> Unit = {}, onClosed: () -> Unit = {}) {
    var deleting by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().trackstarBackground()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                GlassCircleIconButton(onClick = onDismiss, icon = Icons.Filled.HeartBroken, contentDescription = "Close")
                Spacer(modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)
            ) {
                Icon(Icons.Filled.HeartBroken, contentDescription = null, tint = Color(0xFFE5484D), modifier = Modifier.size(64.dp))
                Text("Sorry to see you go", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
                Text(
                    "Your account and all associated data — workouts, plans, and progress — will be permanently deleted.",
                    fontSize = 16.sp, color = Color.White.copy(alpha = 0.55f), textAlign = TextAlign.Center
                )
                Text("This action cannot be undone.", fontSize = 16.sp, color = Color.White.copy(alpha = 0.55f), textAlign = TextAlign.Center)
            }

            Spacer(modifier = Modifier.weight(2f))

            Column(
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 48.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFFE5484D).copy(alpha = 0.85f))
                        .clickable(enabled = !deleting) {
                            deleting = true
                            onClosed()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (deleting) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    } else {
                        Text("Close Account", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                }
                Text("Keep My Account", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.clickable(onClick = onDismiss))
            }
        }
    }
}
