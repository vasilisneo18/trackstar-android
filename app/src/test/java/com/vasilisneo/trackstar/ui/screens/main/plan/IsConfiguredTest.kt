package com.vasilisneo.trackstar.ui.screens.main.plan

import com.vasilisneo.trackstar.data.api.ExerciseData
import com.vasilisneo.trackstar.data.api.ExerciseSet
import com.vasilisneo.trackstar.data.api.FrequencyValue
import com.vasilisneo.trackstar.data.api.ResistanceValue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Regression coverage for ExerciseData.isConfigured() — notably that a BAND resistance counts as
// configured (previously only weight did, so band exercises stayed "unconfigured").
class IsConfiguredTest {

    private fun exercise(
        resistanceType: String?,
        set: ExerciseSet,
    ) = ExerciseData(
        id = "e", name = "Ex", sets = listOf(set), frequencyType = "reps",
        resistanceType = resistanceType, resistanceUnit = null, compoundGroupId = null,
    )

    private fun set(freq: FrequencyValue?, res: ResistanceValue?) =
        ExerciseSet(id = "s", frequencyValue = freq, resistanceValue = res, restSeconds = 60, setType = "normal", repsMax = null)

    @Test fun `band with zero reps is configured`() {
        val ex = exercise("Band", set(FrequencyValue(reps = 0), ResistanceValue(bandLevel = "Medium")))
        assertTrue(ex.isConfigured())
    }

    @Test fun `band with reps is configured`() {
        val ex = exercise("Band", set(FrequencyValue(reps = 12), ResistanceValue(bandLevel = "Heavy")))
        assertTrue(ex.isConfigured())
    }

    @Test fun `band-only set with no frequency is configured`() {
        val ex = exercise("Band", set(freq = null, res = ResistanceValue(bandLevel = "Light")))
        assertTrue(ex.isConfigured())
    }

    @Test fun `weight with reps is configured`() {
        val ex = exercise("Weight", set(FrequencyValue(reps = 8), ResistanceValue(weight = "80")))
        assertTrue(ex.isConfigured())
    }

    @Test fun `bodyweight none with reps is configured`() {
        val ex = exercise("None", set(FrequencyValue(reps = 10), ResistanceValue()))
        assertTrue(ex.isConfigured())
    }

    @Test fun `empty sets is not configured`() {
        val ex = ExerciseData(id = "e", name = "Ex", sets = emptyList(), frequencyType = "reps", resistanceType = "Weight", resistanceUnit = null, compoundGroupId = null)
        assertFalse(ex.isConfigured())
    }

    @Test fun `zero reps with no resistance is not configured`() {
        val ex = exercise("Weight", set(FrequencyValue(reps = 0), ResistanceValue(weight = "")))
        assertFalse(ex.isConfigured())
    }
}
