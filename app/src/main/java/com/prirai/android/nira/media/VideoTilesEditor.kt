package com.prirai.android.nira.media

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Merges queued UI callbacks against current preview, independently of recomposition timing. */
internal class VideoTilesEditor(initial: VideoTilesSettings) {
    var value by mutableStateOf(initial.normalized())
        private set

    fun update(change: (VideoTilesSettings) -> VideoTilesSettings) {
        value = change(value).normalized()
    }
}
