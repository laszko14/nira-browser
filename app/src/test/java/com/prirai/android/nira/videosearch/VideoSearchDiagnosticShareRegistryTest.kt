package com.prirai.android.nira.videosearch

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.app.Application

/** Stage 5: no sharing without an explicit grant and a closed recorder. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class VideoSearchDiagnosticShareRegistryTest {
    private val normal = VideoSearchScope.Normal("owner-A")
    private val privateOwner = VideoSearchScope.Private("owner-A","epoch-A")
    private val foreign = VideoSearchScope.Private("owner-A","epoch-B")

    private fun privateRecorder() = VideoSearchDiagnosticRecorder(privateOwner,
        directory = null, sessionId = "11111111-1111-4111-8111-111111111111")

    @Test fun liveRecordingHasNoShareTicketAndNoAutomaticFile() {
        var clock=100L
        val registry=VideoSearchDiagnosticShareRegistry { clock }
        val recorder=privateRecorder()
        try {
            assertNull(recorder.file)
            assertTrue(recorder.state().active)
            assertNull(registry.issue(recorder))
            assertNull(registry.resolve("f".repeat(32),true))
            assertTrue(recorder.privateLines(privateOwner).isEmpty() ||
                recorder.privateLines(privateOwner).all { JSONObject(it).optString("kind") ==
                    "session_start" })
        } finally {
            recorder.stop()
            assertTrue(recorder.awaitStopped())
        }
    }

    @Test fun closedPrivateReportRequiresGrantCurrentOwnerAndUnexpiredToken() {
        var clock=100L
        val registry=VideoSearchDiagnosticShareRegistry { clock }
        val recorder=privateRecorder()
        recorder.record(VideoSearchDiagnosticEvent(VideoSearchDiagnosticStage.FORM,
            VideoSearchDiagnosticClaim.OBSERVATION,VideoSearchDiagnosticDecision.SEEN,
            "SITE_FIELD_FOUND",fields=mapOf(VideoSearchDiagnosticField.SEARCH_QUERY to
                "Something private")))
        recorder.stop()
        assertTrue(recorder.awaitStopped())
        assertNull(recorder.file)
        val ticket=registry.issue(recorder)!!
        assertTrue(ticket.matches(Regex("[0-9a-f]{32}")))
        assertNull(registry.resolve("a".repeat(32),true))
        assertNull(registry.resolve(ticket,false))
        assertSame(recorder,registry.resolve(ticket,true)?.recorder)
        clock+=VideoSearchDiagnosticShareRegistry.TTL_MS-1
        assertNotNull(registry.resolve(ticket,true))
        clock++
        assertNull(registry.resolve(ticket,true))
        assertNull(registry.resolve(ticket,true))
    }

    @Test fun newShareTicketRevokesPriorAndExplicitRevokeIsImmediate() {
        var clock=100L
        val registry=VideoSearchDiagnosticShareRegistry { clock }
        val recorder=privateRecorder()
        recorder.stop()
        assertTrue(recorder.awaitStopped())
        val first=registry.issue(recorder)!!
        val second=registry.issue(recorder)!!
        assertNotEquals(first,second)
        assertNull(registry.resolve(first,true))
        assertNotNull(registry.resolve(second,true))
        registry.revoke(recorder)
        assertNull(registry.resolve(second,true))
    }

    @Test fun privateLifetimeRetirementInvalidatesShareAndClearsRam() {
        val registry=VideoSearchDiagnosticShareRegistry()
        val recorder=privateRecorder()
        recorder.record(VideoSearchDiagnosticEvent(VideoSearchDiagnosticStage.BRIDGE,
            VideoSearchDiagnosticClaim.OBSERVATION,
            VideoSearchDiagnosticDecision.INTERRUPTED,"DIAG_RESPONSE_TIMEOUT"))
        recorder.stop("USER_STOP_PARTIAL")
        assertTrue(recorder.awaitStopped())
        val token=registry.issue(recorder)!!
        assertFalse(recorder.privateLines(privateOwner).isEmpty())
        assertFalse(recorder.retirePrivateLifetime(foreign))
        assertNotNull(registry.resolve(token,true))
        assertTrue(recorder.retirePrivateLifetime(privateOwner))
        assertNull(registry.resolve(token,true))
        assertTrue(recorder.privateLines(privateOwner).isEmpty())
        assertNull(recorder.file)
    }

    @Test fun exportManifestExplicitlyMarksPartialEvenWhenFooterClosed() {
        val recorder=privateRecorder()
        recorder.record(VideoSearchDiagnosticEvent(VideoSearchDiagnosticStage.BRIDGE,
            VideoSearchDiagnosticClaim.OBSERVATION,
            VideoSearchDiagnosticDecision.INTERRUPTED,"DIAG_PORT_DISCONNECTED"))
        recorder.stop("USER_STOP_PARTIAL")
        assertTrue(recorder.awaitStopped())
        val manifest=JSONObject(VideoSearchDiagnosticReportAccess.manifest(recorder))
        assertEquals("export_manifest",manifest.getString("kind"))
        assertTrue(manifest.getBoolean("private"))
        assertTrue(manifest.getBoolean("incomplete"))
        assertEquals(1L,manifest.getLong("interruptions"))
        assertEquals("CLOSED",manifest.getString("fileState"))
        assertEquals(0L,manifest.getLong("dropped"))
        assertFalse(manifest.toString().contains(privateOwner.profileId))
        assertFalse(manifest.toString().contains("epoch-A"))
    }

    @Test fun cleanEndManifestCannotClaimDeviceOrSiteVerification() {
        val recorder=privateRecorder()
        recorder.stop("USER_STOP_DRAINED")
        assertTrue(recorder.awaitStopped())
        val manifest=JSONObject(VideoSearchDiagnosticReportAccess.manifest(recorder))
        assertFalse(manifest.getBoolean("incomplete"))
        assertEquals("LOCAL_OBSERVATIONS_NOT_DEVICE_PROOF",
            manifest.getString("note"))
        assertEquals(0L,manifest.getLong("recorded"))
    }
}
