package com.prirai.android.nira.media

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

internal object InlineScrollGesture {
    fun starts(dx:Float,dy:Float,slop:Int):Boolean =
        abs(dy)>slop && abs(dy)>abs(dx)
}

/** Keep taps in native children; transfer only vertical drags to the Gecko engine. */
internal class InlineMpvTouchLayout(
    context:Context, private val engine:View, private val enabled:()->Boolean,
):FrameLayout(context) {
    private val slop=ViewConfiguration.get(context).scaledTouchSlop
    // Stored in screen coordinates: the overlay itself moves while Gecko scrolls.
    private var downScreen:MotionEvent?=null
    private var lastScreen:MotionEvent?=null
    private var forwarding=false
    private var interceptedEvent:MotionEvent?=null

    private fun screenCopy(event:MotionEvent):MotionEvent {
        val origin=IntArray(2)
        getLocationOnScreen(origin)
        return MotionEvent.obtain(event).apply {
            offsetLocation(origin[0].toFloat(),origin[1].toFloat())
        }
    }
    private fun forwardScreen(event:MotionEvent) {
        val origin=IntArray(2)
        engine.getLocationOnScreen(origin)
        val copy=MotionEvent.obtain(event)
        try {
            copy.offsetLocation(-origin[0].toFloat(),-origin[1].toFloat())
            engine.dispatchTouchEvent(copy)
        } finally { copy.recycle() }
        lastScreen?.recycle()
        lastScreen=MotionEvent.obtain(event)
    }
    fun cancelGesture() {
        if (forwarding) {
            val cancel=lastScreen?.let { MotionEvent.obtain(it) }
            if (cancel!=null) {
                try {
                    cancel.action=MotionEvent.ACTION_CANCEL
                    forwardScreen(cancel)
                } finally { cancel.recycle() }
            }
        }
        forwarding=false
        downScreen?.recycle()
        downScreen=null
        lastScreen?.recycle()
        lastScreen=null
    }
    override fun dispatchTouchEvent(event:MotionEvent):Boolean {
        if (event.actionMasked==MotionEvent.ACTION_DOWN) {
            cancelGesture()
            if (enabled()) downScreen=screenCopy(event)
        }
        if (event.actionMasked==MotionEvent.ACTION_POINTER_DOWN && !forwarding) {
            downScreen?.recycle()
            downScreen=null
        }
        if (!enabled()) cancelGesture()
        return try { super.dispatchTouchEvent(event) } finally {
            interceptedEvent=null
            if (event.actionMasked==MotionEvent.ACTION_UP ||
                event.actionMasked==MotionEvent.ACTION_CANCEL) cancelGesture()
        }
    }
    override fun onInterceptTouchEvent(event:MotionEvent):Boolean {
        if (!enabled()) return false
        if (forwarding) return true
        val down=downScreen ?: return false
        if (event.actionMasked==MotionEvent.ACTION_MOVE && event.pointerCount==1) {
            val current=screenCopy(event)
            val drag=try {
                InlineScrollGesture.starts(current.x-down.x,current.y-down.y,slop)
            } finally { current.recycle() }
            if (drag) {
                // Android cancels the native child when interception begins.
                // Gecko must see the original DOWN before this threshold MOVE.
                forwarding=true
                forwardScreen(down)
                // With a child target, ViewGroup sends CANCEL to that child but
                // does not deliver this first intercepted MOVE to onTouchEvent.
                val screen=screenCopy(event)
                try { forwardScreen(screen) } finally { screen.recycle() }
                interceptedEvent=event
                return true
            }
        }
        return false
    }
    override fun onTouchEvent(event:MotionEvent):Boolean {
        if (!forwarding || !enabled()) return super.onTouchEvent(event)
        if (event===interceptedEvent) return true
        val screen=screenCopy(event)
        try { forwardScreen(screen) } finally { screen.recycle() }
        // UP/CANCEL already terminates Gecko's stream; don't send a second CANCEL.
        if (event.actionMasked==MotionEvent.ACTION_UP ||
            event.actionMasked==MotionEvent.ACTION_CANCEL) forwarding=false
        return true
    }
    override fun onDetachedFromWindow() {
        cancelGesture()
        super.onDetachedFromWindow()
    }
}
