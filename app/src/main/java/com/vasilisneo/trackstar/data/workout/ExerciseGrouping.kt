package com.vasilisneo.trackstar.data.workout

import com.vasilisneo.trackstar.data.api.ExerciseData

// Mirrors iOS's ExerciseDisplayUnit + Array<ExerciseData>.groupedForDisplay: two consecutive
// exercises sharing the same non-null compoundGroupId are a superset and should render/behave
// as one paired unit everywhere exercises are displayed (plan editor, workout plan preview,
// active session, quick log) rather than two separate sequential entries.
sealed interface ExerciseDisplayUnit {
    data class Single(val exercise: ExerciseData) : ExerciseDisplayUnit
    data class Pair(val a: ExerciseData, val b: ExerciseData) : ExerciseDisplayUnit

    val id: String
        get() = when (this) {
            is Single -> exercise.id ?: ""
            is Pair -> a.id ?: ""
        }
}

fun List<ExerciseData>.groupedForDisplay(): List<ExerciseDisplayUnit> {
    val units = mutableListOf<ExerciseDisplayUnit>()
    var i = 0
    while (i < size) {
        val exercise = this[i]
        val groupId = exercise.compoundGroupId
        if (groupId != null && i + 1 < size && this[i + 1].compoundGroupId == groupId) {
            units.add(ExerciseDisplayUnit.Pair(exercise, this[i + 1]))
            i += 2
        } else {
            units.add(ExerciseDisplayUnit.Single(exercise))
            i += 1
        }
    }
    return units
}
