package com.vasilisneo.trackstar.ui.screens.main.workout

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

// weekIdentifierFor() uses ISO weeks (Monday-first, like iOS and the backend) whatever the phone's
// locale — so the tests run under Locale.US, whose own weeks start on Sunday, to prove it.
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
        assertEquals("2026-W02", weekIdentifierFor(LocalDate.of(2026, 1, 5)))
    }

    @Test
    fun `dates in the same week produce the same identifier`() {
        // 2026-03-16 is a Monday; the Sunday after is still the same ISO week.
        val monday = LocalDate.of(2026, 3, 16)
        val sunday = LocalDate.of(2026, 3, 22)
        assertEquals(weekIdentifierFor(monday), weekIdentifierFor(sunday))
        assertEquals("2026-W12", weekIdentifierFor(sunday))
    }

    @Test
    fun `crossing into the next week changes the identifier`() {
        // Sunday ends the week; Monday starts the next (in the US locale Sunday would start it).
        val sunday = LocalDate.of(2026, 3, 22)
        val nextMonday = LocalDate.of(2026, 3, 23)
        assertNotEquals(weekIdentifierFor(sunday), weekIdentifierFor(nextMonday))
    }

    @Test
    fun `same calendar date always maps to the same identifier`() {
        val d = LocalDate.of(2026, 7, 1)
        assertEquals(weekIdentifierFor(d), weekIdentifierFor(d))
    }
}
