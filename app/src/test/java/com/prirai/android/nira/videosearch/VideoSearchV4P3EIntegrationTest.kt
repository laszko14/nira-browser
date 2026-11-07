package com.prirai.android.nira.videosearch

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * V4 / P3-E: integration of existing P2 query proof, P3-C virtual pagination,
 * exact-URL metadata correlation, owned Room snapshots and P3-D presentation.
 * Every "web document" here is a synthetic fixture; this test NEVER claims
 * that a live GeckoView/site/device has been tested.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class VideoSearchV4P3EIntegrationTest {
    private val scope = VideoSearchScope.Normal("p3e-normal")
    private val other = VideoSearchScope.Normal("p3e-foreign")
    private val site = "https://films.example.org/search?q=Aurora+City"

    private fun film(
        id: String, index: Int, title: String, seconds: Long = 600L,
        source: VideoResultTitleSource = VideoResultTitleSource.CARD,
        image: String? = null,
    ): VideoSearchExtractedFilm = VideoSearchExtractedFilm(
        filmPageUrl = "https://films.example.org/watch?id=$id&lang=pl",
        originalTitle = title, thumbnailUrl = image, durationSeconds = seconds,
        titleSource = source,
        imageSource = if (image == null) VideoResultImageSource.UNKNOWN
            else VideoResultImageSource.STRUCTURED,
        originalIndex = index, publishedAtMs = 1_706_976_000_000L,
    )

    private fun page(vararg entries: VideoSearchExtractedFilm) =
        VideoSearchExtractedPage("ok", entries.toList(), entries.size, false)

    @Test fun queryProofVirtualPagesRoomAndSmartDisplayKeepExactOwnedFilms() = runBlocking {
        val previous = page(film("old", 0, "Old unrelated result"))
        val structured = film("one", 0, "Aurora City S01E01",
            source = VideoResultTitleSource.STRUCTURED,
            image = "https://posters.example.org/aurora.webp")
        val initial = page(
            structured.copy(titleSource = VideoResultTitleSource.LINK,
                originalTitle = "Aurora", thumbnailUrl = null,
                imageSource = VideoResultImageSource.UNKNOWN),
            film("two", 1, "Totally Different Documentary"),
        )
        val correlated = VideoSearchResultCorrelator.correlate(
            listOf(initial.films.first(), structured)
        ).single()
        assertEquals("Aurora City S01E01", correlated.originalTitle)
        assertEquals(VideoResultTitleSource.STRUCTURED, correlated.titleSource)
        assertEquals("https://posters.example.org/aurora.webp", correlated.thumbnailUrl)

        // Search ACK by itself is never proof. A same-document SPA must change
        // exact film identities, not merely the title or loading indicator.
        val afterSearch = page(correlated, initial.films.last())
        val doc = "a".repeat(32)
        assertFalse(VideoSearchWorkerVerification.confirmedAfterQuery(
            doc, doc, previous, previous, site, "Aurora City", site))
        assertTrue(VideoSearchWorkerVerification.confirmedAfterQuery(
            doc, doc, afterSearch, previous, site, "Aurora City", site))

        val collector = VideoSearchPaginationCollector(site, 200)
        assertTrue(collector.beginDocument(site))
        assertEquals(2, collector.appendWindow(0, afterSearch))
        assertEquals(2, collector.uniqueCount)
        assertTrue(collector.recordAction())
        collector.armSameDocumentContinuation()
        assertTrue(VideoSearchDynamicContinuationPolicy.mayProbeReplacement(
            doc, doc, site, site,
            VideoSearchExtractedPage("stale", emptyList(), 0, false)))

        // Old recycled DOM identities do not advance; the existing two
        // originals and all their original metadata must remain untouched.
        try {
            collector.appendVerifiedReplacement(page(
                correlated.copy(originalIndex = 0),
                initial.films.last().copy(originalIndex = 1)))
            fail("Identical SPA cards are not proof of a new page")
        } catch (_: IllegalArgumentException) {}
        assertEquals(2, collector.uniqueCount)
        assertEquals(2, collector.nextCardIndex)

        val replacement = page(
            initial.films.last().copy(originalIndex = 0),
            film("three", 1, "HD 4K Aurora City S01E01", 601L),
        )
        assertEquals(1, collector.appendVerifiedReplacement(replacement))
        assertEquals(3, collector.uniqueCount)
        assertEquals(1, collector.pageActions)
        val crawled = collector.snapshot(VideoSearchPaginationStop.STALLED)
        assertEquals(3, crawled.films.size)
        assertEquals(VideoSearchQueueStatus.STOPPED,
            VideoSearchWorkerVerification.finalQueueStatus(crawled))
        assertEquals(listOf("one", "two", "three"),
            crawled.films.map { it.filmPageUrl.substringAfter("id=").substringBefore("&") })

        val db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), VideoSearchDatabase::class.java
        ).build()
        try {
            val repo = VideoSearchRepository(db.dao())
            val run = VideoSearchRun(
                id = "p3e-normal-run", scope = scope, query = "Aurora City",
                siteIds = listOf("first"), parallelism = VideoSearchParallelism.ONE,
                status = VideoSearchRunState.STOPPED, startedAtMs = 1_000L
            )
            val task = VideoSearchSiteTask(
                runId = run.id, siteId = "first", scope = scope,
                status = VideoSearchSiteState.STOPPED, uniqueResults = 3,
                resultLimit = 200, visitedPages = crawled.visitedPages
            )
            val saved = crawled.films.mapIndexed { index, item ->
                VideoSearchFilm(
                    id = "source-$index", runId = run.id, siteId = "first",
                    scope = scope, siteName = "Verified source",
                    filmPageUrl = item.filmPageUrl,
                    originalTitle = item.originalTitle,
                    thumbnailUrl = item.thumbnailUrl,
                    durationSeconds = item.durationSeconds,
                    titleSource = item.titleSource,
                    imageSource = item.imageSource,
                    originalIndex = index, foundAtMs = 1_001L,
                    publishedAtMs = item.publishedAtMs
                )
            }
            repo.putSnapshot(run, listOf(task), saved)
            assertNull(repo.resultsSnapshot(other, run.id))
            val restored = repo.resultsSnapshot(scope, run.id)!!
            assertEquals(VideoSearchRunState.STOPPED, restored.run.status)
            assertEquals(3, restored.films.size)
            assertEquals(saved.map { it.filmPageUrl },
                restored.films.map { it.filmPageUrl })
            assertEquals(VideoResultTitleSource.STRUCTURED,
                restored.films.first().titleSource)
            assertEquals("https://posters.example.org/aurora.webp",
                restored.films.first().thumbnailUrl)
            assertEquals(1_706_976_000_000L, restored.films.first().publishedAtMs)
            val display = VideoSearchResultsPresenter.project(
                restored.run, restored.tasks, restored.films, restored.siteNames
            )
            assertEquals(3, display.totalFilms)
            assertEquals(1, display.partialSites)
            assertEquals(0, display.completedSites)
            val smart = VideoSearchResultsMerge.smartGroup(display.visibleFilms)
            assertEquals(listOf(listOf("source-0", "source-2"), listOf("source-1")),
                smart.map { group -> group.films.map { it.id } })
            assertEquals(3, smart.sumOf { it.films.size })
            assertEquals(3, VideoSearchResultsMerge.group(
                display.visibleFilms, false, false).size)
            // Existing exact-duration OR mode intentionally links unrelated
            // 600-second films; the new smart mode does NOT.
            assertEquals(1, VideoSearchResultsMerge.group(
                display.visibleFilms, false, true).size)
            assertEquals(1, VideoSearchResultsPresenter.groups(display).size)
            assertFalse(smart.first().toString().contains("films.example.org"))
        } finally {
            db.close()
        }
    }

    @Test fun crossOriginAndWrongGenerationAreRejectedBeforeRoomCanSeeThem() {
        val collector = VideoSearchPaginationCollector(site, 5)
        val doc = "b".repeat(32)
        assertTrue(collector.beginDocument(site))
        assertEquals(1, collector.appendWindow(0, page(
            film("safe", 0, "Verified local film"))))
        assertFalse(VideoSearchDynamicContinuationPolicy.mayProbeReplacement(
            doc, "c".repeat(32), site, site,
            VideoSearchExtractedPage("stale", emptyList(), 0, false)))
        assertFalse(VideoSearchDynamicContinuationPolicy.mayProbeReplacement(
            doc, doc, site, "https://films.example.org/another?q=x",
            VideoSearchExtractedPage("stale", emptyList(), 0, false)))
        assertTrue(collector.recordAction())
        try {
            collector.appendVerifiedReplacement(page(VideoSearchExtractedFilm(
                "https://external.example/watch?id=1", "Cross-site", null, 600,
                VideoResultTitleSource.CARD, VideoResultImageSource.UNKNOWN, 0
            )))
            fail("Cross-origin pagination content must be rejected")
        } catch (_: IllegalArgumentException) {}
        assertEquals(1, collector.uniqueCount)
        assertEquals("https://films.example.org/watch?id=safe&lang=pl",
            collector.snapshot(VideoSearchPaginationStop.UNSAFE)
                .films.single().filmPageUrl)
    }
}
