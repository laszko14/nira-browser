package com.prirai.android.nira.browser

import android.app.Activity
import android.app.Application
import android.graphics.PointF
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Dispatch-focused regressions; not actual GeckoView/Compose instrumentation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class SwipeGestureLayoutArbitrationTest {
    private class Spy : SwipeGestureListener {
        var offers = 0
        var updates = 0
        var finishes = 0
        var cancels = 0
        override fun onSwipeStarted(start: PointF, next: PointF): Boolean {
            offers++
            return start.x < 30f && next.x > start.x &&
                kotlin.math.abs(next.x - start.x) > kotlin.math.abs(next.y - start.y) * 1.5f
        }
        override fun onSwipeUpdate(distanceX: Float, distanceY: Float) { updates++ }
        override fun onSwipeFinished(velocityX: Float, velocityY: Float) { finishes++ }
        override fun onSwipeCancelled() { cancels++ }
    }

    private data class Fixture(val activity: Activity, val root: SwipeGestureLayout,
                               val touches: MutableList<Int>)
    private fun fixture(disallow: Boolean = false): Fixture {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = SwipeGestureLayout(activity)
        val received = mutableListOf<Int>()
        val child = View(activity).apply {
            setOnTouchListener { v, event ->
                received += event.actionMasked
                if (disallow && event.actionMasked == MotionEvent.ACTION_DOWN) {
                    v.parent.requestDisallowInterceptTouchEvent(true)
                }
                true
            }
        }
        root.addView(child, FrameLayout.LayoutParams(-1, -1))
        activity.setContentView(root)
        root.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 400, 800)
        return Fixture(activity, root, received)
    }

    private fun send(root: SwipeGestureLayout, start: Long, time: Long,
                     action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(start, time, action, x, y, 0)
        root.dispatchTouchEvent(event)
        event.recycle()
    }

    @Test fun ordinaryVerticalPageScrollRemainsWithChild() {
        val f = fixture()
        val spy = Spy()
        f.root.addGestureListener(spy)
        send(f.root, 100, 100, MotionEvent.ACTION_DOWN, 2f, 150f)
        send(f.root, 100, 140, MotionEvent.ACTION_MOVE, 4f, 220f)
        send(f.root, 100, 180, MotionEvent.ACTION_MOVE, 4f, 280f)
        send(f.root, 100, 210, MotionEvent.ACTION_UP, 4f, 300f)
        assertEquals(0, spy.offers)
        assertFalse(f.touches.contains(MotionEvent.ACTION_CANCEL))
        assertTrue(f.touches.count { it == MotionEvent.ACTION_MOVE } >= 2)
        f.activity.finish()
    }

    @Test fun diagonalPageScrollIsNotOfferedToHorizontalDrawerListeners() {
        val f = fixture()
        val spy = Spy()
        f.root.addGestureListener(spy)
        send(f.root, 130, 130, MotionEvent.ACTION_DOWN, 2f, 140f)
        send(f.root, 130, 170, MotionEvent.ACTION_MOVE, 20f, 210f)
        send(f.root, 130, 210, MotionEvent.ACTION_MOVE, 42f, 295f)
        send(f.root, 130, 250, MotionEvent.ACTION_UP, 42f, 295f)
        assertEquals(0, spy.offers)
        assertEquals(0, spy.updates)
        assertFalse(f.touches.contains(MotionEvent.ACTION_CANCEL))
        assertTrue(f.touches.contains(MotionEvent.ACTION_UP))
        f.activity.finish()
    }

    @Test fun horizontalGestureAwayFromEdgeDoesNotCancelPage() {
        val f = fixture()
        val spy = Spy()
        f.root.addGestureListener(spy)
        send(f.root, 220, 220, MotionEvent.ACTION_DOWN, 160f, 160f)
        send(f.root, 220, 260, MotionEvent.ACTION_MOVE, 240f, 160f)
        send(f.root, 220, 300, MotionEvent.ACTION_UP, 270f, 160f)
        assertEquals(1, spy.offers)
        assertFalse(f.touches.contains(MotionEvent.ACTION_CANCEL))
        f.activity.finish()
    }

    @Test fun nestedDisallowInterceptProtectsEdgeControls() {
        val f = fixture(disallow = true)
        val spy = Spy()
        f.root.addGestureListener(spy)
        send(f.root, 320, 320, MotionEvent.ACTION_DOWN, 2f, 170f)
        send(f.root, 320, 360, MotionEvent.ACTION_MOVE, 80f, 170f)
        send(f.root, 320, 400, MotionEvent.ACTION_UP, 105f, 170f)
        assertEquals(0, spy.offers)
        assertFalse(f.touches.contains(MotionEvent.ACTION_CANCEL))
        f.activity.finish()
    }

    @Test fun gestureDetectorSeesFirstAcceptedMoveOnlyOnce() {
        val f = fixture()
        val spy = Spy()
        f.root.addGestureListener(spy)
        send(f.root, 450, 450, MotionEvent.ACTION_DOWN, 2f, 160f)
        send(f.root, 450, 490, MotionEvent.ACTION_MOVE, 80f, 160f)
        assertEquals(1, spy.offers)
        assertEquals(1, spy.updates)
        assertTrue(f.touches.contains(MotionEvent.ACTION_CANCEL))
        send(f.root, 450, 540, MotionEvent.ACTION_MOVE, 120f, 160f)
        assertEquals(2, spy.updates)
        send(f.root, 450, 580, MotionEvent.ACTION_UP, 120f, 160f)
        assertEquals(1, spy.finishes)
        f.activity.finish()
    }

    @Test fun firstAcceptingToolbarListenerHasPriority() {
        val f = fixture()
        var toolbar = 0
        var drawer = 0
        val owner = object : SwipeGestureListener {
            override fun onSwipeStarted(start: PointF, next: PointF): Boolean { toolbar++; return true }
            override fun onSwipeUpdate(distanceX: Float, distanceY: Float) {}
            override fun onSwipeFinished(velocityX: Float, velocityY: Float) {}
        }
        val fallback = object : SwipeGestureListener {
            override fun onSwipeStarted(start: PointF, next: PointF): Boolean { drawer++; return true }
            override fun onSwipeUpdate(distanceX: Float, distanceY: Float) {}
            override fun onSwipeFinished(velocityX: Float, velocityY: Float) {}
        }
        f.root.addGestureListener(owner)
        f.root.addGestureListener(fallback)
        send(f.root, 620, 620, MotionEvent.ACTION_DOWN, 2f, 180f)
        send(f.root, 620, 660, MotionEvent.ACTION_MOVE, 85f, 180f)
        assertEquals(1, toolbar)
        assertEquals(0, drawer)
        send(f.root, 620, 695, MotionEvent.ACTION_CANCEL, 85f, 180f)
        f.activity.finish()
    }

    @Test fun activeSidePanelWinsOverEarlierRegisteredToolbarAndRestoresAfterClose() {
        val f = fixture()
        var panelEngaged = true
        var toolbarStarts = 0
        var panelStarts = 0
        var toolbarFinishes = 0
        var panelFinishes = 0

        val toolbar = object : SwipeGestureListener {
            override fun onSwipeStarted(start: PointF, next: PointF): Boolean {
                // Mirrors the live ownership gate supplied by BrowserFragment.
                if (panelEngaged) return false
                toolbarStarts++
                return true
            }
            override fun onSwipeUpdate(distanceX: Float, distanceY: Float) {}
            override fun onSwipeFinished(velocityX: Float, velocityY: Float) {
                toolbarFinishes++
            }
        }
        val panel = object : SwipeGestureListener {
            override fun onSwipeStarted(start: PointF, next: PointF): Boolean {
                if (!panelEngaged) return false
                panelStarts++
                return true
            }
            override fun onSwipeUpdate(distanceX: Float, distanceY: Float) {}
            override fun onSwipeFinished(velocityX: Float, velocityY: Float) {
                panelFinishes++
            }
        }
        f.root.addGestureListener(toolbar) // Same ordering as BrowserFragment.
        f.root.addGestureListener(panel)

        // Swiping left inside the left drawer, across an occluded toolbar.
        send(f.root, 3100, 3100, MotionEvent.ACTION_DOWN, 160f, 40f)
        send(f.root, 3100, 3140, MotionEvent.ACTION_MOVE, 70f, 40f)
        send(f.root, 3100, 3180, MotionEvent.ACTION_UP, 45f, 40f)
        assertEquals(0, toolbarStarts)
        assertEquals(0, toolbarFinishes)
        assertEquals(1, panelStarts)
        assertEquals(1, panelFinishes)

        panelEngaged = false // CLOSED: normal tab navigation is restored.
        send(f.root, 3300, 3300, MotionEvent.ACTION_DOWN, 160f, 40f)
        send(f.root, 3300, 3340, MotionEvent.ACTION_MOVE, 70f, 40f)
        send(f.root, 3300, 3380, MotionEvent.ACTION_UP, 45f, 40f)
        assertEquals(1, toolbarStarts)
        assertEquals(1, toolbarFinishes)
        assertEquals(1, panelStarts)
        f.activity.finish()
    }

    @Test fun rightPanelAndSettlingStateUseSameToolbarExclusionRule() {
        val f = fixture()
        var panelEngaged = true // right drawer OPEN, DRAGGING or SETTLING
        var toolbarStarts = 0
        var panelStarts = 0
        f.root.addGestureListener(object : SwipeGestureListener {
            override fun onSwipeStarted(start: PointF, next: PointF): Boolean {
                if (panelEngaged) return false
                toolbarStarts++
                return true
            }
            override fun onSwipeUpdate(distanceX: Float, distanceY: Float) {}
            override fun onSwipeFinished(velocityX: Float, velocityY: Float) {}
        })
        f.root.addGestureListener(object : SwipeGestureListener {
            override fun onSwipeStarted(start: PointF, next: PointF): Boolean {
                if (!panelEngaged || next.x <= start.x) return false
                panelStarts++
                return true
            }
            override fun onSwipeUpdate(distanceX: Float, distanceY: Float) {}
            override fun onSwipeFinished(velocityX: Float, velocityY: Float) {}
        })
        send(f.root, 3500, 3500, MotionEvent.ACTION_DOWN, 300f, 40f)
        send(f.root, 3500, 3540, MotionEvent.ACTION_MOVE, 380f, 40f)
        send(f.root, 3500, 3580, MotionEvent.ACTION_UP, 380f, 40f)
        assertEquals(0, toolbarStarts)
        assertEquals(1, panelStarts)
        panelEngaged = false
        send(f.root, 3700, 3700, MotionEvent.ACTION_DOWN, 300f, 40f)
        send(f.root, 3700, 3740, MotionEvent.ACTION_MOVE, 380f, 40f)
        send(f.root, 3700, 3780, MotionEvent.ACTION_UP, 380f, 40f)
        assertEquals(1, toolbarStarts)
        f.activity.finish()
    }

    @Test fun scrollingDrawerDoesNotHideToolbarButPageScrollStillDoes() {
        val f = fixture()
        var pageDelta = 0f
        var allow = false
        f.root.onVerticalSwipe = { pageDelta += it }
        f.root.allowPageScrollObservation = { allow }
        send(f.root, 740, 740, MotionEvent.ACTION_DOWN, 190f, 100f)
        send(f.root, 740, 780, MotionEvent.ACTION_MOVE, 190f, 240f)
        assertEquals(0f, pageDelta, 0f)
        send(f.root, 740, 810, MotionEvent.ACTION_UP, 190f, 240f)
        allow = true
        send(f.root, 900, 900, MotionEvent.ACTION_DOWN, 190f, 100f)
        send(f.root, 900, 940, MotionEvent.ACTION_MOVE, 190f, 240f)
        assertTrue(pageDelta < 0f)
        f.activity.finish()
    }

    @Test fun disablingAfterParentInterceptCancelsOwnerImmediatelyAndDrainsUp() {
        val f = fixture()
        val owner = Spy()
        f.root.addGestureListener(owner)
        send(f.root, 1100, 1100, MotionEvent.ACTION_DOWN, 2f, 160f)
        send(f.root, 1100, 1140, MotionEvent.ACTION_MOVE, 90f, 160f)
        assertEquals(1, owner.offers)
        assertEquals(1, owner.updates)
        assertTrue(f.touches.contains(MotionEvent.ACTION_CANCEL))

        f.root.isSwipeEnabled = false
        assertEquals("disable must synchronously cancel an owned gesture", 1, owner.cancels)
        send(f.root, 1100, 1180, MotionEvent.ACTION_MOVE, 120f, 160f)
        send(f.root, 1100, 1220, MotionEvent.ACTION_UP, 120f, 160f)
        assertEquals(1, owner.cancels)
        assertEquals(1, owner.updates)
        assertEquals(0, owner.finishes)
        f.activity.finish()
    }

    @Test fun reEnableDuringBlockedStreamRequiresFreshDownBeforeNextSwipe() {
        val f = fixture()
        val owner = Spy()
        f.root.addGestureListener(owner)
        send(f.root, 1300, 1300, MotionEvent.ACTION_DOWN, 2f, 160f)
        send(f.root, 1300, 1340, MotionEvent.ACTION_MOVE, 85f, 160f)
        assertEquals(1, owner.offers)
        f.root.isSwipeEnabled = false
        f.root.isSwipeEnabled = true
        send(f.root, 1300, 1380, MotionEvent.ACTION_MOVE, 135f, 160f)
        send(f.root, 1300, 1420, MotionEvent.ACTION_UP, 135f, 160f)
        assertEquals(1, owner.cancels)
        assertEquals(1, owner.updates)
        assertEquals(0, owner.finishes)
        send(f.root, 1500, 1500, MotionEvent.ACTION_DOWN, 2f, 160f)
        send(f.root, 1500, 1540, MotionEvent.ACTION_MOVE, 85f, 160f)
        send(f.root, 1500, 1580, MotionEvent.ACTION_UP, 85f, 160f)
        assertEquals(2, owner.offers)
        assertEquals(1, owner.finishes)
        f.activity.finish()
    }

    @Test fun disabledFromDownCannotTakeOverMidStreamAfterReEnable() {
        val f = fixture()
        val owner = Spy()
        f.root.addGestureListener(owner)
        f.root.isSwipeEnabled = false
        send(f.root, 1600, 1600, MotionEvent.ACTION_DOWN, 2f, 160f)
        send(f.root, 1600, 1640, MotionEvent.ACTION_MOVE, 60f, 160f)
        f.root.isSwipeEnabled = true
        send(f.root, 1600, 1680, MotionEvent.ACTION_MOVE, 120f, 160f)
        send(f.root, 1600, 1720, MotionEvent.ACTION_UP, 120f, 160f)
        assertEquals(0, owner.offers)
        assertEquals(0, owner.finishes)
        send(f.root, 1800, 1800, MotionEvent.ACTION_DOWN, 2f, 160f)
        send(f.root, 1800, 1840, MotionEvent.ACTION_MOVE, 85f, 160f)
        send(f.root, 1800, 1880, MotionEvent.ACTION_CANCEL, 85f, 160f)
        assertEquals(1, owner.offers)
        assertEquals(1, owner.cancels)
        assertEquals(0, owner.finishes)
        f.activity.finish()
    }

    @Test fun disableThenPointerCancelDoesNotInvokeSecondCancelOrFinish() {
        val f = fixture()
        val owner = Spy()
        f.root.addGestureListener(owner)
        send(f.root, 2000, 2000, MotionEvent.ACTION_DOWN, 2f, 160f)
        send(f.root, 2000, 2040, MotionEvent.ACTION_MOVE, 85f, 160f)
        f.root.isSwipeEnabled = false
        val properties = arrayOf(
            MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER },
            MotionEvent.PointerProperties().apply { id = 1; toolType = MotionEvent.TOOL_TYPE_FINGER }
        )
        val coords = arrayOf(
            MotionEvent.PointerCoords().apply { x = 85f; y = 160f; pressure = 1f; size = 1f },
            MotionEvent.PointerCoords().apply { x = 105f; y = 160f; pressure = 1f; size = 1f }
        )
        val secondFinger = MotionEvent.obtain(
            2000L, 2080L,
            MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            2, properties, coords, 0, 0, 1f, 1f, 0, 0,
            android.view.InputDevice.SOURCE_TOUCHSCREEN, 0
        )
        try { f.root.dispatchTouchEvent(secondFinger) } finally { secondFinger.recycle() }
        send(f.root, 2000, 2120, MotionEvent.ACTION_CANCEL, 85f, 160f)
        assertEquals(1, owner.cancels)
        assertEquals(0, owner.finishes)
        assertEquals(1, owner.updates)
        f.activity.finish()
    }

}
