package com.prirai.android.nira.media

import com.prirai.android.nira.diagnostics.testlab.*

import android.graphics.Color
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.prirai.android.nira.ui.theme.NiraTheme
import java.net.URI
import java.util.Locale

/** A view-layer snapshot; URLs are never logged, serialized or persisted. */
internal data class MediaUiSnapshot(val tab: MediaTabState, val entries: List<MediaEntry>)

/**
 * App-only anchored FAB and tile BottomSheet. No injected HTML, overlay
 * permission, polling or separate video cache. Media URLs stay in process memory.
 */
internal class MediaFloatingUi(
    private val parent: ViewGroup,
    private val current: () -> MediaUiSnapshot?,
    private val safeToolbarSpace: (Rect) -> Pair<Int, Int>,
    private val openVideo: (Long) -> Unit,
    private val queueDownload: (Long) -> Unit,
) {
    private val context = parent.context
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
    private var sheetContent: ComposeView? = null
    private var sheetSnapshot by mutableStateOf<MediaUiSnapshot?>(null)
    private val tilePreferences = VideoTilesPreferences(context)
    private val sheetSizeListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> resizeSheet() }
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
        parent.addOnLayoutChangeListener(sheetSizeListener)
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
        parent.removeOnLayoutChangeListener(sheetSizeListener)
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
        val initial = current()
        if (initial == null || initial.entries.isEmpty()) {
            Toast.makeText(context, "Brak wykrytych filmów", Toast.LENGTH_SHORT).show()
            return
        }
        val capture = TestLab.capture()
        val measurement = capture?.operation(LabOperation.FAB_OPEN)
        sheet?.dismiss()
        sheetSnapshot = initial
        initial.entries.forEach { TestLab.mediaDisplayed(initial.tab.binding, it) }
        val dialog = BottomSheetDialog(context)
        val bounds = Rect()
        parent.getWindowVisibleDisplayFrame(bounds)
        val maxHeight = (bounds.height() - dp(24)).coerceAtLeast(dp(120))
        val content = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxHeight)
            setContent {
                NiraTheme(darkTheme = true, amoledMode = false, dynamicColor = false) {
                    val landscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    val savedStates = rememberSaveableStateHolder()
                    savedStates.SaveableStateProvider(landscape) {
                        var settings by remember { mutableStateOf(tilePreferences.load(landscape)) }
                        val snapshot = sheetSnapshot
                        val items = remember(snapshot) { tileItems(snapshot?.entries.orEmpty()) }
                        VideoTilesPanel(items, settings,
                            onSettingsChange = { value ->
                                settings = value
                                tilePreferences.save(landscape, value)
                            },
                            onPlay = { id ->
                                val now = current()
                                if (snapshot == null || now == null || !sameSelection(snapshot.tab, now.tab) ||
                                    now.entries.none { it.id == id }) {
                                    stale()
                                    fillSheet()
                                } else {
                                    val token = TestLab.operation(LabOperation.VIDEO_EXTERNAL_REQUEST)
                                    TestLab.note(LabCode.VIDEO_EXTERNAL_REQUEST)
                                    try {
                                        openVideo(id)
                                        TestLab.end(token)
                                    } catch (error: Throwable) {
                                        TestLab.end(token, OperationOutcome.FAILED)
                                        throw error
                                    }
                                }
                            },
                            onDownload = { id ->
                                val latest = current()
                                if (snapshot == null || latest == null ||
                                    !sameSelection(snapshot.tab, latest.tab) ||
                                    latest.entries.none { it.id == id }) {
                                    stale()
                                    fillSheet()
                                } else {
                                    val entry = latest.entries.first { it.id == id }
                                    if (entry.format in setOf(MediaFormat.HLS, MediaFormat.DASH)) {
                                        Toast.makeText(context,
                                            "HLS/DASH wymaga pobrania całego strumienia, nie samego manifestu",
                                            Toast.LENGTH_LONG).show()
                                    } else {
                                        val op = TestLab.operation(LabOperation.VIDEO_MENU)
                                        try {
                                            queueDownload(id)
                                            TestLab.end(op)
                                        } catch (error: Exception) {
                                            TestLab.end(op, OperationOutcome.FAILED)
                                            throw error
                                        }
                                    }
                                }
                            },
                            onCopy = { id ->
                                val latest = current()
                                if (snapshot == null || latest == null ||
                                    !sameSelection(snapshot.tab, latest.tab) ||
                                    latest.entries.none { it.id == id }) {
                                    stale()
                                    fillSheet()
                                } else {
                                    val url = latest.entries.first { it.id == id }.url
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Adres filmu", url))
                                }
                            },
                            onClose = { dialog.dismiss() },
                        )
                    }
                }
            }
        }
        dialog.setContentView(content)
        sheet = dialog
        sheetContent = content
        dialog.setOnDismissListener {
            dialog.window?.let {
                TestLab.detachWindow(it)
            }
            capture?.end(measurement, OperationOutcome.INTERRUPTED)
            content.disposeComposition()
            if (sheet === dialog) {
                sheet = null
                sheetContent = null
                sheetSnapshot = null
            }
        }
        dialog.setOnShowListener {
            val sheetView = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                ?: return@setOnShowListener
            resizeSheet()
            val behavior = BottomSheetBehavior.from(sheetView)
            behavior.skipCollapsed = true
            behavior.isFitToContents = true
            // Content owns vertical overflow; a sheet drag must not swallow the tenth-row scroll.
            behavior.isDraggable = false
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
        dialog.show()
        // The video sheet has its own Android Window. Request the same supported
        // high-refresh rate as BrowserActivity, without touching Gecko or video FPS.
        requestSheetRefreshRate(dialog)
        if (measurement != null) {
            dialog.window?.let(TestLab::attachWindow)
            androidx.core.view.OneShotPreDrawListener.add(content) {
                capture?.end(measurement, proof = CompletionProof.FIRST_PREDRAW)
            }
        }
    }

    private fun requestSheetRefreshRate(dialog: BottomSheetDialog) {
        val window = dialog.window ?: return
        val display = window.decorView.display ?: return
        val mode = display.mode
        val rates = display.supportedModes.filter {
            it.physicalWidth == mode.physicalWidth && it.physicalHeight == mode.physicalHeight
        }.map { it.refreshRate }
        val chosen = com.prirai.android.nira.browser.HighRefreshRatePolicy.choose(rates) ?: return
        val attributes = window.attributes
        if (attributes.preferredDisplayModeId != 0 ||
            kotlin.math.abs(attributes.preferredRefreshRate - chosen) > 0.1f) {
            attributes.preferredDisplayModeId = 0
            attributes.preferredRefreshRate = chosen
            window.attributes = attributes
        }
        if (android.os.Build.VERSION.SDK_INT >= 35) {
            window.decorView.setRequestedFrameRate(chosen)
        }
    }

    private fun resizeSheet() {
        val dialog = sheet ?: return
        val content = sheetContent ?: return
        val bounds = Rect()
        parent.getWindowVisibleDisplayFrame(bounds)
        val height = (bounds.height() - dp(24)).coerceAtLeast(dp(120))
        val sheetView = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
        if (content.layoutParams.height != height) {
            content.layoutParams = content.layoutParams.apply { this.height = height }
        }
        if (sheetView != null && sheetView.layoutParams.height != height) {
            sheetView.layoutParams = sheetView.layoutParams.apply { this.height = height }
        }
    }

    private fun fillSheet() {
        // Update the existing composition. Stable item keys and grid state survive metadata changes.
        sheetSnapshot = current()
        val latest = sheetSnapshot
        latest?.entries?.forEach { TestLab.mediaDisplayed(latest.tab.binding, it) }
    }

    private fun tileItems(entries: List<MediaEntry>): List<VideoTileItem> = mediaTileItems(entries)

    private fun stale() = Toast.makeText(context, "Źródło nie jest już aktualne", Toast.LENGTH_SHORT).show()

    private fun sameSelection(a: MediaTabState, b: MediaTabState): Boolean =
        a.binding.session === b.binding.session && a.binding.tabId == b.binding.tabId &&
            a.binding.contextId == b.binding.contextId && a.binding.isPrivate == b.binding.isPrivate &&
            a.pageUrl == b.pageUrl && a.loading == b.loading
}

