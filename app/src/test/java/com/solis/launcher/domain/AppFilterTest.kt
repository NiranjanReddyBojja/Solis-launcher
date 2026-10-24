package com.solis.launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AppFilterTest {

    private val bank = AppEntry("Bank", "com.bank", "Main")
    private val books = AppEntry("books", "com.books", "Main")
    private val chrome = AppEntry("Chrome", "com.chrome", "Main")
    private val apps = listOf(chrome, bank, books)
    private val hidden = setOf("com.bank")

    @Test
    fun `visible list excludes the vault and is alphabetical ignoring case`() {
        assertEquals(listOf(books, chrome), AppFilter.visible(apps, hidden))
        assertEquals(listOf(bank, books, chrome), AppFilter.visible(apps, emptySet()))
    }

    @Test
    fun `vault holds only hidden apps`() {
        assertEquals(listOf(bank), AppFilter.vault(apps, hidden))
    }

    @Test
    fun `accents sort and file under their base letter`() {
        val eclair = AppEntry("Éclair", "com.eclair", "Main")
        val email = AppEntry("Email", "com.email", "Main")
        val sorted = AppFilter.visible(listOf(email, eclair), emptySet())
        assertEquals(listOf(eclair, email), sorted)
        assertEquals(listOf(AppSection('E', sorted)), AppFilter.sections(sorted))
    }

    @Test
    fun `digits and symbols go under the other heading, first`() {
        val game = AppEntry("2048", "com.game", "Main")
        val sorted = AppFilter.visible(listOf(chrome, game, bank), emptySet())
        assertEquals(
            listOf('#', 'B', 'C'),
            AppFilter.sections(sorted).map { it.letter },
        )
        assertEquals(AppFilter.OTHER, AppFilter.letterOf("  "))
    }
}
