package com.vasilisneo.trackstar.ui.screens.main.diet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

// Cross-route "the diet changed, refetch it" signal. The AI diet planner is a separate full-screen
// route with its own ViewModel, so it can't call the Diet tab's DietViewModel directly. It bumps
// this after applying a plan; DietScreen observes `version` and refetches. A plain signal (not a
// lifecycle callback) because the inner tab-nav entry's lifecycle doesn't track the outer nav, so
// resume-based refresh never fires on return from an outer route.
object DietRefreshSignal {
    var version by mutableIntStateOf(0)
        private set

    fun bump() { version++ }
}
