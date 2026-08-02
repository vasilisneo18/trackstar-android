package com.vasilisneo.trackstar.ui.screens.main.workout

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

// weekIdentifierFor() reads Locale.getDefault(), so pin a fixed locale for deterministic weeks and
// restore it afterwards. Locale.US = weeks start Sunday, minimal-days-in-first-week = 1.
class WeekIdentifierTest {

    private lateinit var original: Locale

    @Before fun setUp() {
        original = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After fun tearDown() {
        Locale.setDefault(original)
    }

    @Test
    fun `format is year dash W two-digit-week`() {
        val id = weekIdentifierFor(LocalDate.of(2026, 3, 15))
        assertTrue("was: $id", id.matches(Regex("""\d{4}-W\d{2}""")))
    }

    @Test
    fun `single-digit week is zero-padded`() {
        // Early January falls in week 1 or 2 under US rules — either way two digits.
        val id = weekIdentifierFor(LocalDate.of(2026, 1, 5))
        assertTrue("was: $id", id.substringAfter("-W").length == 2)
    }

    @Test
    fun `dates in the same week produce the same identifier`() {
        // 2026-03-15 is a Sunday (start of a US week); the following Saturday is the same week.
        val sunday = LocalDate.of(2026, 3, 15)
        val saturday = LocalDate.of(2026, 3, 21)
        assertEquals(weekIdentifierFor(sunday), weekIdentifierFor(saturday))
    }

    @Test
    fun `crossing into the next week changes the identifier`() {
        val saturday = LocalDate.of(2026, 3, 21)
        val nextSunday = LocalDate.of(2026, 3, 22)
        assertNotEquals(weekIdentifierFor(saturday), weekIdentifierFor(nextSunday))
    }

    @Test
    fun `same calendar date always maps to the same identifier`() {
        val d = LocalDate.of(2026, 7, 1)
        assertEquals(weekIdentifierFor(d), weekIdentifierFor(d))
    }
}
