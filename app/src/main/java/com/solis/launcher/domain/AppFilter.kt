package com.solis.launcher.domain

/**
 * Pure filtering rules for the focus drawer, kept free of Android types so they are unit tested.
 */
object AppFilter {

    /** Apps shown in the one-at-a-time carousel: everything except the vault. */
    fun carousel(apps: List<AppEntry>, hidden: Set<String>): List<AppEntry> =
        apps.filterNot { it.packageName in hidden }

    /**
     * Apps shown while searching. Visible apps match on substring. Hidden apps only surface when
     * the query is their exact name (case-insensitive), so the vault stays out of casual reach.
     */
    fun search(apps: List<AppEntry>, hidden: Set<String>, query: String): List<AppEntry> {
        val q = query.trim()
        if (q.isEmpty()) return carousel(apps, hidden)
        return apps.filter { app ->
            if (app.packageName in hidden) {
                app.label.equals(q, ignoreCase = true)
            } else {
                app.label.contains(q, ignoreCase = true)
            }
        }.sortedWith(
            // Prefix matches first, then alphabetical.
            compareByDescending<AppEntry> { it.label.startsWith(q, ignoreCase = true) }
                .thenBy { it.label.lowercase() },
        )
    }
}
