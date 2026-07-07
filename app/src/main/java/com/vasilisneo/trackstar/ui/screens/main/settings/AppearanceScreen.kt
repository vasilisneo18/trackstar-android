package com.vasilisneo.trackstar.ui.screens.main.settings

// Replica of AppearanceView on iOS: a 3-column grid of theme swatches with locked
// (subscription-tier) themes. Selection is visual-only for now — there's no multi-theme
// system on Android yet (only "Midnight" is actually applied), and no subscription system
// to gate the locked tiers, so tapping a locked swatch is currently a no-op rather than
// opening the (unbuilt) subscription screen.

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vasilisneo.trackstar.ui.components.SettingsScaffold

private data class ThemeSwatch(
    val name: String,
    val colors: List<Color>,
    val locked: Boolean = false,
    val tierColor: Color? = null,
)

private val Themes = listOf(
    ThemeSwatch("Midnight", listOf(Color(0xFF0D0D17), Color(0xFF1B2140))),
    ThemeSwatch("Ocean", listOf(Color(0xFF0B3D5C), Color(0xFF11A3B8))),
    ThemeSwatch("Forest", listOf(Color(0xFF0B3D2E), Color(0xFF2E9E6B))),
    ThemeSwatch("Sunset", listOf(Color(0xFFB23A48), Color(0xFFF2A65A))),
    ThemeSwatch("Violet", listOf(Color(0xFF3A1C71), Color(0xFFB359FF))),
    ThemeSwatch("Rose", listOf(Color(0xFF6A1B3A), Color(0xFFE05E8B))),
    ThemeSwatch("Bronze", listOf(Color(0xFF6E4A2A), Color(0xFFCD7F32)), locked = true, tierColor = Color(0xFFCD7F32)),
    ThemeSwatch("Silver", listOf(Color(0xFF6B6B75), Color(0xFFC8C8D2)), locked = true, tierColor = Color(0xFFD2D2DC)),
    ThemeSwatch("Gold", listOf(Color(0xFF7A5E12), Color(0xFFE6B325)), locked = true, tierColor = Color(0xFFE6B325)),
)

@Composable
fun AppearanceScreen(onBackClick: () -> Unit = {}) {
    var selected by remember { mutableStateOf("Midnight") }

    SettingsScaffold(title = "Appearance", subtitle = "Choose a background theme", onBack = onBackClick) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            for (row in Themes.chunked(3)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (theme in row) {
                        ThemeSwatchCell(
                            theme = theme,
                            selected = theme.name == selected,
                            onClick = { if (!theme.locked) selected = theme.name },
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
private fun ThemeSwatchCell(theme: ThemeSwatch, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(theme.colors))
            )
            when {
                theme.locked -> {
                    Box(modifier = Modifier.fillMaxWidth().height(76.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.35f)))
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = theme.tierColor ?: Color.White, modifier = Modifier.size(14.dp))
                        Box(
                            modifier = Modifier.clip(CircleShape).background((theme.tierColor ?: Color.White).copy(alpha = 0.2f)).padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(theme.name, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = theme.tierColor ?: Color.White)
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
            theme.name,
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
