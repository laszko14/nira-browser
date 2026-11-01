package com.prirai.android.nira.browser

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup

/**
 * One-shot, on-device View/window metadata for a persistent landscape rectangle.
 *
 * No screenshot, page content, text, WebView DOM, URL, view text, cookies, IDs of
 * tabs/profiles or private session data. This cannot enumerate other apps' or
 * Android system-owned windows, nor identify Gecko-composited page elements.
 */
internal object OverlayWindowDiagnostics {
    private const val MAX_VISITS = 400
    private const val MAX_DEPTH = 15
    private const val MAX_HITS = 24
    private const val MAX_RAISED = 20

    private data class Node(
        val depth: Int,
        val type: String,
        val resource: String,
        val bounds: Rect,
        val alpha: Float,
        val elevation: Float,
        val backgroundType: String,
        val backgroundOpacity: Int,
        val centerHit: Boolean,
    ) {
        fun line(): String =
            "depth=$depth class=$type id=$resource rect=${bounds.left},${bounds.top},${bounds.right},${bounds.bottom} " +
                "alpha=$alpha elevation=$elevation background=$backgroundType opacity=$backgroundOpacity"
    }

    fun capture(activity: Activity): String {
        val decor = activity.window.decorView
        val offset = IntArray(2)
        decor.getLocationOnScreen(offset)
        val width = decor.width.takeIf { it > 0 } ?: activity.resources.displayMetrics.widthPixels
        val height = decor.height.takeIf { it > 0 } ?: activity.resources.displayMetrics.heightPixels
        // Screenshot reference: upper-right stationary region in landscape.
        // This is only a candidate location, not a claim about the real window.
        val probeX = offset[0] + width * 82 / 100
        val probeY = offset[1] + height * 30 / 100
        val nodes = ArrayList<Node>()
        var visited = 0
        var clipped = false

        fun visit(view: View, depth: Int) {
            if (visited >= MAX_VISITS) { clipped = true; return }
            visited++
            if (depth > MAX_DEPTH || view.visibility != View.VISIBLE || view.alpha <= 0f) return
            val rect = Rect()
            if (!view.getGlobalVisibleRect(rect) || rect.isEmpty) return
            val name = view.javaClass.simpleName.take(55).ifEmpty { "View" }
            val resource = if (view.id == View.NO_ID) "none" else runCatching {
                view.resources.getResourceEntryName(view.id).take(50)
            }.getOrDefault("dynamic")
            val background = view.background
            nodes += Node(
                depth, name, resource, rect, view.alpha, view.elevation,
                background?.javaClass?.simpleName?.take(55) ?: "none",
                background?.opacity ?: -3,
                rect.contains(probeX, probeY)
            )
            // Native app hierarchy only. Gecko renders webpage pixels separately.
            if (view is ViewGroup && !name.contains("Gecko", ignoreCase = true)) {
                for (i in view.childCount - 1 downTo 0) visit(view.getChildAt(i), depth + 1)
            }
        }

        visit(decor, 0)
        val hits = nodes.filter { it.centerHit }.sortedWith(
            compareByDescending<Node> { it.depth }.thenByDescending { it.elevation }
        ).take(MAX_HITS)
        val raised = nodes.filter {
            !it.centerHit && it.depth > 0 && it.elevation > 0f &&
                it.bounds.width() < width && it.bounds.height() < height
        }.sortedByDescending { it.elevation }.take(MAX_RAISED)

        return buildString {
            appendLine("ZAVRO OVERLAY/WINDOW DIAGNOSTICS v1")
            appendLine("scope=APP_DECOR_ONLY; no screenshot, page text, URL or private data")
            appendLine("orientation=" + if (activity.resources.configuration.orientation ==
                Configuration.ORIENTATION_LANDSCAPE) "landscape" else "portrait")
            appendLine("decor_size=${width}x$height sample_point=$probeX,$probeY")
            appendLine("window_type=${activity.window.attributes.type} flags=${activity.window.attributes.flags}")
            appendLine("visited=$visited clipped=$clipped app_decor_hits=${nodes.count { it.centerHit }}")
            appendLine("APP_VIEW_HITS (center of suspected upper-right rectangle):")
            if (hits.isEmpty()) appendLine("none") else hits.forEach { appendLine(it.line()) }
            appendLine("OTHER_RAISED_NATIVE_VIEWS:")
            if (raised.isEmpty()) appendLine("none") else raised.forEach { appendLine(it.line()) }
            appendLine("LIMITATION: system PiP/other-app windows, Android Surface and Gecko pixels cannot be identified here.")
            appendLine("A match is a candidate view, not proof it paints the rectangle.")
        }
    }
}
