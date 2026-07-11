package com.vasilisneo.trackstar.ui.screens.main.settings

// Replica of AppearanceView on iOS: a 3-column grid of theme swatches, each filled with that
// theme's real background gradient. Selecting an unlocked theme persists it and re-tints the
// whole app (accent + background) live, driven by the reactive AppTheme state. Bronze/Silver/
// Gold are subscription-gated — tapping them opens the upgrade screen (as on iOS), since there's
// no subscription system to unlock them yet.

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vasilisneo.trackstar.ui.components.SettingsScaffold
import com.vasilisneo.trackstar.ui.theme.AppTheme
import com.vasilisneo.trackstar.ui.theme.TrackstarBackground
import com.vasilisneo.trackstar.ui.theme.currentAppTheme
import com.vasilisneo.trackstar.ui.theme.selectAppTheme

@Composable
fun AppearanceScreen(onBackClick: () -> Unit = {}, onUpgrade: () -> Unit = {}) {
    val context = LocalContext.current
    val selected = currentAppTheme

    SettingsScaffold(title = "Appearance", subtitle = "Choose a background theme", onBack = onBackClick) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            for (row in AppTheme.entries.chunked(3)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (theme in row) {
                        ThemeSwatchCell(
                            theme = theme,
                            selected = theme == selected,
                            onClick = { if (theme.locked) onUpgrade() else selectAppTheme(context, theme) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // pad the last row if it has fewer than 3 cells
                    repeat(3 - row.size) { Box(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun ThemeSwatchCell(theme: AppTheme, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            // The swatch previews the theme's actual gradient: its accent glow at the top over the
            // shared base — the same brush trackstarBackground() paints app-wide.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .alpha(if (theme.locked) 0.35f else 1f)
                    .background(TrackstarBackground)
                    .background(Brush.verticalGradient(listOf(theme.gradientTop, TrackstarBackground)))
            )
            when {
                theme.locked -> {
                    Box(modifier = Modifier.fillMaxWidth().height(76.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.3f)))
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = theme.accent, modifier = Modifier.size(14.dp))
                        Box(
                            modifier = Modifier.clip(CircleShape).background(theme.accent.copy(alpha = 0.2f)).padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(theme.displayName, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = theme.accent)
                        }
                    }
                }
                selected -> {
                    Box(modifier = Modifier.fillMaxWidth().height(76.dp).clip(RoundedCornerShape(16.dp)).border(2.5.dp, Color.White, RoundedCornerShape(16.dp)))
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
        Text(
            theme.displayName,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = when {
                theme.locked -> Color.White.copy(alpha = 0.3f)
                selected -> Color.White
                else -> Color.White.copy(alpha = 0.5f)
            }
        )
    }
}
