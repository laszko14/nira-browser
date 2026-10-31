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

    @Test fun derivedVariantsJoinTheirExactObservedLocallyTitledMaster() {
        val repository = repository()
        val master = "https://cdn.example/master.m3u8"
        add(repository, master, MediaSource.PAGE, "g1", "Właściwy film")
        add(repository, master, MediaSource.NETWORK, "manifest-1-1")
        val variant = "https://cdn.example/720/index.m3u8"
        val candidate = MediaDetectorMessage.Candidate(variant, source = MediaSource.NETWORK,
            pageUrl = pageUrl, documentKey = 1, groupId = "manifest-1-1", provenance = "derived")
        repository.add(tab, pageUrl, 1, DetectedMedia(variant, MediaFormat.HLS,
            tab.binding.tabId, tab.binding.contextId, false), MediaSource.NETWORK, candidate.groupId, candidate)
        // A later real request for the child must preserve the correlation.
        add(repository, variant, MediaSource.NETWORK, "manifest-1-1")
        val items = mediaTileItems(repository.currentEntries(tab))
        assertEquals(1, items.size)
        assertEquals("Właściwy film", items.single().title)
        assertEquals(2, items.single().variantCount)
        assertEquals("g1", repository.currentEntries(tab).first().groupId)
    }

    @Test fun conflictingLocalOwnersCannotTitleAnUnownedDerivedVariant() {
        val repository = repository()
        add(repository, "https://cdn.example/a.m3u8", MediaSource.PAGE, "g1", "Film A")
        add(repository, "https://cdn.example/b.m3u8", MediaSource.PAGE, "g2", "Film B")
        add(repository, "https://cdn.example/a.m3u8", MediaSource.NETWORK, "manifest-1-1")
        add(repository, "https://cdn.example/b.m3u8", MediaSource.NETWORK, "manifest-1-1")
        val variant = "https://cdn.example/720/index.m3u8"
        val candidate = MediaDetectorMessage.Candidate(variant, source = MediaSource.NETWORK,
            pageUrl = pageUrl, documentKey = 1, groupId = "manifest-1-1", provenance = "derived")
        repository.add(tab, pageUrl, 1, DetectedMedia(variant, MediaFormat.HLS,
            tab.binding.tabId, tab.binding.contextId, false), MediaSource.NETWORK, candidate.groupId, candidate)
        val items = mediaTileItems(repository.currentEntries(tab))
        assertEquals(3, items.size)
        assertEquals("Film #3", items.single { it.entry.url == variant }.title)
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
    @Test fun audit60_16a_networkBeforeDomRejoinsExactMasterAndLocalAlternative() {
        val repo = repository()
        val u = "https://cdn.example/master.m3u8?token=exact"
        val v = "https://cdn.example/backup.m3u8"
        assertEquals(MediaRepository.InsertResult.ADDED,
            add(repo, u, MediaSource.NETWORK, "manifest-1-1"))
        assertEquals(MediaRepository.InsertResult.DUPLICATE,
            add(repo, u, MediaSource.PAGE, "t1-g1", "Episode 12"))
        assertEquals(MediaRepository.InsertResult.ADDED,
            add(repo, v, MediaSource.PAGE, "t1-g1", "Episode 12"))
        val records = repo.currentEntries(tab)
        assertEquals(setOf("t1-g1"), records.map { it.groupId }.toSet())
        val tile = mediaTileItems(records).single()
        assertEquals("Episode 12", tile.title)
        assertEquals(2, tile.variantCount)
        assertEquals(setOf(u, v), records.map { it.url }.toSet())
    }

    @Test fun audit60_16a_parserDerivedBeforeDomIsReconciledInEitherOrder() {
        val u = "https://cdn.example/master.m3u8"
        val v = "https://cdn.example/child/index.m3u8"
        fun run(domFirst: Boolean): List<MediaEntry> {
            val repo = repository()
            if (domFirst) add(repo, u, MediaSource.PAGE, "owner-g1", "Episode 3")
            add(repo, u, MediaSource.NETWORK, "manifest-1-1")
            val derived = MediaDetectorMessage.Candidate(v,
                source = MediaSource.NETWORK, pageUrl = pageUrl, documentKey = 1,
                groupId = "manifest-1-1", provenance = "derived")
            repo.add(tab, pageUrl, 1L, DetectedMedia(v, MediaFormat.HLS,
                tab.binding.tabId, tab.binding.contextId, false),
                MediaSource.NETWORK, derived.groupId, derived)
            if (!domFirst) add(repo, u, MediaSource.PAGE, "owner-g1", "Episode 3")
            add(repo, v, MediaSource.NETWORK, "manifest-1-1")
            assertEquals(1, mediaTileItems(repo.currentEntries(tab)).size)
            assertEquals("Episode 3", mediaTileItems(repo.currentEntries(tab)).single().title)
            return repo.currentEntries(tab)
        }
        val first = run(false)
        val second = run(true)
        assertEquals(setOf("owner-g1"), first.map { it.groupId }.toSet())
        assertEquals(setOf("owner-g1"), second.map { it.groupId }.toSet())
        assertEquals(first.map { it.url }.toSet(), second.map { it.url }.toSet())
    }

    @Test fun audit60_16a_conflictingOwnersWithdrawInheritedParserAlias() {
        val repo = repository()
        val a = "https://cdn.example/a/index.m3u8"
        val b = "https://cdn.example/b/index.m3u8"
        val child = "https://cdn.example/shared/child.m3u8"
        add(repo, a, MediaSource.NETWORK, "manifest-1-1")
        add(repo, b, MediaSource.NETWORK, "manifest-1-1")
        add(repo, a, MediaSource.PAGE, "owner-a", "Same title")
        // Before the second owner's DOM is seen, the parser lineage has one owner.
        assertEquals(setOf("owner-a"), repo.currentEntries(tab).map { it.groupId }.toSet())
        add(repo, b, MediaSource.PAGE, "owner-b", "Same title")
        val derived = MediaDetectorMessage.Candidate(child,
            source = MediaSource.NETWORK, pageUrl = pageUrl, documentKey = 1,
            groupId = "manifest-1-1", provenance = "derived")
        repo.add(tab, pageUrl, 1, DetectedMedia(child, MediaFormat.HLS,
            tab.binding.tabId, tab.binding.contextId, false),
            MediaSource.NETWORK, derived.groupId, derived)
        assertEquals(mapOf(a to "owner-a", b to "owner-b", child to "manifest-1-1"),
            repo.currentEntries(tab).associate { it.url to it.groupId })
        assertEquals(3, mediaTileItems(repo.currentEntries(tab)).size)
    }

    @Test fun audit60_16a_manifestCorrelationRespectsFrameAndNavigation() {
        val repo = repository(frameTracking = true)
        val frameA = "https://player.example/a"
        val frameB = "https://player.example/b"
        assertTrue(repo.registerFrame(tab, MediaDetectorMessage.Frame(pageUrl, 1, frameA, 1, 101, true)))
        assertTrue(repo.registerFrame(tab, MediaDetectorMessage.Frame(pageUrl, 1, frameB, 2, 202, true)))
        val shared = "https://cdn.example/shared.m3u8"
        add(repo, shared, MediaSource.NETWORK, "manifest-1-1", frame = 101, frameUrl = frameA)
        add(repo, shared, MediaSource.NETWORK, "manifest-1-1", frame = 202, frameUrl = frameB)
        add(repo, shared, MediaSource.PAGE, "g-a", "Title A", frame = 101, frameUrl = frameA)
        add(repo, shared, MediaSource.PAGE, "g-b", "Title B", frame = 202, frameUrl = frameB)
        assertEquals(2, mediaTileItems(repo.currentEntries(tab)).size)
        assertEquals(setOf("g-a", "g-b"), repo.currentEntries(tab).mapNotNull { it.groupId }.toSet())
        assertFalse(repo.registerDocument(tab, pageUrl, 0).accepted)
        assertTrue(repo.registerDocument(tab, pageUrl, 2, true).accepted)
        assertTrue(repo.currentEntries(tab).isEmpty())
        assertEquals(MediaRepository.InsertResult.STALE,
            repo.add(tab, pageUrl, 1, DetectedMedia(shared, MediaFormat.HLS,
                tab.binding.tabId, tab.binding.contextId, false), MediaSource.NETWORK, "manifest-1-1"))
    }

    @Test fun audit60_16a_distinctManifestTokensAndTitlesDoNotMerge() {
        val repo = repository()
        val a = "https://cdn.example/one.m3u8"
        val b = "https://cdn.example/two.m3u8"
        add(repo, a, MediaSource.NETWORK, "manifest-1-1")
        add(repo, b, MediaSource.NETWORK, "manifest-2-1")
        add(repo, a, MediaSource.PAGE, "g-a", "Same title")
        add(repo, b, MediaSource.PAGE, "g-b", "Same title")
        assertEquals(2, mediaTileItems(repo.currentEntries(tab)).size)
        assertEquals(setOf("g-a", "g-b"), repo.currentEntries(tab).mapNotNull { it.groupId }.toSet())
    }

}
