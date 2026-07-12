package com.vasilisneo.trackstar.ui.screens.main.coach

// The coach's athlete-detail Diet tab — purpose-built for the detail context (no standalone Diet
// screen chrome). A day selector, the athlete's nutrition summary and meal list, plus an edit-plan
// button; the coach can plan/edit the athlete's meals but never tick them "consumed" on their
// behalf. Reuses the small diet components (day selector, nutrition card, meal card) + sheets.

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vasilisneo.trackstar.data.api.DietMeal
import com.vasilisneo.trackstar.ui.components.GlassCircleIconButton
import com.vasilisneo.trackstar.ui.screens.main.diet.AddMealSheet
import com.vasilisneo.trackstar.ui.screens.main.diet.DaySelector
import com.vasilisneo.trackstar.ui.screens.main.diet.DietViewModel
import com.vasilisneo.trackstar.ui.screens.main.diet.EmptyMeals
import com.vasilisneo.trackstar.ui.screens.main.diet.MealCard
import com.vasilisneo.trackstar.ui.screens.main.diet.MealType
import com.vasilisneo.trackstar.ui.screens.main.diet.NutritionCard
import com.vasilisneo.trackstar.ui.screens.main.diet.PlanDietSheet

@Composable
fun AthleteDietTab(viewModel: DietViewModel, modifier: Modifier = Modifier) {
    val meals = viewModel.activeMeals
    var editingMeal by remember { mutableStateOf<DietMeal?>(null) }
    var showPlanSheet by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            DaySelector(
                current = viewModel.currentDay,
                hasMeals = { viewModel.hasMeals(it) },
                onSelect = { viewModel.currentDay = it },
                modifier = Modifier.padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 8.dp),
            )

            LazyColumn(
                contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (meals.isNotEmpty()) {
                    item { NutritionCard(meals, modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                        Text("Meals", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.weight(1f))
                        GlassCircleIconButton(onClick = { showPlanSheet = true }, contentDescription = "Edit meals", icon = Icons.Filled.Edit)
                    }
                }
                if (meals.isEmpty()) {
                    item { EmptyMeals() }
                } else {
                    items(meals, key = { it.id }) { meal ->
                        MealCard(
                            meal = meal,
                            canConsume = false, // coach never consumes on the athlete's behalf
                            onToggle = {},
                            onEdit = { editingMeal = meal },
                            onDelete = { viewModel.removeMeal(meal.id) },
                            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
                        )
                    }
                }
            }
        }

        editingMeal?.let { target ->
            AddMealSheet(
                existing = target,
                lockedType = MealType.from(target.type),
                onSave = { viewModel.addMeal(it); editingMeal = null },
                onDismiss = { editingMeal = null },
            )
        }
        if (showPlanSheet) {
            PlanDietSheet(
                initialMeals = meals,
                onSave = { viewModel.setMeals(it); showPlanSheet = false },
                onDismiss = { showPlanSheet = false },
            )
        }
    }
}
