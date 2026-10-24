package com.solis.launcher.domain

import java.text.Normalizer

/** One letter of the app list and the apps filed under it. */
data class AppSection(val letter: Char, val apps: List<AppEntry>)

/**
 * Pure ordering rules for the app list, kept free of Android types so they are unit tested.
 */
object AppFilter {

    /** The letter used for names that do not start with a Latin letter. */
    const val OTHER = '#'

    private val byLabel: Comparator<AppEntry> =
        compareBy<AppEntry> { sortKey(it.label) }.thenBy { it.label }.thenBy { it.key }

    /** Apps shown in the list: everything except the vault, A to Z. */
    fun visible(apps: List<AppEntry>, hidden: Set<String>): List<AppEntry> =
        apps.filterNot { it.packageName in hidden }.sortedWith(byLabel)

    /** Apps in the vault, A to Z. Only shown when the user opens the vault on purpose. */
    fun vault(apps: List<AppEntry>, hidden: Set<String>): List<AppEntry> =
        apps.filter { it.packageName in hidden }.sortedWith(byLabel)

    /** Groups an already sorted list by first letter. Digits and symbols go under [OTHER], first. */
    fun sections(sorted: List<AppEntry>): List<AppSection> =
        sorted.groupBy { letterOf(it.label) }
            .map { (letter, apps) -> AppSection(letter, apps) }
            .sortedWith(compareBy<AppSection> { it.letter != OTHER }.thenBy { it.letter })

    /** "Éclair" files under E, "2048" under #. */
    fun letterOf(label: String): Char {
        val first = sortKey(label).firstOrNull() ?: return OTHER
        return if (first in 'a'..'z') first.uppercaseChar() else OTHER
    }

    /** Lowercase, trimmed, accents removed, so sorting ignores case and diacritics. */
    private fun sortKey(label: String): String =
        Normalizer.normalize(label.trim(), Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .lowercase()

    private val DIACRITICS = Regex("\\p{Mn}+")
}
