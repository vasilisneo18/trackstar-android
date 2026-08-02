package com.vasilisneo.trackstar.data.workout

import com.vasilisneo.trackstar.data.api.ExerciseData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Covers groupedForDisplay(): two consecutive exercises sharing the same non-null compoundGroupId
// render as one superset Pair; everything else is a Single.
class ExerciseGroupingTest {

    private fun ex(id: String, group: String? = null) =
        ExerciseData(id = id, name = id, sets = emptyList(), frequencyType = null,
            resistanceType = null, resistanceUnit = null, compoundGroupId = group)

    @Test fun `empty list groups to nothing`() {
        assertTrue(emptyList<ExerciseData>().groupedForDisplay().isEmpty())
    }

    @Test fun `a lone exercise is a Single`() {
        val units = listOf(ex("a")).groupedForDisplay()
        assertEquals(1, units.size)
        assertTrue(units[0] is ExerciseDisplayUnit.Single)
    }

    @Test fun `two exercises sharing a group id form one Pair`() {
        val units = listOf(ex("a", "g1"), ex("b", "g1")).groupedForDisplay()
        assertEquals(1, units.size)
        val pair = units[0] as ExerciseDisplayUnit.Pair
        assertEquals("a", pair.a.id)
        assertEquals("b", pair.b.id)
    }

    @Test fun `two exercises with different group ids stay separate`() {
        val units = listOf(ex("a", "g1"), ex("b", "g2")).groupedForDisplay()
        assertEquals(2, units.size)
        assertTrue(units.all { it is ExerciseDisplayUnit.Single })
    }

    @Test fun `two exercises with null group ids stay separate`() {
        val units = listOf(ex("a"), ex("b")).groupedForDisplay()
        assertEquals(2, units.size)
        assertTrue(units.all { it is ExerciseDisplayUnit.Single })
    }

    @Test fun `a pair followed by a single`() {
        val units = listOf(ex("a", "g1"), ex("b", "g1"), ex("c")).groupedForDisplay()
        assertEquals(2, units.size)
        assertTrue(units[0] is ExerciseDisplayUnit.Pair)
        assertEquals("c", (units[1] as ExerciseDisplayUnit.Single).exercise.id)
    }

    @Test fun `three consecutive sharing a group pair the first two, third is single`() {
        // Grouping is strictly pairwise-consecutive: it never makes a triple.
        val units = listOf(ex("a", "g1"), ex("b", "g1"), ex("c", "g1")).groupedForDisplay()
        assertEquals(2, units.size)
        val pair = units[0] as ExerciseDisplayUnit.Pair
        assertEquals("a", pair.a.id)
        assertEquals("b", pair.b.id)
        assertEquals("c", (units[1] as ExerciseDisplayUnit.Single).exercise.id)
    }

    @Test fun `unit id comes from the first exercise`() {
        val single = ExerciseDisplayUnit.Single(ex("solo"))
        val pair = ExerciseDisplayUnit.Pair(ex("first", "g"), ex("second", "g"))
        assertEquals("solo", single.id)
        assertEquals("first", pair.id)
    }

    @Test fun `a non-null group id with no matching neighbour is a single`() {
        val units = listOf(ex("a", "g1"), ex("b", "g2"), ex("c", "g2")).groupedForDisplay()
        // a is alone (its neighbour has a different group); b+c pair.
        assertEquals(2, units.size)
        assertTrue(units[0] is ExerciseDisplayUnit.Single)
        assertTrue(units[1] is ExerciseDisplayUnit.Pair)
    }
}
