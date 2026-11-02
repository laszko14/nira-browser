package com.prirai.android.nira.videosearch

import android.app.Application
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Guards the actual dialog controls, rather than only asserting source strings. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class VideoSearchQueueDialogUiTest {
    private fun descendants(root: View): List<View> = listOf(root) +
        if (root is ViewGroup) (0 until root.childCount).flatMap {
            descendants(root.getChildAt(it))
        } else emptyList()

    @Test fun queueSurfaceIsNeonAndActionsStaySeparated() {
        val ui = VideoSearchQueueDialogUi.create(RuntimeEnvironment.getApplication(), "rower")
        assertTrue(ui.view is ScrollView)
        val views = descendants(ui.view)
        assertNotNull(views.firstOrNull { it.tag == "video-queue-neon-dialog" })
        val backdrop = views.first { it.tag == "video-queue-neon-dialog" }
        assertTrue(backdrop.background is GradientDrawable)
        assertEquals("rower", ui.phrase.text.toString())
        assertTrue(views.containsAll(listOf(ui.phrase, ui.showPlan, ui.cancel, ui.start)))
        assertEquals("video-queue-phrase", ui.phrase.tag)
        assertEquals("video-queue-show-plan", ui.showPlan.tag)
        assertEquals("video-queue-cancel", ui.cancel.tag)
        assertEquals("video-queue-start", ui.start.tag)
        for (button in listOf(ui.showPlan, ui.cancel, ui.start)) {
            assertTrue(button is Button)
            assertTrue(button.background is GradientDrawable)
            assertEquals(Color.WHITE, button.currentTextColor)
            assertFalse(button.isAllCaps)
        }
        assertTrue(ui.start.text.toString().contains("Uruchom wyszukiwanie"))
        assertTrue(ui.showPlan.text.toString().contains("Pokaż plan"))
        assertTrue(ui.cancel.text.toString().contains("Anuluj"))
        assertFalse(ui.phrase.isSaveEnabled)
        assertEquals(Color.WHITE, ui.phrase.currentTextColor)
    }

    @Test fun dialogFitsNarrowScreenAndKeepsDraftOnRebuild() {
        RuntimeEnvironment.setQualifiers("w320dp-h580dp-port")
        val ctx = RuntimeEnvironment.getApplication<Application>()
        val ui = VideoSearchQueueDialogUi.create(ctx, "test fraza")
        val width = (320f * ctx.resources.displayMetrics.density).toInt()
        val height = (430f * ctx.resources.displayMetrics.density).toInt()
        ui.view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST))
        ui.view.layout(0, 0, ui.view.measuredWidth, ui.view.measuredHeight)
        assertTrue(ui.view.measuredWidth > 0)
        assertTrue(ui.view.measuredHeight <= height)
        assertEquals("test fraza", ui.phrase.text.toString())
        assertEquals("test fraza", VideoSearchQueueDialogUi.create(ctx,
            ui.phrase.text.toString()).phrase.text.toString())
    }
}
