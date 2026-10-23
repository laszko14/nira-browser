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
}

class SwipeGestureLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var isSwipeEnabled = true

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

    fun addGestureListener(listener: SwipeGestureListener) {
        listeners.add(listener)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (!isSwipeEnabled) {
            return false
        }

        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                handledInitialScroll = false
                activeListener = null
                gestureDetector.onTouchEvent(event)
                false
            }
            MotionEvent.ACTION_MOVE -> {
                val shouldIntercept = gestureDetector.onTouchEvent(event)
                // Return true to intercept if we have an active listener
                shouldIntercept && activeListener != null
            }
            else -> gestureDetector.onTouchEvent(event)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return when (event.actionMasked) {
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                gestureDetector.onTouchEvent(event)
                activeListener?.onSwipeFinished(
                    velocityX = 0f,
                    velocityY = 0f
                )
                activeListener = null
                false
            }
            else -> gestureDetector.onTouchEvent(event)
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}