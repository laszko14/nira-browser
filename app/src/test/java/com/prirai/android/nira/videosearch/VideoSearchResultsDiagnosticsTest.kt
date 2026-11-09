package com.prirai.android.nira.videosearch

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class VideoSearchResultsDiagnosticsTest {
    private val owner = VideoSearchScope.Normal("profile-A")
    private fun run(sites: List<String> = listOf("a","b")) = VideoSearchRun(
        id = "run-a", scope = owner, query = "private_search_phrase",
        siteIds = sites, parallelism = VideoSearchParallelism.ALL,
        status = VideoSearchRunState.COMPLETED, startedAtMs = 100L,
    )
    private fun film(site: String, index: Int, title: String = "SECRET_TITLE") =
        VideoSearchFilm(
            id = "$site-$index", runId = "run-a", siteId = site,
            scope = owner, siteName = "SECRET_SITE_NAME",
            filmPageUrl = "https://films.example/video?id=$index&token=SECRET",
            originalTitle = title, titleSource = VideoResultTitleSource.CARD,
            durationSeconds = 100L, originalIndex = index, foundAtMs = 200L,
        )
    private fun task(
        site: String, count: Int,
        status: VideoSearchSiteState = VideoSearchSiteState.COMPLETED,
    ) = VideoSearchSiteTask(
        runId = "run-a", siteId = site, scope = owner,
        status = status, uniqueResults = count, visitedPages = 2,
        completionReason = if (status == VideoSearchSiteState.COMPLETED)
            VideoSearchCompletionReason.NO_MORE_RESULTS else null,
    )
    private fun snapshot(
        sites: List<String> = listOf("a","b"),
        tasks: List<VideoSearchSiteTask> = listOf(task("a",2),task("b",1)),
        films: List<VideoSearchFilm> = listOf(film("a",0),film("a",1),film("b",2)),
    ) = VideoSearchSavedResults(run(sites), tasks, films, emptyMap())

    private fun frame(
        snapshot: VideoSearchSavedResults = snapshot(),
        filter: String? = null, byWebsite: Boolean = true,
        mergeName: Boolean = false, mergeDuration: Boolean = false,
        smart: Boolean = false, limit: Int = 150,
        expanded: Set<String>? = null, selecting: Boolean = false,
        selected: Int = 0,
    ): VideoSearchResultsDiagnosticFrame {
        val projected = VideoSearchResultsPresenter.project(
            snapshot.run, snapshot.tasks, snapshot.films,
            selectedSiteId = filter)
        val merging = !byWebsite && (smart || mergeName || mergeDuration)
        val merged = if (!merging) emptyList() else if (smart)
            VideoSearchResultsMerge.smartGroup(projected.visibleFilms)
        else VideoSearchResultsMerge.group(
            projected.visibleFilms, mergeName, mergeDuration)
        val total = if (merging) merged.size else projected.visibleFilms.size
        val paged = if (merging) merged.take(limit).map { it.primary }
            else projected.visibleFilms.take(limit)
        val slice = projected.copy(visibleFilms = paged)
        val groups = VideoSearchResultsPresenter.groups(slice, filter)
        return VideoSearchResultsDiagnosticFrame(snapshot, projected,
            filter, VideoSearchSort.ORIGINAL, byWebsite, mergeName,
            mergeDuration, smart, merged, total, paged,
            VideoSearchScrollKeys.visible(slice, groups, byWebsite, expanded),
            expanded, limit, VideoSearchView.LIST, selecting, selected)
    }

    @Test fun completedCoherentSiteIsOnlyLocallyOk() {
        val assessments = VideoSearchResultsDiagnosticAnalyzer.assess(frame())
        assertEquals(listOf(VideoSearchDiagnosticSiteVerdict.OK,
            VideoSearchDiagnosticSiteVerdict.OK), assessments.map { it.verdict })
        assertEquals(listOf(2,1), assessments.map { it.stored })
        assertEquals(listOf(2,1), assessments.map { it.renderEligible })
        val all = VideoSearchResultsDiagnosticAnalyzer.events(frame())
        assertEquals(VideoSearchDiagnosticClaim.OBSERVATION,all[0].claim)
        assertEquals("RESULTS_SCREEN_COUNTS",all[0].reason)
        val first = all[1]
        assertEquals(VideoSearchDiagnosticClaim.INFERENCE,first.claim)
        assertEquals(VideoSearchDiagnosticDecision.SEEN,first.decision)
        assertEquals("LOCAL_DISPLAY_COUNTS_MATCH",first.reason)
        assertEquals(3,first.counts[VideoSearchDiagnosticCount.DISPLAYED_FILMS])
        assertTrue(first.fields[VideoSearchDiagnosticField.DETAIL]!!
            .contains("notViewport=true"))
    }

    @Test fun siteFilterAndGroupCollapseAreIntentionalNotMissingFilms() {
        val filtered = frame(filter = "b")
        val assessments = VideoSearchResultsDiagnosticAnalyzer.assess(filtered)
        assertEquals(listOf(0,1),assessments.map { it.projected })
        assertTrue(assessments.all { it.verdict == VideoSearchDiagnosticSiteVerdict.OK })
        assertEquals("USER_SITE_FILTER",
            VideoSearchResultsDiagnosticAnalyzer.events(filtered)[1].reason)
        val collapsed = frame(expanded = setOf("a"))
        assertEquals(listOf(2,0),
            VideoSearchResultsDiagnosticAnalyzer.assess(collapsed).map {
                it.renderEligible })
        assertEquals("USER_GROUP_COLLAPSE",
            VideoSearchResultsDiagnosticAnalyzer.events(collapsed)[1].reason)
        assertTrue(VideoSearchResultsDiagnosticAnalyzer.assess(collapsed)
            .all { it.verdict == VideoSearchDiagnosticSiteVerdict.OK })
    }

    @Test fun mergeAcrossSitesIsNotAStorageLossAndRetainsSiteProvenance() {
        val merged = frame(byWebsite = false, mergeName = true)
        assertEquals(1,merged.allEntries)
        assertEquals(3,merged.snapshot.films.size)
        val summary = VideoSearchResultsDiagnosticAnalyzer.events(merged)[1]
        assertEquals("USER_MERGE_COLLAPSE",summary.reason)
        assertEquals(3,summary.counts[VideoSearchDiagnosticCount.SAVED_FILMS])
        assertEquals(1,summary.counts[VideoSearchDiagnosticCount.DISPLAYED_FILMS])
        assertEquals(2,VideoSearchResultsDiagnosticAnalyzer.assess(merged).size)
        assertFalse(VideoSearchResultsDiagnosticAnalyzer.assess(merged)
            .any { it.verdict == VideoSearchDiagnosticSiteVerdict.PROBLEM })
    }

    @Test fun pageLimitDoesNotFalselyReportDataLoss() {
        val siteFilms=(0 until 202).map { film("a",it) }
        val s=snapshot(sites=listOf("a"),tasks=listOf(task("a",200)),
            films=siteFilms.take(200))
        val f=frame(snapshot=s,byWebsite=false,limit=150)
        val event=VideoSearchResultsDiagnosticAnalyzer.events(f)[1]
        assertEquals("USER_PAGE_LIMIT",event.reason)
        assertEquals(200,event.counts[VideoSearchDiagnosticCount.SAVED_FILMS])
        assertEquals(150,event.counts[VideoSearchDiagnosticCount.DISPLAYED_FILMS])
        assertEquals(VideoSearchDiagnosticSiteVerdict.OK,
            VideoSearchResultsDiagnosticAnalyzer.assess(f).single().verdict)
    }

    @Test fun detectFirstRealDiscrepancyBeforeFiltersOrMerging() {
        val wrongTask=snapshot(tasks=listOf(task("a",1),task("b",1)))
        val found=VideoSearchResultsDiagnosticAnalyzer.assess(frame(snapshot=wrongTask))
        assertEquals("TASK_ROOM_COUNT_MISMATCH",found.first().reason)
        assertEquals(VideoSearchDiagnosticSiteVerdict.PROBLEM,found.first().verdict)
        assertEquals(2,found.first().stored)
        assertEquals(1,found.first().taskClaimed)
        val wrongProjection=frame()
        val altered=wrongProjection.copy(projected=wrongProjection.projected.copy(totalFilms=99))
        assertEquals("STORAGE_TO_PROJECTED_DIVERGENCE",
            VideoSearchResultsDiagnosticAnalyzer.events(altered)[1].reason)
    }

    @Test fun interruptedAndEmptyRemainDistinctFromWebsiteZeroProof() {
        val halted=snapshot(tasks=listOf(task("a",2,VideoSearchSiteState.FAILED),
            task("b",1,VideoSearchSiteState.STOPPED)))
        val a=VideoSearchResultsDiagnosticAnalyzer.assess(frame(snapshot=halted))
        assertEquals(listOf(VideoSearchDiagnosticSiteVerdict.INTERRUPTED,
            VideoSearchDiagnosticSiteVerdict.INTERRUPTED),a.map{it.verdict})
        val empty=snapshot(sites=listOf("a"),tasks=listOf(task("a",0)),
            films=emptyList())
        assertEquals("ZERO_REQUIRES_PAGE_EVIDENCE",
            VideoSearchResultsDiagnosticAnalyzer.assess(frame(snapshot=empty))
                .single().reason)
        assertEquals(VideoSearchDiagnosticSiteVerdict.UNCERTAIN,
            VideoSearchResultsDiagnosticAnalyzer.assess(frame(snapshot=empty))
                .single().verdict)
        val active=snapshot(sites=listOf("a"),tasks=listOf(task("a",0,
            VideoSearchSiteState.ACTIVE)),films=emptyList())
        assertEquals("SITE_ACTIVE",
            VideoSearchResultsDiagnosticAnalyzer.assess(frame(snapshot=active))
                .single().reason)
    }

    @Test fun unexpectedForeignRunOrProfileCannotBeDiagnosedAsOwned() {
        val foreign=film("a",1).copy(scope=VideoSearchScope.Normal("foreign"))
        val s=snapshot(sites=listOf("a"),tasks=listOf(task("a",1)),
            films=listOf(foreign))
        // Presenter normally rejects a foreign row. Diagnostics also guards
        // itself against an accidentally bypassed future projector.
        try {
            VideoSearchResultsDiagnosticAnalyzer.assess(
                VideoSearchResultsDiagnosticFrame(s,
                    VideoSearchResultsDisplay(emptyList(),emptyList(),0,0,0),
                    null,VideoSearchSort.ORIGINAL,false,false,false,false,
                    emptyList(),0,emptyList(),emptyList(),null,150,
                    VideoSearchView.LIST,false,0))
            fail("foreign data must be rejected")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun noRecordingByDefaultAndNoSensitiveDataInNormalJsonl() {
        val f=frame()
        assertFalse(VideoSearchDiagnosticResultsHost.isRecording(owner))
        VideoSearchDiagnosticResultsHost.observe(f)
        val folder=File(RuntimeEnvironment.getApplication().cacheDir,
            "diagnostic-stage4-"+UUID.randomUUID())
        val recorder=VideoSearchDiagnosticRecorder(owner,folder)
        VideoSearchDiagnosticResultsHost.attach(recorder)
        try {
            assertTrue(VideoSearchDiagnosticResultsHost.isRecording(owner))
            assertFalse(VideoSearchDiagnosticResultsHost.isRecording(
                VideoSearchScope.Normal("foreign")))
            VideoSearchDiagnosticResultsHost.observe(f)
            VideoSearchDiagnosticResultsHost.observe(f)
            VideoSearchDiagnosticResultsHost.viewport(
                owner,"run-a",VideoSearchView.LIST,2,1)
            VideoSearchDiagnosticResultsHost.loadFailure(owner,"run-a")
        } finally {
            VideoSearchDiagnosticResultsHost.detach(recorder)
            recorder.stop()
        }
        assertTrue(recorder.awaitStopped())
        val rows=recorder.file!!.readLines().map(::JSONObject)
        val events=rows.filter { it.optString("kind")=="event" }
        assertEquals(6,events.size) // observed counters + inference + 2 sites + viewport + error
        assertEquals(1,events.count { it.optString("reason")=="LOCAL_DISPLAY_COUNTS_MATCH" })
        assertEquals(1,events.count { it.optString("reason")=="VIEWPORT_COMPOSED_FILM_ROWS" })
        val serialized=recorder.file!!.readText()
        assertFalse(serialized.contains("private_search_phrase"))
        assertFalse(serialized.contains("SECRET_TITLE"))
        assertFalse(serialized.contains("SECRET_SITE_NAME"))
        assertFalse(serialized.contains("token=SECRET"))
        assertFalse(f.toString().contains("private_search_phrase"))
    }
}
