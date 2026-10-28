package com.prirai.android.nira.media

import org.junit.Assert.*
import org.junit.Test

/** Synthetic, public fixtures only. Stream identity, not title equality, owns a tile. */
class MediaTitleCorrelationV1171Test {
    private val url = "https://site.example/catalogue"
    private val tab = MediaTabState(
        MediaSessionBinding(Any(), "tab", null, false), url, false
    )
    private fun repo() = MediaRepository().also {
        it.observe(listOf(tab))
        assertTrue(it.registerDocument(tab, url, 1L).accepted)
    }
    private fun add(repository: MediaRepository, id: Int, title: String?,
                    kind: String? = if (title == null) null else "player",
                    group: String? = "g$id", ext: String = "mp4") {
        val sourceUrl = "https://cdn.example/clip$id.$ext"
        val format = if (ext == "m3u8") MediaFormat.HLS else MediaFormat.MP4
        val candidate = MediaDetectorMessage.Candidate(
            url = sourceUrl, source = MediaSource.PAGE, pageUrl = url,
            documentKey = 1L, groupId = group, title = title, titleKind = kind
        )
        val media = DetectedMedia(sourceUrl, format, tab.binding.tabId,
            tab.binding.contextId, tab.binding.isPrivate)
        repository.add(tab, url, 1L, media, MediaSource.PAGE, group, candidate)
    }

    @Test fun twelveDifferentStreamIdentitiesRetainTwelveDifferentTitles() {
        val repository = repo()
        for (i in 1..12) add(repository, i, "Episode $i")
        val items = mediaTileItems(repository.currentEntries(tab))
        assertEquals(12, items.size)
        for (i in 1..12) {
            val tile = items.single { it.entry.url.endsWith("/clip$i.mp4") }
            assertEquals("Episode $i", tile.title)
            assertEquals(tile.entry.id, tile.key)
        }
    }

    @Test fun manyUnknownStreamsNeverBorrowThePageTitle() {
        val repository = repo()
        for (i in 1..12) add(repository, i, null)
        val items = mediaTileItems(repository.currentEntries(tab))
        assertEquals(12, items.size)
        assertEquals((1..12).map { "Plik (niepotwierdzony): clip$it" }.toSet(),
            items.map { it.title }.toSet())
        assertTrue(items.none { it.title == "Shared document title" })
    }

    @Test fun exactVariantsShareTheBestBoundTitleWithoutConfusingOtherGroups() {
        val first = MediaEntry(1, "https://cdn.example/a.mp4", MediaFormat.MP4,
            setOf(MediaSource.DOM), "group-a", "Episode A", "player")
        val variant = MediaEntry(2, "https://cdn.example/a.m3u8", MediaFormat.HLS,
            setOf(MediaSource.NETWORK), "group-a")
        val second = MediaEntry(3, "https://cdn.example/b.mp4", MediaFormat.MP4,
            setOf(MediaSource.DOM), "group-b", "Episode B", "structured")
        val items = mediaTileItems(listOf(first, variant, second))
        assertEquals(2, items.size)
        assertEquals("Episode A", items.single { it.entry.groupId == "group-a" }.title)
        assertEquals(2, items.single { it.entry.groupId == "group-a" }.variantCount)
        assertEquals("Episode B", items.single { it.entry.groupId == "group-b" }.title)
    }

    @Test fun pageWideTitlesAreNotCopiedAcrossGroups() {
        val items = mediaTileItems((1..12).map { i ->
            MediaEntry(i.toLong(), "https://cdn.example/index$i.m3u8", MediaFormat.HLS,
                setOf(MediaSource.NETWORK), "g$i", "A site header", "page")
        })
        assertEquals(12, items.size)
        assertTrue(items.none { it.title == "A site header" })
    }

    @Test fun conflictingVariantTitlesFailClosed() {
        val items = mediaTileItems(listOf(
            MediaEntry(1, "https://cdn.example/a.mp4", MediaFormat.MP4,
                setOf(MediaSource.PAGE), "g", "A", "player"),
            MediaEntry(2, "https://cdn.example/b.mp4", MediaFormat.MP4,
                setOf(MediaSource.PAGE), "g", "B", "player")
        ))
        assertEquals(1, items.size)
        assertNotEquals("A", items.single().title)
        assertNotEquals("B", items.single().title)
    }

    @Test fun unknownMetadataUpgradesOnlyWhenSourceProvesStrongerTitle() {
        val repository = repo()
        add(repository, 1, "Weak page name", "page")
        add(repository, 1, "Specific player name", "player")
        add(repository, 1, "Different equally ranked longer player title", "player")
        assertEquals("Specific player name", repository.currentEntries(tab).single().title)
        assertEquals("player", repository.currentEntries(tab).single().titleKind)
    }

    @Test fun equalHumanTitlesNeverMergeDifferentStreams() {
        val repository = repo()
        add(repository, 1, "Untitled concert")
        add(repository, 2, "Untitled concert")
        val tiles = mediaTileItems(repository.currentEntries(tab))
        assertEquals(2, tiles.size)
        assertEquals(2, tiles.map { it.entry.id }.toSet().size)
    }

    @Test fun navigationDropsOldTitlesAndPreventsLateCandidateRebinding() {
        val repository = repo()
        add(repository, 1, "Old video title")
        val next = tab.copy(pageUrl = "https://site.example/next")
        repository.observe(listOf(next))
        assertTrue(repository.currentEntries(next).isEmpty())
        val candidate = MediaDetectorMessage.Candidate(
            url = "https://cdn.example/old.mp4", source = MediaSource.PAGE,
            pageUrl = url, documentKey = 1L, title = "Old video title", titleKind = "player")
        val detected = DetectedMedia(candidate.url, MediaFormat.MP4,
            tab.binding.tabId, tab.binding.contextId, false)
        assertEquals(MediaRepository.InsertResult.STALE,
            repository.add(next, url, 1L, detected, MediaSource.PAGE, metadata = candidate))
    }

    @Test fun tileIdentityAndTitleRemainStableAcrossRepeatedMapping() {
        val source = MediaEntry(11, "https://cdn.example/a.mp4", MediaFormat.MP4,
            setOf(MediaSource.DOM), "g", "Correct name", "player")
        val first = mediaTileItems(listOf(source))
        val reopened = mediaTileItems(listOf(source))
        assertEquals(first.single().key, reopened.single().key)
        assertEquals(first.single().title, reopened.single().title)
    }
}
