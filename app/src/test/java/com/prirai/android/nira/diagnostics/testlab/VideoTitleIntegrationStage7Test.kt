package com.prirai.android.nira.diagnostics.testlab

import com.prirai.android.nira.media.DetectedMedia
import com.prirai.android.nira.media.MediaDetectorMessage
import com.prirai.android.nira.media.MediaEntry
import com.prirai.android.nira.media.MediaFormat
import com.prirai.android.nira.media.MediaRepository
import com.prirai.android.nira.media.MediaSessionBinding
import com.prirai.android.nira.media.MediaSource
import com.prirai.android.nira.media.MediaTabState
import com.prirai.android.nira.media.mediaTileItems
import org.junit.Assert.*
import org.junit.Test

/**
 * Synthetic end-to-end native fixture:
 * accepted source -> title resolver -> grouped panel item -> anonymous TestLab.
 * Does not claim verification against a particular website or drawn UI pixels.
 */
class VideoTitleIntegrationStage7Test {
    private val page = "https://site.example/catalogue"
    private val binding = MediaSessionBinding(Any(), "tab", null, false)
    private val tab = MediaTabState(binding, page, false)

    private fun repository(trackedFrames: Boolean = false): MediaRepository =
        MediaRepository().also {
            it.observe(listOf(tab))
            assertTrue(it.registerDocument(tab, page, 1L, trackedFrames).accepted)
        }

    private fun add(
        repository: MediaRepository, url: String, group: String? = null,
        title: String? = null, kind: String? = if (title == null) null else "video",
        frame: Long? = null, frameUrl: String? = null,
    ): MediaRepository.InsertResult {
        val format = if (url.substringBefore('?').endsWith(".m3u8"))
            MediaFormat.HLS else MediaFormat.MP4
        val metadata = MediaDetectorMessage.Candidate(
            url = url, source = MediaSource.PAGE, pageUrl = page,
            documentKey = 1L, groupId = group, title = title,
            titleKind = kind, frameKey = frame, frameUrl = frameUrl)
        val detected = DetectedMedia(url, format, binding.tabId,
            binding.contextId, binding.isPrivate)
        return repository.add(tab, page, 1L, detected, MediaSource.PAGE, group, metadata)
    }

    private fun snapshot(repository: MediaRepository, ledger: VideoDiscoveryLedger): List<MediaEntry> {
        val entries = repository.currentEntries(tab)
        entries.forEach { ledger.observe(binding, it) }
        return entries
    }

    @Test fun twelveTitlesAndVariantsAppearAsTwelveAnonymousNamedGroups() {
        val repository = repository()
        val ledger = VideoDiscoveryLedger()
        for (i in 1..12) {
            add(repository, "https://cdn.example/film$i/480.mp4", "g$i", "Episode $i")
            add(repository, "https://cdn.example/film$i/1080.mp4", "g$i")
        }
        val entries = snapshot(repository, ledger)
        assertEquals(24, entries.size)
        val tiles = mediaTileItems(entries)
        assertEquals(12, tiles.size)
        assertTrue(tiles.all { it.variantCount == 2 })
        assertEquals((1..12).map { "Episode $it" }.toSet(), tiles.map { it.title }.toSet())
        entries.forEach { ledger.displayed(binding, it) }
        val measured = ledger.snapshot()
        assertEquals(24, measured.uiObservedSources)
        assertEquals(12, measured.uiObservedGroups)
        assertEquals(12, measured.uiObservedTitledGroups)
        assertEquals(0, measured.uiObservedConflictedGroups)
        assertEquals(0, measured.groupsWithoutTitle)
        assertFalse(measured.toString().contains("Episode 1"))
        assertFalse(measured.toString().contains("cdn.example"))
    }

    @Test fun sameGroupIdInTwoIndependentFramesCountsAsTwoFilms() {
        val repository = repository(trackedFrames = true)
        val ledger = VideoDiscoveryLedger()
        val firstFrame = "https://player.example/one"
        val otherFrame = "https://player.example/two"
        assertTrue(repository.registerFrame(tab, MediaDetectorMessage.Frame(
            page, 1L, firstFrame, 5L, 101L, true)))
        assertTrue(repository.registerFrame(tab, MediaDetectorMessage.Frame(
            page, 1L, otherFrame, 6L, 202L, true)))
        add(repository, "https://cdn.example/one.mp4", "g1", "Pierwszy",
            "video", 101L, firstFrame)
        add(repository, "https://cdn.example/two.mp4", "g1", "Drugi",
            "video", 202L, otherFrame)
        val sources = snapshot(repository, ledger)
        assertEquals(2, mediaTileItems(sources).size)
        sources.forEach { ledger.displayed(binding, it) }
        val result = ledger.snapshot()
        assertEquals(2, result.uiObservedGroups)
        assertEquals(2, result.uiObservedTitledGroups)
        assertEquals(0, result.groupTitleConflicts)
    }

