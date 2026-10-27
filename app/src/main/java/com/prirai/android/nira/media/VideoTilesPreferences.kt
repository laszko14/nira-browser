package com.prirai.android.nira.media

import android.content.Context
import androidx.core.content.edit
import com.prirai.android.nira.preferences.UserPreferences

/** Uses the existing application preference file, with independent orientation values. */
internal class VideoTilesPreferences(context: Context) {
    private val preferences = UserPreferences(context.applicationContext).preferences
    private fun key(landscape: Boolean, field: String) = "video_tiles_${if (landscape) "landscape" else "portrait"}_$field"

    fun load(landscape: Boolean): VideoTilesSettings = VideoTilesSettings(
        rows = preferences.getInt(key(landscape, "rows"), 4),
        widthDp = preferences.getInt(key(landscape, "width"), if (landscape) 160 else 120),
        gapDp = preferences.getInt(key(landscape, "gap"), 8),
        showThumbnail = preferences.getBoolean(key(landscape, "thumbnail"), true),
        showTitle = preferences.getBoolean(key(landscape, "title"), true),
        showFormat = preferences.getBoolean(key(landscape, "format"), true),
        showQuality = preferences.getBoolean(key(landscape, "quality"), true),
        showSize = preferences.getBoolean(key(landscape, "size"), true),
        showActions = preferences.getBoolean(key(landscape, "actions"), true),
    ).normalized()

    /** Called on completed slider changes or discrete checkbox actions, never each drag frame. */
    fun save(landscape: Boolean, settings: VideoTilesSettings) {
        val bounded = settings.normalized()
        preferences.edit {
            putInt(key(landscape, "rows"), bounded.rows)
            putInt(key(landscape, "width"), bounded.widthDp)
            putInt(key(landscape, "gap"), bounded.gapDp)
            putBoolean(key(landscape, "thumbnail"), bounded.showThumbnail)
            putBoolean(key(landscape, "title"), bounded.showTitle)
            putBoolean(key(landscape, "format"), bounded.showFormat)
            putBoolean(key(landscape, "quality"), bounded.showQuality)
            putBoolean(key(landscape, "size"), bounded.showSize)
            putBoolean(key(landscape, "actions"), bounded.showActions)
        }
    }
}
