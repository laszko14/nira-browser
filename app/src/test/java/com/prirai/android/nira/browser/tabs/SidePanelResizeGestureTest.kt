package com.prirai.android.nira.browser.tabs

import android.app.Application
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class SidePanelResizeGestureTest {
    private class Fixture {
        var width = 400
        var starts = 0
        val completed = mutableListOf<Triple<Boolean, Int, Int>>()
        val persisted = mutableMapOf<String, Int>()
        var orientation = "width_portrait"
        var downOrientation = ""
        val gesture = SidePanelResizeGesture(
            initialWidth = { width },
            allowedWidths = { 220..452 },
            onStart = { starts++; downOrientation = orientation },
            onResize = { width = it },
            onFinish = { committed, initial, final ->
                completed.add(Triple(committed, initial, final))
                if (committed) persisted[downOrientation] = final
            },
        )
        fun send(action: Int, vararg points: Pair<Int, Float>) {
            val properties = Array(points.size) { index ->
                MotionEvent.PointerProperties().apply {
                    id = points[index].first
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }
            }
            val coords = Array(points.size) { index ->
                MotionEvent.PointerCoords().apply {
                    x = points[index].second
                    y = 5f
                    pressure = 1f
                    size = 1f
                }
            }
            val event = MotionEvent.obtain(
                0L, 12L, action, points.size, properties, coords,
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0
            )
            try { assertTrue(gesture.onTouch(event)) } finally { event.recycle() }
        }
    }

    @Test fun oneFingerResizesAndPersistsOnlyAtUp() {
        val f = Fixture()
        f.send(MotionEvent.ACTION_DOWN, 7 to 390f)
        f.send(MotionEvent.ACTION_MOVE, 7 to 425f)
        assertEquals(435, f.width)
        assertTrue(f.persisted.isEmpty())
        f.send(MotionEvent.ACTION_UP, 7 to 430f)
        assertEquals(440, f.width)
        assertEquals(listOf(Triple(true, 400, 440)), f.completed)
        assertEquals(mapOf("width_portrait" to 440), f.persisted)
    }

    @Test fun anotherFingerCancelsAndDoesNotJumpOrPersistAfterOwnerLifts() {
        val f = Fixture()
        f.send(MotionEvent.ACTION_DOWN, 0 to 395f)
        f.send(MotionEvent.ACTION_MOVE, 0 to 420f)
        assertEquals(425, f.width)
        f.send(MotionEvent.ACTION_POINTER_DOWN or
            (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 0 to 420f, 1 to 390f)
        assertEquals(400, f.width) // Roll back the draft width.
        f.send(MotionEvent.ACTION_POINTER_UP, 0 to 420f, 1 to 245f)
        f.send(MotionEvent.ACTION_MOVE, 1 to 245f)
        f.send(MotionEvent.ACTION_UP, 1 to 245f)
        assertEquals(400, f.width)
        assertTrue(f.persisted.isEmpty())
        assertEquals(listOf(Triple(false, 400, 400)), f.completed)
    }

    @Test fun cancelRollsBackAndNextDownCanResizeCleanly() {
        val f = Fixture()
        f.send(MotionEvent.ACTION_DOWN, 2 to 300f)
        f.send(MotionEvent.ACTION_MOVE, 2 to 315f)
        assertEquals(415, f.width)
        f.send(MotionEvent.ACTION_CANCEL, 2 to 315f)
        assertEquals(400, f.width)
        assertTrue(f.persisted.isEmpty())
        f.send(MotionEvent.ACTION_DOWN, 3 to 390f)
        f.send(MotionEvent.ACTION_UP, 3 to 420f)
        assertEquals(430, f.width)
        assertEquals(2, f.starts)
        assertEquals(listOf(Triple(false, 400, 400), Triple(true, 400, 430)), f.completed)
    }

    @Test fun orientationKeyIsCapturedWhenTheFingerGoesDown() {
        val f = Fixture()
        f.orientation = "width_landscape"
        f.send(MotionEvent.ACTION_DOWN, 0 to 300f)
        f.orientation = "width_portrait"
        f.send(MotionEvent.ACTION_UP, 0 to 310f)
        assertEquals(mapOf("width_landscape" to 410), f.persisted)
    }

    @Test fun gripIsOnPhysicalRightOfLeftPanelInBothLayoutDirections() {
        assertEquals(0, SidePanelResizeGeometry.contentStartPaddingDp(false))
        assertEquals(20, SidePanelResizeGeometry.contentEndPaddingDp(false))
        assertEquals(20, SidePanelResizeGeometry.contentStartPaddingDp(true))
        assertEquals(0, SidePanelResizeGeometry.contentEndPaddingDp(true))
        for (direction in listOf(View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL)) {
            val ctx = RuntimeEnvironment.getApplication<Application>()
            val panel = CoordinatorLayout(ctx)
            panel.layoutDirection = direction
            val grip = View(ctx)
            panel.addView(grip, CoordinatorLayout.LayoutParams(20, 200).apply {
                gravity = SidePanelResizeGeometry.GRIP_GRAVITY
            })
            panel.measure(
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY)
            )
            panel.layout(0, 0, 400, 200)
            assertEquals(380, grip.left)
            assertEquals(400, grip.right)
        }
    }
}
