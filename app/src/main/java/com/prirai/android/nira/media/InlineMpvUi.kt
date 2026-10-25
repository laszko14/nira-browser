package com.prirai.android.nira.media

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.prirai.android.nira.R
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode

/** Native libmpv surface aligned to ONE verified top-level HTMLVideoElement. */
internal class InlineMpvUi(
    private val host: CoordinatorLayout, private val engine: View,
    private val activity: Activity, private val context: Context,
    val token: String, private val session: Any,
    private val exit: () -> Unit,
) {
    private val main=Handler(Looper.getMainLooper())
    private val density=context.resources.displayMetrics.density
    // Keep a single decor parent for the entire lifecycle: never reparent SurfaceView.
    private val windowHost=activity.window.decorView as ViewGroup
    private val overlay=InlineMpvTouchLayout(context,engine) { !fullscreen && !destroyed }.apply {
        clipChildren=true
        clipToPadding=true
        setBackgroundColor(Color.BLACK)
        elevation=16f*density
        isClickable=true
    }
    private var player: ZavroMpvView?=null
    private var loaded=false
    private var destroyed=false
    private var fullscreen=false
    private var previousRect: MediaElementPlaybackBridge.InlineRect?=null
    private var timeout: Runnable?=null
    private var controlBar: LinearLayout?=null
    private var controlMode=-1
    private var controlsVisible=true
    private var hideControlsTask:Runnable?=null
    private var speed=1.5
    @Volatile private var lastPosition=Double.NaN
    private var returnAt=Double.NaN
    private var pendingReturnCheck=false
    private fun dp(n:Int)=(n*density+0.5f).toInt()
    private fun trace(code:String)=MpvDebugTrace.note(MpvDebugTrace.Section.MPV,code,session)
    private fun scheduleHide() {
        hideControlsTask?.let(main::removeCallbacks)
        val task=Runnable {
            if (!destroyed && controlsVisible) {
                controlsVisible=false
                controlBar?.visibility=View.GONE
                trace("inline_controls_hidden")
            }
        }
        hideControlsTask=task
        main.postDelayed(task,3_000L)
    }
    private fun revealControls() {
        if (destroyed) return
        controlsVisible=true
        controlBar?.visibility=View.VISIBLE
        trace("inline_controls_shown")
        scheduleHide()
    }
    private val oldUi=activity.window.decorView.systemUiVisibility
    private val logs=object: MPVLib.LogObserver {
        override fun logMessage(prefix:String,level:Int,text:String) {
            MpvDebugTrace.classifyMpvLog(prefix,level,text)?.let { code ->
                MpvDebugTrace.note(MpvDebugTrace.Section.MPV,code,session)
            }
        }
    }
    private val observer=object: MPVLib.EventObserver {
        override fun eventProperty(property:String) {}
        override fun eventProperty(property:String,value:Long) {}
        override fun eventProperty(property:String,value:String) {}
        override fun eventProperty(property:String,value:MPVNode) {}
        override fun eventProperty(property:String,value:Boolean) {
            if (property=="paused-for-cache") MpvDebugTrace.note(
                MpvDebugTrace.Section.MPV,
                if (value) "mpv_cache_wait" else "mpv_cache_resume",session)
        }
        override fun eventProperty(property:String,value:Double) {
            if (property=="time-pos" && !destroyed) {
                lastPosition=value
                MpvSupertest.sample(session,value)
                if (pendingReturnCheck && value.isFinite()) main.post {
                    if (!destroyed && pendingReturnCheck) {
                        pendingReturnCheck=false
                        if (returnAt.isFinite() && kotlin.math.abs(value-returnAt)<4.0)
                            trace("inline_position_preserved")
                    }
                }
            }
        }
        override fun event(eventId:Int,data:MPVNode) {
            if (eventId==MPVLib.MpvEvent.MPV_EVENT_START_FILE) {
                trace("mpv_start_file")
                if (loaded) trace("inline_unexpected_reload")
            }
            if (eventId==MPVLib.MpvEvent.MPV_EVENT_PLAYBACK_RESTART) MpvDebugTrace.note(
                MpvDebugTrace.Section.MPV,"mpv_playback_started",session)
            if (eventId==MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED) main.post {
                if (!destroyed) {
                    loaded=true
                    timeout?.let(main::removeCallbacks)
                    MpvDebugTrace.note(MpvDebugTrace.Section.MPV,"mpv_file_loaded",session)
                    MpvDebugTrace.note(MpvDebugTrace.Section.MPV,"inline_loaded",session)
                }
            }
            if (eventId==MPVLib.MpvEvent.MPV_EVENT_END_FILE) main.post {
                if (!destroyed && !loaded) {
                    MpvDebugTrace.note(MpvDebugTrace.Section.MPV,"inline_failed",session)
                    exit()
                }
            }
        }
    }
    private fun button(label:String,action:()->Unit):FrameLayout {
        // Visual icon 32 dp; whole 48 dp container is the touch target.
        val cell=FrameLayout(context).apply {
            layoutParams=LinearLayout.LayoutParams(dp(48),dp(48))
            isClickable=true
            isFocusable=true
            contentDescription=label
            setOnClickListener { revealControls();action() }
        }
        val text=TextView(context).apply {
            this.text=label
            textSize=if (label.endsWith("×")) 12f else 17f
            setTextColor(Color.WHITE)
            gravity=Gravity.CENTER
            background=GradientDrawable().apply {
                cornerRadius=dp(8).toFloat()
                setColor(Color.argb(100,40,44,52))
            }
        }
        cell.addView(text,FrameLayout.LayoutParams(dp(32),dp(32),Gravity.CENTER))
        return cell
    }
    private fun seek(amount:Int) {
        runCatching { MPVLib.command("seek",amount.toString(),"relative") }
    }
    private fun changeSpeed() {
        speed=if (speed>=3.0) 1.5 else speed+0.5
        runCatching { MPVLib.setPropertyDouble("speed",speed) }
        updateControlsForWidth(if(fullscreen) windowHost.width else overlay.width,true)
    }
    private fun overflow(anchor:View) {
        val popup=PopupMenu(context,anchor)
        popup.menu.add(0,1,0,"Prędkość: ${speed}×")
        popup.menu.add(0,2,1,"Napisy")
        popup.menu.add(0,3,2,"Zamknij mini MPV")
        popup.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> changeSpeed()
                2 -> runCatching { MPVLib.command("cycle","sid") }
                3 -> exit()
            }
            true
        }
        popup.show()
    }
    private fun updateControlsForWidth(width:Int,force:Boolean=false) {
        if (width<=0 || destroyed) return
        val available=width/density
        val mode=if (fullscreen || available>=346f) 0
            else if (available>=248f) 1 else 2
        if (!force && controlMode==mode) return
        controlMode=mode
        controlBar?.let { overlay.removeView(it) }
        val bar=LinearLayout(context).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER
            background=GradientDrawable().apply {
                cornerRadius=dp(12).toFloat()
                setColor(Color.argb(160,12,16,22))
            }
        }
        fun add(label:String,action:()->Unit){bar.addView(button(label,action))}
        if (mode!=2) add("↶") { seek(-10) }
        add("⏯") { runCatching { MPVLib.command("cycle","pause") } }
        if (mode==0) add("${speed}×") { changeSpeed() }
        if (mode!=2) add("↷") { seek(10) }
        if (mode==0) add("CC") { runCatching { MPVLib.command("cycle","sid") } }
        add("⛶") { toggleFullscreen() }
        if (mode==0) add("×") { exit() }
        else add("⋮") { overflow(bar.getChildAt(bar.childCount-1)) }
        controlBar=bar
        val params=FrameLayout.LayoutParams(-2,dp(48),
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin=dp(6)
        }
        overlay.addView(bar,params)
        bar.visibility=if (controlsVisible) View.VISIBLE else View.GONE
        if (controlsVisible) scheduleHide()
    }
    fun open(url:String,userAgent:String?,referrer:String?,
             rect:MediaElementPlaybackBridge.InlineRect):Boolean {
        // MPVLib is process-global. Never open inline and fullscreen Activity concurrently.
        if (!MpvEngineGate.claim(token)) return false
        val video=LayoutInflater.from(context).inflate(R.layout.zavro_mpv_surface,
            overlay,false) as ZavroMpvView
        video.userAgent=userAgent
        video.referrer=referrer
        video.tlsCaFile=MpvTlsTrust.prepare(context)
        MpvDebugTrace.note(MpvDebugTrace.Section.MPV,
            if (video.tlsCaFile != null) "mpv_tls_ca_prepared"
            else "mpv_tls_ca_unavailable",session)
        MpvDebugTrace.note(MpvDebugTrace.Section.MPV,
            MpvDebugTrace.classifySource(url),session)
        video.segmentedNetworkReconnect=MpvDebugTrace.classifySource(url) in
            setOf("source_hls","source_dash")
        return try {
            video.initialize(context.filesDir.absolutePath,context.cacheDir.absolutePath)
            video.playFile(url) // queue before attaching surface
            MPVLib.addObserver(observer)
            MPVLib.addLogObserver(logs)
            MpvDebugTrace.note(MpvDebugTrace.Section.MPV,"mpv_logger_registered",session)
            MPVLib.observeProperty("time-pos",MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
            MPVLib.observeProperty("paused-for-cache",MPVLib.MpvFormat.MPV_FORMAT_FLAG)
            player=video
            overlay.addView(video,FrameLayout.LayoutParams(-1,-1))
            // The same native video surface stays attached while only overlay bounds
            // change. This prevents SurfaceView destruction during fullscreen.
            player?.setOnClickListener { revealControls() }
            overlay.setOnClickListener { revealControls() }
            overlay.visibility=View.INVISIBLE
            windowHost.addView(overlay,FrameLayout.LayoutParams(1,1))
            overlay.post { if (!destroyed) place(rect) }
            val action=Runnable { if (!destroyed && !loaded) exit() }
            timeout=action
            main.postDelayed(action,35_000L)
            true
        } catch (_: Exception) { detach();false }
          catch (_: LinkageError) { detach();false }
    }
    fun place(rect:MediaElementPlaybackBridge.InlineRect) {
        if (destroyed) return
        previousRect=rect
        if (fullscreen) return
        val screen=IntArray(2)
        engine.getLocationOnScreen(screen)
        val origin=IntArray(2)
        windowHost.getLocationOnScreen(origin)
        val engineVisible=Rect()
        val hostVisible=Rect()
        // getGlobalVisibleRect is in root-view coordinates, NOT screen pixels.
        // Convert local visible bounds to screen pixels for correct clipping.
        if (!engine.getLocalVisibleRect(engineVisible) ||
            !host.getLocalVisibleRect(hostVisible)) {
            hideInline()
            return
        }
        engineVisible.offset(screen[0],screen[1])
        val hostScreen=IntArray(2)
        host.getLocationOnScreen(hostScreen)
        hostVisible.offset(hostScreen[0],hostScreen[1])
        // Map the visual CSS viewport to actual GeckoView pixels (including page scale). Intersect
        // in one common screen coordinate space so chrome remains unobscured.
        val pixels=InlineMpvGeometry.pixels(rect.x,rect.y,rect.w,rect.h,
            rect.viewportWidth,engine.width,rect.dpr)
        val left=screen[0]+pixels[0]
        val top=screen[1]+pixels[1]
        val rectangle=Rect(left,top,left+pixels[2],top+pixels[3])
        val fullRectangle=Rect(rectangle)
        if (!rectangle.intersect(engineVisible) ||
            !rectangle.intersect(hostVisible) ||
            rectangle.width()<dp(120) || rectangle.height()<dp(70)) {
            hideInline()
            return
        }
        val wasHidden=overlay.visibility!=View.VISIBLE
        overlay.visibility=View.VISIBLE
        overlay.clipBounds=Rect(rectangle.left-fullRectangle.left,
            rectangle.top-fullRectangle.top,rectangle.right-fullRectangle.left,
            rectangle.bottom-fullRectangle.top)
        val params=FrameLayout.LayoutParams(fullRectangle.width(),fullRectangle.height(),
            Gravity.TOP or Gravity.START).apply {
            leftMargin=fullRectangle.left-origin[0]
            topMargin=fullRectangle.top-origin[1]
        }
        val old=overlay.layoutParams as? FrameLayout.LayoutParams
        if (old==null || old.width!=params.width || old.height!=params.height ||
            old.leftMargin!=params.leftMargin || old.topMargin!=params.topMargin)
            overlay.layoutParams=params
        updateControlsForWidth(rectangle.width())
        if (wasHidden) trace("inline_surface_visible")
    }
    private fun hideInline() {
        if (overlay.visibility!=View.INVISIBLE) {
            overlay.visibility=View.INVISIBLE
            trace("inline_surface_hidden")
        }
    }
    private fun toggleFullscreen() {
        if (destroyed) return
        if (!fullscreen) {
            overlay.cancelGesture()
            fullscreen=true
            overlay.clipBounds=null
            // Do not remove/re-add the SurfaceView or call loadfile.
            overlay.visibility=View.VISIBLE
            overlay.layoutParams=FrameLayout.LayoutParams(-1,-1,Gravity.TOP or Gravity.START)
            windowHost.systemUiVisibility=View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            trace("inline_fullscreen")
        } else {
            returnAt=lastPosition
            pendingReturnCheck=true
            fullscreen=false
            windowHost.systemUiVisibility=oldUi
            previousRect?.let(::place) ?: hideInline()
            trace("inline_return")
        }
        updateControlsForWidth(if(fullscreen) windowHost.width else overlay.width,true)
        revealControls()
    }
    /** Android Back collapses fullscreen without stopping or re-opening media. */
    fun exitFullscreen(): Boolean {
        if (!fullscreen || destroyed) return false
        toggleFullscreen()
        return true
    }

    fun detach() {
        if (destroyed) return
        overlay.cancelGesture()
        destroyed=true
        timeout?.let(main::removeCallbacks)
        hideControlsTask?.let(main::removeCallbacks)
        if (fullscreen) activity.window.decorView.systemUiVisibility=oldUi
        (overlay.parent as? ViewGroup)?.removeView(overlay)
        player?.isExiting=true
        runCatching { MPVLib.removeObserver(observer) }
        runCatching { MPVLib.removeLogObserver(logs) }
        runCatching { player?.destroy() }
        player=null
        MpvEngineGate.release(token)
        MpvDebugTrace.note(MpvDebugTrace.Section.MPV,"inline_closed",session)
    }
}
