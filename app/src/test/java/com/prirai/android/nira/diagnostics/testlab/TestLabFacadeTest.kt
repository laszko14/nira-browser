package com.prirai.android.nira.diagnostics.testlab

import android.app.Application
import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,manifest=Config.NONE)
class TestLabFacadeTest {
    @Test fun initializationFailureReleasesGateAndAllowsAValidRetry() {
        val app=RuntimeEnvironment.getApplication();val manager=app.packageManager
        val info=manager.getPackageInfo(app.packageName,0)
        info.versionCode=0;shadowOf(manager).installPackage(info)
        assertFalse(TestLab.start(app,TestType.COMPLEX))
        assertFalse(TestLab.busy);assertNull(DiagnosticSessionGate.shared.owner)
        assertEquals(LabStartFailure.INITIALIZATION,TestLab.lastStartFailure)
        info.versionCode=1165;shadowOf(manager).installPackage(info)
        try {assertTrue(TestLab.start(app,TestType.COMPLEX));assertTrue(TestLab.busy)}
        finally {
            TestLab.stop()
            val until=System.nanoTime()+5_000_000_000L
            while(TestLab.busy&&System.nanoTime()<until){shadowOf(Looper.getMainLooper()).idle();Thread.yield()}
        }
        assertFalse(TestLab.busy);assertNull(DiagnosticSessionGate.shared.owner)
    }
}
