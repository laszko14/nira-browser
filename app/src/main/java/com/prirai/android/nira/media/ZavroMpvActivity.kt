package com.prirai.android.nira.media

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.prirai.android.nira.R
import `is`.xyz.mpv.BaseMPVView
import `is`.xyz.mpv.MPVLib

/**
 * A minimal native libmpv player in Zavro x MPV. No external player required.
 * The browser remains installed separately and retains its own profile/data.
 */
class ZavroMpvActivity : AppCompatActivity() {
    private var player: ZavroMpvView? = null
    private var ready = false
    private var paused = false
    private var playButton: TextView? = null

    private fun dp(size: Int): Int =
        (size * resources.displayMetrics.density + .5f).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent?.data?.takeIf(::allowedVideoUrl)?.toString()
        if (url == null) {
            Toast.makeText(this, "Nieprawidłowy adres filmu", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        val container = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val surface = LayoutInflater.from(this)
            .inflate(R.layout.zavro_mpv_surface, container, false) as ZavroMpvView
        surface.userAgent = intent.getStringExtra(EXTRA_USER_AGENT)?.takeIf(::safeHeader)
        surface.referrer = intent.getStringExtra(EXTRA_REFERER)
            ?.takeIf(::safeHeader)
            ?.let(Uri::parse)
            ?.takeIf(::allowedVideoUrl)?.toString()
        try {
            // Native library is initialized before the SurfaceView is attached.
            surface.initialize(filesDir.absolutePath, cacheDir.absolutePath)
            ready = true
            player = surface
            surface.playFile(url)
            container.addView(surface, FrameLayout.LayoutParams(-1, -1))
        } catch (e: LinkageError) {
            Toast.makeText(this, "Nie udało się załadować biblioteki libmpv", Toast.LENGTH_LONG).show()
            finish()
            return
        } catch (e: RuntimeException) {
            Toast.makeText(this, "Błąd inicjalizacji libmpv", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(7), dp(8), dp(7))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(Color.argb(210, 24, 28, 32))
            }
            elevation = dp(8).toFloat()
        }
        row.addView(control("✕") { finish() })
        row.addView(control("−10 s") { seek(-10) })
        row.addView(control("+10 s") { seek(10) })
        row.addView(control("+30 s") { seek(30) })
        playButton = control("⏸") {
            if (!ready) return@control
            paused = !paused
            MPVLib.setPropertyBoolean("pause", paused)
            playButton?.text = if (paused) "▶" else "⏸"
        }
        row.addView(playButton)
        row.addView(control("1.5×") {
            // First version deliberately keeps the user's default 1.5x speed.
            if (ready) MPVLib.setPropertyDouble("speed", 1.5)
        })
        val rowParams = FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        rowParams.bottomMargin = dp(18)
        container.addView(row, rowParams)
        setContentView(container)
    }

    private fun control(title: String, tap: () -> Unit): TextView =
        TextView(this).apply {
            text = title
            contentDescription = title
            setTextColor(Color.WHITE)
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            minWidth = dp(50)
            minHeight = dp(48)
            setPadding(dp(5), dp(2), dp(5), dp(2))
            isClickable = true
            isFocusable = true
            setOnClickListener { tap() }
        }

    private fun seek(seconds: Int) {
        if (ready) MPVLib.command("seek", seconds.toString(), "relative")
    }

    override fun onStop() {
        if (ready) {
            paused = true
            runCatching { MPVLib.setPropertyBoolean("pause", true) }
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (ready) {
            ready = false
            runCatching { player?.destroy() }
        }
        player = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_REFERER = "zavro.mpv.referrer"
        const val EXTRA_USER_AGENT = "zavro.mpv.userAgent"

        internal fun allowedVideoUrl(uri: Uri): Boolean =
            (uri.scheme == "https" || uri.scheme == "http") &&
                !uri.host.isNullOrBlank() && uri.userInfo == null

        private fun safeHeader(value: String) =
            value.isNotBlank() && value.length <= 2048 &&
                value.none { it == '\r' || it == '\n' || it.isISOControl() }
    }
}

/** One small player surface; the native audio/video/rendering is performed by libmpv. */
class ZavroMpvView(context: android.content.Context, attrs: AttributeSet) :
    BaseMPVView(context, attrs) {

    var userAgent: String? = null
    var referrer: String? = null

    override fun initOptions() {
        setVo("gpu")
        MPVLib.setOptionString("hwdec", "mediacodec-copy")
        MPVLib.setOptionString("speed", "1.5")
        MPVLib.setOptionString("audio-pitch-correction", "yes")
        MPVLib.setOptionString("ytdl", "no")
        MPVLib.setOptionString("tls-verify", "yes")
        userAgent?.let { MPVLib.setOptionString("user-agent", it) }
        referrer?.let { MPVLib.setOptionString("referrer", it) }
    }

    override fun postInitOptions() {}
    override fun observeProperties() {}
}
