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
 * P4-F2 cross-module regression: P4 results presentation, original metadata,
 * Room/profile ownership, P4-D smart episodes, E bulk selection and F1 index,
 * plus E1 sizing, restored scroll keys and private RAM lifetime.
 *
 * All film sites, addresses and page data are synthetic. These JVM tests
 * are not live GeckoView, touch, physical RAM/FPS or device verification.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class VideoSearchV4P4F2IntegrationTest {
    private val normal = VideoSearchScope.Normal("p4-f2-normal")
    private val foreign = VideoSearchScope.Normal("p4-f2-foreign")

    private fun database() = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication(), VideoSearchDatabase::class.java
    ).build()

    private fun run(
        scope: VideoSearchScope.Normal, id: String = "same-run",
        sites: List<String> = listOf("first", "second"),
    ) = VideoSearchRun(
        id = id, scope = scope, query = "Space Adventure",
        siteIds = sites, parallelism = VideoSearchParallelism.TWO,
        status = VideoSearchRunState.STOPPED, startedAtMs = 1_000L,
    )

    private fun task(
        run: VideoSearchRun, site: String, count: Int,
        state: VideoSearchSiteState = VideoSearchSiteState.STOPPED,
    ) = VideoSearchSiteTask(
        runId = run.id, scope = run.scope, siteId = site,
        status = state, uniqueResults = count, resultLimit = 500,
        visitedPages = if (count == 0) 0 else 2,
    )

    private fun film(
        run: VideoSearchRun, site: String, index: Int, title: String,
        url: String = "https://" + site + ".example.org/watch?id=" + index +
            "&lang=pl&source=exact",
        seconds: Long? = null,
    ) = VideoSearchFilm(
        id = site + "-" + index,
        runId = run.id, siteId = site, scope = run.scope,
        siteName = "Source " + site, filmPageUrl = url,
        originalTitle = title, originalIndex = index, foundAtMs = 1_100L,
        durationSeconds = seconds, titleSource = VideoResultTitleSource.STRUCTURED,
        publishedAtMs = 1_706_976_000_000L,
    )

    @Test fun roomSmartEpisodeMergeBulkSelectionAndForeignProfileNeverCross() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            val ownerRun = run(normal)
            val a = film(ownerRun, "first", 0, "Space Adventure S02E03", seconds = 1800)
            val b = film(ownerRun, "second", 1, "Space Adventure 2x03", seconds = 1801)
            val next = film(ownerRun, "second", 2, "Space Adventure S02E04", seconds = 1800)
            val original = listOf(a, b, next)
            repo.putSnapshot(ownerRun,
                listOf(task(ownerRun, "first", 1), task(ownerRun, "second", 2)),
                original)
            val otherRun = run(foreign)
            val otherFilm = film(otherRun, "first", 0, "Space Adventure S02E03",
                url = "https://foreign.example.org/watch?id=secret&lang=pl")
            repo.putSnapshot(otherRun,
                listOf(task(otherRun, "first", 1), task(otherRun, "second", 0)),
                listOf(otherFilm))

            val restored = repo.resultsSnapshot(normal, ownerRun.id)!!
            val foreignRestored = repo.resultsSnapshot(foreign, ownerRun.id)!!
            assertEquals(original.map { it.filmPageUrl },
                restored.films.map { it.filmPageUrl })
            assertEquals(listOf(otherFilm.filmPageUrl),
                foreignRestored.films.map { it.filmPageUrl })
            val display = VideoSearchResultsPresenter.project(
                restored.run, restored.tasks, restored.films, restored.siteNames)
            assertEquals(3, display.totalFilms)
            val groups = VideoSearchResultsMerge.smartGroup(display.visibleFilms)
            assertEquals(listOf(2, 1), groups.map { it.films.size })
            assertEquals(setOf(a.id, b.id), groups.first().films.map { it.id }.toSet())
            assertEquals(next.id, groups.last().primary.id)

            val index = VideoSearchBulkSelection.index(restored)!!
            val selected = VideoSearchBulkSelection.toggleMergedIndexed(
                index, emptySet(), groups.first().films)
            assertTrue(VideoSearchBulkSelection.allSelected(selected, groups.first().films))
            assertEquals(listOf(a, b),
                VideoSearchBulkSelection.resolveIndexed(index, selected))
            assertEquals(listOf(a.filmPageUrl, b.filmPageUrl),
                VideoSearchBulkSelection.uniqueLinks(
                    VideoSearchBulkSelection.resolveIndexed(index, selected)))
            assertFalse(VideoSearchBulkSelection.owns(index, otherFilm))
            assertEquals(selected, VideoSearchBulkSelection.toggleMergedIndexed(
                index, selected, listOf(a, otherFilm)))
            assertEquals(selected, VideoSearchBulkSelection.toggleMergedIndexed(
                index, selected, listOf(a.copy(filmPageUrl =
                    "https://first.example.org/watch?id=stale"))))
            assertEquals(3, repo.resultsSnapshot(normal, ownerRun.id)!!.films.size)
            assertEquals(1, repo.resultsSnapshot(foreign, ownerRun.id)!!.films.size)
        } finally {
            db.close()
        }
    }

    @Test fun hundredsOfSavedResultsRemainSelectableBeyondPageAndGroupHeaders() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            val ownerRun = run(normal, id = "f2-hundreds",
                sites = listOf("first", "second", "empty"))
            val first = (0 until 180).map {
                film(ownerRun, "first", it, "Film " + it.toString().padStart(4, '0'))
            }
            val second = (0 until 160).map {
                film(ownerRun, "second", it, "Film " + (180 + it).toString().padStart(4, '0'))
            }
            val originals = first + second
            repo.putSnapshot(ownerRun, listOf(
                task(ownerRun, "first", 180),
                task(ownerRun, "second", 160),
                task(ownerRun, "empty", 0, VideoSearchSiteState.NEEDS_ATTENTION),
            ), originals)
            val saved = repo.resultsSnapshot(normal, ownerRun.id)!!
            val display = VideoSearchResultsPresenter.project(
                saved.run, saved.tasks, saved.films, saved.siteNames,
                sort = VideoSearchSort.TITLE_A_Z)
            assertEquals(340, display.totalFilms)
            assertEquals(340, display.visibleFilms.size)
            val siteGroups = VideoSearchResultsPresenter.groups(display)
            assertEquals(listOf("first", "second", "empty"),
                siteGroups.map { it.source.siteId })
            assertEquals(listOf(180, 160, 0), siteGroups.map { it.savedCount })
            assertEquals(3, display.partialSites) // stopped and attention sites are partial
            val window = display.copy(visibleFilms = display.visibleFilms.take(150))
            assertEquals(150, window.visibleFilms.size)
            val pageGroups = VideoSearchResultsPresenter.groups(window)
            val scrollKeys = VideoSearchScrollKeys.visible(
                window, pageGroups, grouped = true, expanded = null)
            assertTrue(VideoSearchScrollKeys.site("first") in scrollKeys)
            assertTrue(VideoSearchScrollKeys.site("second") in scrollKeys)
            assertTrue(VideoSearchScrollKeys.site("empty") in scrollKeys)
            assertEquals(153, scrollKeys.size)

            val index = VideoSearchBulkSelection.index(saved)!!
            val chosen = VideoSearchBulkSelection.select(
                emptySet(), display.visibleFilms)
            assertEquals(340, chosen.size)
            assertEquals(340, VideoSearchBulkSelection.resolveIndexed(index, chosen).size)
            assertEquals(340, VideoSearchBulkSelection.uniqueLinks(
                VideoSearchBulkSelection.resolveIndexed(index, chosen)).size)
            assertTrue(originals.last().filmPageUrl in
                VideoSearchBulkSelection.uniqueLinks(
                    VideoSearchBulkSelection.resolveIndexed(index, chosen)))
            val filtered = VideoSearchResultsPresenter.project(
                saved.run, saved.tasks, saved.films, saved.siteNames,
                selectedSiteId = "second", sort = VideoSearchSort.DURATION_LONGEST)
            assertEquals(160, filtered.visibleFilms.size)
            assertTrue(filtered.visibleFilms.all { it.siteId == "second" })
            assertEquals(340, filtered.totalFilms)
            assertEquals(originals.map { it.filmPageUrl }.toSet(),
                repo.resultsSnapshot(normal, ownerRun.id)!!.films
                    .map { it.filmPageUrl }.toSet())
        } finally {
            db.close()
        }
    }

    @Test fun returnedResultsTabKeepsSelectionSortingAndScrollWithoutProfileLeak() {
        val currentTab = "p4-f2-results"
        val active = setOf(currentTab, "p4-f2-film-tab")
        val retained = VideoSearchResultsTabUiMemory.forTab(
            currentTab, normal.profileId, active).forRun("f2-run")
        retained.selecting.value = true
        retained.byWebsite.value = false
        retained.mergeSmart.value = true
        retained.sort.value = VideoSearchSort.DATE_NEWEST
        retained.controlsCollapsed.value = true
        retained.controlsCollapsedTouched.value = true
        retained.siteFilter.value = "second"
        retained.selectedFilmKeys.value = setOf("first:first-0", "second:second-1")
        val count = retained.pageSizeFor("second", VideoSearchSort.DATE_NEWEST,
            byWebsite = false, mergeSmart = true)
        count.intValue = 300
        val grid = retained.gridState
        val list = retained.listState
        val returned = VideoSearchResultsTabUiMemory.forTab(
            currentTab, normal.profileId, active).forRun("f2-run")
        assertSame(retained, returned)
        assertSame(grid, returned.gridState)
        assertSame(list, returned.listState)
        assertEquals(300, returned.pageSizeFor("second", VideoSearchSort.DATE_NEWEST,
            byWebsite = false, mergeSmart = true).intValue)
        assertEquals(setOf("first:first-0", "second:second-1"),
            returned.selectedFilmKeys.value)
        assertEquals("second", returned.siteFilter.value)
        assertTrue(returned.mergeSmart.value)
        assertTrue(returned.controlsCollapsed.value)
        assertEquals(VideoSearchSort.DATE_NEWEST, returned.sort.value)

        assertTrue(VideoSearchResultsTabUiMemory.forTab(
            currentTab, foreign.profileId, active).forRun("f2-run")
            .selectedFilmKeys.value.isEmpty())
        assertTrue(VideoSearchResultsTabUiMemory.forTab(
            currentTab, normal.profileId, active).forRun("other-run")
            .selectedFilmKeys.value.isEmpty())
        assertTrue(VideoSearchResultsTabUiMemory.forTab(
            "p4-f2-other-tab", normal.profileId, active + "p4-f2-other-tab")
            .forRun("f2-run").selectedFilmKeys.value.isEmpty())

        // A closed results tab must not resurrect its selection on a reused tab ID.
        VideoSearchResultsTabUiMemory.forTab("p4-f2-film-tab", normal.profileId,
            setOf("p4-f2-film-tab"))
        val reused = VideoSearchResultsTabUiMemory.forTab(currentTab,
            normal.profileId, active).forRun("f2-run")
        assertTrue(reused.selectedFilmKeys.value.isEmpty())
        assertFalse(reused.selecting.value)
    }

    @Test fun privateSearchSessionIsRamOnlyAndNeverCreatesNormalRoomHistory() = runBlocking {
        val db = database()
        try {
            val repo = VideoSearchRepository(db.dao())
            val privateScope = VideoSearchScope.Private(
                normal.profileId, "p4-f2-private-lifetime")
            val memory = VideoSearchPrivateStore()
            memory.start(privateScope)
            val privateRun = VideoSearchRun(
                id = "private-only-run", scope = privateScope, query = "Private",
                siteIds = listOf("first"), parallelism = VideoSearchParallelism.ONE,
                status = VideoSearchRunState.RUNNING, startedAtMs = 1L)
            val privateFilm = VideoSearchFilm(
                id = "private-film", runId = privateRun.id, siteId = "first",
                scope = privateScope, siteName = "Private source",
                filmPageUrl = "https://private.example.org/watch?id=secret",
                originalIndex = 0, foundAtMs = 2L)
            memory.putRun(privateRun)
            assertTrue(memory.putFilm(privateFilm))
            assertEquals(1, memory.countFilms(privateScope, privateRun.id, "first"))
            assertEquals(1, memory.films(privateScope, privateRun.id, "first").size)
            assertNull(repo.resultsSnapshot(normal, privateRun.id))
            assertNull(repo.resultsSnapshot(foreign, privateRun.id))
            memory.finish(privateScope)
            try {
                memory.films(privateScope, privateRun.id, "first")
                fail("Private results remained accessible after retirement")
            } catch (_: IllegalStateException) { }
            try {
                memory.start(privateScope)
                fail("Retired private epoch was resurrected")
            } catch (_: IllegalStateException) { }
            assertNull(repo.resultsSnapshot(normal, privateRun.id))
            assertTrue(repo.runs(normal).isEmpty())
        } finally {
            db.close()
        }
    }

    @Test fun responsivePoliciesAndSourceScrollAnchorsAreStableAcrossLayouts() {
        assertEquals(140, VideoSearchResultsLayoutPolicy.tileMinWidthDp(350, false))
        assertEquals(190, VideoSearchResultsLayoutPolicy.tileMinWidthDp(850, true))
        val portrait = VideoSearchResultsLayoutPolicy.filterPanelMaxHeightDp(780, false)
        val landscape = VideoSearchResultsLayoutPolicy.filterPanelMaxHeightDp(370, true)
        assertTrue(portrait > landscape)
        assertTrue(landscape < 370)
        val siteId = "first"
        val anchor = VideoSearchScrollKeys.film(siteId, "first-17")
        val rows = listOf(VideoSearchScrollKeys.site(siteId),
            VideoSearchScrollKeys.film(siteId, "first-1"), anchor)
        assertEquals(2, VideoSearchScrollKeys.indexForKey(rows, anchor))
        assertEquals(0, VideoSearchScrollKeys.indexForKey(rows,
            VideoSearchScrollKeys.film(siteId, "removed")))
        assertEquals(0, VideoSearchScrollKeys.indexForKey(rows,
            VideoSearchScrollKeys.film("foreign-site", "removed")))
    }
}
