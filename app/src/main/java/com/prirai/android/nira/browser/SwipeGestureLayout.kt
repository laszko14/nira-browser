package com.prirai.android.nira.browser

import android.content.Context
import android.graphics.PointF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.core.view.GestureDetectorCompat

interface SwipeGestureListener {
    fun onSwipeStarted(start: PointF, next: PointF): Boolean
    fun onSwipeUpdate(distanceX: Float, distanceY: Float)
    fun onSwipeFinished(velocityX: Float, velocityY: Float)
    fun onSwipeCancelled() {}
}

class SwipeGestureLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    internal var testLabGestureOrigin: com.prirai.android.nira.diagnostics.testlab.GestureOrigin? = null
        private set
    var isSwipeEnabled = true

    /** Passive vertical scroll signal; never intercepts GeckoView's page gestures. */
    var onVerticalSwipe: ((Float) -> Unit)? = null
    /** Prevent panel scrolling from hiding the browser toolbar. */
    var allowPageScrollObservation: (() -> Boolean)? = null
    private var verticalStartX = 0f
    private var verticalStartY = 0f
    private var verticalLastY = 0f
    private var directionDecided = false
    private var verticalDirection = false

    private fun observePageGesture(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                testLabGestureOrigin=com.prirai.android.nira.diagnostics.testlab.TestLab.gestureOrigin()
                verticalStartX = event.x
                verticalStartY = event.y
                verticalLastY = event.y
                directionDecided = false
                verticalDirection = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (allowPageScrollObservation?.invoke() == false) {
                    verticalDirection = false
                    directionDecided = true
                    verticalLastY = event.y
                    return
                }
                val dx = kotlin.math.abs(event.x - verticalStartX)
                val dy = kotlin.math.abs(event.y - verticalStartY)
                if (!directionDecided && maxOf(dx, dy) >= 12f * resources.displayMetrics.density) {
                    directionDecided = true
                    verticalDirection = dy > dx * 1.3f
                }
                if (verticalDirection) onVerticalSwipe?.invoke(verticalLastY - event.y)
                verticalLastY = event.y
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                directionDecided = false
                verticalDirection = false
            }
        }
    }

    private val gestureListener = object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            return true
        }

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            val start = e1.let { event -> PointF(event!!.rawX, event.rawY) }
            val next = e2.let { event -> PointF(event.rawX, event.rawY) }

            if (activeListener == null && !handledInitialScroll) {
                val totalX = kotlin.math.abs(next.x - start.x)
                val totalY = kotlin.math.abs(next.y - start.y)
                val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop.toFloat()
                // Do not reject the entire gesture on the first small MOVE.
                // Decide once enough travel establishes a horizontal/vertical intent.
                if (totalX >= maxOf(12f * resources.displayMetrics.density, slop * 1.5f) ||
                    totalY >= maxOf(12f * resources.displayMetrics.density, slop * 1.5f)) {
                    activeListener = listeners.firstOrNull { listener ->
                        listener.onSwipeStarted(start, next)
                    }
                    handledInitialScroll = true
                }
            }
            activeListener?.onSwipeUpdate(distanceX, distanceY)
            return activeListener != null
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            activeListener?.onSwipeFinished(velocityX, velocityY)
            return if (activeListener != null) {
                activeListener = null
                true
            } else {
                false
            }
        }
    }

    private val gestureDetector = GestureDetectorCompat(context, gestureListener)

    private val listeners = mutableListOf<SwipeGestureListener>()
    private var activeListener: SwipeGestureListener? = null
    private var handledInitialScroll = false
    private var multiTouchStream = false

    // Interception hands the same MOVE to onTouchEvent after cancelling the child.
    // Keep an allocation-free signature to prevent double delivery to GestureDetector.
    private var lastInterceptDown = Long.MIN_VALUE
    private var lastInterceptTime = Long.MIN_VALUE
    private var lastInterceptAction = -1
    private var lastInterceptX = Float.NaN
    private var lastInterceptY = Float.NaN
    private var lastInterceptPointers = -1

    private fun feedFromIntercept(event: MotionEvent): Boolean {
        val handled = gestureDetector.onTouchEvent(event)
        lastInterceptDown = event.downTime
        lastInterceptTime = event.eventTime
        lastInterceptAction = event.action
        lastInterceptX = event.x
        lastInterceptY = event.y
        lastInterceptPointers = event.pointerCount
        return handled
    }

    private fun wasFedFromIntercept(event: MotionEvent): Boolean =
        lastInterceptDown == event.downTime && lastInterceptTime == event.eventTime &&
            lastInterceptAction == event.action && lastInterceptX == event.x &&
            lastInterceptY == event.y && lastInterceptPointers == event.pointerCount

    private fun cancelOwnedSwipe() {
        activeListener?.onSwipeCancelled()
        activeListener = null
        handledInitialScroll = true
    }

    fun addGestureListener(listener: SwipeGestureListener) {
        listeners.add(listener)
    }

    /** View-scoped handlers must not survive fragment view teardown. */
    fun removeGestureListener(listener: SwipeGestureListener) {
        if (activeListener === listener) {
            activeListener = null
            handledInitialScroll = true // Do not hand this finger stream to another listener.
            listener.onSwipeCancelled()
        }
        listeners.remove(listener)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // dispatchTouchEvent sees moves even when GeckoView asks its parent
        // not to intercept; ordinary page scrolling remains uninterrupted.
        observePageGesture(event)
        return super.dispatchTouchEvent(event)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            multiTouchStream = false
            handledInitialScroll = false
            activeListener = null
            if (isSwipeEnabled) feedFromIntercept(event)
            return false
        }
        if (!isSwipeEnabled) {
            if (activeListener != null) cancelOwnedSwipe()
            return false
        }
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN || event.pointerCount > 1) {
            multiTouchStream = true
            cancelOwnedSwipe()
            return false
        }
        if (multiTouchStream) return false

        return when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> feedFromIntercept(event) && activeListener != null
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                feedFromIntercept(event)
                false
            }
            else -> false
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val owned = activeListener != null
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN || event.pointerCount > 1) {
            val wasOwned = owned || multiTouchStream
            multiTouchStream = true
            cancelOwnedSwipe()
            // Keep a stream already intercepted by this parent until ACTION_UP.
            return wasOwned
        }
        if (multiTouchStream) {
            if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL) multiTouchStream = false
            return true
        }
        if (!wasFedFromIntercept(event)) gestureDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_CANCEL -> cancelOwnedSwipe()
            MotionEvent.ACTION_UP -> {
                // onFling may have already completed and cleared the listener.
                activeListener?.onSwipeFinished(0f, 0f)
                activeListener = null
            }
        }
        return owned || activeListener != null
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}