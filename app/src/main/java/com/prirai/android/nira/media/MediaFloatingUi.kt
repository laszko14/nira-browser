package com.prirai.android.nira.media

import com.prirai.android.nira.diagnostics.testlab.*
import com.prirai.android.nira.BrowserApp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy

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
    private val videoTiles = mutableStateOf<MediaUiSnapshot?>(null)
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
            if (amount == 0 || isFullscreen) sheet?.dismiss() else videoTiles.value = value
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
        val opened = current()
        if (opened == null || opened.entries.isEmpty()) {
            Toast.makeText(context, "Brak wykrytych filmów", Toast.LENGTH_SHORT).show()
            return
        }
        val capture = TestLab.capture()
        val measurement = capture?.operation(LabOperation.FAB_OPEN)
        sheet?.dismiss()
        videoTiles.value = opened
        val dialog = BottomSheetDialog(context)
        val compose = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                DetectedVideoTiles(
                    snapshot = videoTiles.value,
                    onPlay = { streamId ->
                        val fresh = current()
                        val entry = fresh?.entries?.firstOrNull { it.id == streamId }
                        if (fresh == null || !sameSelection(opened.tab, fresh.tab) ||
                            entry == null || entry.playbackStatus in setOf(
                                MediaPlaybackStatus.EXPIRED, MediaPlaybackStatus.AUTH_ERROR,
                                MediaPlaybackStatus.UNPLAYABLE
                            )) {
                            stale()
                            videoTiles.value = fresh
                        } else {
                            openVideo(streamId)
                        }
                    },
                    onClose = { dialog.dismiss() }
                )
            }
        }
        dialog.setContentView(compose)
        sheet = dialog
        dialog.setOnDismissListener {
            dialog.window?.let { window ->
                (context.applicationContext as? BrowserApp)?.displayPolicy?.detachDialog(window)
                TestLab.detachWindow(window)
            }
            capture?.end(measurement, OperationOutcome.INTERRUPTED)
            sheet = null
            videoTiles.value = null
        }
        dialog.setOnShowListener {
            val sheetView = dialog.findViewById<FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet
            ) ?: return@setOnShowListener
            val bounds = Rect()
            parent.getWindowVisibleDisplayFrame(bounds)
            val maxHeight = (bounds.height() - dp(24)).coerceAtLeast(dp(180))
            val behavior = BottomSheetBehavior.from(sheetView)
            behavior.skipCollapsed = true
            behavior.isFitToContents = true
            // Compose content can grow to 10 rows while header remains pinned.
            compose.post {
                sheetView.layoutParams = sheetView.layoutParams.apply {
                    height = compose.measuredHeight.coerceIn(dp(170), maxHeight)
                }
                sheetView.requestLayout()
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
            dialog.window?.let { window ->
                (context.applicationContext as? BrowserApp)?.displayPolicy?.attachDialog(window)
            }
        }
        dialog.show()
        if (measurement != null) {
            dialog.window?.let(TestLab::attachWindow)
            androidx.core.view.OneShotPreDrawListener.add(compose) {
                capture?.end(measurement, proof = CompletionProof.FIRST_PREDRAW)
            }
        }
    }

    private fun stale() = Toast.makeText(context, "Źródło nie jest już aktualne", Toast.LENGTH_SHORT).show()

    private fun sameSelection(a: MediaTabState, b: MediaTabState): Boolean =
        a.binding.session === b.binding.session && a.binding.tabId == b.binding.tabId &&
            a.binding.contextId == b.binding.contextId && a.binding.isPrivate == b.binding.isPrivate &&
            a.pageUrl == b.pageUrl && a.loading == b.loading
}
