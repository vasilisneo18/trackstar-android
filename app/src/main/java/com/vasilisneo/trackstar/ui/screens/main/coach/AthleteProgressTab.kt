package com.vasilisneo.trackstar.ui.screens.main.coach

// The coach's athlete-detail Progress tab — the athlete's exercise charts. Ports iOS's
// progressContent (ExerciseProgressView(sessions:)). Reuses the granular progress components
// (exercise picker, swipeable chart pager, per-session list) with a chart/list toggle, fed by the
// athlete's completed sessions.

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vasilisneo.trackstar.data.api.WorkoutSessionResponse
import com.vasilisneo.trackstar.ui.components.GlassCircleIconButton
import com.vasilisneo.trackstar.ui.screens.main.stats.ChartPage
import com.vasilisneo.trackstar.ui.screens.main.stats.ExercisePicker
import com.vasilisneo.trackstar.ui.screens.main.stats.ListPage
import com.vasilisneo.trackstar.ui.screens.main.stats.NoSessionsCard
import com.vasilisneo.trackstar.ui.screens.main.stats.exerciseNames
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AthleteProgressTab(sessions: List<WorkoutSessionResponse>, modifier: Modifier = Modifier) {
    val names = remember(sessions) { exerciseNames(sessions) }
    var showList by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { names.size })
    val scope = rememberCoroutineScope()
    var selected by remember(names) { mutableStateOf(names.firstOrNull() ?: "") }

    LaunchedEffect(pagerState.currentPage, names) { names.getOrNull(pagerState.currentPage)?.let { selected = it } }
    LaunchedEffect(showList) {
        if (!showList) {
            val idx = names.indexOf(selected).coerceAtLeast(0)
            if (idx != pagerState.currentPage) pagerState.scrollToPage(idx)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (names.isEmpty()) {
            NoSessionsCard()
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // Chart/list toggle (the standalone screen keeps this in its nav bar).
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    GlassCircleIconButton(
                        onClick = { showList = !showList }, contentDescription = "Toggle view",
                        icon = if (showList) Icons.Filled.ShowChart else Icons.AutoMirrored.Filled.List,
                    )
                }
                ExercisePicker(names, selected, onSelect = { name ->
                    selected = name
                    if (!showList) scope.launch { pagerState.animateScrollToPage(names.indexOf(name).coerceAtLeast(0)) }
                })
                Spacer(modifier = Modifier.height(10.dp))
                if (showList) {
                    ListPage(selected, sessions)
                } else {
                    HorizontalPager(state = pagerState, verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().weight(1f)) { page ->
                        ChartPage(names[page], sessions)
                    }
                }
            }
        }
    }
}
