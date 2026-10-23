package com.solis.launcher.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurfewPolicyTest {

    private fun at(h: Int, m: Int = 0) = h * 60 + m

    @Test
    fun `disabled curfew never locks`() {
        assertFalse(CurfewPolicy.isLocked(CurfewConfig(enabled = false), at(23)))
    }

    @Test
    fun `overnight window wraps midnight`() {
        val c = CurfewConfig(enabled = true, startMinutes = at(22), endMinutes = at(7))
        assertTrue(CurfewPolicy.isLocked(c, at(22)))
        assertTrue(CurfewPolicy.isLocked(c, at(3)))
        assertFalse(CurfewPolicy.isLocked(c, at(7)))
        assertFalse(CurfewPolicy.isLocked(c, at(12)))
    }

    @Test
    fun `same-day window`() {
        val c = CurfewConfig(enabled = true, startMinutes = at(9), endMinutes = at(17))
        assertTrue(CurfewPolicy.isLocked(c, at(9)))
        assertFalse(CurfewPolicy.isLocked(c, at(17)))
        assertFalse(CurfewPolicy.isLocked(c, at(8, 59)))
    }

    @Test
    fun `zero length window never locks`() {
        val c = CurfewConfig(enabled = true, startMinutes = at(9), endMinutes = at(9))
        assertFalse(CurfewPolicy.isLocked(c, at(9)))
    }
}
