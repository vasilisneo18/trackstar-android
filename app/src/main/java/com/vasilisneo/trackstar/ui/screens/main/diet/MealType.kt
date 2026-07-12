package com.vasilisneo.trackstar.ui.screens.main.diet

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

// Ports iOS MealType (DietMeal.swift). `label` is the backend/JSON raw value stored on DietMeal.type
// ("Breakfast"/"Lunch"/"Dinner"/"Snack"); icon + color mirror iOS's sunrise/sun.max/moon.stars/leaf
// on orange/yellow/indigo/green.
enum class MealType(val label: String, val icon: ImageVector, val color: Color) {
    BREAKFAST("Breakfast", Icons.Filled.WbTwilight, Color(0xFFFF9500)),
    LUNCH("Lunch", Icons.Filled.WbSunny, Color(0xFFFFD60A)),
    DINNER("Dinner", Icons.Filled.Bedtime, Color(0xFF5E5CE6)),
    SNACK("Snack", Icons.Filled.Spa, Color(0xFF34C759));

    companion object {
        // Unknown/blank types fall back to Snack's slot but render a neutral fork-knife glyph.
        fun from(label: String): MealType = entries.firstOrNull { it.label == label } ?: SNACK
        val fallbackIcon: ImageVector = Icons.Filled.Restaurant
    }
}
