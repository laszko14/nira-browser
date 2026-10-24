package com.prirai.android.nira.components.toolbar.modern

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.prirai.android.nira.theme.ThemeManager
import mozilla.components.browser.state.state.TabSessionState

/**
 * A small Android View tab row for the detached BOTTOM strip in portrait.
 *
 * In portrait on some devices the ComposeView has a measured host but paints
 * no tab content. This independently measured native row never depends on the
 * Compose lifecycle or LazyRow constraints. Landscape retains full Compose UI.
 * The caller supplies already profile/private-scoped tabs.
 */
class PortraitNativeTabStrip(context: Context) : LinearLayout(context) {
    private val density = resources.displayMetrics.density
    private fun dp(value: Int): Int = (value * density + 0.5f).toInt()
    private val row = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(6), 0, dp(6), 0)
    }
    private val scroller = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        isFillViewport = true
        overScrollMode = View.OVER_SCROLL_NEVER
    }
    private var lastSignature: List<String>? = null

    var onTabSelected: ((String) -> Unit)? = null
    var onTabClosed: ((String) -> Unit)? = null
    var onNewTab: (() -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val height = dp(40)
        minimumHeight = height
        scroller.addView(row, HorizontalScrollView.LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT
        ))
        addView(scroller, LayoutParams(
            LayoutParams.MATCH_PARENT, height
        ))
    }

    private fun pillBackground(selected: Boolean, dark: Boolean): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(
                if (selected) {
                    if (dark) Color.rgb(87, 49, 72) else Color.rgb(244, 217, 233)
                } else {
                    if (dark) Color.rgb(45, 43, 48) else Color.rgb(239, 237, 242)
                }
            )
            if (selected) setStroke(dp(1), Color.rgb(225, 104, 161))
        }

    private fun label(text: String, width: Int, selected: Boolean, dark: Boolean) =
        TextView(context).apply {
            this.text = text
            gravity = Gravity.CENTER
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(if (dark) Color.WHITE else Color.rgb(30, 28, 34))
            setPadding(dp(8), 0, dp(8), 0)
            background = pillBackground(selected, dark)
            minimumHeight = dp(32)
            layoutParams = LayoutParams(dp(width), dp(32)).apply {
                marginEnd = dp(4)
            }
        }

    /**
     * Rebuild only for a changed tab ID/title/selection snapshot. URL loading
     * events otherwise arrive frequently and must not disrupt user scrolling.
     */
    fun updateTabs(tabs: List<TabSessionState>, selectedId: String?) {
        val signature = buildList {
            add(selectedId.orEmpty())
            tabs.forEach {
                add(it.id)
                add(it.content.title)
                add(it.content.url)
            }
        }
        if (signature == lastSignature) return
        lastSignature = signature
        val dark = ThemeManager.isDarkMode(context)
        val oldScroll = scroller.scrollX
        row.removeAllViews()

        val count = label("(${tabs.size})", 44, false, dark).apply {
            contentDescription = "Karty: ${tabs.size}"
            setOnClickListener {
                // The tab counter does not consume a selection action.
            }
        }
        row.addView(count)

        tabs.forEach { tab ->
            val selected = tab.id == selectedId
            val title = tab.content.title.takeIf { it.isNotBlank() }
                ?: tab.content.url.takeIf { it.isNotBlank() }
                ?: "Nowa karta"
            val tabContainer = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = pillBackground(selected, dark)
                layoutParams = LayoutParams(
                    LayoutParams.WRAP_CONTENT, dp(32)
                ).apply { marginEnd = dp(4) }
                setOnClickListener { onTabSelected?.invoke(tab.id) }
            }
            val titleView = TextView(context).apply {
                text = title
                setTextColor(if (dark) Color.WHITE else Color.rgb(30, 28, 34))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                gravity = Gravity.CENTER_VERTICAL
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(9), 0, dp(4), 0)
                contentDescription = "Karta: $title"
                layoutParams = LayoutParams(dp(92), LayoutParams.MATCH_PARENT)
                setOnClickListener { onTabSelected?.invoke(tab.id) }
            }
            val closeView = TextView(context).apply {
                text = "×"
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
                setTextColor(if (dark) Color.LTGRAY else Color.DKGRAY)
                contentDescription = "Zamknij kartę: $title"
                layoutParams = LayoutParams(dp(32), LayoutParams.MATCH_PARENT)
                setOnClickListener { onTabClosed?.invoke(tab.id) }
            }
            tabContainer.addView(titleView)
            tabContainer.addView(closeView)
            row.addView(tabContainer)
        }

        row.addView(label("+", 40, false, dark).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            contentDescription = "Nowa karta"
            setOnClickListener { onNewTab?.invoke() }
        })

        scroller.post { scroller.scrollTo(oldScroll.coerceAtMost((row.width - scroller.width).coerceAtLeast(0)), 0) }
    }
}
