package com.prirai.android.nira.browser.tabs

import android.content.Context
import com.prirai.android.nira.preferences.UserPreferences

/**
 * One list/grid preference for the settings switch and Compose tab manager.
 * The actual tab-manager value takes precedence; migrate explicit legacy
 * choices only if that newer key is absent. The side drawer remains a list.
 */
object TabGridViewPreference {
    private const val FILE = "tabs_view_prefs"
    private const val KEY = "is_grid_view"
    private const val LEGACY_KEY = "show_tabs_in_grid"

    fun read(context: Context): Boolean {
        val canonical = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val legacy = UserPreferences(context)
        val legacyPrefs = legacy.preferences
        val value = when {
            canonical.contains(KEY) -> canonical.getBoolean(KEY, false)
            legacyPrefs.contains(LEGACY_KEY) -> legacy.showTabsInGrid
            else -> false
        }
        if (!canonical.contains(KEY)) canonical.edit().putBoolean(KEY, value).apply()
        if (!legacyPrefs.contains(LEGACY_KEY) || legacy.showTabsInGrid != value) {
            legacy.showTabsInGrid = value
        }
        return value
    }

    fun store(context: Context, grid: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, grid).apply()
        UserPreferences(context).showTabsInGrid = grid
    }
}
