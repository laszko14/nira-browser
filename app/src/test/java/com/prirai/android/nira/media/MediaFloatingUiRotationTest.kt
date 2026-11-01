package com.prirai.android.nira.media

import android.app.Activity
import android.app.Application
import android.view.View
import android.widget.FrameLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.prirai.android.nira.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class MediaFloatingUiRotationTest {
    private fun activity(): Activity {
        val controller = Robolectric.buildActivity(Activity::class.java)
        controller.get().setTheme(R.style.AppThemeNotActionBar)
        return controller.setup().visible().get()
    }

    private fun floating(parent: FrameLayout): MediaFloatingUi =
        MediaFloatingUi(
            parent = parent,
            current = { null },
            safeToolbarSpace = { 0 to 0 },
            openVideo = {},
            queueDownload = {},
        )

    @Test fun rotationKeepsExactlyOneAnchoredFabAndDetachRemovesIt() {
        val activity = activity()
        val parent = FrameLayout(activity)
        activity.setContentView(parent)
        val overlay = floating(parent)
        val button = parent.getChildAt(0)
        assertNotNull(button)
        assertEquals(1, parent.childCount)
        repeat(3) {
            overlay.onHostConfigurationChanged()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, parent.childCount)
            assertSame(button, parent.getChildAt(0))
            assertEquals(View.VISIBLE, button.visibility)
        }
        overlay.detach()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, parent.childCount)
        activity.finish()
    }

    @Test fun rotationDismissesOldDialogWindowAndClearsItsReferences() {
        val activity = activity()
        val parent = FrameLayout(activity)
        activity.setContentView(parent)
        val overlay = floating(parent)
        val oldWindow = BottomSheetDialog(activity)
        oldWindow.setContentView(FrameLayout(activity))
        oldWindow.show()
        assertTrue(oldWindow.isShowing)

        // Simulate an already-open media sheet without invoking a Gecko session or
        // parsing any URL; the rotation contract is about Dialog ownership only.
        val field = MediaFloatingUi::class.java.getDeclaredField("sheet").apply { isAccessible = true }
        field.set(overlay, oldWindow)
        overlay.onHostConfigurationChanged()
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(oldWindow.isShowing)
        assertNull(field.get(overlay))
        assertEquals(1, parent.childCount)
        overlay.detach()
        assertEquals(0, parent.childCount)
        activity.finish()
    }
}
