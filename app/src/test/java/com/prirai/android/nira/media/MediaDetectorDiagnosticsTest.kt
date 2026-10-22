package com.prirai.android.nira.media

import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test

class MediaDetectorDiagnosticsTest {
    @Before fun prepare() = MediaDetectorDiagnostics.clearForTest()
    @After fun cleanup() = MediaDetectorDiagnostics.clearForTest()

    @Test fun reportsInstallationAndTheIndependentNetworkProbe() {
        MediaDetectorDiagnostics.installed()
        MediaDetectorDiagnostics.setPrivateAllowed(false)
        MediaDetectorDiagnostics.networkProbe()
        val snapshot = MediaDetectorDiagnostics.snapshot(null)
        assertEquals("Zainstalowane", snapshot.installState)
        assertEquals("wyłączone", snapshot.privatePermission)
        assertEquals(1, snapshot.networkProbeCount)
        assertEquals(0, snapshot.allTabMediaCount)
        assertTrue(snapshot.asPolishReport().contains("webRequest: TAK"))
    }

    @Test fun isolatesOpenTabsAndDoesNotPrintAnyIdentifierOrUrl() {
        MediaDetectorDiagnostics.detected(MediaSessionBinding(Any(), "tab-A-supersecret", "profile-secret", true), MediaFormat.MP4)
        MediaDetectorDiagnostics.detected(MediaSessionBinding(Any(), "tab-B-supersecret", "profile-secret", true), MediaFormat.WEBM)
        MediaDetectorDiagnostics.detected("tab-B-supersecret", MediaFormat.WEBM)
        val current = MediaDetectorDiagnostics.snapshot("tab-A-supersecret", true, "profile-secret")
        assertEquals(3, current.allTabMediaCount)
        assertEquals(1, current.selectedTabMediaCount)
        assertTrue(current.selectedIsPrivate)
        assertTrue(current.selectedHasExtraProfile)
        assertEquals(MediaFormat.MP4, current.selectedLastFormat)
        val report = current.asPolishReport()
        assertFalse(report.contains("secret"))
        assertFalse(report.contains("http"))
        assertEquals(0, MediaDetectorDiagnostics.snapshot("tab-A-supersecret", false, "profile-secret").allTabMediaCount)
        assertEquals(0, MediaDetectorDiagnostics.snapshot("tab-A-supersecret", true, null).selectedTabMediaCount)
        MediaDetectorDiagnostics.retainOpenTabs(setOf("tab-B-supersecret"))
        assertEquals(0, MediaDetectorDiagnostics.snapshot("tab-A-supersecret", true, "profile-secret").selectedTabMediaCount)
        assertEquals(2, MediaDetectorDiagnostics.snapshot("tab-B-supersecret", true, "profile-secret").allTabMediaCount)
    }

    @Test fun failedInstallAndRejectedEventsHaveVisibleStatus() {
        MediaDetectorDiagnostics.installFailed()
        MediaDetectorDiagnostics.rejected()
        val snapshot = MediaDetectorDiagnostics.snapshot(null)
        assertEquals(1, snapshot.rejectedCount)
        assertTrue(snapshot.asPolishReport().contains("BŁĄD"))
    }
}
