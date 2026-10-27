package com.prirai.android.nira.diagnostics.testlab

import android.app.Activity
import android.app.Application
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
    private fun engine()=TestLabEngine({SystemClock.elapsedRealtimeNanos()},LabIdentity(1165,"0.5.22-alpha71-mpv39"))
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
    class ProbeActivity:Activity()
}
