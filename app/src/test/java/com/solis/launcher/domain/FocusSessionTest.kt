package com.solis.launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusSessionTest {

    @Test
    fun `remaining time rounds up and stops at zero`() {
        val end = FocusSession.endsAt(nowMillis = 1_000L, minutes = 25)
        assertEquals(1_000L + 25 * 60_000L, end)
        assertEquals(25 * 60L, FocusSession.remainingSeconds(end, 1_000L))
        assertEquals(1L, FocusSession.remainingSeconds(end, end - 1))
        assertEquals(0L, FocusSession.remainingSeconds(end, end))
        assertEquals(0L, FocusSession.remainingSeconds(0L, 1_000L))
    }

    @Test
    fun `format shows hours only when needed`() {
        assertEquals("24:59", FocusSession.format(24 * 60L + 59))
        assertEquals("1:30:00", FocusSession.format(90 * 60L))
        assertEquals("00:01", FocusSession.format(1))
    }
}
