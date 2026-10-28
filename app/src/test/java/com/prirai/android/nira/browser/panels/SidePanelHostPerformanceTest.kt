package com.prirai.android.nira.browser.panels

import android.app.Activity
import android.app.Application
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Render-loop contract at 120 logical samples. Not an FPS or jank benchmark.
 * The transition must change only composited properties, not layout params.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class SidePanelHostPerformanceTest {
    private fun host(activity: Activity): SidePanelHost {
        val root=SidePanelHost(activity)
        activity.setContentView(root)
        root.measure(View.MeasureSpec.makeMeasureSpec(420,View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(840,View.MeasureSpec.EXACTLY))
        root.layout(0,0,420,840)
        return root
    }

    @Test fun full120SampleRenderSequenceDoesNotRewritePanelLayoutParams() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
        val panel=host(activity)
        panel.setPanelWidth(SidePanelSide.LEFT,300)
        val layout=panel.leftContainer.layoutParams
        for(i in 1..120) {
            val progress=i/120f
            panel.render(SidePanelSnapshot(SidePanelSide.LEFT,SidePanelPhase.DRAGGING,progress))
            assertSame(layout,panel.leftContainer.layoutParams)
            assertEquals(-300f*(1f-progress),panel.leftContainer.translationX,0.001f)
        }
        assertEquals(View.VISIBLE,panel.visibility)
        assertEquals(0.55f,panel.getChildAt(0).alpha,0.0001f)
        activity.finish()
    }

    @Test fun unchangedWidthKeepsSameLayoutParamsAndMeasuredWidth() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
        val panel=host(activity)
        panel.setPanelWidth(SidePanelSide.RIGHT,250)
        val old=panel.rightContainer.layoutParams
        panel.setPanelWidth(SidePanelSide.RIGHT,250)
        assertSame(old,panel.rightContainer.layoutParams)
        panel.setPanelWidth(SidePanelSide.RIGHT,270)
        assertEquals(270,panel.rightContainer.layoutParams.width)
        activity.finish()
    }

    @Test fun closedHostStillHidesScrimAndCannotBlockGeckoContent() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
        val panel=host(activity)
        panel.setPanelWidth(SidePanelSide.RIGHT,280)
        for(i in 1..120)panel.render(
            SidePanelSnapshot(SidePanelSide.RIGHT,SidePanelPhase.SETTLING,i/120f,true))
        panel.render(SidePanelSnapshot(null,SidePanelPhase.CLOSED,0f))
        assertEquals(View.GONE,panel.visibility)
        assertEquals(View.GONE,panel.getChildAt(0).visibility)
        assertEquals(View.GONE,panel.rightContainer.visibility)
        activity.finish()
    }
}
