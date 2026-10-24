package com.solis.launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuoteRotationTest {

    private val defaults = listOf("a", "b", "c")

    @Test
    fun `rotation is due after six hours`() {
        val sixHours = QuoteRotation.ROTATION_INTERVAL_MILLIS
        assertTrue(QuoteRotation.isDue(0L, 1L))
        assertFalse(QuoteRotation.isDue(1_000L, 1_000L + sixHours - 1))
        assertTrue(QuoteRotation.isDue(1_000L, 1_000L + sixHours))
    }

    @Test
    fun `custom quotes need the toggle and at least one quote`() {
        val custom = listOf("mine")
        val base = LauncherPreferences(customQuotes = custom, useCustomQuotes = true)
        assertEquals(custom, QuoteRotation.activeBank(base, defaults))
        assertEquals(defaults, QuoteRotation.activeBank(base.copy(useCustomQuotes = false), defaults))
        assertEquals(defaults, QuoteRotation.activeBank(base.copy(customQuotes = emptyList()), defaults))
    }

    @Test
    fun `index wraps in both directions`() {
        assertEquals("a", QuoteRotation.quoteAt(defaults, 3))
        assertEquals("c", QuoteRotation.quoteAt(defaults, -1))
    }
}
