package com.prirai.android.nira.media

import android.app.Application
import com.prirai.android.nira.preferences.UserPreferences
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class VideoTilesPreferencesTest {
    @Test fun independentOrientationsSurviveNewPreferenceInstance() {
        val context = RuntimeEnvironment.getApplication()
        UserPreferences(context).preferences.edit().clear().commit()
        val first = VideoTilesPreferences(context)
        first.save(false, VideoTilesSettings(10, 120))
        first.save(true, VideoTilesSettings(2, 260))
        val restored = VideoTilesPreferences(context)
        assertEquals(VideoTilesSettings(10, 120), restored.load(false))
        assertEquals(VideoTilesSettings(2, 260), restored.load(true))
    }

    @Test fun orientationDefaultsMatchReferenceDensityWithoutSharingSettings() {
        val context = RuntimeEnvironment.getApplication()
        UserPreferences(context).preferences.edit().clear().commit()
        val preferences = VideoTilesPreferences(context)
        assertEquals(VideoTilesSettings(4, 120, 8), preferences.load(false))
        assertEquals(VideoTilesSettings(4, 160, 8), preferences.load(true))
    }

    @Test fun gapAndEveryVisibilityFlagPersistSeparatelyForBothOrientations() {
        val context = RuntimeEnvironment.getApplication()
        UserPreferences(context).preferences.edit().clear().commit()
        val settings = VideoTilesSettings(10, 100, 2, false, false, false, false, false, false)
        val first = VideoTilesPreferences(context)
        first.save(false, settings)
        first.save(true, VideoTilesSettings(2, 280, 16))
        val restored = VideoTilesPreferences(context)
        assertEquals(settings, restored.load(false))
        assertEquals(VideoTilesSettings(2, 280, 16), restored.load(true))
    }

    @Test fun invalidPersistedValuesAreBoundedAndOtherPreferencesSurvive() {
        val context = RuntimeEnvironment.getApplication()
        val existing = UserPreferences(context).preferences
        existing.edit().clear().putInt("video_tiles_portrait_rows", 999)
            .putInt("video_tiles_portrait_width", -5).putString("unrelated", "retained").commit()
        val stored = VideoTilesPreferences(context)
        assertEquals(VideoTilesSettings(10, 100), stored.load(false))
        stored.save(true, VideoTilesSettings(10, 160))
        assertEquals("retained", existing.getString("unrelated", null))
    }
}
