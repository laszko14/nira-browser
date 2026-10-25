package com.prirai.android.nira.media

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.components
import `is`.xyz.mpv.BaseMPVView
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode
import java.util.Locale
import kotlin.math.abs

/**
 * E2: internal libmpv host. Web playback is NOT intercepted automatically.
 * Signed media URLs remain process-local and are never saved as Intent data.
 */
class ZavroMpvActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var player: ZavroMpvView? = null
    private var currentUrl: String? = null
    private var gateTicket: String? = null
    private var ready = false
    private var fileLoaded = false
    private var userPaused = false
    private var lifecyclePaused = false
    private var speed = 1.5
    private var position = 0.0
    private var duration = 0.0
    private var holdFrom = 1.5
    private var holdActive = false
    private var holdAction: Runnable? = null
    private var timeoutAction: Runnable? = null
    private var status: TextView? = null
    private var hud: TextView? = null
    private var speedControl: TextView? = null
    private var pauseControl: TextView? = null
    private var seekBar: SeekBar? = null
    private var dragging = false

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + .5f).toInt()

    private fun selectedTab(): MediaTabState? {
        val state = applicationContext.components.store.state
        val tab = state.tabs.firstOrNull { it.id == state.selectedTabId } ?: return null
        val session = tab.engineState.engineSession ?: return null
        return MediaTabState(
            MediaSessionBinding(session, tab.id, tab.contextId, tab.content.private),
            tab.content.url, tab.content.loading
        )
    }

    private val mpvEvents = object : MPVLib.EventObserver {
        override fun eventProperty(property: String) {}
        override fun eventProperty(property: String, value: Long) {}
        override fun eventProperty(property: String, value: String) {}
        override fun eventProperty(property: String, value: MPVNode) {}
        override fun eventProperty(property: String, value: Boolean) {
            if (property == "pause") runOnUiThread {
                if (ready) pauseControl?.text = if (value) "▶" else "⏸"
            }
        }
        override fun eventProperty(property: String, value: Double) {
            if (property == "time-pos" || property == "duration") runOnUiThread {
                if (ready) {
                    if (property == "time-pos") position = value else duration = value
                    updateHud()
                }
            }
        }
        override fun event(eventId: Int, data: MPVNode) {
            when (eventId) {
                MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> runOnUiThread {
                    if (ready) {
                        fileLoaded = true
                        timeoutAction?.let(handler::removeCallbacks)
                        status?.text = "MPV w Zavro • odtwarzanie"
                        updateHud()
                    }
                }
                MPVLib.MpvEvent.MPV_EVENT_END_FILE -> runOnUiThread {
                    if (ready) status?.text = "Koniec lub błąd filmu • Ponów / Wróć"
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ticket = intent?.getStringExtra(EXTRA_TICKET_ID)
        val source = PlaybackSourceRegistry.take(ticket, selectedTab())
        val url = source?.url?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?.takeIf(::allowedVideoUrl)?.toString()
        if (ticket == null || url == null) {
            Toast.makeText(this, "Film nieaktualny lub zmieniono kartę", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (!MpvEngineGate.claim(ticket)) {
            Toast.makeText(this, "MPV jest już uruchomiony", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        gateTicket = ticket
        currentUrl = url

        if (source.isPrivate) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val surface = LayoutInflater.from(this)
            .inflate(R.layout.zavro_mpv_surface, root, false) as ZavroMpvView
        surface.userAgent = source.userAgent?.takeIf(::safeHeader)
        surface.referrer = source.referrer?.takeIf(::safeHeader)
            ?.let(Uri::parse)?.takeIf(::allowedVideoUrl)?.toString()
        try {
            surface.initialize(filesDir.absolutePath, cacheDir.absolutePath)
            player = surface
            ready = true
            MPVLib.addObserver(mpvEvents)
            MPVLib.observeProperty("time-pos", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
            MPVLib.observeProperty("duration", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
            MPVLib.observeProperty("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
            surface.playFile(url)
            root.addView(surface, FrameLayout.LayoutParams(-1, -1))
        } catch (e: LinkageError) {
            Toast.makeText(this, "Nie można uruchomić libmpv", Toast.LENGTH_LONG).show()
            finish()
            return
        } catch (e: RuntimeException) {
            Toast.makeText(this, "Błąd inicjalizacji MPV", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        installGestures(surface)
        installControls(root)
        setContentView(root)
        scheduleTimeout()
    }

    private fun installControls(root: FrameLayout) {
        status = TextView(this).apply {
            text = "Otwieranie strumienia…"
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(5), dp(12), dp(5))
            background = pill()
        }
        root.addView(status, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
            .apply { topMargin = dp(9) })
        hud = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = pill()
        }
        root.addView(hud, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END)
            .apply { topMargin = dp(9); marginEnd = dp(9) })

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = pill()
        }
        seekBar = SeekBar(this).apply {
            max = 1000
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(bar: SeekBar) { dragging = true }
                override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {}
                override fun onStopTrackingTouch(bar: SeekBar) {
                    dragging = false
                    if (ready && duration > 0.0) runCatching {
                        MPVLib.command("seek", (bar.progress / 1000.0 * duration).toString(), "absolute+exact")
                    }
                }
            })
        }
        bottom.addView(seekBar, LinearLayout.LayoutParams(-1, dp(34)))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        row.addView(control("✕") { finish() })
        row.addView(control("−10 s") { seek(-10) })
        row.addView(control("+10 s") { seek(10) })
        row.addView(control("+30 s") { seek(30) })
        pauseControl = control("⏸") {
            if (ready) {
                userPaused = !userPaused
                runCatching { MPVLib.setPropertyBoolean("pause", userPaused) }
                pauseControl?.text = if (userPaused) "▶" else "⏸"
            }
        }
        row.addView(pauseControl)
        speedControl = control("1.5×") { cycleSpeed() }
        row.addView(speedControl)
        row.addView(control("Ponów") { retry() })
        bottom.addView(row)
        root.addView(bottom, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            .apply { bottomMargin = dp(12) })
        updateHud()
    }

    private fun pill() = GradientDrawable().apply {
        cornerRadius = dp(14).toFloat()
        setColor(Color.argb(215, 24, 28, 32))
    }

    private fun control(title: String, tap: () -> Unit) = TextView(this).apply {
        text = title
        contentDescription = title
        textSize = 13f
        setTextColor(Color.WHITE)
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
        minWidth = dp(48)
        minHeight = dp(48)
        setPadding(dp(5), dp(2), dp(5), dp(2))
        isClickable = true
        isFocusable = true
        setOnClickListener { tap() }
    }

    private fun installGestures(surface: ZavroMpvView) {
        val gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val x = e.x / surface.width.coerceAtLeast(1)
                when {
                    x < .34f -> setSpeed(1.5)
                    x < .67f -> cycleSpeed()
                    else -> return false
                }
                return true
            }
        })
        var x0 = 0f
        var y0 = 0f
        surface.setOnTouchListener { view, event ->
            gestures.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    x0 = event.x; y0 = event.y
                    holdFrom = speed
                    holdActive = false
                    val action = Runnable {
                        if (ready) {
                            holdActive = true
                            setSpeed((holdFrom + .5).coerceAtMost(3.0))
                        }
                    }
                    holdAction = action
                    handler.postDelayed(action, 300L)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(event.x - x0) > dp(20) || abs(event.y - y0) > dp(20)) {
                        holdAction?.let(handler::removeCallbacks)
                        if (holdActive) {
                            setSpeed(holdFrom)
                            holdActive = false
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    holdAction?.let(handler::removeCallbacks)
                    if (holdActive) {
                        setSpeed(holdFrom)
                        holdActive = false
                    }
                    if (event.actionMasked == MotionEvent.ACTION_UP) view.performClick()
                }
            }
            true
        }
    }

    private fun cycleSpeed() {
        setSpeed(when {
            speed < 2.0 -> 2.0
            speed < 2.5 -> 2.5
            speed < 3.0 -> 3.0
            else -> 2.0
        })
    }

    private fun setSpeed(newSpeed: Double) {
        if (!ready) return
        val value = newSpeed.coerceIn(.5, 3.0)
        if (runCatching { MPVLib.setPropertyDouble("speed", value) }.isSuccess) {
            speed = value
            speedControl?.text = String.format(Locale.US, "%.1f×", value)
            updateHud()
        }
    }

    private fun seek(seconds: Int) {
        if (ready) runCatching { MPVLib.command("seek", seconds.toString(), "relative") }
    }

    private fun retry() {
        val url = currentUrl ?: return
        if (!ready) return
        fileLoaded = false
        status?.text = "Ponowne łączenie…"
        runCatching {
            MPVLib.command("stop")
            MPVLib.command("loadfile", url)
        }.onFailure { status?.text = "Błąd otwarcia • wróć do strony" }
        scheduleTimeout()
    }

    private fun scheduleTimeout() {
        timeoutAction?.let(handler::removeCallbacks)
        val action = Runnable {
            if (ready && !fileLoaded) status?.text =
                "Brak obrazu po 20 s • Ponów albo ✕, aby wrócić"
        }
        timeoutAction = action
        handler.postDelayed(action, 20_000L)
    }

    private fun updateHud() {
        val pos = if (position.isFinite()) position.coerceAtLeast(0.0) else 0.0
        val len = if (duration.isFinite()) duration.coerceAtLeast(0.0) else 0.0
        val remain = (len - pos).coerceAtLeast(0.0)
        hud?.text = if (len > 0.0)
            formatTime(pos) + " / " + formatTime(len) +
                "  •  −" + formatTime(remain / speed) + " (1×: −" + formatTime(remain) + ")"
        else String.format(Locale.US, "MPV • %.1f×", speed)
        if (!dragging) seekBar?.progress =
            if (len > 0) (pos / len * 1000.0).toInt().coerceIn(0, 1000) else 0
    }

    private fun formatTime(s: Double): String {
        val n = s.toLong().coerceAtLeast(0L)
        return if (n >= 3600) String.format(Locale.US, "%d:%02d:%02d", n / 3600, n % 3600 / 60, n % 60)
        else String.format(Locale.US, "%02d:%02d", n / 60, n % 60)
    }

    override fun onStart() {
        super.onStart()
        if (ready && lifecyclePaused && !userPaused) {
            runCatching { MPVLib.setPropertyBoolean("pause", false) }
            lifecyclePaused = false
        }
    }

    override fun onStop() {
        if (ready && !userPaused) {
            lifecyclePaused = true
            runCatching { MPVLib.setPropertyBoolean("pause", true) }
        }
        super.onStop()
    }

    override fun onDestroy() {
        holdAction?.let(handler::removeCallbacks)
        timeoutAction?.let(handler::removeCallbacks)
        player?.isExiting = true
        if (ready) {
            ready = false
            runCatching { MPVLib.removeObserver(mpvEvents) }
            runCatching { player?.destroy() }
        }
        player = null
        currentUrl = null
        gateTicket?.let(MpvEngineGate::release)
        gateTicket = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TICKET_ID = "zavro.mpv.ticketId"
        internal fun allowedVideoUrl(uri: Uri) =
            (uri.scheme == "https" || uri.scheme == "http") &&
                !uri.host.isNullOrBlank() && uri.userInfo == null
        private fun safeHeader(value: String): Boolean =
            value.isNotBlank() && value.length <= 2048 &&
                value.none { it == '\r' || it == '\n' || it.isISOControl() }
    }
}

class ZavroMpvView(context: android.content.Context, attrs: AttributeSet) :
    BaseMPVView(context, attrs) {
    var userAgent: String? = null
    var referrer: String? = null
    var isExiting = false

    override fun initOptions() {
        setVo("gpu")
        MPVLib.setOptionString("hwdec", "mediacodec,no")
        MPVLib.setOptionString("speed", "1.5")
        MPVLib.setOptionString("audio-pitch-correction", "yes")
        MPVLib.setOptionString("ytdl", "no")
        MPVLib.setOptionString("tls-verify", "yes")
        userAgent?.let { MPVLib.setOptionString("user-agent", it) }
        referrer?.let { MPVLib.setOptionString("referrer", it) }
    }
    override fun postInitOptions() {}
    override fun observeProperties() {}

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        super.surfaceChanged(holder, format, width, height)
        if (!isExiting && runCatching { MPVLib.getPropertyBoolean("pause") }.getOrNull() == true) {
            runCatching { MPVLib.command("seek", "0", "relative+exact") }
        }
    }
}
