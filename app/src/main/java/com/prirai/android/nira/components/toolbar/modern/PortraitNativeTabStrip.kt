package com.prirai.android.nira.components.toolbar.modern

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import com.prirai.android.nira.theme.ThemeManager
import com.prirai.android.nira.utils.FaviconLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.TabSessionState

/**
 * Native fallback for the detached BOTTOM tab strip in portrait.
 *
 * Landscape still uses the fully featured Compose bar. Portrait cannot rely
 * on that Compose host painting reliably on some devices, so it renders the
 * same compact favicon-only controls in Android Views.
 *
 * BrowserStore has already filtered tabs for the active profile/private mode.
 */
class PortraitNativeTabStrip(context: Context) : LinearLayout(context) {
    private val density = resources.displayMetrics.density
    private fun dp(value: Int): Int = (value * density + 0.5f).toInt()

    private val barHeight = dp(
        com.prirai.android.nira.preferences.UserPreferences(context)
            .niraTabStripHeightDp.coerceIn(40, 80)
    )
    private val pillHeight = (barHeight - dp(4)).coerceIn(dp(32), dp(42))
    private val row = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(5), 0, dp(5), 0)
    }
    private val scroller = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        isFillViewport = false
        overScrollMode = View.OVER_SCROLL_NEVER
        clipToPadding = false
    }
    private var iconScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastSignature: List<String>? = null
    private var lastTabs: List<TabSessionState> = emptyList()
    private var lastSelectedId: String? = null

    var onTabSelected: ((String) -> Unit)? = null
    var onTabClosed: ((String) -> Unit)? = null
    var onNewTab: (() -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = barHeight
        clipChildren = false
        clipToPadding = false

        // A single small capsule, just like the landscape compact bar.
        // Do not stretch the pill background across the entire screen.
        row.background = GradientDrawable().apply {
            cornerRadius = dp(13).toFloat()
            setColor(if (ThemeManager.isDarkMode(context))
                Color.rgb(76, 42, 61) else Color.rgb(244, 217, 233))
        }
        scroller.addView(row, FrameLayout.LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT
        ))
        addView(scroller, LinearLayout.LayoutParams(
            LayoutParams.WRAP_CONTENT, pillHeight
        ))
    }

    override fun onDetachedFromWindow() {
        iconScope.cancel()
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!iconScope.isActive) {
            iconScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            lastSignature = null
            updateTabs(lastTabs, lastSelectedId)
        }
    }

    private fun tabBackground(selected: Boolean, dark: Boolean): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(
                if (selected) {
                    if (dark) Color.rgb(98, 47, 76) else Color.rgb(251, 231, 242)
                } else {
                    Color.TRANSPARENT
                }
            )
            if (selected) setStroke(dp(1), Color.rgb(227, 96, 157))
        }

    private fun actionText(text: String, width: Int, sizeSp: Float, dark: Boolean): TextView =
        TextView(context).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sizeSp)
            setTextColor(if (dark) Color.rgb(231, 108, 167) else Color.rgb(145, 42, 98))
            layoutParams = LinearLayout.LayoutParams(dp(width), pillHeight)
        }

    private fun tabTitle(tab: TabSessionState): String =
        tab.content.title.takeIf { it.isNotBlank() }
            ?: tab.content.url.takeIf { it.isNotBlank() }
            ?: "Nowa karta"

    private fun openTabChooser(anchor: View) {
        PopupMenu(context, anchor).apply {
            lastTabs.forEachIndexed { index, tab ->
                val caption = tabTitle(tab).take(48)
                menu.add(if (tab.id == lastSelectedId) "✓ ${index + 1}. $caption"
                    else "${index + 1}. $caption").setOnMenuItemClickListener {
                    onTabSelected?.invoke(tab.id)
                    true
                }
            }
            menu.add("Nowa karta").setOnMenuItemClickListener {
                onNewTab?.invoke()
                true
            }
            show()
        }
    }

    private fun openTabActions(anchor: View, tab: TabSessionState) {
        PopupMenu(context, anchor).apply {
            menu.add("Przejdź do karty").setOnMenuItemClickListener {
                onTabSelected?.invoke(tab.id)
                true
            }
            menu.add("Zamknij kartę").setOnMenuItemClickListener {
                onTabClosed?.invoke(tab.id)
                true
            }
            show()
        }
    }

    private fun bindFavicon(image: ImageView, tab: TabSessionState) {
        val url = tab.content.url
        val bitmap = tab.content.icon ?: FaviconLoader.getFromMemorySync(context, url)
        if (bitmap != null) {
            image.setImageBitmap(bitmap)
            return
        }
        image.setImageResource(android.R.drawable.ic_menu_compass)
        if (!url.startsWith("http://") && !url.startsWith("https://")) return

        // Never block the main thread or reassign an icon to a recycled tab.
        val requestKey = tab.id to url
        image.tag = requestKey
        iconScope.launch {
            val loaded = FaviconLoader.loadFavicon(context, url)
            if (image.tag == requestKey && image.parent != null && loaded != null) {
                image.setImageBitmap(loaded)
            }
        }
    }

    /**
     * A compact strip always contains: chevron, count, favicon pills and '+'.
     * URL/icon/title/selection changes are considered; otherwise the user's
     * horizontal scroll position is preserved instead of rebuilding the row.
     */
    fun updateTabs(tabs: List<TabSessionState>, selectedId: String?) {
        lastTabs = tabs
        val previousSelectedId = lastSelectedId
        lastSelectedId = selectedId
        val signature = buildList {
            add(selectedId.orEmpty())
            add(ThemeManager.isDarkMode(context).toString())
            tabs.forEach { tab ->
                add(tab.id)
                add(tab.content.url)
                add(tab.content.title)
                add(tab.content.icon?.let { System.identityHashCode(it) }.toString())
            }
        }
        if (signature == lastSignature) return
        lastSignature = signature

        val dark = ThemeManager.isDarkMode(context)
        val oldScroll = scroller.scrollX
        row.removeAllViews()

        row.addView(actionText("⌃", 21, 19f, dark).apply {
            contentDescription = "Wybierz kartę"
            setOnClickListener { openTabChooser(this) }
        })
        row.addView(actionText("(${tabs.size})", 36, 13f, dark).apply {
            contentDescription = "Karty: ${tabs.size}"
            setOnClickListener { openTabChooser(this) }
        })

        var selectedPill: View? = null
        tabs.forEach { tab ->
            val selected = tab.id == selectedId
            val title = tabTitle(tab)
            val tabContainer = FrameLayout(context).apply {
                background = tabBackground(selected, dark)
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(34)).apply {
                    marginEnd = dp(3)
                }
                contentDescription = "Karta: $title"
                isClickable = true
                isFocusable = true
                setOnClickListener { onTabSelected?.invoke(tab.id) }
                setOnLongClickListener {
                    openTabActions(this, tab)
                    true
                }
            }
            val image = ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "Ikona strony: $title"
            }
            tabContainer.addView(image, FrameLayout.LayoutParams(
                dp(23), dp(23), Gravity.CENTER
            ))
            bindFavicon(image, tab)
            row.addView(tabContainer)
            if (selected) selectedPill = tabContainer
        }

        row.addView(actionText("+", 34, 24f, dark).apply {
            contentDescription = "Nowa karta"
            setOnClickListener { onNewTab?.invoke() }
        })

        val focused = selectedPill
        scroller.post {
            val maxScroll = (row.width - scroller.width).coerceAtLeast(0)
            var newScroll = oldScroll.coerceIn(0, maxScroll)
            if (focused != null && previousSelectedId != selectedId) {
                // Keep the selected favicon visible without moving the strip
                // on every title/loading update.
                if (focused.left < newScroll) newScroll = focused.left
                if (focused.right > newScroll + scroller.width) {
                    newScroll = focused.right - scroller.width
                }
            }
            scroller.scrollTo(newScroll.coerceIn(0, maxScroll), 0)
        }
    }
}
