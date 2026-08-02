package com.vasilisneo.trackstar.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class InitialsTest {

    @Test fun `two names give first-plus-last uppercased`() {
        assertEquals("VN", initialsFrom("Vasilis Neophytou"))
    }

    @Test fun `single name gives one initial`() {
        assertEquals("V", initialsFrom("Vasilis"))
    }

    @Test fun `more than two names uses first and last`() {
        assertEquals("JD", initialsFrom("John Michael Doe"))
    }

    @Test fun `null or blank gives a question mark`() {
        assertEquals("?", initialsFrom(null))
        assertEquals("?", initialsFrom(""))
        assertEquals("?", initialsFrom("   "))
    }

    @Test fun `surrounding whitespace is trimmed`() {
        assertEquals("VN", initialsFrom("  Vasilis Neophytou  "))
    }

    @Test fun `result is always uppercase`() {
        assertEquals("JD", initialsFrom("john doe"))
    }
}
