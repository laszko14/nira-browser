package com.prirai.android.nira.media

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
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
    private val overlay=FrameLayout(context).apply {
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
    private val oldUi=activity.window.decorView.systemUiVisibility
    private val observer=object: MPVLib.EventObserver {
        override fun eventProperty(property:String) {}
        override fun eventProperty(property:String,value:Long) {}
        override fun eventProperty(property:String,value:String) {}
        override fun eventProperty(property:String,value:MPVNode) {}
        override fun eventProperty(property:String,value:Boolean) {}
        override fun eventProperty(property:String,value:Double) {
            if (property=="time-pos" && !destroyed)
                MpvSupertest.sample(session,value)
        }
        override fun event(eventId:Int,data:MPVNode) {
            if (eventId==MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED) main.post {
                if (!destroyed) {
                    loaded=true
                    timeout?.let(main::removeCallbacks)
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
    private fun button(title:String,action:()->Unit)=TextView(context).apply {
        text=title
        textSize=13f
        setTextColor(Color.WHITE)
        gravity=Gravity.CENTER
        minWidth=(44*density).toInt()
        minHeight=(44*density).toInt()
        background=GradientDrawable().apply {
            cornerRadius=7*density
            setColor(Color.argb(230,28,32,36))
        }
        setOnClickListener { action() }
    }
    fun open(url:String,userAgent:String?,referrer:String?,
             rect:MediaElementPlaybackBridge.InlineRect):Boolean {
        val video=LayoutInflater.from(context).inflate(R.layout.zavro_mpv_surface,
            overlay,false) as ZavroMpvView
        video.userAgent=userAgent
        video.referrer=referrer
        video.tlsCaFile=MpvTlsTrust.prepare(context)
        video.segmentedNetworkReconnect=MpvDebugTrace.classifySource(url) in
            setOf("source_hls","source_dash")
        return try {
            video.initialize(context.filesDir.absolutePath,context.cacheDir.absolutePath)
            video.playFile(url) // queue before attaching surface
            MPVLib.addObserver(observer)
            MPVLib.observeProperty("time-pos",MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
            player=video
            overlay.addView(video,FrameLayout.LayoutParams(-1,-1))
            val controls=LinearLayout(context).apply {
                gravity=Gravity.CENTER_HORIZONTAL
                orientation=LinearLayout.HORIZONTAL
            }
            controls.addView(button("−10") { runCatching { MPVLib.command("seek","-10","relative") } })
            controls.addView(button("⏯") { runCatching { MPVLib.command("cycle","pause") } })
            var speed=1.5
            controls.addView(button("1.5×") {
                speed=if(speed>=3.0)1.5 else speed+.5
                runCatching { MPVLib.setPropertyDouble("speed",speed) }
            })
            controls.addView(button("+10") { runCatching { MPVLib.command("seek","10","relative") } })
            controls.addView(button("CC") { runCatching { MPVLib.command("cycle","sid") } })
            controls.addView(button("⛶") { toggleFullscreen() })
            controls.addView(button("✕") { exit() })
            overlay.addView(controls,FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
            host.addView(overlay,CoordinatorLayout.LayoutParams(1,1))
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
        val viewport=IntArray(2);val origin=IntArray(2)
        engine.getLocationOnScreen(viewport)
        host.getLocationOnScreen(origin)
        val left=(viewport[0]-origin[0]+rect.x*rect.dpr).toInt()
        val top=(viewport[1]-origin[1]+rect.y*rect.dpr).toInt()
        val right=left+(rect.w*rect.dpr).toInt()
        val bottom=top+(rect.h*rect.dpr).toInt()
        val x=left.coerceAtLeast(0);val y=top.coerceAtLeast(0)
        val r=right.coerceAtMost(host.width);val b=bottom.coerceAtMost(host.height)
        if (r-x<120 || b-y<70) { overlay.visibility=View.INVISIBLE;return }
        overlay.visibility=View.VISIBLE
        overlay.layoutParams=CoordinatorLayout.LayoutParams(r-x,b-y)
        overlay.translationX=x.toFloat()
        overlay.translationY=y.toFloat()
        overlay.bringToFront()
    }
    private fun toggleFullscreen() {
        if (destroyed) return
        val decor=activity.window.decorView as? ViewGroup ?: return
        if (!fullscreen) {
            (overlay.parent as? ViewGroup)?.removeView(overlay)
            overlay.translationX=0f;overlay.translationY=0f
            fullscreen=true
            decor.addView(overlay,FrameLayout.LayoutParams(-1,-1))
            activity.window.decorView.systemUiVisibility=
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            MpvDebugTrace.note(MpvDebugTrace.Section.MPV,"inline_fullscreen",session)
        } else {
            decor.removeView(overlay)
            fullscreen=false
            host.addView(overlay,CoordinatorLayout.LayoutParams(1,1))
            activity.window.decorView.systemUiVisibility=oldUi
            previousRect?.let(::place)
            MpvDebugTrace.note(MpvDebugTrace.Section.MPV,"inline_return",session)
        }
    }
    fun detach() {
        if (destroyed) return
        destroyed=true
        timeout?.let(main::removeCallbacks)
        if (fullscreen) activity.window.decorView.systemUiVisibility=oldUi
        (overlay.parent as? ViewGroup)?.removeView(overlay)
        player?.isExiting=true
        runCatching { MPVLib.removeObserver(observer) }
        runCatching { player?.destroy() }
        player=null
        MpvDebugTrace.note(MpvDebugTrace.Section.MPV,"inline_closed",session)
    }
}
