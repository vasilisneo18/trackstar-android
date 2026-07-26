package com.vasilisneo.trackstar.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vasilisneo.trackstar.data.network.ConnectivityMonitor

// "No connection — showing cached data" pill, shown only while offline. Mirrors iOS's OfflineBanner.
// Reads ConnectivityMonitor directly, so any screen can just drop OfflineBanner() near its top with
// no view-model wiring. Animates in/out as connectivity changes.
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    val isOnline by ConnectivityMonitor.isOnline.collectAsStateWithLifecycle()

    AnimatedVisibility(
        visible = !isOnline,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xFFFFC61A).copy(alpha = 0.14f))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.CloudOff,
                contentDescription = null,
                tint = Color(0xFFFFC61A),
                modifier = Modifier.size(15.dp),
            )
            Text(
                "No connection — showing cached data",
                modifier = Modifier.padding(start = 8.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFFFC61A),
            )
        }
    }
}
