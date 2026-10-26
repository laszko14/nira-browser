package com.prirai.android.nira.components.toolbar

import android.app.Activity
import android.app.Application
import android.graphics.Rect
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.prirai.android.nira.media.MediaFloatingUi
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE, qualifiers = "w1000dp-h1200dp")
class MediaButtonLayoutTest {
    @Test fun portraitLandscapeInsetsKeyboardAndLiveStripGeometryKeepTouchAreasSeparate() {
        for ((width, height) in listOf(430 to 900, 900 to 430)) {
            val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
            var visible = Rect(0, 0, width, height)
            val parent = object : FrameLayout(activity) {
                override fun getWindowVisibleDisplayFrame(out: Rect) {
                    val origin = IntArray(2); getLocationOnScreen(origin)
                    out.set(visible); out.offset(origin[0], origin[1])
                }
            }
            activity.setContentView(parent)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            val strip = FrameLayout(activity)
            parent.addView(strip, FrameLayout.LayoutParams(-1, 60, Gravity.BOTTOM))
            var clicks = 0
            val lastTab = View(activity).apply { setOnClickListener { clicks++ } }
            strip.addView(lastTab, FrameLayout.LayoutParams(100, -1, Gravity.RIGHT))
            val overlay = MediaFloatingUi(parent, { null }, { viewport ->
                BrowserChromeBounds.reservations(parent, viewport, emptyList(), listOf(strip))
            }, {})
            val button = parent.getChildAt(1)
            fun draw() {
                parent.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                parent.layout(0, 0, width, height)
                parent.viewTreeObserver.dispatchOnPreDraw()
            }
            draw()
            val bottomVisible = button.y + button.height
            assertTrue("button bottom=$bottomVisible strip top=${strip.top} shown=${strip.isShown}", bottomVisible < strip.top)
            // Hit test the last tab through the actual parent's dispatch, not an icon's bounds.
            val x = width - 25f; val y = height - 25f
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(0, 1, action, x, y, 0)
                parent.dispatchTouchEvent(event); event.recycle()
            }
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(1, clicks)
            draw()
            strip.visibility = View.GONE; draw()
            assertEquals(bottomVisible + 60, button.y + button.height, 0.1f)
            strip.visibility = View.VISIBLE
            strip.layoutParams.height = 90; strip.requestLayout(); draw()
            assertEquals(bottomVisible - 30, button.y + button.height, 0.1f)
            strip.translationY = 45f; parent.viewTreeObserver.dispatchOnPreDraw()
            assertEquals(bottomVisible + 15, button.y + button.height, 0.1f)
            // IME clips a bar below the visible viewport: do not reserve its height twice.
            visible = Rect(12, 24, width - 30, height - 200)
            parent.viewTreeObserver.dispatchOnPreDraw()
            assertTrue(button.y + button.height < visible.bottom)
            assertTrue(button.x + button.width < visible.right)
            visible = Rect(0, 0, width, height); strip.translationY = 0f
            overlay.setFullscreen(true); assertEquals(View.GONE, button.visibility)
            overlay.setFullscreen(false); draw()
            assertTrue(button.y + button.height < strip.top)
            val previousY = button.y
            visible = Rect(width + 100, height + 100, width + 200, height + 200)
            parent.viewTreeObserver.dispatchOnPreDraw()
            assertEquals(previousY, button.y, 0.1f) // No intersection: keep the previous valid placement.
            overlay.detach()
            parent.viewTreeObserver.dispatchOnPreDraw()
            assertEquals(1, parent.childCount)
            activity.finish()
        }
    }
    @Test fun chromeChangeWithoutParentResizeMovesTheEntireClickableButton() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val parent = FrameLayout(activity)
        activity.setContentView(parent)
        parent.layout(0, 0, 900, 450)
        var reserved = 0
        val overlay = MediaFloatingUi(parent, { null }, { 0 to reserved }, {})
        parent.measure(View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(450, View.MeasureSpec.EXACTLY))
        parent.layout(0, 0, 900, 450)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        parent.viewTreeObserver.dispatchOnPreDraw()
        val button = parent.getChildAt(0)
        val initialBottom = button.y + button.height
        reserved = 90
        parent.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(initialBottom - 90, button.y + button.height, 0.1f)
        reserved = 0
        parent.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(initialBottom, button.y + button.height, 0.1f)
        overlay.setFullscreen(true); assertEquals(View.GONE, button.visibility)
        overlay.setFullscreen(false); assertEquals(View.VISIBLE, button.visibility)
        // Rotate/resize the SAME parent and overlay, without reconstructing the button.
        parent.measure(View.MeasureSpec.makeMeasureSpec(450, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY))
        parent.layout(0, 0, 450, 900)
        parent.viewTreeObserver.dispatchOnPreDraw()
        assertTrue(button.x + button.width < 450)
        assertTrue(button.y + button.height <= 900)
        overlay.detach(); assertEquals(0, parent.childCount)
        activity.finish()
    }
}
