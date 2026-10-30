package com.prirai.android.nira.diagnostics.testlab

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.widget.TextView
import android.os.Looper
import android.os.SystemClock
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,manifest=Config.NONE)
class TestLabRuntimeTest {
    private fun engine()=TestLabEngine({SystemClock.elapsedRealtimeNanos()},LabIdentity(1168,"0.5.22-alpha73-clean"))
    @Test fun settingsDoesNotStartMeasurementCountdownBeginsInEligibleActivityOnly() {
        val e=engine();val runtime=TestLabRuntime(RuntimeEnvironment.getApplication(),e,{it is ProbeActivity},false)
        assertTrue(runtime.prepare(TestType.COMPLEX))
        val settings=Robolectric.buildActivity(Activity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4))
        assertEquals(LabPhase.PREPARING,e.phase)
        val browser=Robolectric.buildActivity(ProbeActivity::class.java).setup().visible()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals(LabPhase.PREPARING,e.phase)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1500))
        assertEquals(LabPhase.RUNNING,e.phase)
        assertTrue((runtime.ui.value.remainingSeconds?:0)>=59)
        runtime.stop();assertEquals(LabPhase.PARTIAL,e.phase)
        assertEquals(0,runtime.trackedWindowCount)
        settings.pause().stop().destroy();browser.pause().stop().destroy()
    }
    @Test fun pauseResetsPreparationAndOldWindowIsReleased() {
        val e=engine();val runtime=TestLabRuntime(RuntimeEnvironment.getApplication(),e,{true},false)
        runtime.prepare(TestType.PANELS_GESTURES)
        val first=Robolectric.buildActivity(ProbeActivity::class.java).setup().visible()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        first.pause().stop().destroy()
        assertEquals(0,runtime.trackedWindowCount)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        assertEquals(LabPhase.PREPARING,e.phase)
        val next=Robolectric.buildActivity(ProbeActivity::class.java).setup().visible()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(3500))
        assertEquals(LabPhase.RUNNING,e.phase)
        runtime.stop();next.pause().stop().destroy()
    }
    @Test fun passiveCounterCannotTakeTouchOrKeyboardFocusAndCleanupRemovesIt() {
        val e=engine();val runtime=TestLabRuntime(RuntimeEnvironment.getApplication(),e,{true},false)
        runtime.prepare(TestType.RESOURCES)
        val browser=Robolectric.buildActivity(ProbeActivity::class.java).setup().visible()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        val counter=browser.get().window.decorView.findViewWithTag<android.view.View>(TestLabRuntime.COUNTER_TAG)
        assertNotNull(counter);assertFalse(counter.isClickable);assertFalse(counter.isFocusable);assertFalse(counter.isLongClickable)
        runtime.stop()
        assertNull(browser.get().window.decorView.findViewWithTag<android.view.View>(TestLabRuntime.COUNTER_TAG))
        assertEquals(LabPhase.CANCELLED,e.phase)
        browser.pause().stop().destroy()
    }
    @Test fun videoDiscoveryRetainsLedgerThroughAsyncWorkerFinalization() {
        val e = engine()
        // probesEnabled=true starts the background worker; this was the v1172 loss path.
        val runtime = TestLabRuntime(RuntimeEnvironment.getApplication(), e, { it is ProbeActivity }, true)
        assertTrue(runtime.prepare(TestType.VIDEO_DISCOVERY))
        val browser = Robolectric.buildActivity(ProbeActivity::class.java).setup().visible()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(3500))
        assertEquals(LabPhase.RUNNING, e.phase)
        val observer = runtime.mediaObserver
        assertNotNull(observer)
        observer!!.entry(
            com.prirai.android.nira.media.MediaSessionBinding(Any(), "test-tab", null, false),
            com.prirai.android.nira.media.MediaEntry(
                19L, "https://example.invalid/secret.mp4",
                com.prirai.android.nira.media.MediaFormat.MP4,
                setOf(com.prirai.android.nira.media.MediaSource.DOM),
                "test-group", "Private title", "player"
            )
        )
        runtime.stop()
        val deadline = System.nanoTime() + 5_000_000_000L
        while (e.phase == LabPhase.FINALIZING && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertEquals(LabPhase.PARTIAL, e.phase)
        val report = e.latest()!!
        assertEquals(1, report.videoDiscovery?.sources)
        assertEquals(1, report.videoDiscovery?.groups)
        val exported = TestLabReportGenerator.generate(report)
        assertTrue(exported.text.contains("Film #1"))
        assertFalse(exported.text.contains("Private title"))
        assertFalse(exported.json.contains("secret.mp4"))
        browser.pause().stop().destroy()
    }

    @Test fun videoMetadataRetainsSnapshotWithBackgroundWorker() {
        val e = engine()
        val runtime = TestLabRuntime(RuntimeEnvironment.getApplication(), e, { it is ProbeActivity }, true)
        assertTrue(runtime.prepare(TestType.VIDEO_METADATA))
        val activity = Robolectric.buildActivity(ProbeActivity::class.java).setup().visible()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(3500))
        assertEquals(LabPhase.RUNNING, e.phase)
        val observer = runtime.mediaObserver!!
        val binding = com.prirai.android.nira.media.MediaSessionBinding(Any(), "t", null, false)
        val entry = com.prirai.android.nira.media.MediaEntry(1L,
            "https://example.invalid/signed.mp4", com.prirai.android.nira.media.MediaFormat.MP4,
            setOf(com.prirai.android.nira.media.MediaSource.NETWORK),
            durationSeconds = 73, sizeBytes = 987654321)
        observer.entry(binding, entry)
        observer.displayed(binding, entry)
        runtime.stop()
        val deadline = System.nanoTime() + 5_000_000_000L
        while (e.phase == LabPhase.FINALIZING && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertEquals(LabPhase.PARTIAL, e.phase)
        val metadata = e.latest()!!.videoMetadata!!
        assertEquals(1, metadata.observedSources)
        assertEquals(1, metadata.sizeExact)
        assertEquals(1, metadata.uiDisplayed)
        val export = TestLabReportGenerator.generate(e.latest()!!)
        assertFalse(export.json.contains("signed.mp4"))
        activity.pause().stop().destroy()
    }


    @Test fun sheetOpenedDuringCountdownIsRegisteredBeforeTrackingBegins() {
        val e=engine()
        val runtime=TestLabRuntime(RuntimeEnvironment.getApplication(),e,{it is ProbeActivity},false)
        assertTrue(runtime.prepare(TestType.FULL_AUDIT))
        val browser=Robolectric.buildActivity(ProbeActivity::class.java).setup().visible()
        val sheet=Dialog(browser.get()).apply { setContentView(TextView(context)); show() }
        val window=sheet.window!!
        // No FAB_OPEN token is available in PREPARING, but Window ownership is real.
        assertEquals(LabPhase.PREPARING,e.phase)
        assertNull(runtime.capture)
        runtime.attachWindow(window)
        assertEquals(2,runtime.trackedWindowCount)
        assertNotNull(window.decorView.findViewWithTag<android.view.View>(TestLabRuntime.COUNTER_TAG))
        // The already registered Window is included in begin()'s JankStats attach loop.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(3500))
        assertEquals(LabPhase.RUNNING,e.phase)
        assertNotNull(runtime.capture)
        assertEquals(2,runtime.trackedWindowCount)
        runtime.detachWindow(window)
        assertEquals(1,runtime.trackedWindowCount)
        assertNull(window.decorView.findViewWithTag<android.view.View>(TestLabRuntime.COUNTER_TAG))
        sheet.dismiss()
        runtime.stop()
        assertEquals(0,runtime.trackedWindowCount)
        browser.pause().stop().destroy()
    }
    @Test fun sheetDismissBeforeBeginAndStopWithOpenSheetBothReleaseWindow() {
        val e=engine()
        val runtime=TestLabRuntime(RuntimeEnvironment.getApplication(),e,{it is ProbeActivity},false)
        assertTrue(runtime.prepare(TestType.FULL_AUDIT))
        val browser=Robolectric.buildActivity(ProbeActivity::class.java).setup().visible()
        val before=Dialog(browser.get()).apply {setContentView(TextView(context));show()}
        runtime.attachWindow(before.window!!)
        assertEquals(2,runtime.trackedWindowCount)
        runtime.detachWindow(before.window!!)
        before.dismiss()
        assertEquals(1,runtime.trackedWindowCount)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(3500))
        assertEquals(LabPhase.RUNNING,e.phase)
        val during=Dialog(browser.get()).apply {setContentView(TextView(context));show()}
        // No operation token is requested; opening a Window is enough.
        runtime.attachWindow(during.window!!)
        runtime.attachWindow(during.window!!)
        assertEquals(2,runtime.trackedWindowCount)
        runtime.stop()
        assertEquals(0,runtime.trackedWindowCount)
        assertNull(during.window!!.decorView.findViewWithTag<android.view.View>(TestLabRuntime.COUNTER_TAG))
        during.dismiss()
        browser.pause().stop().destroy()
    }
    @Test fun cancelDuringCountdownReleasesSheetAndNeverStartsMeasurement() {
        val e=engine()
        val runtime=TestLabRuntime(RuntimeEnvironment.getApplication(),e,{it is ProbeActivity},false)
        assertTrue(runtime.prepare(TestType.FULL_AUDIT))
        val browser=Robolectric.buildActivity(ProbeActivity::class.java).setup().visible()
        val sheet=Dialog(browser.get()).apply {setContentView(TextView(context));show()}
        runtime.attachWindow(sheet.window!!)
        runtime.stop()
        assertEquals(LabPhase.CANCELLED,e.phase)
        assertEquals(0,runtime.trackedWindowCount)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4))
        assertEquals(LabPhase.CANCELLED,e.phase)
        sheet.dismiss()
        browser.pause().stop().destroy()
    }

    class ProbeActivity:Activity()
}
