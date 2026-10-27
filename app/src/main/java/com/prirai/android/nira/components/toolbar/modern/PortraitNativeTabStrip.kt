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
import com.prirai.android.nira.browser.tabgroups.TabGroupData
import com.prirai.android.nira.browser.tabs.compose.UnifiedTabOrder
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
    private var currentGroups: List<TabGroupData> = emptyList()
    private var currentOrder: UnifiedTabOrder? = null
    var onGroupToggle: ((String) -> Unit)? = null
    private var selectedPill: View? = null
    private var lastSelectedId: String? = null
    // Title, favicon and selection updates reuse the same native Views.
    private data class RenderedPill(
        val container: FrameLayout,
        val image: ImageView,
        var title: String,
        var url: String,
        var iconIdentity: Int?,
        var selected: Boolean
    )
    private val renderedPills = mutableMapOf<String, RenderedPill>()

    var onTabSelected: ((String) -> Unit)? = null
    var onTabClosed: ((String) -> Unit)? = null
    var onNewTab: (() -> Unit)? = null
    var onNewTabInGroup: ((String) -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = barHeight
        clipChildren = false
        clipToPadding = false

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
        // Invalidate any pending async request when the same view is rebound.
        image.tag = null
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
     * Room group updates are independent of BrowserStore. Rebuild after either
     * stream changes, not just when tabs are opened/closed.
     */
    fun updateGroups(groups: List<TabGroupData>) {
        if (currentGroups == groups) return
        currentGroups = groups
        updateTabs(lastTabs, lastSelectedId)
    }

    /** Shared, persisted ordering used by the side sheet and landscape bar. */
    fun updateOrder(order: UnifiedTabOrder?) {
        if (currentOrder == order) return
        currentOrder = order
        updateTabs(lastTabs, lastSelectedId)
    }

    private fun groupBackground(color: Int): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(13).toFloat()
            // Keep groups unmistakably separate even when the user disables chips.
            setColor(Color.argb(67, Color.red(color), Color.green(color), Color.blue(color)))
        }

    private fun tabChip(
        tab: TabSessionState,
        selectedId: String?,
        dark: Boolean,
        parent: LinearLayout
    ) {
        val title = tabTitle(tab)
        val selected = tab.id == selectedId
        val tabContainer = FrameLayout(context).apply {
            background = tabBackground(selected, dark)
            layoutParams = LinearLayout.LayoutParams(dp(39), dp(34)).apply {
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
        renderedPills[tab.id] = RenderedPill(tabContainer, image, title, tab.content.url,
            tab.content.icon?.let { System.identityHashCode(it) }, selected)
        parent.addView(tabContainer)
        if (selected) selectedPill = tabContainer
    }

    private fun groupSegment(
        group: TabGroupData,
        members: List<TabSessionState>,
        selectedId: String?,
        dark: Boolean
    ): View {
        val segment = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = groupBackground(group.color)
            setPadding(dp(3), 0, dp(3), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, pillHeight
            ).apply { marginEnd = dp(6) }
        }
        val collapsed = currentOrder?.primaryOrder
            ?.filterIsInstance<UnifiedTabOrder.OrderItem.TabGroup>()
            ?.firstOrNull { it.groupId == group.id }?.let { !it.isExpanded } ?: group.isCollapsed
        val header = TextView(context).apply {
            text = (if (collapsed) "› " else "⌃ ") +
                "(${members.size}) " + group.name.ifBlank { "Grupa" }
            setTextColor(if (dark) group.color else Color.rgb(45, 38, 42))
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            setPadding(dp(8), 0, dp(8), 0)
            maxLines = 1
            maxWidth = dp(150)
            isClickable = true
            contentDescription = (if (collapsed) "Rozwiń" else "Zwiń") +
                " grupę ${group.name}, ${members.size} kart"
            setOnClickListener {
                onGroupToggle?.invoke(group.id)
            }
        }
        segment.addView(header, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, pillHeight
        ))
        if (!collapsed) {
            members.forEach { tab ->
                segment.addView(View(context).apply {
                    setBackgroundColor(Color.argb(70, Color.red(group.color),
                        Color.green(group.color), Color.blue(group.color)))
                }, LinearLayout.LayoutParams(dp(1), dp(23)))
                tabChip(tab, selectedId, dark, segment)
            }
            segment.addView(actionText("+", 29, 22f, dark).apply {
                setTextColor(group.color)
                contentDescription = "Nowa karta w grupie ${group.name}"
                setOnClickListener { onNewTabInGroup?.invoke(group.id) }
            })
        }
        return segment
    }

    private fun selectedOffsetInsideRow(focused: View): Int {
        var offset = focused.left
        var ancestor = focused.parent as? View
        while (ancestor != null && ancestor !== row) {
            offset += ancestor.left
            ancestor = ancestor.parent as? View
        }
        return offset
    }

    /** Shows separate colored groups and a neutral ungrouped section. */
    fun updateTabs(tabs: List<TabSessionState>, selectedId: String?) {
        val byIdForChooser = tabs.associateBy { it.id }
        val orderedIds = currentOrder?.reconciled(tabs.map { it.id }, currentGroups)
            ?.getAllTabIds().orEmpty().distinct()
        lastTabs = orderedIds.mapNotNull { byIdForChooser[it] } + tabs.filterNot { it.id in orderedIds }
        val previousSelectedId = lastSelectedId
        lastSelectedId = selectedId
        val visibleIds = tabs.map { it.id }.toSet()
        val visibleGroups = currentGroups.filter { g -> g.tabIds.any { it in visibleIds } }
        val dark = ThemeManager.isDarkMode(context)
        // Only a structural change (group/order/membership/theme) recreates the row.
        val signature = buildList {
            add(dark.toString())
            currentOrder?.primaryOrder?.forEach { item ->
                when (item) {
                    is UnifiedTabOrder.OrderItem.SingleTab -> add("t:" + item.tabId)
                    is UnifiedTabOrder.OrderItem.TabGroup -> {
                        add("g:" + item.groupId)
                        add(item.tabIds.joinToString(","))
                        add(item.isExpanded.toString())
                    }
                }
            }
            visibleGroups.forEach { group ->
                add(group.id)
                add(group.name)
                add(group.color.toString())
                add(group.tabIds.joinToString(","))
                add(group.isCollapsed.toString())
            }
            tabs.forEach { add(it.id) }
        }
        val structureChanged = signature != lastSignature
        val oldScroll = scroller.scrollX
        if (structureChanged) {
            lastSignature = signature
            row.removeAllViews()
            renderedPills.clear()
            selectedPill = null
        val byId = tabs.associateBy { it.id }
        val groupsById = visibleGroups.associateBy { it.id }
        val groupedIds = visibleGroups.flatMap { it.tabIds }.toSet()
        val renderedGroups = mutableSetOf<String>()
        val renderedTabs = mutableSetOf<String>()
        val pendingUngrouped = mutableListOf<TabSessionState>()

        fun addUngroupedSegment() {
            if (pendingUngrouped.isEmpty()) return
            val segmentTabs = pendingUngrouped.toList()
            pendingUngrouped.clear()
            val neutral = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(3), 0, dp(3), 0)
                background = GradientDrawable().apply {
                    cornerRadius = dp(13).toFloat()
                    setColor(if (dark) Color.rgb(76, 42, 61) else Color.rgb(244, 217, 233))
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, pillHeight
                ).apply { marginEnd = dp(6) }
            }
            neutral.addView(actionText("⌃", 21, 19f, dark).apply {
                contentDescription = "Wybierz kartę"
                setOnClickListener { openTabChooser(this) }
            })
            neutral.addView(actionText("(${segmentTabs.size})", 36, 13f, dark).apply {
                contentDescription = "Karty bez grupy: ${segmentTabs.size}"
                setOnClickListener { openTabChooser(this) }
            })
            segmentTabs.forEach { tab -> tabChip(tab, selectedId, dark, neutral) }
            row.addView(neutral)
        }

        // Preserve exact interleaving of groups and individual tabs. The old
        // implementation always rendered all Room groups before all ungrouped
        // tabs, which differed from the persisted order in the side drawer.
        currentOrder?.primaryOrder?.forEach { item ->
            when (item) {
                is UnifiedTabOrder.OrderItem.TabGroup -> {
                    val group = groupsById[item.groupId]
                    if (group != null && renderedGroups.add(group.id)) {
                        addUngroupedSegment()
                        val members = group.tabIds.mapNotNull { byId[it] }
                        renderedTabs.addAll(members.map { it.id })
                        row.addView(groupSegment(group, members, selectedId, dark))
                    }
                }
                is UnifiedTabOrder.OrderItem.SingleTab -> {
                    val tab = byId[item.tabId]
                    if (tab != null && tab.id !in groupedIds && renderedTabs.add(tab.id)) {
                        pendingUngrouped.add(tab)
                    }
                }
            }
        }
        // Newly opened cards/groups are visible before the persisted order
        // catches up, but never duplicate a previously rendered item.
        visibleGroups.forEach { group ->
            if (renderedGroups.add(group.id)) {
                addUngroupedSegment()
                val members = group.tabIds.mapNotNull { byId[it] }
                renderedTabs.addAll(members.map { it.id })
                row.addView(groupSegment(group, members, selectedId, dark))
            }
        }
        tabs.forEach { tab ->
            if (tab.id !in groupedIds && renderedTabs.add(tab.id)) {
                pendingUngrouped.add(tab)
            }
        }
        addUngroupedSegment()
        row.addView(actionText("+", 32, 24f, dark).apply {
            contentDescription = "Nowa karta poza grupami"
            setOnClickListener { onNewTab?.invoke() }
        })

        }
        // Incrementally rebind only the pills whose visible content changed.
        tabs.forEach { tab ->
            val pill = renderedPills[tab.id] ?: return@forEach // collapsed group
            val title = tabTitle(tab)
            if (pill.title != title) {
                pill.title = title
                pill.container.contentDescription = "Karta: $title"
                pill.image.contentDescription = "Ikona strony: $title"
            }
            val iconIdentity = tab.content.icon?.let { System.identityHashCode(it) }
            if (pill.url != tab.content.url || pill.iconIdentity != iconIdentity) {
                pill.url = tab.content.url
                pill.iconIdentity = iconIdentity
                bindFavicon(pill.image, tab)
            }
            val selected = tab.id == selectedId
            if (pill.selected != selected) {
                pill.selected = selected
                pill.container.background = tabBackground(selected, dark)
            }
        }
        selectedPill = selectedId?.let { renderedPills[it]?.container }
        if (!structureChanged && previousSelectedId == selectedId) return
        val focused = selectedPill
        scroller.post {
            val maxScroll = (row.width - scroller.width).coerceAtLeast(0)
            var newScroll = oldScroll.coerceIn(0, maxScroll)
            if (focused != null && previousSelectedId != selectedId) {
                val left = selectedOffsetInsideRow(focused)
                if (left < newScroll) newScroll = left
                if (left + focused.width > newScroll + scroller.width) {
                    newScroll = left + focused.width - scroller.width
                }
            }
            scroller.scrollTo(newScroll.coerceIn(0, maxScroll), 0)
        }
    }
}
