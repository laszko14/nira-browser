package com.prirai.android.nira.videosearch

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Stage 6: synthetic integration, not a real Gecko website or device test. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class VideoSearchDiagnosticsStage6Test {
    private val owner = VideoSearchScope.Private("profile-A", "epoch-A")

    private fun run(state: VideoSearchRunState = VideoSearchRunState.COMPLETED) =
        VideoSearchRun("run-1", owner, "SECRET_PRIVATE_QUERY",
            listOf("a", "b"), VideoSearchParallelism.ALL, state, 100L)

    private fun film(site: String, index: Int) = VideoSearchFilm(
        id = site + index, runId = "run-1", siteId = site, scope = owner,
        siteName = "SECRET_PRIVATE_SITE",
        filmPageUrl = "https://example.invalid/v?id=" + index + "&token=SECRET",
        originalTitle = "SECRET_PRIVATE_TITLE",
        originalIndex = index, foundAtMs = 101L,
    )

    private fun task(
        site: String, count: Int, state: VideoSearchSiteState = VideoSearchSiteState.COMPLETED,
    ) = VideoSearchSiteTask(
        runId = "run-1", siteId = site, scope = owner,
        uniqueResults = count, status = state, visitedPages = 2,
        completionReason = if (state == VideoSearchSiteState.COMPLETED)
            VideoSearchCompletionReason.NO_MORE_RESULTS else null,
    )

    private fun snapshot(
        tasks: List<VideoSearchSiteTask> = listOf(task("a", 2), task("b", 1)),
        state: VideoSearchRunState = VideoSearchRunState.COMPLETED,
    ) = VideoSearchPrivateRunSnapshot(run(state),
        listOf(film("a", 1), film("a", 2), film("b", 3)), tasks)

    @Test fun privatePerSiteCountsDoNotMistakePaginationForLostResults() {
        val snapshot = snapshot()
        val first = VideoSearchPrivateResultsDiagnosticAnalyzer.events(
            snapshot, snapshot.films.take(2))
        assertEquals(3, first.size)
        assertEquals("PRIVATE_RESULTS_SCREEN_COUNTS", first[0].reason)
        assertEquals(2, first[0].counts[VideoSearchDiagnosticCount.DISPLAYED_FILMS])
        assertEquals("PRIVATE_LOCAL_RAM_COUNTS_MATCH", first[1].reason)
        assertEquals("PRIVATE_PAGE_LIMIT_NOT_LOSS", first[2].reason)
        val all = VideoSearchPrivateResultsDiagnosticAnalyzer.events(
            snapshot, snapshot.films)
        assertEquals(listOf(2, 1), all.drop(1).map {
            it.counts[VideoSearchDiagnosticCount.DISPLAYED_FILMS]
        })
        assertTrue(all.drop(1).all {
            it.reason == "PRIVATE_LOCAL_RAM_COUNTS_MATCH" &&
                it.decision == VideoSearchDiagnosticDecision.SEEN
        })
    }

    @Test fun privateTaskMismatchAndStoppedRunRemainExplicitFailures() {
        val snapshot = snapshot(tasks = listOf(task("a", 1),
            task("b", 1, VideoSearchSiteState.STOPPED)))
        val events = VideoSearchPrivateResultsDiagnosticAnalyzer.events(
            snapshot, snapshot.films)
        assertEquals("PRIVATE_TASK_RAM_COUNT_MISMATCH", events[1].reason)
        assertEquals(VideoSearchDiagnosticDecision.FAILED, events[1].decision)
        assertEquals(VideoSearchDiagnosticDecision.INTERRUPTED, events[2].decision)
        val missing = VideoSearchPrivateResultsDiagnosticAnalyzer.events(
            snapshot(tasks = listOf(task("a", 2))), emptyList())
        assertEquals("PRIVATE_MISSING_SITE_TASK", missing[2].reason)
        val pending = VideoSearchPrivateResultsDiagnosticAnalyzer.events(
            snapshot(state = VideoSearchRunState.RUNNING), emptyList())
        assertTrue(pending.drop(1).all {
            it.decision == VideoSearchDiagnosticDecision.UNCERTAIN
        })
    }

    @Test fun privateZeroAndForeignEpochCannotBePresentedAsSuccess() {
        val emptyRun = VideoSearchRun("empty", owner, "SECRET",
            listOf("a"), VideoSearchParallelism.ONE,
            VideoSearchRunState.COMPLETED, 100L)
        val empty = VideoSearchPrivateRunSnapshot(emptyRun, emptyList(),
            listOf(VideoSearchSiteTask("empty", "a", owner,
                status = VideoSearchSiteState.COMPLETED,
                completionReason = VideoSearchCompletionReason.NO_MORE_RESULTS)))
        val events = VideoSearchPrivateResultsDiagnosticAnalyzer.events(empty, emptyList())
        assertEquals("PRIVATE_ZERO_NEEDS_PAGE_EVIDENCE", events[1].reason)
        assertEquals(VideoSearchDiagnosticDecision.UNCERTAIN, events[1].decision)
        val s = snapshot()
        try {
            VideoSearchPrivateResultsDiagnosticAnalyzer.events(s,
                listOf(s.films[2], s.films[0]))
            fail("only exact adapter prefix is accepted")
        } catch (_: IllegalArgumentException) {}
        try {
            VideoSearchPrivateResultsDiagnosticAnalyzer.events(s.copy(
                films = s.films + s.films[0].copy(
                    scope = VideoSearchScope.Private("profile-A", "epoch-B"))),
                emptyList())
            fail("foreign private epoch must be rejected")
        } catch (_: IllegalArgumentException) {}
        assertFalse(s.toString().contains("SECRET_PRIVATE_QUERY"))
        assertFalse(s.toString().contains("SECRET_PRIVATE_TITLE"))
    }

    @Test fun optInRecorderRamExportAndDedupeAcrossPageChanges() {
        val saved = snapshot()
        val recorder = VideoSearchDiagnosticRecorder(owner, directory = null,
            sessionId = "11111111-1111-4111-8111-111111111111")
        VideoSearchDiagnosticResultsHost.attach(recorder)
        try {
            assertTrue(VideoSearchDiagnosticResultsHost.isRecording(owner))
            VideoSearchDiagnosticResultsHost.observePrivate(saved, saved.films.take(2))
            VideoSearchDiagnosticResultsHost.observePrivate(saved, saved.films.take(2))
            VideoSearchDiagnosticResultsHost.observePrivate(saved, saved.films)
        } finally {
            VideoSearchDiagnosticResultsHost.detach(recorder)
            recorder.stop()
        }
        assertTrue(recorder.awaitStopped())
        assertNull(recorder.file)
        val lines = recorder.privateLines(owner)
        val records = lines.map(::JSONObject)
        val events = records.filter { it.optString("kind") == "event" }
        assertEquals(6, events.size)
        assertEquals(2, events.count {
            it.optString("reason") == "PRIVATE_RESULTS_SCREEN_COUNTS"
        })
        assertEquals("session_end", records.last().getString("kind"))
        assertEquals("LOCAL_OBSERVATIONS_NOT_DEVICE_PROOF",
            JSONObject(VideoSearchDiagnosticReportAccess.manifest(recorder))
                .getString("note"))
        val serialized = lines.joinToString("\n")
        assertFalse(serialized.contains("SECRET_PRIVATE_QUERY"))
        assertFalse(serialized.contains("SECRET_PRIVATE_TITLE"))
        assertFalse(serialized.contains("SECRET_PRIVATE_SITE"))
        assertFalse(serialized.contains("token=SECRET"))
        assertTrue(recorder.retirePrivateLifetime(owner))
        assertTrue(recorder.privateLines(owner).isEmpty())
    }

    @Test fun foreignRecorderAndInactiveRecorderNeverAcceptPrivateFrames() {
        val saved = snapshot()
        val foreign = VideoSearchDiagnosticRecorder(
            VideoSearchScope.Private("profile-A", "another-epoch"), directory = null)
        VideoSearchDiagnosticResultsHost.attach(foreign)
        try {
            VideoSearchDiagnosticResultsHost.observePrivate(saved, saved.films)
        } finally {
            VideoSearchDiagnosticResultsHost.detach(foreign)
            foreign.stop()
        }
        assertTrue(foreign.awaitStopped())
        assertEquals(0, foreign.state().recorded)
        VideoSearchDiagnosticResultsHost.observePrivate(saved, saved.films)
    }
}
