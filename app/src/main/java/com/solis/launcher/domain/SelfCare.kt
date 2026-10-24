package com.solis.launcher.domain

/** One saved answer to a reflection prompt. [atMillis] doubles as its id. */
data class Reflection(val atMillis: Long, val prompt: String, val text: String)

/** The self-care space: box breathing and one reflection prompt a day. */
object SelfCare {

    /** Box breathing: four phases of the same length, repeated. */
    val BREATH_PHASES = listOf("breathe in", "hold", "breathe out", "hold")
    const val BREATH_PHASE_SECONDS = 4
    const val BREATH_ROUNDS = 4

    const val MAX_REFLECTIONS = 200
    const val MAX_REFLECTION_LENGTH = 1_000

    val PROMPTS = listOf(
        "what did you notice today that you would have missed on a screen?",
        "what is one thing your body is asking you for right now?",
        "who would you like to call instead of scrolling?",
        "what are you avoiding by reaching for your phone?",
        "name three small things that went right today.",
        "what would make tomorrow morning feel calm?",
        "what are you proud of this week?",
        "when did you last feel bored, and what came out of it?",
        "what is something you want to learn with the time you get back?",
        "how do you want to feel when you put your phone down tonight?",
        "what is one kind thing you can do for yourself today?",
        "what drained you today, and what filled you up?",
        "describe the room you are in, without looking at a screen.",
        "what would the version of you without the habit do this evening?",
        "what feeling usually comes right before the urge to scroll?",
        "write a short note to yourself one month from now.",
        "what made you laugh recently?",
        "what is one thing you can let go of today?",
        "who has been kind to you lately? what did they do?",
        "what does a good day look like for you, start to finish?",
        "where do you feel tension in your body right now? breathe into it.",
    )

    /** Same prompt all day, a new one tomorrow. */
    fun promptFor(epochDay: Long): String = PROMPTS[Math.floorMod(epochDay, PROMPTS.size.toLong()).toInt()]

    /** Adds a reflection to the front, trimmed and capped. Blank text is ignored. */
    fun add(existing: List<Reflection>, entry: Reflection): List<Reflection> {
        val text = entry.text.trim().take(MAX_REFLECTION_LENGTH)
        if (text.isEmpty()) return existing
        return (listOf(entry.copy(text = text)) + existing).take(MAX_REFLECTIONS)
    }

    // ---- Persistence -----------------------------------------------------------------------

    /** ASCII record and unit separators: typed text may hold newlines, never these. */
    private const val RECORD = '\u001E'
    private const val UNIT = '\u001F'

    fun encode(entries: List<Reflection>): String = entries.joinToString(RECORD.toString()) { e ->
        listOf(e.atMillis.toString(), clean(e.prompt), clean(e.text)).joinToString(UNIT.toString())
    }

    fun decode(raw: String?): List<Reflection> =
        raw?.split(RECORD)?.mapNotNull { record ->
            val parts = record.split(UNIT)
            if (parts.size != 3) return@mapNotNull null
            val at = parts[0].toLongOrNull() ?: return@mapNotNull null
            if (parts[2].isBlank()) null else Reflection(at, parts[1], parts[2])
        } ?: emptyList()

    private fun clean(s: String) = s.replace(RECORD, ' ').replace(UNIT, ' ')
}
