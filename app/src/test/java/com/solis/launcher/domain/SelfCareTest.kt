package com.solis.launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SelfCareTest {

    @Test
    fun `prompt is stable for a day and changes the next`() {
        assertEquals(SelfCare.promptFor(20_000L), SelfCare.promptFor(20_000L))
        assertNotEquals(SelfCare.promptFor(20_000L), SelfCare.promptFor(20_001L))
        // Negative epoch days still map into the list.
        SelfCare.promptFor(-1L)
    }

    @Test
    fun `reflections survive a round trip with newlines`() {
        val entries = listOf(
            Reflection(2L, "how are you?", "tired.\nbut better"),
            Reflection(1L, "what went right?", "a walk, a call, a meal"),
        )
        assertEquals(entries, SelfCare.decode(SelfCare.encode(entries)))
        assertEquals(emptyList<Reflection>(), SelfCare.decode(null))
    }

    @Test
    fun `separators typed by the user cannot corrupt the store`() {
        val tricky = Reflection(5L, "p", "a\u001Eb\u001Fc")
        val decoded = SelfCare.decode(SelfCare.encode(listOf(tricky)))
        assertEquals(listOf(Reflection(5L, "p", "a b c")), decoded)
    }

    @Test
    fun `add puts newest first, ignores blanks and caps the list`() {
        var list = emptyList<Reflection>()
        list = SelfCare.add(list, Reflection(1L, "p", "   "))
        assertEquals(0, list.size)
        repeat(SelfCare.MAX_REFLECTIONS + 5) { i -> list = SelfCare.add(list, Reflection(i.toLong(), "p", " t$i ")) }
        assertEquals(SelfCare.MAX_REFLECTIONS, list.size)
        assertEquals("t${SelfCare.MAX_REFLECTIONS + 4}", list.first().text)
    }
}
