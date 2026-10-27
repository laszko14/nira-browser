package com.prirai.android.nira.media

import org.junit.Assert.*
import org.junit.Test

/**
 * Frame-local group tokens may repeat across independently injected Gecko documents.
 * Tests use synthetic URLs and titles only; no signed URLs appear in logs.
 */
class MediaFrameGroupCorrelationStage5Test {
    private val pageUrl = "https://site.example/catalogue"
    private val tab = MediaTabState(
        MediaSessionBinding(Any(), "tab", null, false), pageUrl, false
    )

    private fun entry(
        id: Long, url: String, frame: Long?, group: String?,
        title: String?, kind: String? = if (title == null) null else "player",
        source: MediaSource = MediaSource.PAGE,
    ) = MediaEntry(
        id, url, MediaFormat.HLS, setOf(source), groupId = group,
        title = title, titleKind = kind, frameKey = frame,
    )

    @Test fun sameGroupTokenInTwoIframesNeverMixesTitles() {
        val items = mediaTileItems(listOf(
            entry(1, "https://cdn.example/a/index.m3u8", 101L, "g1", "Film A"),
            entry(2, "https://cdn.example/b/index.m3u8", 202L, "g1", "Film B")
        ))
        assertEquals(2, items.size)
        assertEquals("Film A", items.single { it.key == 1L }.title)
        assertEquals("Film B", items.single { it.key == 2L }.title)
        assertEquals(2, items.map { it.key }.distinct().size)
    }

    @Test fun topDocumentAndIframeCannotShareLocalGroupToken() {
        val items = mediaTileItems(listOf(
            entry(1, "https://cdn.example/main.m3u8", null, "g1", "Main title"),
            entry(2, "https://cdn.example/embed.m3u8", 11L, "g1", "Embed title")
        ))
        assertEquals(2, items.size)
        assertEquals("Main title", items.single { it.key == 1L }.title)
        assertEquals("Embed title", items.single { it.key == 2L }.title)
    }

    @Test fun qualityVariantsWithinOneFrameStillShareTheirLocalGroup() {
        val items = mediaTileItems(listOf(
            entry(1, "https://cdn.example/720.m3u8", 11L, "g2", "Episode 4"),
            entry(2, "https://cdn.example/1080.m3u8", 11L, "g2", null,
                source = MediaSource.NETWORK),
            entry(3, "https://cdn.example/different.m3u8", 12L, "g2", "Other episode")
        ))
        assertEquals(2, items.size)
        assertEquals("Episode 4", items.single { it.key == 1L }.title)
        assertEquals(2, items.single { it.key == 1L }.variantCount)
        assertEquals("Other episode", items.single { it.key == 3L }.title)
    }

    @Test fun sameUngroupedStreamUrlAcrossFramesRemainsSeparate() {
        val same = "https://cdn.example/shared/index.m3u8"
        val items = mediaTileItems(listOf(
            entry(1, same, 7L, null, "Player one"),
            entry(2, same, 8L, null, "Player two")
        ))
        assertEquals(2, items.size)
        assertEquals("Player one", items.single { it.key == 1L }.title)
        assertEquals("Player two", items.single { it.key == 2L }.title)
    }

    private fun repository(frameTracking: Boolean = false): MediaRepository =
        MediaRepository().also {
            it.observe(listOf(tab))
            assertTrue(it.registerDocument(tab, pageUrl, 1L, frameTracking).accepted)
        }

    private fun add(
        repository: MediaRepository, sourceUrl: String,
        source: MediaSource, group: String?,
        title: String? = null, kind: String? = if (title == null) null else "player",
        frame: Long? = null, frameUrl: String? = null,
    ): MediaRepository.InsertResult {
        val candidate = MediaDetectorMessage.Candidate(
            url = sourceUrl, source = source, pageUrl = pageUrl,
            documentKey = 1L, groupId = group, title = title,
            titleKind = kind, frameKey = frame, frameUrl = frameUrl,
        )
        val media = DetectedMedia(sourceUrl, MediaFormat.HLS, tab.binding.tabId,
            tab.binding.contextId, tab.binding.isPrivate)
        return repository.add(tab, pageUrl, 1L, media, source, group, candidate)
    }

    @Test fun repositoryAssignsDifferentFramesTheirOwnTitlesForSameStream() {
        val repository = repository(frameTracking = true)
        val urlA = "https://player.example/embed/a"
        val urlB = "https://player.example/embed/b"
        assertTrue(repository.registerFrame(tab, MediaDetectorMessage.Frame(
            pageUrl, 1L, urlA, 11L, 101L, true)))
        assertTrue(repository.registerFrame(tab, MediaDetectorMessage.Frame(
            pageUrl, 1L, urlB, 22L, 202L, true)))
        val sharedUrl = "https://cdn.example/playlist.m3u8"
        assertEquals(MediaRepository.InsertResult.ADDED, add(repository,
            sharedUrl, MediaSource.PAGE, "g1", "Film A", frame = 101L, frameUrl = urlA))
        assertEquals(MediaRepository.InsertResult.ADDED, add(repository,
            sharedUrl, MediaSource.PAGE, "g1", "Film B", frame = 202L, frameUrl = urlB))
        val tiles = mediaTileItems(repository.currentEntries(tab))
        assertEquals(2, tiles.size)
        assertEquals(setOf("Film A", "Film B"), tiles.map { it.title }.toSet())
    }

    @Test fun laterManifestNetworkGroupDoesNotStealLocallyTitledStream() {
        val repository = repository()
        val url = "https://cdn.example/index.m3u8"
        assertEquals(MediaRepository.InsertResult.ADDED,
            add(repository, url, MediaSource.PAGE, "g1", "Właściwy film"))
        assertEquals(MediaRepository.InsertResult.DUPLICATE,
            add(repository, url, MediaSource.NETWORK, "manifest-1-1"))
        val record = repository.currentEntries(tab).single()
        assertEquals("g1", record.groupId)
        assertEquals("Właściwy film", record.title)
        assertEquals(setOf(MediaSource.PAGE, MediaSource.NETWORK), record.sources)
    }

    @Test fun pageOnlyTitleCannotBlockAnonymousManifestReconciliation() {
        val repository = repository()
        val url = "https://cdn.example/page-only.m3u8"
        add(repository, url, MediaSource.PAGE, "g1", "Global site heading", "page")
        add(repository, url, MediaSource.NETWORK, "manifest-1-1")
        assertEquals("manifest-1-1", repository.currentEntries(tab).single().groupId)
    }

    @Test fun anonymousPageHintMayStillJoinItsNetworkManifestVariants() {
        val repository = repository()
        val url = "https://cdn.example/master.m3u8"
        add(repository, url, MediaSource.PAGE, "g1")
        add(repository, url, MediaSource.NETWORK, "manifest-1-1")
        assertEquals("manifest-1-1", repository.currentEntries(tab).single().groupId)
        val variant = "https://cdn.example/720/index.m3u8"
        add(repository, variant, MediaSource.NETWORK, "manifest-1-1")
        val items = mediaTileItems(repository.currentEntries(tab))
        assertEquals(1, items.size)
        assertEquals(2, items.single().variantCount)
    }
}
