package com.prirai.android.nira.media

import com.prirai.android.nira.diagnostics.testlab.TestLab
import com.prirai.android.nira.diagnostics.testlab.LabOperation
import com.prirai.android.nira.diagnostics.testlab.LabCode
import com.prirai.android.nira.diagnostics.testlab.OperationOutcome

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
import com.google.android.material.bottomsheet.BottomSheetBehavior
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
    private val safeToolbarSpace: (Rect) -> Pair<Int, Int>,
    private val openVideo: (Long) -> Unit,
    private val queueDownload: (Long) -> Unit,
) {
    private val context = parent.context
    private val gridPrefs = context.getSharedPreferences("zavro_media_grid", Context.MODE_PRIVATE)
    private var gridSettingsExpanded = false
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density + 0.5f).toInt()
    private val size = dp(56)
    private val padding = dp(16)
    private val button = FrameLayout(context).apply {
        background = circle(Color.rgb(126, 202, 196))
        elevation = dp(8).toFloat()
        contentDescription = "Wykryte filmy"
        isClickable = true
        isFocusable = true
        visibility = View.VISIBLE
    }
    private val count = TextView(context).apply {
        gravity = Gravity.CENTER
        textSize = 16f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setTextColor(Color.WHITE)
        setPadding(dp(2), 0, dp(2), 0)
        background = circle(Color.rgb(255, 12, 17))
        elevation = dp(2).toFloat()
    }
    private lateinit var downloadGlyph: View
    private var isFullscreen = false
    private var displayed: MediaUiSnapshot? = null
    private var sheet: BottomSheetDialog? = null
    private var sheetBody: LinearLayout? = null
    private val placementListener = android.view.ViewTreeObserver.OnPreDrawListener {
        place()
        true
    }

    init {
        // Same two-state appearance as the supplied 1DM reference:
        // white filled arrow/tray when empty; centered red counter when detected.
        downloadGlyph = object : View(context) {
            private val ink = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = android.graphics.Paint.Style.FILL
            }
            override fun onDraw(canvas: android.graphics.Canvas) {
                super.onDraw(canvas)
                val cx = width / 2f
                val cy = height / 2f
                val arrow = android.graphics.Path().apply {
                    moveTo(cx - dp(3).toFloat(), cy - dp(15).toFloat())
                    lineTo(cx + dp(3).toFloat(), cy - dp(15).toFloat())
                    lineTo(cx + dp(3).toFloat(), cy + dp(1).toFloat())
                    lineTo(cx + dp(9).toFloat(), cy + dp(1).toFloat())
                    lineTo(cx, cy + dp(11).toFloat())
                    lineTo(cx - dp(9).toFloat(), cy + dp(1).toFloat())
                    lineTo(cx - dp(3).toFloat(), cy + dp(1).toFloat())
                    close()
                }
                canvas.drawPath(arrow, ink)
                canvas.drawRoundRect(
                    android.graphics.RectF(cx - dp(13), cy + dp(11), cx + dp(13), cy + dp(17)),
                    dp(1).toFloat(), dp(1).toFloat(), ink
                )
            }
        }
        button.addView(downloadGlyph, FrameLayout.LayoutParams(-1, -1))
        button.addView(count, FrameLayout.LayoutParams(dp(29), dp(29), Gravity.CENTER))
        parent.addView(button, ViewGroup.LayoutParams(size, size))
        button.setOnClickListener { showSheet() }
        // Chrome visibility/translation and IME insets can change without resizing parent.
        parent.viewTreeObserver.addOnPreDrawListener(placementListener)
    }

    private fun circle(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    fun render(value: MediaUiSnapshot?) {
        displayed = value?.takeIf { it.entries.isNotEmpty() }
        val amount = displayed?.entries?.groupBy { it.groupId ?: "url:" + it.url }?.size ?: 0
        count.text = if (amount > 99) "99+" else amount.toString()
        count.visibility = if (amount > 0) View.VISIBLE else View.GONE
        downloadGlyph.visibility = if (amount > 0) View.GONE else View.VISIBLE
        button.visibility = if (!isFullscreen) View.VISIBLE else View.GONE
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
        if (parent.viewTreeObserver.isAlive) {
            parent.viewTreeObserver.removeOnPreDrawListener(placementListener)
        }
        parent.removeView(button)
    }

    private fun bounds(): FloatArray? {
        // browserLayout is already bounded by Nira's host activity. Keep within
        // window visible frame (including IME resize/pan) and toolbar reservations.
        val visible = Rect()
        parent.getWindowVisibleDisplayFrame(visible)
        val location = IntArray(2)
        parent.getLocationOnScreen(location)
        visible.offset(-location[0], -location[1])
        if (!visible.intersect(0, 0, parent.width, parent.height)) return null
        val (topToolbar, bottomToolbar) = safeToolbarSpace(visible)
        val minY = maxOf(padding, visible.top + padding, topToolbar + padding).toFloat()
        val windowBottom = visible.bottom.coerceAtMost(parent.height)
        val maxY = maxOf(minY, (windowBottom - size - bottomToolbar - padding).toFloat())
        val minX = (visible.left + padding).toFloat()
        val maxX = maxOf(minX, (visible.right - size - padding).toFloat())
        return floatArrayOf(minX, maxX, minY, maxY)
    }

    // Always dock at bottom-right. Never persist or restore finger-dragged offsets.
    private fun place() {
        if (parent.width <= 0 || parent.height <= 0) return
        val b = bounds() ?: return
        if (button.x != b[1]) button.x = b[1]
        if (button.y != b[3]) button.y = b[3]
    }

    private fun showSheet() {
        if (current()?.entries.isNullOrEmpty()) {
            Toast.makeText(context, "Brak wykrytych filmów", Toast.LENGTH_SHORT).show()
            return
        }
        sheet?.dismiss()
        val dialog = BottomSheetDialog(context)
        val scroll = ScrollView(context).apply {
            isFillViewport = false
            setBackgroundColor(Color.rgb(63, 63, 63))
        }
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(24))
        }
        scroll.addView(body, FrameLayout.LayoutParams(-1, -2))
        dialog.setContentView(scroll)
        sheet = dialog
        sheetBody = body
        dialog.setOnDismissListener { sheet = null; sheetBody = null }
        // Force a fully expanded, content-sized sheet on the very first tap.
        // With one video there is no wasted full-screen blank area; with many,
        // ScrollView owns overflow while the sheet fills the available height.
        dialog.setOnShowListener {
            val sheetView = dialog.findViewById<FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet
            ) ?: return@setOnShowListener
            val bounds = Rect()
            parent.getWindowVisibleDisplayFrame(bounds)
            val maxHeight = (bounds.height() - dp(24)).coerceAtLeast(dp(180))
            val sheetWidth = (parent.width - dp(28)).coerceAtLeast(dp(240))
            body.measure(
                View.MeasureSpec.makeMeasureSpec(sheetWidth, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            sheetView.layoutParams = sheetView.layoutParams.apply {
                height = body.measuredHeight.coerceIn(dp(130), maxHeight)
            }
            sheetView.requestLayout()
            val behavior = BottomSheetBehavior.from(sheetView)
            behavior.skipCollapsed = true
            behavior.isFitToContents = true
            sheetView.post { behavior.state = BottomSheetBehavior.STATE_EXPANDED }
        }
        fillSheet()
        val lab = TestLab.operation(LabOperation.VIDEO_PANEL)
        dialog.show()
        TestLab.end(lab)
        TestLab.note(LabCode.VIDEO_PANEL_OPEN)
    }

    private fun label(text: String, sizeSp: Float, bold: Boolean = false) = TextView(context).apply {
        this.text = text
        textSize = sizeSp
        setTextColor(if (bold) Color.WHITE else Color.rgb(206, 206, 206))
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, dp(4), 0, dp(4))
    }


    private fun sizeLabel(entry: MediaEntry): String {
        // HLS/DASH Content-Length would describe only the manifest, not the movie.
        if (entry.format == MediaFormat.HLS || entry.format == MediaFormat.DASH)
            return "...MB"
        val bytes = entry.sizeBytes ?: return "...MB"
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
        // Portrait and landscape keep independent, locally stored grid geometry.
        val suffix = if (context.resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE) "landscape" else "portrait"
        val rowsKey = "rows_" + suffix
        val widthKey = "tile_width_" + suffix
        val rowLimit = gridPrefs.getInt(rowsKey, 3).coerceIn(1, 10)
        val tileWidth = gridPrefs.getInt(widthKey, 290).coerceIn(220, 420)
        val toolbar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        toolbar.addView(label("Wykryte filmy • ${groups.size}", 15f, true),
            LinearLayout.LayoutParams(0, -2, 1f))
        toolbar.addView(TextView(context).apply {
            text = "⚙"
            textSize = 21f
            gravity = Gravity.CENTER
            contentDescription = "Ustawienia siatki wykrytych filmów"
            setTextColor(Color.WHITE)
            setOnClickListener { gridSettingsExpanded = !gridSettingsExpanded; fillSheet() }
        }, LinearLayout.LayoutParams(dp(45), dp(40)))
        body.addView(toolbar)
        if (gridSettingsExpanded) {
            body.addView(label("Rzędy: $rowLimit (1–10)", 12f))
            body.addView(android.widget.SeekBar(context).apply {
                max = 9
                progress = rowLimit - 1
                setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                    override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                    override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {}
                    override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                        gridPrefs.edit().putInt(rowsKey, (seekBar?.progress ?: 0) + 1).apply()
                        fillSheet()
                    }
                })
            })
            body.addView(label("Szerokość kafelka: $tileWidth dp", 12f))
            body.addView(android.widget.SeekBar(context).apply {
                max = 20
                progress = (tileWidth - 220) / 10
                setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                    override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                    override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {}
                    override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                        gridPrefs.edit().putInt(widthKey, 220 + (seekBar?.progress ?: 7) * 10).apply()
                        fillSheet()
                    }
                })
            })
        }
        // The horizontal viewport keeps 1–10 complete rows accessible even on small screens.
        val horizontal = android.widget.HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = true
            isFillViewport = false
        }
        val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val gridRows = (0 until minOf(rowLimit, groups.size)).map {
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
                grid.addView(this, LinearLayout.LayoutParams(-2, -2))
            }
        }
        horizontal.addView(grid, android.widget.FrameLayout.LayoutParams(-2, -2))
        body.addView(horizontal, LinearLayout.LayoutParams(-1, -2))
        groups.values.forEachIndexed { index, found ->
            // Prefer network-confirmed playable URLs; keep the current stream ID until the user chooses an external player.
            val variants = found.sortedWith(
                compareByDescending<MediaEntry> { MediaSource.NETWORK in it.sources }
                    .thenByDescending { MediaSource.DOM in it.sources }
            )
            val entry = variants.first()
            val bestTitle = variants.filter { !it.title.isNullOrBlank() }.maxByOrNull {
                when (it.titleKind) {
                    "video" -> 3
                    "link" -> 2
                    "page" -> 1
                    else -> 0
                }
            }
            val name = bestTitle?.title ?: variants.firstNotNullOfOrNull { friendlyFileName(it.url) }
                ?: "Film ${index + 1}"
            val item = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(12), dp(4), dp(12))
                background = GradientDrawable().apply {
                    cornerRadius = dp(5).toFloat()
                    setColor(Color.rgb(65, 65, 65))
                }
            }
            val iconColumn = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            }
            // Draw a simple 1DM-style film reel icon locally. No poster loading.
            iconColumn.addView(object : View(context) {
                private val ink = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                }
                override fun onDraw(canvas: android.graphics.Canvas) {
                    super.onDraw(canvas)
                    val cx = width / 2f
                    val cy = height / 2f - dp(2)
                    val radius = dp(15).toFloat()
                    ink.style = android.graphics.Paint.Style.STROKE
                    ink.strokeWidth = dp(3).toFloat()
                    canvas.drawCircle(cx, cy, radius, ink)
                    ink.style = android.graphics.Paint.Style.FILL
                    for (a in 0..3) {
                        val angle = Math.PI * a / 2.0
                        val dx = kotlin.math.cos(angle).toFloat() * dp(8)
                        val dy = kotlin.math.sin(angle).toFloat() * dp(8)
                        canvas.drawCircle(cx + dx, cy + dy, dp(3).toFloat(), ink)
                    }
                    canvas.drawCircle(cx, cy, dp(3).toFloat(), ink)
                    canvas.drawRoundRect(
                        android.graphics.RectF(cx + dp(9), cy + dp(11), cx + dp(20), cy + dp(17)),
                        dp(2).toFloat(), dp(2).toFloat(), ink
                    )
                }
            }.apply { contentDescription = "Ikona filmu" },
                LinearLayout.LayoutParams(dp(62), dp(42)))
            iconColumn.addView(label(sizeLabel(entry), 11f).apply {
                gravity = Gravity.CENTER
                maxLines = 1
            }, LinearLayout.LayoutParams(dp(76), -2))
            item.addView(iconColumn, LinearLayout.LayoutParams(dp(80), -2))
            val details = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(5), 0, dp(6), 0)
            }
            details.addView(label(name, 15f, true).apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            val technical = buildList {
                add(entry.format.name)
                entry.quality?.takeIf { it.isNotBlank() }?.let { add(it) }
                entry.bitrate?.let { add("${it / 1000} kb/s") }
                if (variants.size > 1) add("${variants.size} źródła")
            }.joinToString("  •  ")
            details.addView(label(technical, 12f))
            details.addView(label(durationLabel(entry.durationSeconds) ?: "Czas nieznany", 12f))
            item.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
            val playable = entry.playbackStatus !in setOf(
                MediaPlaybackStatus.EXPIRED, MediaPlaybackStatus.AUTH_ERROR, MediaPlaybackStatus.UNPLAYABLE
            )
            item.addView(TextView(context).apply {
                text = "▶"
                textSize = 23f
                background = GradientDrawable().apply {
                    cornerRadius = dp(6).toFloat()
                    setColor(Color.TRANSPARENT)
                    setStroke(dp(1), Color.rgb(190, 190, 190))
                }
                gravity = Gravity.CENTER
                setTextColor(if (playable) Color.WHITE else Color.GRAY)
                contentDescription = "Otwórz w odtwarzaczu"
                isEnabled = playable
                setOnClickListener {
                    val now = current()
                    if (now == null || !sameSelection(snapshot.tab, now.tab) ||
                        now.entries.none { it.id == entry.id }) {
                        stale()
                        fillSheet()
                    } else {
                        val lab = TestLab.operation(LabOperation.VIDEO_EXTERNAL_OPEN)
                        TestLab.note(LabCode.VIDEO_EXTERNAL_REQUEST)
                        openVideo(entry.id)
                        // Native callback dispatched; third-party playback itself remains unverified.
                        TestLab.end(lab, OperationOutcome.UNKNOWN)
                    }
                }
            }, LinearLayout.LayoutParams(dp(48), dp(58)))
            val options = TextView(context).apply {
                text = "⋮"
                textSize = 23f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                contentDescription = "Opcje filmu: pobieranie i kolejka"
                setOnClickListener { anchor ->
                    val now = current()
                    if (now == null || !sameSelection(snapshot.tab, now.tab) ||
                        now.entries.none { it.id == entry.id }) {
                        stale()
                        fillSheet()
                    } else {
                        android.widget.PopupMenu(context, anchor).apply {
                            menu.add(0, 1, 0, "Pobierz / dodaj do kolejki")
                                .isEnabled = entry.format !in setOf(MediaFormat.HLS, MediaFormat.DASH)
                            menu.add(0, 2, 1, "Kopiuj adres filmu")
                            setOnMenuItemClickListener { menuItem ->
                                val latest = current()
                                val available = latest != null && sameSelection(snapshot.tab, latest.tab) &&
                                    latest.entries.any { it.id == entry.id }
                                if (!available) {
                                    stale()
                                    fillSheet()
                                    true
                                } else when (menuItem.itemId) {
                                    1 -> { queueDownload(entry.id); true }
                                    2 -> {
                                        val clip = android.content.ClipData.newPlainText(
                                            "Adres filmu", latest!!.entries.first { it.id == entry.id }.url)
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                            as android.content.ClipboardManager
                                        clipboard.setPrimaryClip(clip)
                                        true
                                    }
                                    else -> false
                                }
                            }
                            val lab = TestLab.operation(LabOperation.VIDEO_MENU)
                            show()
                            TestLab.end(lab)
                            TestLab.note(LabCode.VIDEO_MENU_OPEN)
                        }
                    }
                }
            }
            item.addView(options, LinearLayout.LayoutParams(dp(33), dp(54)))
            gridRows[index % gridRows.size].addView(item,
                LinearLayout.LayoutParams(dp(tileWidth), -2).apply {
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                })
        }
    }

    private fun stale() = Toast.makeText(context, "Źródło nie jest już aktualne", Toast.LENGTH_SHORT).show()

    private fun sameSelection(a: MediaTabState, b: MediaTabState): Boolean =
        a.binding.session === b.binding.session && a.binding.tabId == b.binding.tabId &&
            a.binding.contextId == b.binding.contextId && a.binding.isPrivate == b.binding.isPrivate &&
            a.pageUrl == b.pageUrl && a.loading == b.loading
}
