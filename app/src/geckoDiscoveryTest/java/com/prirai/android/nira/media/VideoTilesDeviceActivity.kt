package com.prirai.android.nira.media

import android.os.Bundle
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf

/** Controlled, network-free host available only in the explicit instrumentation build. */
class VideoTilesDeviceActivity : ComponentActivity() {
    private var floating: MediaFloatingUi? = null
    private val settings = mutableStateOf(VideoTilesSettings(rows = 10, widthDp = 160))
    var lastPlayed: Long = -1L
        private set
    var playCount: Int = 0
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val preferences = VideoTilesPreferences(this)
        settings.value = preferences.load(landscape)
        val visual = intent.getBooleanExtra("visual", false)
        val entries = (1..intent.getIntExtra("count", 60).coerceIn(0, 60)).map { number ->
            val title = if (visual) "Film testowy $number — przykład dwóch wierszy" else "Kafelek $number"
            val entry = MediaEntry(
                id = number.toLong(), url = "https://fixture.invalid/movie-$number.mp4",
                format = MediaFormat.MP4, sources = setOf(MediaSource.DOM),
                title = title, playbackStatus = MediaPlaybackStatus.CONFIRMED,
                quality = if (visual) "1080p" else null,
                sizeBytes = if (visual) 213_000_000L else null,
                durationSeconds = if (visual) 2535 else null,
            )
            VideoTileItem(number.toLong(), entry, title, 1)
        }
        if (intent.getBooleanExtra("overlay", false)) {
            val parent = android.widget.FrameLayout(this)
            setContentView(parent)
            val tab = MediaTabState(MediaSessionBinding(Any(), "fixture", null, false), "https://fixture.invalid/page", false)
            val snapshot = MediaUiSnapshot(tab, entries.map { it.entry })
            floating = MediaFloatingUi(parent, { snapshot }, { 0 to 0 }, {
                lastPlayed = it
                playCount++
            }).also { it.render(snapshot) }
            return
        }
        setContent {
            MaterialTheme {
                VideoTilesPanel(
                    entries = entries, settings = settings.value,
                    onSettingsChange = {
                        settings.value = it
                        preferences.save(landscape, it)
                    },
                    onPlay = { lastPlayed = it; playCount++ },
                    onClose = { finish() },
                )
            }
        }
    }

    override fun onDestroy() {
        floating?.detach()
        floating = null
        super.onDestroy()
    }

    fun configuredRows(): Int = settings.value.rows
    fun configuredWidth(): Int = settings.value.widthDp
}
