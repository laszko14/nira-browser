package com.prirai.android.nira.browser.tabs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.prirai.android.nira.preferences.UserPreferences
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TabGridViewPreferenceTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val canonical get() = context.getSharedPreferences("tabs_view_prefs", Context.MODE_PRIVATE)
    private val legacy get() = UserPreferences(context).preferences

    @Before fun before() = clear()
    @After fun after() = clear()
    private fun clear() {
        canonical.edit().remove("is_grid_view").commit()
        legacy.edit().remove("show_tabs_in_grid").commit()
    }

    @Test fun savedDialogModeWinsOverContradictoryLegacySetting() {
        canonical.edit().putBoolean("is_grid_view", false).commit()
        legacy.edit().putBoolean("show_tabs_in_grid", true).commit()
        assertFalse(TabGridViewPreference.read(context))
        assertFalse(legacy.getBoolean("show_tabs_in_grid", true))
    }

    @Test fun legacySettingMigratesOnlyIfDialogHasNoValue() {
        legacy.edit().putBoolean("show_tabs_in_grid", true).commit()
        assertTrue(TabGridViewPreference.read(context))
        assertTrue(canonical.getBoolean("is_grid_view", false))
        legacy.edit().putBoolean("show_tabs_in_grid", false).commit()
        assertTrue(TabGridViewPreference.read(context))
    }

    @Test fun noSavedChoiceUsesExistingTabDialogListDefault() {
        assertFalse(TabGridViewPreference.read(context))
        assertFalse(canonical.getBoolean("is_grid_view", true))
    }

    @Test fun settingsAndDialogReadBothNewAndOldKeysConsistently() {
        TabGridViewPreference.store(context, true)
        assertTrue(TabGridViewPreference.read(context))
        assertTrue(UserPreferences(context).showTabsInGrid)
        TabGridViewPreference.store(context, false)
        assertFalse(TabGridViewPreference.read(context))
        assertFalse(UserPreferences(context).showTabsInGrid)
    }
}
