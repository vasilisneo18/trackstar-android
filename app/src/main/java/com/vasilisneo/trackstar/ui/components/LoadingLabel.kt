package com.vasilisneo.trackstar.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vasilisneo.trackstar.ui.i18n.Text

/** What a button shows while its action runs: a spinner beside "Saving…"-style text. Every loading
 *  button uses this so they all look the same (LoadingLabel on iOS). */
@Composable
fun LoadingLabel(text: String, color: Color = Color.Black, fontSize: TextUnit = 17.sp) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(color = color, strokeWidth = 2.dp,
            modifier = Modifier.size(if (fontSize.value < 15f) 13.dp else 18.dp))
        Text(text, color = color, fontSize = fontSize, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
