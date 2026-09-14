package com.vasilisneo.trackstar.ui.screens.main.attendance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vasilisneo.trackstar.ui.theme.trackstarBackground

// Collapsing large-title scaffold (ports iOS's CollapsingLargeTitle + CollapsingNavBar): a big
// title sits at the top of the scroll content and, as it scrolls up under the nav bar, an inline
// title fades in. `actions` are trailing nav-bar buttons.
@Composable
fun CollapsingTitleScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val listState = rememberLazyListState()
    val thresholdPx = with(LocalDensity.current) { 40.dp.toPx() }
    val collapse by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / thresholdPx).coerceIn(0f, 1f)
        }
    }
    val inlineAlpha = (collapse * 2.5f).coerceIn(0f, 1f)

    Box(modifier = Modifier.fillMaxSize().trackstarBackground()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 56.dp, bottom = 40.dp),
            modifier = Modifier.fillMaxSize().statusBarsPadding()
        ) {
            item {
                Text(
                    title, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).alpha((1f - collapse).coerceIn(0f, 1f))
                )
            }
            content()
        }
        // Fixed nav bar: back · inline title (fades in) · trailing actions. Background fades in too.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().background(Color(0xFF0D0D17).copy(alpha = inlineAlpha))
                .statusBarsPadding().height(52.dp).padding(horizontal = 16.dp)
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Back", tint = Color.White, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(12.dp))
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.alpha(inlineAlpha))
            Spacer(Modifier.weight(1f))
            actions()
        }
    }
}
