package com.prirai.android.nira.diagnostics.testlab

import android.app.Application
import android.os.Looper
import com.prirai.android.nira.media.MpvSupertest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,manifest=Config.NONE)
class TestLabSupertestGateTest {
    @Test fun originalSupertestStillLastsFortySecondsAndGateExcludesBothDirections() {
        val gate=DiagnosticSessionGate.shared;val app=RuntimeEnvironment.getApplication()
        try {
            assertTrue(gate.acquire(DiagnosticOwner.TESTLAB))
            assertFalse(MpvSupertest.start(app,Any(),"test"))
            gate.release(DiagnosticOwner.TESTLAB)
            assertTrue(MpvSupertest.start(app,Any(),"test"))
            assertFalse(gate.acquire(DiagnosticOwner.TESTLAB))
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(39))
            assertTrue(MpvSupertest.remainingSeconds()>0)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100))
            assertEquals(0,MpvSupertest.remainingSeconds());assertNotNull(MpvSupertest.latest())
            assertTrue(gate.acquire(DiagnosticOwner.TESTLAB))
        } finally {gate.release(DiagnosticOwner.TESTLAB);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(41))}
    }
}