// Pure mapping for unit tests: grouping, identity, and titles must stay coupled.
internal fun mediaTileItems(entries: List<MediaEntry>): List<VideoTileItem> =
    // Group IDs are minted independently per Gecko document/frame.
    // "g1" inside iframe A must not absorb "g1" inside iframe B.
    entries.groupBy { it.frameKey to (it.groupId ?: "url:${it.url}") }
        .values.mapIndexed { index, found ->
        val variants = found.sortedWith(compareByDescending<MediaEntry> { MediaSource.NETWORK in it.sources }
            .thenByDescending { MediaSource.DOM in it.sources })
        // Same resolver decides both record-level conflicts and grouped variants.
        val bestTitle = MediaTitleResolver.selectGroup(variants, entries.size == 1).text
        val title = bestTitle ?: variants.firstNotNullOfOrNull { friendlyFileName(it.url) }
            ?: "Film ${index + 1}"
        VideoTileItem(found.minOf { it.id }, variants.first(), title, variants.size)
    }

private fun friendlyFileName(url: String): String? {
    val raw = try { URI(url).path?.substringAfterLast('/') } catch (_: Exception) { null }
    val part = raw?.substringBeforeLast('.', raw)?.replace(Regex("[_-]+"), " ")?.trim() ?: return null
    if (part.length !in 3..65 || !part.any { it.isLetter() } ||
        part.lowercase(Locale.ROOT) in setOf("video", "master", "playlist", "index", "stream", "watch", "download", "manifest")) return null
    return part
}

