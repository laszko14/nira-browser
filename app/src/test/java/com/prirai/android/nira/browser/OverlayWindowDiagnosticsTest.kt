package com.prirai.android.nira.browser

import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class OverlayWindowDiagnosticsTest {
    @Test fun oneShotSamplesNativeTopRightWithoutExposingPageText() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val root = FrameLayout(activity)
        activity.setContentView(root)
        val rectangle = TextView(activity).apply {
            id = View.generateViewId()
            text = "secret private text https://example.invalid/access?token=private"
            contentDescription = "another private label"
            setBackgroundColor(Color.GRAY)
            elevation = 14f
        }
        root.addView(rectangle, FrameLayout.LayoutParams(250, 170).apply {
            leftMargin = 730
            topMargin = 100
        })
        // A deterministic 1000x600 decor layout: sample is at 820x180.
        activity.window.decorView.measure(
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY)
        )
        activity.window.decorView.layout(0, 0, 1000, 600)
        val report = OverlayWindowDiagnostics.capture(activity)
        assertTrue(report.contains("scope=APP_DECOR_ONLY"))
        assertTrue(report.contains("APP_VIEW_HITS"))
        assertTrue(report.contains("class=TextView"))
        assertTrue(report.contains("elevation=14.0"))
        assertFalse(report.contains("private text"))
        assertFalse(report.contains("example.invalid"))
        assertFalse(report.contains("another private label"))
        activity.finish()
    }

    @Test fun hugeTreeIsBoundedAndNeverEnumeratesPageContent() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val parent = FrameLayout(activity)
        activity.setContentView(parent)
        repeat(500) {
            parent.addView(View(activity), FrameLayout.LayoutParams(10, 10))
        }
        activity.window.decorView.measure(
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY)
        )
        activity.window.decorView.layout(0, 0, 1000, 600)
        val report = OverlayWindowDiagnostics.capture(activity)
        assertTrue(report.contains("visited="))
        assertTrue(report.length < 12000)
        assertTrue(report.contains("system PiP/other-app windows"))
        activity.finish()
    }
}
