package com.vasilisneo.trackstar.ui.screens.main.coach

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.workout.TemplateRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import java.util.UUID

private val weekdayOrder = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

// One row of the templates list: name + which weekdays have exercises + total exercise count.
data class TemplateSummary(val id: String, val name: String, val activeDays: List<String>, val exerciseCount: Int)

// Ports iOS's TemplatesViewModel (API-first, no local DB): loads the coach's templates and, per
// template, its sessions to derive the active-days / exercise-count summary shown on each card.
class TemplatesViewModel : ViewModel() {

    private val repo = TemplateRepository()

    var templates by mutableStateOf<List<TemplateSummary>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set

    init { fetch() }

    fun fetch() {
        viewModelScope.launch {
            isLoading = templates.isEmpty()
            when (val r = repo.getTemplates()) {
                is ApiResult.Success -> {
                    templates = r.data.map { t ->
                        async {
                            val sessions = (repo.getTemplateSessions(t.id) as? ApiResult.Success)?.data.orEmpty()
                            val activeDays = sessions.filter { it.exercises.isNotEmpty() }.map { it.day }.distinct()
                                .sortedBy { weekdayOrder.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }
                            val exerciseCount = sessions.sumOf { it.exercises.size }
                            TemplateSummary(t.id, t.name, activeDays, exerciseCount)
                        }
                    }.awaitAll()
                }
                is ApiResult.Error -> Unit
            }
            isLoading = false
        }
    }

    fun create(name: String, onCreated: (String) -> Unit = {}) {
        val id = UUID.randomUUID().toString()
        templates = listOf(TemplateSummary(id, name.trim(), emptyList(), 0)) + templates
        viewModelScope.launch {
            repo.createTemplate(id, name.trim())
            onCreated(id)
        }
    }

    fun delete(id: String) {
        templates = templates.filterNot { it.id == id }
        viewModelScope.launch { repo.deleteTemplate(id) }
    }
}
