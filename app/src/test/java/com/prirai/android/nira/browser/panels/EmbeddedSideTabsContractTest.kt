package com.prirai.android.nira.browser.panels

import android.app.Activity
import android.app.Application
import android.view.View
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.tabs.TabsBottomSheetFragment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Contract checks without instantiating a full BrowserActivity/GeckoView.
 * Fragment rendering, profile switch and actual resize are device/instrumentation work.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class EmbeddedSideTabsContractTest {
    @Test fun embeddedAndDialogFactoriesRemainSeparate() {
        val embedded = TabsBottomSheetFragment.newEmbeddedSidePanel()
        val legacy = TabsBottomSheetFragment.newSidePanel()
        val bottomSheet = TabsBottomSheetFragment.newInstance()
        assertFalse(embedded.showsDialog)
        assertTrue(legacy.showsDialog)
        assertTrue(bottomSheet.showsDialog)
        assertTrue(embedded.arguments!!.getBoolean("nira_side_panel"))
        assertTrue(legacy.arguments!!.getBoolean("nira_side_panel"))
        assertFalse(bottomSheet.arguments?.getBoolean("nira_side_panel") ?: false)
    }

    @Test fun hostUsesStableFragmentContainerIdsAndIsInertByDefault() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val host = SidePanelHost(activity)
        assertEquals(R.id.nira_embedded_side_tabs, host.leftContainer.id)
        assertEquals(R.id.nira_embedded_side_bookmarks, host.rightContainer.id)
        assertNotEquals(host.leftContainer.id, host.rightContainer.id)
        assertEquals(View.GONE, host.visibility)
        activity.finish()
    }

    @Test fun emptyContentCannotActivateGesturesOrStealBack() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val layout = com.prirai.android.nira.browser.SwipeGestureLayout(activity)
        val infrastructure = SidePanelInfrastructure.attach(layout, 36) { true }
        assertFalse(infrastructure.activate(SidePanelSide.LEFT, 290))
        assertFalse(infrastructure.closeOnBack())
        assertEquals(View.GONE, infrastructure.host.visibility)
        infrastructure.dispose()
        activity.finish()
    }
}
