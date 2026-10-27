package com.prirai.android.nira.display

import android.app.Activity
import android.app.Dialog
import android.hardware.display.DisplayManager
import android.os.PowerManager
import android.view.Display
import android.view.Window
import android.view.WindowManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDisplayManager

/** Runs real Android Window/Display adapters at the API floor and modern Android. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27, 35], application = android.app.Application::class, manifest = Config.NONE)
class BrowserRefreshRateTest {
    private val controllers = mutableListOf<ActivityController<Activity>>()
    private val dialogs = mutableListOf<Dialog>()

    private fun window(rate: Float, saving: Boolean = false): Window {
        val app = RuntimeEnvironment.getApplication()
        val display = app.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val current = display.mode
        val mode = ShadowDisplayManager.ModeBuilder.modeBuilder(current.modeId)
            .setWidth(current.physicalWidth).setHeight(current.physicalHeight)
            .setRefreshRate(rate).build()
        ShadowDisplayManager.setSupportedModes(display.displayId, mode)
        shadowOf(app.getSystemService(PowerManager::class.java)).setIsPowerSaveMode(saving)
        val controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
        controllers.add(controller)
        return controller.get().window.apply {
            attributes.preferredDisplayModeId = 42
            attributes.flags = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        }
    }

    @After fun cleanup() {
        dialogs.forEach { it.dismiss() }
        controllers.asReversed().forEach { it.pause().stop().destroy() }
    }

    @Test fun fractionalSupportedPreferenceWritesOnceWithoutChangingDisplayModeOrFlags() {
        val window = window(119.88f)
        assertTrue(BrowserRefreshRate.apply(window))
        assertEquals(119.88f, window.attributes.preferredRefreshRate, 0f)
        assertEquals(42, window.attributes.preferredDisplayModeId)
        assertEquals(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, window.attributes.flags)
        assertFalse(BrowserRefreshRate.apply(window))
    }

    @Test fun unavailable120DoesNotPinLowerDisplayFrequency() {
        for (rate in listOf(60f, 90f)) {
            val window = window(rate)
            assertFalse(BrowserRefreshRate.apply(window))
            assertEquals(0f, window.attributes.preferredRefreshRate, 0f)
        }
    }

    @Test fun powerSaverClearsStalePreferenceAndLeavesSystemChoice() {
        val window = window(120f, saving = true)
        window.attributes.preferredRefreshRate = 120f
        assertTrue(BrowserRefreshRate.apply(window))
        assertEquals(0f, window.attributes.preferredRefreshRate, 0f)
        assertFalse(BrowserRefreshRate.apply(window))
    }

    @Test fun returningToSupportedDisplayReappliesAfterExternalWindowReset() {
        val window = window(120f)
        BrowserRefreshRate.apply(window)
        window.attributes.preferredRefreshRate = 0f
        assertTrue(BrowserRefreshRate.apply(window))
        assertEquals(120f, window.attributes.preferredRefreshRate, 0f)
    }

    @Test fun popupRefreshPreservesOwningWindowAttributesIdentity() {
        val owner = window(119.88f)
        val dialog = Dialog(owner.context).also { dialogs.add(it); it.show() }
        val window = requireNotNull(dialog.window)
        val root = window.decorView
        // Dialog.show may make its initial params copy for forward navigation.
        // A subsequent real owner dispatch installs Window.attributes in the root.
        window.attributes = window.attributes
        assertSame(window.attributes, root.layoutParams)
        assertTrue(BrowserRefreshRate.applyRoot(root))
        assertEquals(119.88f, window.attributes.preferredRefreshRate, 0f)
        assertSame(window.attributes, root.layoutParams)
        shadowOf(window.context.getSystemService(PowerManager::class.java)).setIsPowerSaveMode(true)
        assertTrue(BrowserRefreshRate.applyRoot(root))
        assertEquals(0f, window.attributes.preferredRefreshRate, 0f)
        // A later owner flag dispatch must retain the new preference.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        assertEquals(0f, (root.layoutParams as WindowManager.LayoutParams).preferredRefreshRate, 0f)
    }

    @Test fun trackedDialogReactsToPowerChangesAndDetachesCleanly() {
        val owner = window(119.88f)
        val dialogOwner = Dialog(owner.context).also { dialogs.add(it); it.show() }
        val dialog = requireNotNull(dialogOwner.window)
        val registry = RefreshRateWindowRegistry()
        registry.attach(dialog)
        assertTrue(registry.containsRoot(dialog.decorView))
        assertEquals(119.88f, dialog.attributes.preferredRefreshRate, 0f)
        val power = dialog.context.getSystemService(PowerManager::class.java)
        shadowOf(power).setIsPowerSaveMode(true)
        registry.refresh()
        assertEquals(0f, dialog.attributes.preferredRefreshRate, 0f)
        shadowOf(power).setIsPowerSaveMode(false)
        registry.refresh()
        assertEquals(119.88f, dialog.attributes.preferredRefreshRate, 0f)
        registry.detach(dialog)
        assertFalse(registry.containsRoot(dialog.decorView))
        dialog.attributes.preferredRefreshRate = 0f
        registry.refresh()
        assertEquals(0f, dialog.attributes.preferredRefreshRate, 0f)
    }
    @Test fun componentDialogOwnerIsRecoveredWithPublicLifecycleTree() {
        val owner = window(119.88f)
        val dialog = androidx.activity.ComponentDialog(owner.context).also {
            // ComponentDialog installs lifecycle tree owners when content is attached, not show().
            it.setContentView(android.view.View(it.context))
            dialogs.add(it)
            it.show()
        }
        val window = requireNotNull(dialog.window)
        assertSame(window, BrowserRefreshRate.dialogWindow(window.decorView))
        assertNull(BrowserRefreshRate.dialogWindow(owner.decorView))
        val registry = RefreshRateWindowRegistry()
        registry.attach(requireNotNull(BrowserRefreshRate.dialogWindow(window.decorView)))
        assertEquals(119.88f, window.attributes.preferredRefreshRate, 0f)
        shadowOf(window.context.getSystemService(PowerManager::class.java)).setIsPowerSaveMode(true)
        registry.refresh()
        assertEquals(0f, window.attributes.preferredRefreshRate, 0f)
    }

}
