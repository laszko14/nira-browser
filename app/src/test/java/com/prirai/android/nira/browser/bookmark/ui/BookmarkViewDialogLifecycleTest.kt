package com.prirai.android.nira.browser.bookmark.ui

import android.app.Dialog
import android.app.Application
import android.view.View
import androidx.fragment.app.FragmentActivity
import com.prirai.android.nira.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/** ZC-AUD-011: a destroyed embedded or dialog panel must release its Activity window. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class BookmarkViewDialogLifecycleTest {

    @Test fun scopeInvalidatesLateCallbacksAndDismissesExactlyOnce() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        val scope = BookmarkViewDialogScope()
        val dialog = Dialog(activity)
        var refreshed = 0
        var detached = 0
        scope.track(dialog) { detached++ }
        dialog.show()
        scope.ifActive { refreshed++ }
        assertEquals(1, scope.openDialogCount)

        scope.close()
        scope.close()
        scope.ifActive { refreshed++ }
        assertFalse(dialog.isShowing)
        assertFalse(scope.isActive)
        assertEquals(0, scope.openDialogCount)
        assertEquals(1, detached)
        assertEquals(1, refreshed)
        activity.finish()
    }

    @Test fun normallyDismissedDialogIsUntrackedAndCleared() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        val scope = BookmarkViewDialogScope()
        val dialog = Dialog(activity)
        var cleaned = 0
        scope.track(dialog) { cleaned++ }
        dialog.show()
        dialog.dismiss()
        assertEquals(0, scope.openDialogCount)
        scope.close()
        assertEquals(1, cleaned)
        activity.finish()
    }

    @Test fun embeddedPanelDismissesAddFolderWindowOnViewDestruction() {
        verifyPanelCleanup(embedded = true)
    }

    @Test fun normalDialogPanelDismissesAddFolderWindowOnViewDestruction() {
        verifyPanelCleanup(embedded = false)
    }

    private fun verifyPanelCleanup(embedded: Boolean) {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        activity.setTheme(R.style.AppThemeNotActionBar)
        val fragment = if (embedded) BookmarksBottomSheetFragment.newEmbeddedSidePanel()
                       else BookmarksBottomSheetFragment.newInstance()
        if (embedded) {
            activity.supportFragmentManager.beginTransaction()
                .add(android.R.id.content, fragment, "embedded-bookmarks")
                .commitNow()
        } else {
            fragment.showNow(activity.supportFragmentManager, "dialog-bookmarks")
        }
        val addFolder = fragment.requireView().findViewById<View>(R.id.addFolderButton)
        assertNotNull(addFolder)
        assertTrue(addFolder.performClick())
        val folderWindow = ShadowDialog.getLatestDialog()
        assertNotNull(folderWindow)
        assertTrue(folderWindow.isShowing)

        // Equivalent to the embedded host's unmount on rotation/fullscreen.
        if (embedded) {
            activity.supportFragmentManager.beginTransaction().remove(fragment).commitNow()
        } else {
            fragment.dismissNow()
        }
        assertFalse(folderWindow.isShowing)
        assertFalse(fragment.isAdded)
        activity.finish()
    }
}
