package com.prirai.android.nira.media

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.prirai.android.nira.display.BrowserRefreshRate
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

/** Real Compose measure/scroll/touch/Window checks. No network or native MPV decoding claim. */
@RunWith(AndroidJUnit4::class)
class VideoTilesDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var activity: Activity
    private lateinit var beforePortrait: VideoTilesSettings
    private lateinit var beforeLandscape: VideoTilesSettings
    private val fixtureName = "com.prirai.android.nira.media.VideoTilesDeviceActivity"
    private val lifecycle = ActivityLifecycleCallback { candidate, stage ->
        if (candidate.javaClass.name == fixtureName && stage == Stage.RESUMED) activity = candidate
    }

    @Before fun setup() {
        val preferences = VideoTilesPreferences(instrumentation.targetContext)
        beforePortrait = preferences.load(false)
        beforeLandscape = preferences.load(true)
        preferences.save(false, VideoTilesSettings(10, 160))
        preferences.save(true, VideoTilesSettings(10, 160))
        onMain { ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(lifecycle) }
        activity = instrumentation.startActivitySync(Intent().setClassName(
            instrumentation.targetContext.packageName, fixtureName
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        await("Compose content") { node { it.contentDescription?.toString() == "Ustawienia widoku" } != null }
    }

    @After fun cleanup() {
        onMain {
            ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(lifecycle)
            if (::activity.isInitialized) activity.finish()
        }
        val preferences = VideoTilesPreferences(instrumentation.targetContext)
        if (::beforePortrait.isInitialized) preferences.save(false, beforePortrait)
        if (::beforeLandscape.isInitialized) preferences.save(true, beforeLandscape)
    }

    @Test fun tenRealRowsRemainReachableWithReadablePlayInPortraitAndLandscape() {
        for (orientation in listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)) {
            val expected = if (orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                android.content.res.Configuration.ORIENTATION_PORTRAIT else android.content.res.Configuration.ORIENTATION_LANDSCAPE
            onMain { activity.requestedOrientation = orientation }
            await("orientation settled") { onMain { activity.resources.configuration.orientation == expected } }
            await("fixed header") { node { it.contentDescription?.toString() == "Ustawienia widoku" } != null }
            // Each iteration uses a fresh Activity so both axes start at zero after rotation.
            val beforeReset = activity
            onMain { activity.recreate() }
            await("fresh orientation Activity") { activity !== beforeReset }
            await("first row visible") { play(1) != null && play(11) != null }
            assertNull("Ten rows must overflow, rather than be compressed into tiny thumbnails", play(10))
            val firstPlay = bounds(play(1)!!)
            val nextColumn = bounds(play(11)!!)
            assertEquals("11th item starts the next column of a real ten-row grid", firstPlay.centerY(), nextColumn.centerY())
            assertTrue("Grid retains horizontal columns", nextColumn.centerX() > firstPlay.centerX())
            val density = onMain { activity.resources.displayMetrics.density }
            assertTrue("Play width remains >=48dp", firstPlay.width() >= 48 * density - 2)
            assertTrue("Play height remains >=48dp", firstPlay.height() >= 48 * density - 2)
            repeatUntil("tenth row in first column", 30, { play(10) != null }) { swipe(vertical = true) }
            val lastRow = bounds(play(10)!!)
            assertTrue("Last row Play retains its touch target", lastRow.height() >= 48 * density - 2)
            assertNotNull("Header remains available after vertical scroll", node {
                it.contentDescription?.toString() == "Ustawienia widoku"
            })
            repeatUntil("last row in last horizontal column", 30, { play(60) != null }) { swipe(vertical = false) }
            assertTrue("Only deliberate Play clicks invoke playback", onMain { intProperty("getPlayCount") } == 0)
            val selected = play(60)!!
            assertTrue("Play is focusable", selected.isFocusable)
            assertTrue(selected.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            await("exact final tile callback") { onMain {
                intProperty("getPlayCount") == 1 && activity.javaClass.getMethod("getLastPlayed").invoke(activity) == 60L
            } }
            println("VIDEO_TILES_EMULATOR_ONLY orientation=$expected actualRows=10 lastRowAndColumnReachable=true playTouchTarget48dp=true")
        }
    }

    @Test fun realFloatingBottomSheetAttachesCompositionAndReopensAfterRotation() {
        val preferences = VideoTilesPreferences(instrumentation.targetContext)
        preferences.save(false, VideoTilesSettings(4, 120))
        preferences.save(true, VideoTilesSettings(4, 120))
        onMain { activity.finish() }
        activity = instrumentation.startActivitySync(Intent().setClassName(
            instrumentation.targetContext.packageName, fixtureName
        ).putExtra("overlay", true).putExtra("visual", true).putExtra("count", 12)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        for (orientation in listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)) {
            val expected = if (orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                android.content.res.Configuration.ORIENTATION_PORTRAIT else android.content.res.Configuration.ORIENTATION_LANDSCAPE
            onMain { activity.requestedOrientation = orientation }
            await("real overlay orientation") { onMain { activity.resources.configuration.orientation == expected } }
            var fab: AccessibilityNodeInfo? = null
            await("anchored detector FAB") { fab = node { it.contentDescription?.toString() == "Wykryte filmy" }; fab != null }
            assertTrue(fab!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            await("real BottomSheetDialog composition attached") { node { it.contentDescription?.toString() == "Ustawienia widoku" } != null }
            assertNotNull("Real sheet uses production tiles", node {
                it.contentDescription?.toString()?.startsWith("Odtwórz w MPV Alpha: Film testowy 1 —") == true
            })
            captureScreenshot(if (expected == android.content.res.Configuration.ORIENTATION_PORTRAIT)
                "reference-panel-portrait.png" else "reference-panel-landscape.png")
            if (expected == android.content.res.Configuration.ORIENTATION_PORTRAIT) {
                clickDescription("Ustawienia widoku")
                await("reference settings visible") { node { it.contentDescription?.toString() == "Wróć do filmów" } != null }
                captureScreenshot("reference-settings-portrait.png")
                clickDescription("Wróć do filmów")
            }
            assertEquals(0, onMain { intProperty("getPlayCount") })
            val close = node { it.contentDescription?.toString() == "Zamknij wykryte filmy" }!!
            assertTrue(close.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            await("real sheet dismissed") { node { it.contentDescription?.toString() == "Wykryte filmy" } != null }
        }
    }

    @Test fun emptyAndSingleEntryRemainUsableWithoutThumbnailOrMetadata() {
        fun relaunch(count: Int) {
            onMain { activity.finish() }
            activity = instrumentation.startActivitySync(Intent().setClassName(
                instrumentation.targetContext.packageName, fixtureName
            ).putExtra("count", count).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        relaunch(0)
        await("empty panel message") { node { it.text?.toString() == "Brak wykrytych filmów" } != null }
        assertNull(play(1))
        assertNotNull(node { it.contentDescription?.toString() == "Zamknij wykryte filmy" })
        relaunch(1)
        await("single Play without poster") { play(1) != null }
        assertNotNull(node { it.text?.toString() == "Rozmiar nieznany" })
        assertNotNull(node { it.text?.toString() == "Czas nieznany" || it.contentDescription?.toString()?.contains("Czas nieznany") == true })
        val title = node { it.text?.toString() == "Kafelek 1" }!!
        assertFalse("Tile title does not silently launch a second action", title.isClickable)
        assertEquals(0, onMain { intProperty("getPlayCount") })
        assertTrue(play(1)!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        await("single explicit Play") { onMain { intProperty("getPlayCount") == 1 } }
    }

    @Test fun sliderChangesPersistAndRemainIndependentAcrossRotationAndRecreation() {
        fun rotate(orientation: Int) {
            val expected = if (orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                android.content.res.Configuration.ORIENTATION_PORTRAIT else android.content.res.Configuration.ORIENTATION_LANDSCAPE
            onMain { activity.requestedOrientation = orientation }
            await("preference orientation") { onMain { activity.resources.configuration.orientation == expected } }
        }
        rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        val settings = node { it.contentDescription?.toString() == "Ustawienia widoku" }!!
        assertTrue(settings.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        fun setSlider(min: Float, max: Float, target: Float) {
            val slider = scrollTo { it.rangeInfo?.let { range -> range.min == min && range.max == max } == true }
            val args = android.os.Bundle().apply {
                putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, target)
            }
            assertTrue(slider.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id, args))
        }
        setSlider(1f, 10f, 3f)
        setSlider(100f, 280f, 220f)
        setSlider(2f, 16f, 12f)
        val preferences = VideoTilesPreferences(instrumentation.targetContext)
        await("finished slider changes saved") { preferences.load(false) == VideoTilesSettings(3, 220, gapDp = 12) }
        for (label in listOf("Miniatura z przyciskiem Play", "Tytuł (maks. 2 wiersze)", "Format pliku",
            "Rozdzielczość", "Rozmiar pliku", "Menu akcji (⋮)")) {
            clickNode(scrollTo { it.text?.toString() == label })
        }
        val saved = VideoTilesSettings(3, 220, gapDp = 12, showThumbnail = false, showTitle = false,
            showFormat = false, showQuality = false, showSize = false, showActions = false)
        await("all element checkbox choices saved") { preferences.load(false) == saved }
        clickDescription("Wróć do filmów")
        await("Play remains when decorative elements are hidden") { play(1) != null }
        assertNull(node { it.contentDescription?.toString() == "Akcje filmu: Kafelek 1" })
        assertEquals(VideoTilesSettings(10, 160), preferences.load(true))
        rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        await("landscape appearance loaded") { onMain { intProperty("configuredRows") == 10 && intProperty("configuredWidth") == 160 } }
        rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        await("portrait appearance restored") { onMain { intProperty("configuredRows") == 3 && intProperty("configuredWidth") == 220 } }
        val before = activity
        onMain { activity.recreate() }
        await("recreated panel reloads persisted values") { activity !== before && onMain {
            intProperty("configuredRows") == 3 && intProperty("configuredWidth") == 220
        } }
    }

    @Test fun compactTileMenuHasSeparateTargetAndThumbnailToggleKeepsPlay() {
        val preferences = VideoTilesPreferences(instrumentation.targetContext)
        preferences.save(false, VideoTilesSettings(1, 100))
        preferences.save(true, VideoTilesSettings(1, 100))
        onMain { activity.finish() }
        activity = instrumentation.startActivitySync(Intent().setClassName(
            instrumentation.targetContext.packageName, fixtureName
        ).putExtra("count", 1).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        await("compact tile controls") { play(1) != null && node { it.contentDescription?.toString() == "Akcje filmu: Kafelek 1" } != null }
        fun verifySeparateControls() {
            val playBounds = bounds(play(1)!!)
            val actionBounds = bounds(node { it.contentDescription?.toString() == "Akcje filmu: Kafelek 1" }!!)
            val density = onMain { activity.resources.displayMetrics.density }
            assertTrue(playBounds.width() >= 48 * density - 2 && playBounds.height() >= 48 * density - 2)
            assertTrue("Visible menu target remains distinct at minimum tile width",
                actionBounds.width() >= 23 * density - 2 && actionBounds.height() >= 23 * density - 2)
            assertFalse("Compact Play and menu targets never overlap", Rect.intersects(playBounds, actionBounds))
        }
        verifySeparateControls()
        tapNode(node { it.contentDescription?.toString() == "Akcje filmu: Kafelek 1" }!!)
        await("menu opened by real finger tap") { node { it.text?.toString() == "Odtwórz w MPV Alpha" } != null }
        assertEquals(0, onMain { intProperty("getPlayCount") })
        clickNode(scrollTo { it.text?.toString() == "Odtwórz w MPV Alpha" })
        await("menu Play invokes selected entry once") { onMain { intProperty("getPlayCount") == 1 } }
        preferences.save(false, VideoTilesSettings(1, 100, showThumbnail = false))
        preferences.save(true, VideoTilesSettings(1, 100, showThumbnail = false))
        val before = activity
        onMain { activity.recreate() }
        await("thumbnail-free compact control") { activity !== before && play(1) != null }
        verifySeparateControls()
        // Exercise the effective touch dispatch, including Compose's minimum-target expansion.
        // A finger near Play's right edge must select Play rather than the adjacent menu.
        val playBounds = bounds(play(1)!!)
        tapAt(playBounds.right - 2f, playBounds.exactCenterY())
        await("thumbnail-free real Play touch") { onMain { intProperty("getPlayCount") == 1 } }
        assertNull(node { it.text?.toString() == "Odtwórz w MPV Alpha" })
    }

    @Test fun windowPreferenceIsIdempotentAcrossResumeAndActivityRecreation() {
        fun check() = onMain {
            BrowserRefreshRate.apply(activity.window)
            val requested = activity.window.attributes.preferredRefreshRate
            assertTrue("Window preference is automatic or around 120Hz", requested == 0f || kotlin.math.abs(requested - 120f) <= 1f)
            assertFalse("Repeated policy application makes no redundant Window update", BrowserRefreshRate.apply(activity.window))
        }
        check()
        val previous = activity
        onMain { activity.recreate() }
        await("recreated Activity resumes") { activity !== previous && onMain { activity.window.decorView.isAttachedToWindow } }
        check()
        val covering = instrumentation.startActivitySync(Intent().setClassName(
            instrumentation.targetContext.packageName,
            "com.prirai.android.nira.media.discovery.GeckoDiscoveryActivity"
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        onMain { covering.finish() }
        await("return to foreground") { onMain { activity.hasWindowFocus() } }
        check()
    }

    private fun tapNode(target: AccessibilityNodeInfo) {
        val bounds = bounds(target)
        tapAt(bounds.exactCenterX(), bounds.exactCenterY())
    }
    private fun tapAt(x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(now, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { instrumentation.sendPointerSync(event) } finally { event.recycle() }
        }
        instrumentation.waitForIdleSync()
    }

    private fun captureScreenshot(name: String) {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(350) // Allow the real window's entry animation to settle before capture.
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
        try {
            java.io.File(instrumentation.targetContext.filesDir, name).outputStream().use { stream ->
                assertTrue("Screenshot encoded", bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream))
            }
        } finally { bitmap.recycle() }
    }
    private fun clickDescription(description: String) {
        var target: AccessibilityNodeInfo? = null
        await(description) { target = node { it.contentDescription?.toString() == description }; target != null }
        var clickable = target!!
        while (!clickable.isClickable) clickable = clickable.parent ?: error("No clickable parent for $description")
        assertTrue(clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }

    private fun clickNode(target: AccessibilityNodeInfo) {
        var clickable = target
        while (!clickable.isClickable) clickable = clickable.parent ?: error("No clickable parent")
        assertTrue(clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }
    private fun scrollTo(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        repeat(15) {
            node(predicate)?.let { return it }
            val scroll = node { it.isScrollable }
            assertNotNull("Settings content is reachable by scrolling", scroll)
            assertTrue(scroll!!.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))
            instrumentation.waitForIdleSync()
        }
        return node(predicate) ?: error("Expected settings control unavailable after scrolling")
    }

    private fun intProperty(name: String) = activity.javaClass.getMethod(name).invoke(activity) as Int
    private fun play(number: Int) = node {
        it.contentDescription?.toString() == "Odtwórz w MPV Alpha: Kafelek $number"
    }
    private fun node(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return null
        fun find(current: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (current.isVisibleToUser && !bounds(current).isEmpty && predicate(current)) return current
            for (index in 0 until current.childCount) current.getChild(index)?.let { find(it)?.let { found -> return found } }
            return null
        }
        return find(root)
    }
    private fun bounds(node: AccessibilityNodeInfo) = Rect().also { node.getBoundsInScreen(it) }
    private fun swipe(vertical: Boolean) {
        val rect = onMain { Rect().also { activity.window.decorView.getWindowVisibleDisplayFrame(it) } }
        val density = onMain { activity.resources.displayMetrics.density }
        // Stay below the fixed header and above system navigation; horizontal swipe is at
        // the last visible row so it targets the finite-height LazyHorizontalGrid itself.
        val x1 = if (vertical) rect.left + rect.width() * .4f else rect.right - 30 * density
        val x2 = if (vertical) x1 else rect.left + 30 * density
        val y1 = rect.bottom - 70 * density
        val y2 = if (vertical) maxOf(rect.top + 135 * density, y1 - rect.height() * .6f) else y1
        val start = SystemClock.uptimeMillis()
        fun send(action: Int, x: Float, y: Float) {
            val pointer = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER }
            val coords = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = if (action == MotionEvent.ACTION_UP) 0f else 1f; size = 1f }
            val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, 1, arrayOf(pointer), arrayOf(coords),
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { instrumentation.sendPointerSync(event) } finally { event.recycle() }
        }
        send(MotionEvent.ACTION_DOWN, x1, y1)
        repeat(18) { index ->
            val f = (index + 1) / 18f
            send(MotionEvent.ACTION_MOVE, x1 + (x2 - x1) * f, y1 + (y2 - y1) * f)
            SystemClock.sleep(15)
        }
        send(MotionEvent.ACTION_UP, x2, y2)
        instrumentation.waitForIdleSync()
        SystemClock.sleep(120)
    }
    private fun repeatUntil(label: String, attempts: Int, done: () -> Boolean, action: () -> Unit) {
        repeat(attempts) { if (done()) return; action() }
        assertTrue(label, done())
    }
    private fun await(label: String, condition: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < until) { if (condition()) return; SystemClock.sleep(50) }
        assertTrue(label, condition())
    }
    private fun <T> onMain(block: () -> T): T {
        val value = AtomicReference<T>()
        instrumentation.runOnMainSync { value.set(block()) }
        return value.get()
    }
}
