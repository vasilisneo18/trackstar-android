package com.vasilisneo.trackstar.ui.screens.main.diet

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vasilisneo.trackstar.data.api.DietMeal
import com.vasilisneo.trackstar.data.api.WeeklyDietPlanDto
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.auth.TokenStore
import com.vasilisneo.trackstar.data.workout.DietRepository
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

// Ports iOS's WeeklyDietViewModel: the weekly diet plan (meals keyed by weekday name), the
// currently-selected day, and mutations that immediately sync to the backend. API-first — the
// plan lives in memory and every change POSTs /api/diet (no local DB, like the plan/session VMs).
// `athleteId` non-null puts the VM in coach mode: it reads/writes the athlete's diet via
// /coach/athletes/{id}/diet (@JvmOverloads so the no-arg AndroidViewModel factory still finds an
// (Application) constructor for the signed-in user's own diet).
class DietViewModel @JvmOverloads constructor(app: Application, private val athleteId: String? = null) : AndroidViewModel(app) {

    private val repo = DietRepository()
    private val tokenStore = TokenStore(app)

    // meals keyed by weekday name ("Monday" … "Sunday").
    var weeklyPlan by mutableStateOf<Map<String, List<DietMeal>>>(emptyMap())
        private set
    var currentDay by mutableStateOf(todayName())
    var isLoading by mutableStateOf(false)
        private set

    val userInitials: String = com.vasilisneo.trackstar.ui.components.initialsFrom(
        listOfNotNull(tokenStore.firstName?.ifBlank { null }, tokenStore.lastName?.ifBlank { null })
            .joinToString(" ").ifBlank { null }
    )

    val activeMeals: List<DietMeal> get() = weeklyPlan[currentDay].orEmpty()
    fun hasMeals(day: String): Boolean = !weeklyPlan[day].isNullOrEmpty()

    // iOS: canConsume = viewModel.currentDay == DayTabModel.from(date: Date()) — only today's meals
    // can be ticked off as consumed. A coach viewing an athlete never consumes on their behalf.
    val canConsumeToday: Boolean get() = athleteId == null && currentDay == todayName()

    init { fetch() }

    fun fetch() {
        viewModelScope.launch {
            isLoading = true
            when (val r = repo.getDiet(athleteId)) {
                is ApiResult.Success -> weeklyPlan = r.data.meals
                is ApiResult.Error -> Unit // keep stale data on failure
            }
            isLoading = false
        }
    }

    // MARK: - Mutations (each syncs to the backend, mirroring iOS)

    fun setMeals(meals: List<DietMeal>) = update(currentDay, meals)

    fun addMeal(meal: DietMeal) {
        val list = activeMeals
        val idx = list.indexOfFirst { it.id == meal.id }
        update(currentDay, if (idx >= 0) list.toMutableList().apply { this[idx] = meal } else list + meal)
    }

    fun removeMeal(id: String) = update(currentDay, activeMeals.filterNot { it.id == id })

    fun toggleConsumed(id: String) =
        update(currentDay, activeMeals.map { if (it.id == id) it.copy(isConsumed = !it.isConsumed) else it })

    private fun update(day: String, meals: List<DietMeal>) {
        weeklyPlan = weeklyPlan.toMutableMap().apply { put(day, meals) }
        sync()
    }

    private fun sync() {
        val snapshot = weeklyPlan
        viewModelScope.launch { repo.saveDiet(WeeklyDietPlanDto(snapshot), athleteId) }
    }
}

// Backend/iOS key names are the full capitalized weekday ("Monday" …), matching DayTabModel.rawValue.
internal fun todayName(): String = LocalDate.now().dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
