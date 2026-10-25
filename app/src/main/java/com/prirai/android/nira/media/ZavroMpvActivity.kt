package com.prirai.android.nira.media

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.util.Rational
import android.content.pm.ActivityInfo
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
    private var traceSession: Any? = null
    private fun trace(code: String) {
        MpvDebugTrace.note(MpvDebugTrace.Section.MPV, code, traceSession)
    }
    private var ready = false
    private var fileLoaded = false
    private val retryController = MpvRetryController()
    private var retryGeneration = 0
    private var autoHandoff = false
    private var loadStartMs = 0L
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
    private var supertestControl: TextView? = null
    private var supertestTick: Runnable? = null
    private var seekBar: SeekBar? = null
    private var dragging = false
    private var nativeControls: View? = null
    private var miniProgress: FrameLayout? = null
    private var miniFill: View? = null
    private var controlsHideTask: Runnable? = null
    private var focusRequest: AudioFocusRequest? = null
    private var audioManager: AudioManager? = null
    private var focusSuspended = false
    private var audioFocusAcquired = false
    // Experimental opt-in filters: none is enabled until the user requests it.
    private var denoiseLevel = 0
    private var dialogueLevel = 0
    private var highEq = false
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        runOnUiThread {
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    if (ready && !userPaused) {
                        focusSuspended = true
                        runCatching { MPVLib.setPropertyBoolean("pause", true) }
                        status?.text = "MPV wstrzymane: inne źródło dźwięku"
                    }
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    if (ready && focusSuspended && !userPaused && !lifecyclePaused) {
                        focusSuspended = false
                        runCatching { MPVLib.setPropertyBoolean("pause", false) }
                    }
                }
            }
        }
    }

    private fun requestMediaAudioFocus(): Boolean {
        val manager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager = manager
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
            .build()
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(focusListener)
                .build()
            focusRequest = request
            manager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(
                focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN
            )
        }
        audioFocusAcquired = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return audioFocusAcquired
    }

    private fun abandonMediaAudioFocus() {
        if (!audioFocusAcquired) return
        val manager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { manager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(focusListener)
        }
        audioFocusAcquired = false
        audioManager = null
        focusRequest = null
    }

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

    private val mpvLogs = object : MPVLib.LogObserver {
        override fun logMessage(prefix: String, level: Int, text: String) {
            val code = MpvDebugTrace.classifyMpvLog(prefix, level, text) ?: return
            trace(code)
            // Categorize post-load segment TLS errors without storing the raw log,
            // hostname, signed query, or HTTP headers.
            if (code == "mpv_tls_error" || code == "mpv_tls_certificate_error" ||
                code == "mpv_tls_handshake_error") runOnUiThread {
                if (ready && fileLoaded) trace("mpv_tls_after_load")
            }
        }
    }

    private val mpvEvents = object : MPVLib.EventObserver {
        override fun eventProperty(property: String) {}
        override fun eventProperty(property: String, value: Long) {}
        override fun eventProperty(property: String, value: String) {}
        override fun eventProperty(property: String, value: MPVNode) {}
        override fun eventProperty(property: String, value: Boolean) {
            if (property == "paused-for-cache") trace(
                if (value) "mpv_cache_wait" else "mpv_cache_resume"
            )
            if (property == "pause") runOnUiThread {
                if (ready) pauseControl?.text = if (value) "▶" else "⏸"
            }
        }
        override fun eventProperty(property: String, value: Double) {
            if (property == "time-pos" || property == "duration") runOnUiThread {
                if (ready) {
                    if (property == "time-pos") {
                        position = value
                        MpvSupertest.sample(traceSession, position)
                    } else duration = value
                    updateHud()
                }
            }
        }
        override fun event(eventId: Int, data: MPVNode) {
            when (eventId) {
                MPVLib.MpvEvent.MPV_EVENT_START_FILE -> trace("mpv_start_file")
                MPVLib.MpvEvent.MPV_EVENT_VIDEO_RECONFIG -> trace("mpv_video_ready")
                MPVLib.MpvEvent.MPV_EVENT_AUDIO_RECONFIG -> trace("mpv_audio_ready")
                MPVLib.MpvEvent.MPV_EVENT_PLAYBACK_RESTART -> trace("mpv_playback_started")
                MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> runOnUiThread {
                    if (ready) {
                        fileLoaded = true
                        val resumeAt = retryController.onFileLoaded()
                        trace("mpv_file_loaded")
                        timeoutAction?.let(handler::removeCallbacks)
                        status?.text = "MPV w Zavro • odtwarzanie"
                        updateHud()
                        if (resumeAt != null) {
                            val generation = retryGeneration
                            handler.postDelayed({
                                if (ready && fileLoaded && !isFinishing &&
                                    generation == retryGeneration) {
                                    trace("mpv_retry_resume_attempt")
                                    runCatching {
                                        MPVLib.command("seek", resumeAt.toString(), "absolute+keyframes")
                                    }.onFailure { trace("mpv_retry_resume_failed") }
                                }
                            }, 350L)
                        }
                    }
                }
                MPVLib.MpvEvent.MPV_EVENT_END_FILE -> runOnUiThread {
                    if (!ready || isFinishing) return@runOnUiThread
                    // A 'loadfile replace' unloads the OLD file first. It is
                    // not evidence that opening the new stream failed.
                    if (retryController.consumeReplacementEnd()) {
                        trace("mpv_retry_replacement_end")
                        return@runOnUiThread
                    }
                    val previouslyLoaded = fileLoaded
                    fileLoaded = false
                    retryController.clear()
                    if (!previouslyLoaded) {
                        trace("mpv_end_before_load")
                        if (autoHandoff) {
                            trace("mpv_auto_rollback")
                            // Return Gecko to its paused element instead of trapping
                            // the user in a black screen after failed network opening.
                            setResult(Activity.RESULT_CANCELED)
                            finish()
                        } else {
                            trace("mpv_manual_error")
                            status?.text = "Nie można otworzyć strumienia • " +
                                "możliwy wygasły link, autoryzacja lub format. Ponów / Wróć"
                        }
                    } else {
                        trace("mpv_end_after_load")
                        status?.text = "Koniec filmu • Ponów / Wróć"
                    }
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
            trace("mpv_ticket_missing")
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
        traceSession = source.owner.session
        trace("mpv_ticket_valid")
        trace(MpvDebugTrace.classifySource(url))
        autoHandoff = source.autoHandoff
        currentUrl = url
        if (!requestMediaAudioFocus()) {
            trace("mpv_audio_focus_failed")
            Toast.makeText(this, "Brak dostępu do dźwięku", Toast.LENGTH_LONG).show()
            finish()
            return
        }

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
        // Retries are limited to HLS/DASH HTTP requests, not application-level
        // replays. No automatic retry on authentication/permission failures.
        surface.segmentedNetworkReconnect = MpvDebugTrace.classifySource(url) in
            setOf("source_hls", "source_dash")
        // libmpv/FFmpeg on Android may not discover the OS trust store.
        // Export only Android's system CA roots as an app-private PEM; never bypass TLS.
        surface.tlsCaFile = MpvTlsTrust.prepare(this)
        trace(if (surface.tlsCaFile != null) "mpv_tls_ca_prepared" else "mpv_tls_ca_unavailable")
        try {
            surface.prepareAlphaBridge(filesDir.absolutePath)
            surface.initialize(filesDir.absolutePath, cacheDir.absolutePath)
            // BaseMPVView.playFile queues the file for surfaceCreated().
            // Queue BEFORE attaching this SurfaceView, never from a later post{}.
            surface.playFile(url)
            surface.onSurfaceReady = {
                if (ready && !isFinishing && currentUrl == url) {
                    trace("mpv_surface_attached")
                    trace("mpv_loading")
                    loadStartMs = android.os.SystemClock.elapsedRealtime()
                    scheduleTimeout()
                }
            }
            player = surface
            ready = true
            MPVLib.addObserver(mpvEvents)
            MPVLib.addLogObserver(mpvLogs)
            trace("mpv_logger_registered")
            MPVLib.observeProperty("paused-for-cache", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
            MPVLib.observeProperty("time-pos", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
            MPVLib.observeProperty("duration", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
            MPVLib.observeProperty("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
            root.addView(surface, FrameLayout.LayoutParams(-1, -1))
        } catch (e: LinkageError) {
            trace("mpv_linkage_failed")
            Toast.makeText(this, "Nie można uruchomić libmpv", Toast.LENGTH_LONG).show()
            finish()
            return
        } catch (e: RuntimeException) {
            trace("mpv_init_failed")
            Toast.makeText(this, "Błąd inicjalizacji MPV", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        installGestures(surface)
        installControls(root)
        setContentView(root)
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
        row.addView(control("Audio") {
            if (ready) runCatching { MPVLib.command("cycle", "aid") }
                .onFailure { status?.text = "Nie można zmienić ścieżki audio" }
        })
        row.addView(control("Napisy") {
            if (ready) runCatching { MPVLib.command("cycle", "sid") }
                .onFailure { status?.text = "Nie można zmienić napisów" }
        })
        row.addView(control("Filtry") { showAudioFilters() })
        row.addView(control("Ponów") { retry() })
        supertestControl = control("SUPERTEST 40 s") { startMpvSupertest() }
        row.addView(supertestControl)
        row.addView(control("PiP") { enterNativePip() })
        bottom.addView(row)
        root.addView(bottom, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            .apply { bottomMargin = dp(12) })
        nativeControls = bottom
        val mini = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(120, 255, 255, 255))
            visibility = View.GONE
            isClickable = true
            setOnClickListener { revealControls() }
        }
        miniFill = View(this).apply { setBackgroundColor(Color.rgb(126, 202, 196)) }
        mini.addView(miniFill, FrameLayout.LayoutParams(0, -1, Gravity.START))
        root.addView(mini, FrameLayout.LayoutParams(-1, dp(3), Gravity.BOTTOM))
        miniProgress = mini
        updateHud()
        revealControls()
        updateSupertestCountdown()
    }

    private fun startMpvSupertest() {
        if (MpvSupertest.start(applicationContext, traceSession,
                if (autoHandoff) "AUTO" else "MPV ręcznie")) {
            Toast.makeText(this, "SUPERTEST 40 s rozpoczęty • raport skopiuje się sam",
                Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Test trwa: ${MpvSupertest.remainingSeconds()} s",
                Toast.LENGTH_SHORT).show()
        }
        updateSupertestCountdown()
        revealControls()
    }

    private fun updateSupertestCountdown() {
        supertestTick?.let(handler::removeCallbacks)
        val remaining = MpvSupertest.remainingSeconds()
        supertestControl?.text = if (remaining > 0) "TEST ${remaining} s"
            else if (MpvSupertest.latest() != null) "✓ RAPORT" else "SUPERTEST 40 s"
        if (remaining > 0 && ready) {
            val tick = Runnable { if (!isFinishing) updateSupertestCountdown() }
            supertestTick = tick
            handler.postDelayed(tick, 1_000L)
        }
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

    private fun revealControls() {
        if (isInPictureInPictureMode) return
        nativeControls?.visibility = View.VISIBLE
        miniProgress?.visibility = View.GONE
        controlsHideTask?.let(handler::removeCallbacks)
        val hide = Runnable {
            if (ready && !isInPictureInPictureMode) {
                nativeControls?.visibility = View.GONE
                miniProgress?.visibility = View.VISIBLE
                updateHud()
            }
        }
        controlsHideTask = hide
        handler.postDelayed(hide, 5_000L)
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
                    revealControls()
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

    private fun showAudioFilters() {
        if (!ready || isFinishing) return
        val options = arrayOf(
            "Szum: $denoiseLevel/7  •  następny poziom",
            "Głos: $dialogueLevel/3  •  następny poziom",
            "EQ wysokie: ${if (highEq) "ON" else "OFF"}",
            "Wyłącz wszystkie filtry",
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("MPV • Filtry audio (testowe)")
            .setItems(options) { _, item ->
                when (item) {
                    0 -> denoiseLevel = (denoiseLevel + 1) % 8
                    1 -> dialogueLevel = (dialogueLevel + 1) % 4
                    2 -> highEq = !highEq
                    3 -> { denoiseLevel = 0; dialogueLevel = 0; highEq = false }
                }
                applyAudioFilters()
            }
            .setNegativeButton("Zamknij", null)
            .show()
    }

    private fun applyAudioFilters() {
        if (!ready) return
        val filters = mutableListOf<String>()
        if (denoiseLevel > 0) {
            // Gentle 1–4 and stronger 5–7; values are FFmpeg afftdn nr in dB.
            val levels = intArrayOf(4, 6, 8, 10, 12, 16, 20)
            filters += "lavfi=[afftdn=nr=${levels[denoiseLevel - 1]}]"
        }
        if (dialogueLevel > 0) {
            val gains = arrayOf("1.0", "2.0", "3.0")
            filters += "lavfi=[equalizer=f=2300:t=q:w=1:g=${gains[dialogueLevel - 1]}]"
        }
        if (highEq) filters += "lavfi=[equalizer=f=4300:t=q:w=1:g=1.5]"
        // af set replaces only the filters owned by this experimental player.
        // Never use speechnorm or constant loudness normalization.
        runCatching { MPVLib.command("af", "set", filters.joinToString(",")) }
            .onSuccess {
                status?.text = if (filters.isEmpty()) "Filtry wyłączone"
                    else "Żądanie filtrów wysłane • sprawdź działanie i dźwięk"
            }.onFailure { status?.text = "MPV odrzuciło zmianę filtrów" }
    }

    private fun enterNativePip() {
        if (!ready || !fileLoaded || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }.onFailure { status?.text = "PiP nie jest obsługiwany na tym urządzeniu" }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean, newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        nativeControls?.visibility = if (isInPictureInPictureMode) View.GONE else View.VISIBLE
        miniProgress?.visibility = View.GONE
        if (!isInPictureInPictureMode) revealControls()
        hud?.visibility = if (isInPictureInPictureMode) View.GONE else View.VISIBLE
        status?.visibility = if (isInPictureInPictureMode) View.GONE else View.VISIBLE
    }

    private fun retry() {
        val url = currentUrl ?: return
        if (!ready || isFinishing) return
        retryController.prepare(fileLoaded, position, duration)
        retryGeneration++
        fileLoaded = false
        status?.text = "Ponowne łączenie…"
        // One atomic playlist replacement; an extra 'stop' created a spurious
        // END_FILE in v1140 and could show 'mpv_manual_error' while reopening.
        val started = runCatching {
            MPVLib.command("loadfile", url, "replace")
        }.isSuccess
        if (!started) {
            retryController.clear()
            status?.text = "Błąd ponownego otwarcia • wróć do strony"
            return
        }
        trace("mpv_retry")
        scheduleTimeout()
    }

    private fun scheduleTimeout() {
        timeoutAction?.let(handler::removeCallbacks)
        val action = Runnable {
            if (ready && !fileLoaded && !isFinishing) {
                trace("mpv_timeout")
                if (autoHandoff) {
                    trace("mpv_auto_rollback")
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                } else {
                    status?.text = "Nie udało się rozpocząć odtwarzania • " +
                        "strumień może wymagać logowania lub nowego adresu. Ponów / Wróć"
                }
            }
        }
        timeoutAction = action
        // HLS/DASH can require manifest + segment fetch before FILE_LOADED.
        val isSegmented = MpvDebugTrace.classifySource(currentUrl.orEmpty()) in
            setOf("source_hls", "source_dash")
        handler.postDelayed(action, if (isSegmented) 35_000L else 25_000L)
    }

    private fun updateHud() {
        val pos = if (position.isFinite()) position.coerceAtLeast(0.0) else 0.0
        val len = if (duration.isFinite()) duration.coerceAtLeast(0.0) else 0.0
        val remain = (len - pos).coerceAtLeast(0.0)
        hud?.text = if (len > 0.0)
            formatTime(pos) + " / " + formatTime(len) +
                "  •  −" + formatTime(remain / speed) + " (1×: −" + formatTime(remain) + ")"
        else String.format(Locale.US, "MPV • %.1f×", speed)
        val proportion = if (len > 0) (pos / len).coerceIn(0.0, 1.0) else 0.0
        if (!dragging) seekBar?.progress = (proportion * 1000.0).toInt()
        val mini = miniProgress
        val fill = miniFill
        if (mini != null && fill != null && mini.width > 0) {
            fill.layoutParams = fill.layoutParams.apply {
                width = (mini.width * proportion).toInt().coerceIn(0, mini.width)
            }
        }
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
        if (ready && !userPaused && !isInPictureInPictureMode) {
            lifecyclePaused = true
            runCatching { MPVLib.setPropertyBoolean("pause", true) }
        }
        super.onStop()
    }

    override fun onDestroy() {
        trace("mpv_destroyed")
        holdAction?.let(handler::removeCallbacks)
        timeoutAction?.let(handler::removeCallbacks)
        controlsHideTask?.let(handler::removeCallbacks)
        supertestTick?.let(handler::removeCallbacks)
        player?.isExiting = true
        retryController.clear()
        if (ready) {
            ready = false
            runCatching { MPVLib.removeObserver(mpvEvents) }
            runCatching { MPVLib.removeLogObserver(mpvLogs) }
            runCatching { player?.destroy() }
            player?.cleanupCustomScripts()
        }
        player = null
        currentUrl = null
        abandonMediaAudioFocus()
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
    var tlsCaFile: String? = null
    var segmentedNetworkReconnect = false
    var isExiting = false
    var onSurfaceReady: (() -> Unit)? = null

    override fun surfaceCreated(holder: SurfaceHolder) {
        super.surfaceCreated(holder) // executes queued loadfile exactly once
        onSurfaceReady?.invoke()
    }

    private var hudBottomPx=0f
    internal fun setOsdHudBottom(value:Float) {hudBottomPx=value;updateNativeOsdLayout()}
    private fun updateNativeOsdLayout() {
        if(isExiting || !nativeOsdConfigured || height<=0)return
        com.prirai.android.nira.media.alpha.OsdGeometry.options(height,
            context.resources.displayMetrics.density.toDouble(),hudBottomPx.toDouble()).forEach {(key,value)->
                MPVLib.setOptionString(key,value)
            }
    }
    private var nativeOsdFonts:String?=null
    internal var nativeOsdConfigured=false
        private set
    private var alphaBridgePrepared=false
    private var alphaBridgeInstalled=false
    private var alphaBridgeScript=""
    internal var alphaBridgeLoadConfigured=false
        private set
    internal fun prepareAlphaBridge(configDir:String,scriptStore:com.prirai.android.nira.media.alpha.scripts.CustomLuaStore?=null):Boolean {
        if(!alphaBridgePrepared) {
            alphaBridgePrepared=true
            nativeOsdFonts=com.prirai.android.nira.media.alpha.NativeOsd.prepare(context)
            alphaBridgeInstalled=com.prirai.android.nira.media.alpha.AlphaLuaBridge.install(java.io.File(configDir))
            if(alphaBridgeInstalled)alphaBridgeScript=java.io.File(configDir,
                "scripts/"+com.prirai.android.nira.media.alpha.AlphaLuaBridge.FILE_NAME).absolutePath
            // Device tests use isolated directories; never read the user's real store there.
            if(alphaBridgeInstalled && (scriptStore!=null || java.io.File(configDir).canonicalFile==context.filesDir.canonicalFile)) {
                customRuntime=runCatching {com.prirai.android.nira.media.alpha.scripts.CustomLuaRuntime.prepare(
                    scriptStore?:com.prirai.android.nira.media.alpha.scripts.CustomLuaRepository.get(context),
                    java.io.File(configDir),context.assets.open("mpv/zavro_custom_client.lua").bufferedReader().use {it.readText()})}.getOrNull()
            }
            customScriptsOption=(listOf(alphaBridgeScript).filter {it.isNotEmpty()}+
                customRuntime?.paths.orEmpty()).joinToString(java.io.File.pathSeparator)
        }
        return alphaBridgeInstalled
    }

    internal var customRuntime:com.prirai.android.nira.media.alpha.scripts.CustomLuaRuntime?=null
        private set
    internal var customOptionsAccepted=0
        private set
    internal var customOptionsRejected=0
        private set
    private var customScriptsOption=""
    internal var customScriptsConfigured=false
        private set
    internal fun cleanupCustomScripts() {customRuntime?.cleanup();customRuntime=null}

    override fun initOptions() {
        // BaseMPVView sets config=yes/config-dir before this hook, then calls MPVLib.init.
        // Do not autoload unrelated scripts, even with consent off. Explicit script
        // options are loaded by mp_load_scripts BEFORE its auto_load_scripts check.
        // Supported Zavro preferences are applied through API/adapters, not mpv.conf.
        // Do not let an unrelated config file re-enable autoload or add scripts.
        val fileConfigDisabled=MPVLib.setOptionString("config","no")>=0
        val autoloadDisabled=MPVLib.setOptionString("load-scripts","no")>=0
        val bridgeSelected=MPVLib.setOptionString("scripts",customScriptsOption)>=0
        alphaBridgeLoadConfigured=alphaBridgeInstalled && fileConfigDisabled && autoloadDisabled && bridgeSelected
        customScriptsConfigured=alphaBridgeLoadConfigured && customRuntime?.prepared==true
        nativeOsdConfigured=nativeOsdFonts?.let {fonts->
            com.prirai.android.nira.media.alpha.NativeOsd.options(fonts).map {(key,value)->MPVLib.setOptionString(key,value)>=0}.all {it}
        }==true
        setVo("gpu")
        MPVLib.setOptionString("hwdec", "mediacodec,no")
        MPVLib.setOptionString("speed", "1.5")
        MPVLib.setOptionString("audio-pitch-correction", "yes")
        MPVLib.setOptionString("ytdl", "no")
        MPVLib.setOptionString("tls-verify", "yes")
        tlsCaFile?.let { MPVLib.setOptionString("tls-ca-file", it) }
        if (segmentedNetworkReconnect) {
            // Bounded FFmpeg reconnect for mid-playback HLS/DASH segment failures.
            // Keep TLS verification; do NOT retry 401/403 or loop at segment EOF.
            val retryOptions = "reconnect=1,reconnect_on_network_error=1," +
                "reconnect_streamed=1,reconnect_delay_max=2"
            MPVLib.setOptionString("stream-lavf-o", retryOptions)
            MPVLib.setOptionString("demuxer-lavf-o", retryOptions)
        }
        // Stream directly over HTTP: seekable cache, bounded buffering,
        // retry button. Never download the whole movie to a temporary file.
        MPVLib.setOptionString("cache", "yes")
        MPVLib.setOptionString("cache-secs", "180")
        MPVLib.setOptionString("cache-pause", "yes")
        MPVLib.setOptionString("cache-pause-wait", "2")
        MPVLib.setOptionString("demuxer-seekable-cache", "yes")
        MPVLib.setOptionString("network-timeout", "15")
        // Only validated, explicitly confirmed local options; mandatory script/TLS/owner options
        // are outside this allowlist. Native option parser supplies actual acceptance status.
        customRuntime?.options.orEmpty().forEach { (key,value)->
            if(MPVLib.setOptionString(key,value)>=0)customOptionsAccepted++ else customOptionsRejected++
        }
        userAgent?.let { MPVLib.setOptionString("user-agent", it) }
        referrer?.let { MPVLib.setOptionString("referrer", it) }
    }
    override fun postInitOptions() {}
    override fun observeProperties() {}

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // Surface resize must not silently reset playback to the beginning.
        super.surfaceChanged(holder, format, width, height)
        updateNativeOsdLayout()
    }
}