    @Test fun latePlayerAttributionUpdatesExistingIdAndAnonymousGroupCount() {
        val repository = repository()
        val ledger = VideoDiscoveryLedger()
        val url = "https://cdn.example/clip.mp4"
        add(repository, url, "g1", "Odcinek 4", "video")
        val original = snapshot(repository, ledger).single()
        assertEquals(MediaRepository.InsertResult.DUPLICATE,
            add(repository, url, "g1", "Odcinek 4", "player"))
        val updated = snapshot(repository, ledger).single()
        assertEquals(original.id, updated.id)
        assertEquals("player", updated.titleKind)
        ledger.displayed(binding, updated)
        val result = ledger.snapshot()
        assertEquals(1, result.improvements)
        assertEquals(1, result.uiObservedTitledGroups)
        assertEquals("PLAYER", result.examples.single().titleSource)
    }

    @Test fun conflictingSameRankFilmsCannotReportNamedGroup() {
        val repository = repository()
        val ledger = VideoDiscoveryLedger()
        add(repository, "https://cdn.example/movie-a.mp4", "one", "Film A", "player")
        add(repository, "https://cdn.example/movie-b.mp4", "one", "Film B", "player")
        val entries = snapshot(repository, ledger)
        val tiles = mediaTileItems(entries)
        assertEquals(1, tiles.size)
        assertNotEquals("Film A", tiles.single().title)
        assertNotEquals("Film B", tiles.single().title)
        entries.forEach { ledger.displayed(binding, it) }
        val result = ledger.snapshot()
        assertEquals(1, result.uiObservedGroups)
        assertEquals(0, result.uiObservedTitledGroups)
        assertEquals(1, result.uiObservedConflictedGroups)
        assertEquals(1, result.groupsWithoutTitle)
    }

    @Test fun pageWideNameDoesNotCountAsIndividualMovie() {
        val repository = repository()
        val ledger = VideoDiscoveryLedger()
        add(repository, "https://cdn.example/one.mp4", "g1", "Shared page name", "page")
        add(repository, "https://cdn.example/two.mp4", "g2", "Shared page name", "page")
        val entries = snapshot(repository, ledger)
        entries.forEach { ledger.displayed(binding, it) }
        val result = ledger.snapshot()
        assertEquals(2, mediaTileItems(entries).size)
        assertEquals(2, result.uiObservedGroups)
        assertEquals(0, result.uiObservedTitledGroups)
        assertEquals(2, result.groupsWithoutTitle)
    }

    @Test fun unknownSourceMayReceiveStrongTitleWithoutNewTileId() {
        val repository = repository()
        val ledger = VideoDiscoveryLedger()
        val url = "https://cdn.example/untitled.mp4"
        add(repository, url, "g1")
        val initial = snapshot(repository, ledger).single()
        assertNull(initial.title)
        add(repository, url, "g1", "Właściwy odcinek", "player")
        val updated = snapshot(repository, ledger).single()
        assertEquals(initial.id, updated.id)
        assertEquals("Właściwy odcinek", mediaTileItems(listOf(updated)).single().title)
        ledger.displayed(binding, updated)
        assertEquals(1, ledger.snapshot().initialTitles)
        assertEquals(1, ledger.snapshot().uiObservedTitledGroups)
    }

    @Test fun opaqueManifestTokensCannotMixGroupOrIndividualNames() {
        val repository = repository()
        val ledger = VideoDiscoveryLedger()
        add(repository, "https://cdn.example/index.m3u8?token=film-a",
            "manifest-1-1", "Film pierwszy", "structured")
        add(repository, "https://cdn.example/index.m3u8?token=film-b",
            "manifest-1-2", "Film drugi", "structured")
        val entries = snapshot(repository, ledger)
        val tiles = mediaTileItems(entries)
        assertEquals(2, tiles.size)
        assertEquals(setOf("Film pierwszy", "Film drugi"), tiles.map { it.title }.toSet())
        entries.forEach { ledger.displayed(binding, it) }
        assertEquals(2, ledger.snapshot().uiObservedTitledGroups)
    }
}
