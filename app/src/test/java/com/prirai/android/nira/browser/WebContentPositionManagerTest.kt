package com.prirai.android.nira.browser

import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import mozilla.components.concept.engine.EngineView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class WebContentPositionManagerTest {
    @Test fun internalResultsHeaderStaysBelowChromeInPortraitAndLandscape() {
        val context = RuntimeEnvironment.getApplication()
        val root = CoordinatorLayout(context)
        val engine = View(context)
        val results = View(context)
        root.addView(engine, CoordinatorLayout.LayoutParams(-1, -1))
        root.addView(results, CoordinatorLayout.LayoutParams(-1, -1))
        val manager = WebContentPositionManager(
            engineView = Mockito.mock(EngineView::class.java),
            swipeRefreshView = engine,
            internalPageView = results,
            rootView = root,
            getTopToolbarHeight = { 0 },
            getBottomToolbarHeight = { 0 },
        )
        try {
            manager.setChromeReservation(236, 48)
            assertMargins(engine, 236, 48)
            assertMargins(results, 236, 48)

            // Toolbar collapse must never hide the internal page's header.
            manager.setChromeReservation(92, 48)
            assertMargins(engine, 92, 48)
            assertMargins(results, 92, 48)

            // A bottom toolbar places the internal page above it.
            manager.setChromeReservation(0, 112)
            assertMargins(engine, 0, 112)
            assertMargins(results, 0, 112)

            manager.setFullscreen(true)
            assertMargins(engine, 0, 0)
            assertMargins(results, 0, 0)

            manager.setFullscreen(false)
            assertMargins(engine, 0, 112)
            assertMargins(results, 0, 112)
        } finally {
            manager.destroy()
        }
    }

    @Test fun collapsedToolbarStillReservesStatusBarForClickableHeader() {
        val context = RuntimeEnvironment.getApplication()
        val root = CoordinatorLayout(context)
        val engine = View(context)
        val results = View(context)
        root.addView(engine, CoordinatorLayout.LayoutParams(-1, -1))
        root.addView(results, CoordinatorLayout.LayoutParams(-1, -1))
        val manager = WebContentPositionManager(
            engineView = Mockito.mock(EngineView::class.java),
            swipeRefreshView = engine,
            internalPageView = results,
            rootView = root,
            getTopToolbarHeight = { 0 },
            getBottomToolbarHeight = { 0 },
        )
        try {
            manager.initialize()
            ViewCompat.dispatchApplyWindowInsets(root, WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 72, 0, 0))
                .build())
            manager.setChromeReservation(220, 44)
            assertMargins(results, 220, 44)

            manager.setChromeReservation(0, 44)
            assertMargins(engine, 0, 44) // Gecko may render edge-to-edge.
            assertMargins(results, 72, 44) // Results actions must remain tappable.

            manager.setFullscreen(true)
            assertMargins(results, 0, 0)
            manager.setFullscreen(false)
            assertMargins(results, 72, 44)
        } finally {
            manager.destroy()
        }
    }

    private fun assertMargins(view: View, top: Int, bottom: Int) {
        val lp = view.layoutParams as CoordinatorLayout.LayoutParams
        assertEquals("top margin", top, lp.topMargin)
        assertEquals("bottom margin", bottom, lp.bottomMargin)
    }
}
