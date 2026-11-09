package com.prirai.android.nira.videosearch

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Synthetic lifecycle/regression checks; these do not measure physical device FPS. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class VideoSearchDiagnosticsStage7FixTest {
    private val owner = VideoSearchScope.Private("profile-A", "epoch-A")
    private fun recorder(id: String) = VideoSearchDiagnosticRecorder(
        owner, directory = null, sessionId = id,
    )
    private fun events() = listOf(VideoSearchDiagnosticEvent(
        stage = VideoSearchDiagnosticStage.DISPLAY,
        claim = VideoSearchDiagnosticClaim.OBSERVATION,
        decision = VideoSearchDiagnosticDecision.SEEN,
        reason = "PRIVATE_RESULTS_SCREEN_COUNTS",
        trace = VideoSearchDiagnosticTrace(runId = "run-id"),
        counts = mapOf(VideoSearchDiagnosticCount.DISPLAYED_FILMS to 12),
    ))

    @Test fun freshSessionTriggersObservationEvenWithoutViewStateChange() {
        val a = recorder("11111111-1111-4111-8111-111111111111")
        VideoSearchDiagnosticResultsHost.attach(a)
        try {
            assertEquals(a.sessionId,
                VideoSearchDiagnosticResultsHost.recordingSession.value)
            assertEquals(a.sessionId,
                VideoSearchDiagnosticResultsHost.recordingToken(owner))
            VideoSearchDiagnosticResultsHost.submitPrivateEvents(owner, a.sessionId, events())
        } finally {
            VideoSearchDiagnosticResultsHost.detach(a)
            a.stop()
        }
        assertNull(VideoSearchDiagnosticResultsHost.recordingSession.value)
        assertTrue(a.awaitStopped())
        assertEquals(1, a.state().recorded)
        val b = recorder("22222222-2222-4222-8222-222222222222")
        VideoSearchDiagnosticResultsHost.attach(b)
        try {
            assertEquals(b.sessionId,
                VideoSearchDiagnosticResultsHost.recordingSession.value)
            VideoSearchDiagnosticResultsHost.submitPrivateEvents(owner, a.sessionId, events())
            VideoSearchDiagnosticResultsHost.submitPrivateEvents(owner, b.sessionId, events())
        } finally {
            VideoSearchDiagnosticResultsHost.detach(b)
            b.stop()
        }
        assertTrue(b.awaitStopped())
        assertEquals(1, b.state().recorded)
        assertEquals("session_end", JSONObject(b.privateLines(owner).last()).getString("kind"))
        assertNull(VideoSearchDiagnosticResultsHost.recordingSession.value)
    }

    @Test fun foreignEpochAndLateStopCannotAppendToDifferentRecorder() {
        val recorder = recorder("33333333-3333-4333-8333-333333333333")
        VideoSearchDiagnosticResultsHost.attach(recorder)
        try {
            VideoSearchDiagnosticResultsHost.submitPrivateEvents(
                VideoSearchScope.Private("profile-A", "foreign"), recorder.sessionId, events())
            assertEquals(0, recorder.state().attempted)
            VideoSearchDiagnosticResultsHost.submitPrivateEvents(owner, recorder.sessionId, events())
            VideoSearchDiagnosticResultsHost.submitPrivateEvents(owner, recorder.sessionId, events())
            assertEquals(1, recorder.state().attempted)
        } finally {
            VideoSearchDiagnosticResultsHost.detach(recorder)
            recorder.stop()
        }
        VideoSearchDiagnosticResultsHost.submitPrivateEvents(owner, recorder.sessionId, events())
        assertTrue(recorder.awaitStopped())
        assertEquals(1, recorder.state().recorded)
        assertNull(recorder.file)
    }

    @Test fun largePrivateFramesAreBoundedAndRemainLocalCountObservations() {
        for (count in listOf(200, 2_000, 20_000)) {
            val run = VideoSearchRun("run-$count", owner, "SECRET_QUERY",
                listOf("a"), VideoSearchParallelism.ONE,
                VideoSearchRunState.COMPLETED, startedAtMs = 10L)
            val films = List(count) { idx ->
                VideoSearchFilm(
                    id = "film-$idx", runId = run.id, siteId = "a", scope = owner,
                    siteName = "PRIVATE_SITE", filmPageUrl = "https://example.org/v/$idx",
                    originalTitle = "SECRET_TITLE", originalIndex = idx, foundAtMs = 11L)
            }
            val snapshot = VideoSearchPrivateRunSnapshot(run, films,
                listOf(VideoSearchSiteTask(run.id, "a", owner,
                    status = VideoSearchSiteState.COMPLETED, uniqueResults = count,
                    resultLimit = 20_000,
                    completionReason = VideoSearchCompletionReason.NO_MORE_RESULTS)))
            val results = VideoSearchPrivateResultsDiagnosticAnalyzer.events(
                snapshot, films.subList(0, 150))
            assertEquals(2, results.size)
            assertEquals(150, results.first().counts[VideoSearchDiagnosticCount.DISPLAYED_FILMS])
            assertEquals(count, results.first().counts[VideoSearchDiagnosticCount.SAVED_FILMS])
            assertEquals("PRIVATE_RESULTS_SCREEN_COUNTS", results.first().reason)
            assertEquals(
                if (count == 20_000) "PRIVATE_AGGREGATE_LIMIT_UNKNOWN"
                else "PRIVATE_PAGE_LIMIT_NOT_LOSS",
                results[1].reason)
            val json = VideoSearchDiagnosticCodec.event("session", "owner", true, 1L, 20L,
                results[1])
            assertFalse(json.contains("SECRET_TITLE"))
            assertFalse(json.contains("SECRET_QUERY"))
            assertFalse(json.contains("PRIVATE_SITE"))
        }
    }
}
