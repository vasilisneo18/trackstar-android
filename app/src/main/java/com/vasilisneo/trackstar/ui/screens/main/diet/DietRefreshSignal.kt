package com.vasilisneo.trackstar.ui.screens.main.diet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vasilisneo.trackstar.data.api.DietMeal

// Cross-route "the diet changed, refetch it" signal. The AI diet planner is a separate full-screen
// route with its own ViewModel, so it can't call the Diet tab's DietViewModel directly. It bumps
// this after applying a plan; DietScreen observes `version` and refetches. A plain signal (not a
// lifecycle callback) because the inner tab-nav entry's lifecycle doesn't track the outer nav, so
// resume-based refresh never fires on return from an outer route.
object DietRefreshSignal {
    var version by mutableIntStateOf(0)
        private set

    fun bump() { version++ }

    // The signed-in user's latest diet plan (weekday → meals), published by every DietViewModel
    // that fetches or edits it, so separate instances (the Home Diet card vs the Diet screen)
    // stay in sync without a refetch.
    var latestPlan by mutableStateOf<Map<String, List<DietMeal>>?>(null)
        private set

    fun publish(plan: Map<String, List<DietMeal>>) { latestPlan = plan }
}
