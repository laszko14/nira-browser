package com.prirai.android.nira.media

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.PersistableBundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import coil.load
import coil.request.CachePolicy
import java.net.URI
import java.util.Locale
import kotlin.math.abs

/** A view-layer snapshot; URLs are never logged, serialized or persisted. */
internal data class MediaUiSnapshot(val tab: MediaTabState, val entries: List<MediaEntry>)

/**
 * App-only draggable View overlay and Material BottomSheet. No injected HTML, overlay
 * permission, polling or separate video cache. Only normalized position is persisted.
 */
internal class MediaFloatingUi(
    private val parent: ViewGroup,
    private val current: () -> MediaUiSnapshot?,
    private val safeToolbarSpace: () -> Pair<Int, Int>,
    private val openVideo: (String) -> Unit,
) {
    private val context = parent.context
    private val prefs = context.getSharedPreferences("nira_media_floating_position", Context.MODE_PRIVATE)
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density + 0.5f).toInt()
    private val size = dp(56)
    private val padding = dp(24)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val button = FrameLayout(context).apply {
        background = circle(Color.rgb(26, 180, 176))
        elevation = dp(8).toFloat()
        contentDescription = "Wykryte filmy"
        isClickable = true
        isFocusable = true
        visibility = View.GONE
    }
    private val count = TextView(context).apply {
        gravity = Gravity.CENTER
        textSize = 11f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setTextColor(Color.WHITE)
        setPadding(dp(3), 0, dp(3), 0)
        background = circle(Color.rgb(211, 40, 53))
        elevation = dp(2).toFloat()
    }
    private var isFullscreen = false
    private var displayed: MediaUiSnapshot? = null
    private var sheet: BottomSheetDialog? = null
    private var sheetBody: LinearLayout? = null
    private var placed = false
    private var lastWidth = 0
    private var lastHeight = 0
    private var x = 0f
    private var y = 0f
    private var dragging = false
    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0f
    private var startY = 0f

    init {
        button.addView(TextView(context).apply {
            text = "▶"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            contentDescription = null
        }, FrameLayout.LayoutParams(-1, -1))
        button.addView(count, FrameLayout.LayoutParams(dp(27), dp(27), Gravity.END or Gravity.TOP))
        parent.addView(button, ViewGroup.LayoutParams(size, size))
        button.setOnTouchListener { _, event -> handleTouch(event) }
        parent.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            if (right - left != lastWidth || bottom - top != lastHeight) {
                lastWidth = right - left
                lastHeight = bottom - top
                button.post { place() }
            } else {
                button.post { if (!dragging) place() }
            }
        }
        button.post { place() }
    }

    private fun circle(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    fun render(value: MediaUiSnapshot?) {
        displayed = value?.takeIf { it.entries.isNotEmpty() }
        val amount = displayed?.entries?.groupBy { it.groupId ?: "url:" + it.url }?.size ?: 0
        count.text = if (amount > 99) "99+" else amount.toString()
        button.visibility = if (!isFullscreen && amount > 0) View.VISIBLE else View.GONE
        if (button.visibility == View.VISIBLE) button.bringToFront()
        if (sheet?.isShowing == true) {
            if (amount == 0 || isFullscreen) sheet?.dismiss() else fillSheet()
        }
    }

    fun setFullscreen(value: Boolean) {
        isFullscreen = value
        render(displayed)
    }

    fun detach() {
        sheet?.dismiss()
        sheet = null
        parent.removeView(button)
    }

    private fun bounds(): FloatArray {
        // browserLayout is already bounded by Nira's host activity. Keep within
        // window visible frame (including IME resize/pan) and toolbar reservations.
        val visible = Rect()
        parent.getWindowVisibleDisplayFrame(visible)
        val location = IntArray(2)
        parent.getLocationOnScreen(location)
        val (topToolbar, bottomToolbar) = safeToolbarSpace()
        val minY = maxOf(padding, visible.top - location[1] + padding, topToolbar + padding).toFloat()
        val windowBottom = (visible.bottom - location[1]).coerceAtMost(parent.height)
        val maxY = maxOf(minY, (windowBottom - size - bottomToolbar - padding).toFloat())
        val minX = padding.toFloat()
        val maxX = maxOf(minX, (parent.width - size - padding).toFloat())
        return floatArrayOf(minX, maxX, minY, maxY)
    }

    private fun place() {
        if (parent.width <= 0 || parent.height <= 0 || dragging) return
        val b = bounds()
        if (!placed) {
            x = if (prefs.contains("x")) b[0] + prefs.getFloat("x", 1f).coerceIn(0f, 1f) * (b[1] - b[0]) else b[1]
            y = if (prefs.contains("y")) b[2] + prefs.getFloat("y", 1f).coerceIn(0f, 1f) * (b[3] - b[2]) else b[3]
            placed = true
        }
        x = x.coerceIn(b[0], b[1])
        y = y.coerceIn(b[2], b[3])
        button.translationX = x
        button.translationY = y
    }

    private fun savePosition() {
        val b = bounds()
        prefs.edit()
            .putFloat("x", if (b[1] > b[0]) ((x - b[0]) / (b[1] - b[0])).coerceIn(0f, 1f) else 1f)
            .putFloat("y", if (b[3] > b[2]) ((y - b[2]) / (b[3] - b[2])).coerceIn(0f, 1f) else 1f)
            .apply()
    }

    private fun handleTouch(event: MotionEvent): Boolean = when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            dragging = false
            downRawX = event.rawX
            downRawY = event.rawY
            startX = x
            startY = y
            button.parent.requestDisallowInterceptTouchEvent(true)
            true
        }
        MotionEvent.ACTION_MOVE -> {
            val dx = event.rawX - downRawX
            val dy = event.rawY - downRawY
            if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) dragging = true
            if (dragging) {
                val b = bounds()
                x = (startX + dx).coerceIn(b[0], b[1])
                y = (startY + dy).coerceIn(b[2], b[3])
                button.translationX = x
                button.translationY = y
            }
            true
        }
        MotionEvent.ACTION_UP -> {
            if (dragging) savePosition() else if (!isFullscreen) showSheet()
            dragging = false
            true
        }
        MotionEvent.ACTION_CANCEL -> {
            dragging = false
            place()
            true
        }
        else -> true
    }

    private fun showSheet() {
        if (current()?.entries.isNullOrEmpty()) return
        sheet?.dismiss()
        val dialog = BottomSheetDialog(context)
        val scroll = ScrollView(context).apply { isFillViewport = false }
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
        }
        scroll.addView(body, FrameLayout.LayoutParams(-1, -2))
        dialog.setContentView(scroll)
        sheet = dialog
        sheetBody = body
        dialog.setOnDismissListener { sheet = null; sheetBody = null }
        fillSheet()
        dialog.show()
    }

    private fun label(text: String, sizeSp: Float, bold: Boolean = false) = TextView(context).apply {
        this.text = text
        textSize = sizeSp
        setTextColor(if (bold) Color.rgb(35, 48, 52) else Color.rgb(83, 93, 100))
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, dp(4), 0, dp(4))
    }


    private fun sizeLabel(entry: MediaEntry): String {
        // HLS/DASH Content-Length would describe only the manifest, not the movie.
        if (entry.format == MediaFormat.HLS || entry.format == MediaFormat.DASH)
            return "rozmiar nieznany"
        val bytes = entry.sizeBytes ?: return "rozmiar nieznany"
        return when {
            bytes >= 1_000_000_000L -> String.format(Locale("pl", "PL"), "%.2f GB", bytes / 1e9)
            bytes >= 1_000_000L -> String.format(Locale("pl", "PL"), "%.1f MB", bytes / 1e6)
            else -> String.format(Locale("pl", "PL"), "%.0f KB", bytes / 1000.0)
        }
    }

    private fun durationLabel(seconds: Int?): String? {
        val duration = seconds ?: return null
        val hours = duration / 3600
        val minutes = (duration % 3600) / 60
        val rest = duration % 60
        return if (hours > 0) String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, rest)
            else String.format(Locale.ROOT, "%d:%02d", minutes, rest)
    }

    private fun friendlyFileName(url: String): String? {
        val raw = try { URI(url).path?.substringAfterLast('/') } catch (_: Exception) { null }
        val part = raw?.substringBeforeLast('.', raw)?.replace(Regex("[_-]+"), " ")?.trim()
            ?: return null
        if (part.length !in 3..65 || !part.any { it.isLetter() } ||
            part.lowercase(Locale.ROOT) in setOf("video", "master", "playlist", "index",
                "stream", "watch", "download", "manifest")) return null
        return part
    }

    private fun originLabel(entry: MediaEntry): String = when {
        MediaSource.NETWORK in entry.sources -> "Potwierdzone odpowiedzią sieciową"
        MediaSource.PAGE in entry.sources -> "Znalezione na stronie"
        else -> "Znalezione w odtwarzaczu"
    }

    private fun fillSheet() {
        val body = sheetBody ?: return
        val snapshot = current()
        body.removeAllViews()
        if (snapshot == null || snapshot.entries.isEmpty()) {
            sheet?.dismiss()
            return
        }
        val groups = snapshot.entries.groupBy { it.groupId ?: "url:" + it.url }
        body.addView(label("Wykryte filmy  •  ${groups.size}", 20f, true))
        body.addView(label("Nazwa i rozmiar pojawiają się, gdy strona lub sieć udostępnia te dane.", 12f))
        groups.values.forEachIndexed { index, found ->
            // Network-confirmed sources first; declarative URLs may be stale,
            // session-bound or even point at a non-playable player endpoint.
            val variants = found.sortedWith(
                compareByDescending<MediaEntry> { MediaSource.NETWORK in it.sources }
                    .thenByDescending { MediaSource.DOM in it.sources }
            )
            val bestTitle = variants.filter { !it.title.isNullOrBlank() }.maxByOrNull {
                when (it.titleKind) {
                    "video" -> 3
                    "link" -> 2
                    "page" -> 1
                    else -> 0
                }
            }
            val name = bestTitle?.title ?: variants.firstNotNullOfOrNull { friendlyFileName(it.url) }
                ?: "Film ${index + 1} — tytuł nieznany"
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(Color.rgb(243, 248, 248))
                }
            }
            val posterUrl = variants.firstNotNullOfOrNull { it.posterUrl }
            if (posterUrl != null) {
                val thumb = ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription = "Miniatura: $name"
                    load(posterUrl) {
                        diskCachePolicy(CachePolicy.DISABLED) // Never persist signed media URLs.
                        crossfade(true)
                    }
                }
                card.addView(thumb, LinearLayout.LayoutParams(-1, dp(156)).apply {
                    bottomMargin = dp(8)
                })
            }
            card.addView(label(name, 18f, true))
            if (bestTitle?.titleKind == "page")
                card.addView(label("Nazwa z tytułu strony — może nie odpowiadać filmowi.", 11f))
            if (variants.size > 1)
                card.addView(label("${variants.size} dostępne źródła", 12f))
            variants.forEachIndexed { sourceIndex, entry ->
                val time = durationLabel(entry.durationSeconds)
                val headline = buildString {
                    if (variants.size > 1) append("Źródło ${sourceIndex + 1}  •  ")
                    append(entry.format.name)
                    if (entry.quality != null) append("  •  ").append(entry.quality)
                    if (entry.bitrate != null) append("  •  ").append(entry.bitrate / 1000).append(" kb/s")
                    append("  •  ")
                    append(sizeLabel(entry))
                    if (time != null) append("  •  ").append(time)
                }
                card.addView(label(headline, 14f, true))
                val host = try { URI(entry.url).host ?: "nieznany serwer" }
                    catch (_: Exception) { "nieznany serwer" }
                card.addView(label("${originLabel(entry)}  •  ${host.take(65)}", 12f))
                if (entry.sources == setOf(MediaSource.PAGE)) {
                    card.addView(label("Adres z deklaracji strony — odtwarzanie w MPV niepotwierdzone.", 11f))
                }
                val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                val open = MaterialButton(context).apply {
                    text = "▶ MPV Alpha"
                    setOnClickListener {
                        val now = current()
                        if (now == null || !sameSelection(snapshot.tab, now.tab) ||
                            now.entries.none { it.id == entry.id && it.url == entry.url }) {
                            stale()
                            fillSheet()
                        } else openVideo(entry.url)
                    }
                }
                actions.addView(open, LinearLayout.LayoutParams(0, -2, 1.2f))
                val copy = MaterialButton(context, null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    text = "Kopiuj"
                    setTextColor(Color.rgb(33, 62, 65))
                    setOnClickListener {
                        val now = current()
                        if (now == null || !sameSelection(snapshot.tab, now.tab) ||
                            now.entries.none { it.id == entry.id && it.url == entry.url }) {
                            stale()
                            fillSheet()
                        } else {
                            val clip = ClipData.newPlainText("Adres źródła wideo", entry.url)
                            if (Build.VERSION.SDK_INT >= 33) {
                                clip.description.extras = PersistableBundle().apply {
                                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                                }
                            }
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                .setPrimaryClip(clip)
                            Toast.makeText(context, "Adres skopiowany — może zawierać token",
                                Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                actions.addView(copy, LinearLayout.LayoutParams(0, -2, 0.8f))
                card.addView(actions, LinearLayout.LayoutParams(-1, -2))
            }
            body.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(12)
            })
        }
    }

    private fun stale() = Toast.makeText(context, "Źródło nie jest już aktualne", Toast.LENGTH_SHORT).show()

    private fun sameSelection(a: MediaTabState, b: MediaTabState): Boolean =
        a.binding.session === b.binding.session && a.binding.tabId == b.binding.tabId &&
            a.binding.contextId == b.binding.contextId && a.binding.isPrivate == b.binding.isPrivate &&
            a.pageUrl == b.pageUrl && a.loading == b.loading
}
