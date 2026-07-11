package com.vasilisneo.trackstar.ui.screens.main

// Visual replica of SettingsView (Trackstar/UI/View/MainApp/Profile/SettingsView.swift) on
// iOS: a grouped list of nav rows (Notifications, Appearance, App Settings, About) plus a
// destructive Close Account row. The sub-screens those rows push to aren't built yet — for
// now each opens a lightweight "coming soon" stub (SettingsDetailScreen).

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vasilisneo.trackstar.ui.components.GlassCircleIconButton
import com.vasilisneo.trackstar.ui.theme.trackstarBackground

private val GroupSurface = Color.White.copy(alpha = 0.06f)

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit = {},
    onOpenDetail: (String) -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize().trackstarBackground()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                GlassCircleIconButton(onClick = onBackClick, contentDescription = "Back")
                Spacer(modifier = Modifier.weight(1f))
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text("Settings", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(top = 4.dp))

                SettingsGroup {
                    NavRow(Icons.Filled.Notifications, "Notifications") { onOpenDetail("notifications") }
                    RowDivider()
                    NavRow(Icons.Filled.Palette, "Appearance") { onOpenDetail("appearance") }
                    RowDivider()
                    NavRow(Icons.Filled.Tune, "App Settings") { onOpenDetail("app_settings") }
                    RowDivider()
                    NavRow(Icons.Filled.Info, "About") { onOpenDetail("about") }
                }

                SettingsGroup {
                    NavRow(Icons.Filled.HeartBroken, "Close Account", tint = Color(0xFFE5484D)) { onOpenDetail("close_account") }
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(GroupSurface)) {
        content()
    }
}

@Composable
private fun NavRow(icon: ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Box(modifier = Modifier.width(32.dp), contentAlignment = Alignment.CenterStart) {
            Icon(icon, contentDescription = null, tint = if (tint == Color.White) tint.copy(alpha = 0.7f) else tint, modifier = Modifier.padding(end = 0.dp))
        }
        Text(label, fontSize = 16.sp, color = tint, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.25f), modifier = Modifier.padding(2.dp))
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(start = 62.dp))
}
