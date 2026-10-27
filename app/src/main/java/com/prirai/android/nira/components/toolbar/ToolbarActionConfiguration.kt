package com.prirai.android.nira.components.toolbar

import com.prirai.android.nira.preferences.UserPreferences

/** Stable action IDs shared by preferences, settings and Android toolbar views. */
object ToolbarActionConfiguration {
    val editableIds = listOf("new", "close", "settings", "back", "forward")
    val labels = mapOf("new" to "Nowa karta (+)", "close" to "Zamknij kartę (×)",
        "settings" to "Menu (⋮)", "back" to "Wstecz (‹)", "forward" to "Dalej (›)")

    fun normalizedOrder(raw: String): List<String> {
        val parsed = raw.split(",").filter { it in editableIds }.distinct()
        return parsed + editableIds.filterNot { it in parsed }
    }

    fun normalizedHidden(raw: String): Set<String> =
        raw.split(",").filter { it in editableIds }.toSet()

    fun getOrder(prefs: UserPreferences, landscape: Boolean): List<String> {
        val saved = if (landscape) prefs.niraToolbarLandscapeActionOrder
            else prefs.niraToolbarPortraitActionOrder
        // Honor the previous toolbar drag order until a separate orientation is configured.
        return normalizedOrder(saved.ifBlank { prefs.niraToolbarActionOrder })
    }

    fun getHidden(prefs: UserPreferences, landscape: Boolean): Set<String> =
        normalizedHidden(if (landscape) prefs.niraToolbarLandscapeHiddenActions
            else prefs.niraToolbarPortraitHiddenActions)

    fun getSize(prefs: UserPreferences, landscape: Boolean): Int {
        val key = if (landscape) "nira_toolbar_landscape_action_size_dp"
            else "nira_toolbar_portrait_action_size_dp"
        val size = if (!prefs.preferences.contains(key)) prefs.niraToolbarActionSlotDp
            else if (landscape) prefs.niraToolbarLandscapeActionSizeDp
            else prefs.niraToolbarPortraitActionSizeDp
        return size.coerceIn(24, 64)
    }

    fun storeOrder(prefs: UserPreferences, landscape: Boolean, order: List<String>) {
        val safe = normalizedOrder(order.joinToString(",")).joinToString(",")
        if (landscape) prefs.niraToolbarLandscapeActionOrder = safe
        else prefs.niraToolbarPortraitActionOrder = safe
    }

    fun storeHidden(prefs: UserPreferences, landscape: Boolean, hidden: Set<String>) {
        val safe = editableIds.filter { it in hidden }.joinToString(",")
        if (landscape) prefs.niraToolbarLandscapeHiddenActions = safe
        else prefs.niraToolbarPortraitHiddenActions = safe
    }

    fun storeSize(prefs: UserPreferences, landscape: Boolean, dp: Int) {
        val safe = dp.coerceIn(24, 64)
        if (landscape) prefs.niraToolbarLandscapeActionSizeDp = safe
        else prefs.niraToolbarPortraitActionSizeDp = safe
    }

    fun restore(prefs: UserPreferences, landscape: Boolean) {
        storeOrder(prefs, landscape, editableIds)
        storeHidden(prefs, landscape, emptySet())
        storeSize(prefs, landscape, 40)
    }
}
