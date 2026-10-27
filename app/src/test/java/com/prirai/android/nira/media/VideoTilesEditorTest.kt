package com.prirai.android.nira.media

import org.junit.Assert.*
import org.junit.Test

class VideoTilesEditorTest {
    @Test fun callbacksCreatedTogetherMergeDifferentFieldsBeforeRecomposition() {
        val editor = VideoTilesEditor(VideoTilesSettings(10, 160))
        val rows = { value: Int -> editor.update { it.copy(rows = value) } }
        val width = { value: Int -> editor.update { it.copy(widthDp = value) } }
        val gap = { value: Int -> editor.update { it.copy(gapDp = value) } }
        rows(3)
        width(220)
        gap(12)
        assertEquals(VideoTilesSettings(3, 220, 12), editor.value)
    }

    @Test fun rapidCheckboxCallbacksKeepEveryPriorChoiceAndNormalizeGeometry() {
        val editor = VideoTilesEditor(VideoTilesSettings())
        val thumbnail = { editor.update { it.copy(showThumbnail = false) } }
        val title = { editor.update { it.copy(showTitle = false) } }
        val actions = { editor.update { it.copy(showActions = false) } }
        thumbnail(); title(); actions()
        editor.update { it.copy(rows = 999, widthDp = 5, gapDp = 99) }
        assertEquals(VideoTilesSettings(10, 100, 16, showThumbnail = false, showTitle = false, showActions = false), editor.value)
    }
}
