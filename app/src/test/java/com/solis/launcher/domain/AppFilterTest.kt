package com.solis.launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AppFilterTest {

    private val bank = AppEntry("Bank", "com.bank", "Main")
    private val books = AppEntry("Books", "com.books", "Main")
    private val chrome = AppEntry("Chrome", "com.chrome", "Main")
    private val apps = listOf(bank, books, chrome)
    private val hidden = setOf("com.bank")

    @Test
    fun `carousel excludes hidden apps`() {
        assertEquals(listOf(books, chrome), AppFilter.carousel(apps, hidden))
    }

    @Test
    fun `partial query does not reveal hidden apps`() {
        assertEquals(listOf(books), AppFilter.search(apps, hidden, "b"))
        assertEquals(emptyList<AppEntry>(), AppFilter.search(apps, hidden, "ban"))
    }

    @Test
    fun `exact name reveals hidden app regardless of case and spaces`() {
        assertEquals(listOf(bank), AppFilter.search(apps, hidden, "  bAnK "))
    }

    @Test
    fun `prefix matches sort before substring matches`() {
        val maps = AppEntry("Maps", "com.maps", "Main")
        val gmail = AppEntry("Gmail", "com.gmail", "Main")
        assertEquals(listOf(maps, gmail), AppFilter.search(listOf(gmail, maps), emptySet(), "ma"))
    }
}
