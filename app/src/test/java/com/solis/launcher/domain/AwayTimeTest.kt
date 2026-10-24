package com.solis.launcher.domain

import com.solis.launcher.domain.AwayTime.DAY
import com.solis.launcher.domain.AwayTime.HOUR
import com.solis.launcher.domain.AwayTime.MINUTE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AwayTimeTest {

    @Test
    fun `format shows at most two units`() {
        assertEquals("under a minute", AwayTime.format(59_000L))
        assertEquals("12m", AwayTime.format(12 * MINUTE + 30_000L))
        assertEquals("5h 12m", AwayTime.format(5 * HOUR + 12 * MINUTE))
        assertEquals("3h", AwayTime.format(3 * HOUR))
        assertEquals("3d 4h", AwayTime.format(3 * DAY + 4 * HOUR + 59 * MINUTE))
        assertEquals("2d", AwayTime.format(2 * DAY + 10 * MINUTE))
    }

    @Test
    fun `away time never goes negative`() {
        assertEquals(0L, AwayTime.awayMillis(lastSeenMillis = 2_000L, nowMillis = 1_000L))
    }

    @Test
    fun `next milestone is the first one above the streak`() {
        assertEquals(HOUR, AwayTime.nextMilestone(0L))
        assertEquals(DAY, AwayTime.nextMilestone(6 * HOUR))
        assertNull(AwayTime.nextMilestone(400 * DAY))
    }

    @Test
    fun `encouragement changes as the streak grows`() {
        val messages = listOf(0L, 30 * MINUTE, 2 * HOUR, 12 * HOUR, 2 * DAY, 5 * DAY, 10 * DAY, 60 * DAY)
            .map(AwayTime::encouragement)
        assertEquals(messages.size, messages.toSet().size)
    }

    @Test
    fun `stats skip unflagged and unknown apps and sort shortest first`() {
        val insta = AppEntry("Insta", "com.insta", "Main")
        val tube = AppEntry("Tube", "com.tube", "Main")
        val notes = AppEntry("Notes", "com.notes", "Main")
        val now = 10 * DAY
        val stats = AwayTime.stats(
            flagged = setOf("com.insta", "com.tube", "com.gone"),
            apps = listOf(insta, tube, notes),
            lastSeen = mapOf("com.insta" to now - 3 * DAY, "com.tube" to now - HOUR, "com.notes" to 0L),
            best = mapOf("com.insta" to 5 * DAY, "com.tube" to 10 * MINUTE),
            nowMillis = now,
        )
        assertEquals(listOf(tube, insta), stats.map { it.app })
        assertEquals(HOUR, stats[0].bestMillis) // current streak beats the stored best
        assertEquals(5 * DAY, stats[1].bestMillis)
    }

    @Test
    fun `bests survive a round trip and junk is dropped`() {
        val best = mapOf("com.a" to 1L, "com.b.c" to 99L)
        assertEquals(best, AwayTime.decodeBest(AwayTime.encodeBest(best)))
        assertEquals(emptyMap<String, Long>(), AwayTime.decodeBest("nonsense"))
        assertEquals(emptyMap<String, Long>(), AwayTime.decodeBest(null))
    }
}
