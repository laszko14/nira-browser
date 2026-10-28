package com.prirai.android.nira.browser.panels

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.View
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.SwipeGestureLayout
import com.prirai.android.nira.browser.bookmark.ui.BookmarksBottomSheetFragment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Factory and dormant-host contracts only. Real bookmark rows/folders require
 * FragmentManager integration or instrumented Android tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class EmbeddedSideBookmarksContractTest {
    @Test fun embeddedAndDialogFactoriesRemainSeparate() {
        val embedded = BookmarksBottomSheetFragment.newEmbeddedSidePanel()
        val legacy = BookmarksBottomSheetFragment.newSidePanel()
        val regular = BookmarksBottomSheetFragment.newInstance(42L)

        assertFalse(embedded.showsDialog)
        assertTrue(embedded.arguments!!.getBoolean("nira_embedded_bookmarks"))
        assertTrue(embedded.arguments!!.getBoolean("nira_side_panel"))
        assertTrue(legacy.showsDialog)
        assertTrue(legacy.arguments!!.getBoolean("nira_side_panel"))
        assertFalse(legacy.arguments!!.getBoolean("nira_embedded_bookmarks"))
        assertTrue(regular.showsDialog)
        assertFalse(regular.arguments!!.getBoolean("nira_side_panel"))
        assertEquals(42L, regular.arguments!!.getLong("folder_id"))
    }

    @Test fun rightContainerIsStableHiddenAndNotEnabledByDefault() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = SwipeGestureLayout(activity)
        activity.setContentView(root)
        val panels = SidePanelInfrastructure.attach(root, 36) { true }

        assertEquals(R.id.nira_embedded_side_bookmarks, panels.host.rightContainer.id)
        assertEquals(View.GONE, panels.host.visibility)
        assertEquals(0, panels.host.rightContainer.childCount)
        assertFalse(panels.activate(SidePanelSide.RIGHT, 300))
        assertFalse(panels.closeOnBack())
        panels.dispose()
        assertEquals(0, root.childCount)
        activity.finish()
    }

    @Test fun embeddedFlagSurvivesArgumentCopyWithoutChangingNormalFactory() {
        val embedded = BookmarksBottomSheetFragment.newEmbeddedSidePanel()
        val copiedArguments = Bundle(embedded.arguments!!)
        assertTrue(copiedArguments.getBoolean("nira_embedded_bookmarks"))
        assertTrue(copiedArguments.getBoolean("nira_side_panel"))
        assertFalse(BookmarksBottomSheetFragment.newInstance()
            .arguments!!.getBoolean("nira_embedded_bookmarks"))
    }

    @Test fun newEmbeddedBookmarksCannotReadFragmentResourcesBeforeAttachment() {
        val detached = BookmarksBottomSheetFragment.newEmbeddedSidePanel()
        assertFalse(detached.isAdded)
        try {
            detached.embeddedSidePanelWidth()
            fail("Detached fragment must not provide a Context before commitNow")
        } catch (_: IllegalStateException) { }
    }

}
