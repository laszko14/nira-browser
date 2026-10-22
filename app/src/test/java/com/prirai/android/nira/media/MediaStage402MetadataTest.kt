package com.prirai.android.nira.media

import org.junit.Assert.*
import org.junit.Test

class MediaStage402MetadataTest {
    private val binding = MediaSessionBinding(Any(), "tab-a", "profile-a", false)
    private val page = "https://site.example/watch"

    @Test fun protocolAcceptsBoundedTitlesAndSizesInRightSource() {
        val pageCandidate = MediaMessageProtocol.decode(
            """{"schemaVersion":1,"event":"media_candidate","source":"page","url":"https://cdn.example/v.mp4","title":"Film testowy","titleKind":"video","durationSeconds":3601}"""
        ) as MediaDetectorMessage.Candidate
        assertEquals("Film testowy", pageCandidate.title)
        assertEquals("video", pageCandidate.titleKind)
        assertEquals(3601, pageCandidate.durationSeconds)
        val network = MediaMessageProtocol.decode(
            """{"schemaVersion":1,"event":"media_candidate","source":"network","url":"https://cdn.example/v.mp4","isPrivate":false,"sizeBytes":123000000}"""
        ) as MediaDetectorMessage.Candidate
        assertEquals(123000000L, network.sizeBytes)
        assertNull(network.title)
    }

    @Test fun protocolRejectsImpossibleOrMisassignedMetadata() {
        val base = """{"schemaVersion":1,"event":"media_candidate","source":"page","url":"https://cdn.example/v.mp4","title":"Film","titleKind":"video"}"""
        for (raw in listOf(
            base.replace(",\"titleKind\":\"video\"", ""),
            base.replace("\"titleKind\":\"video\"", "\"titleKind\":\"bogus\""),
            base.replace("\"Film\"", "\"Bad\\nTitle\""),
            base.replace("}", ",\"sizeBytes\":1000}"),
            base.replace("}", ",\"durationSeconds\":0}"),
            base.replace("}", ",\"durationSeconds\":604801}"),
            """{"schemaVersion":1,"event":"media_candidate","source":"network","url":"https://cdn.example/v.mp4","isPrivate":false,"title":"Film","titleKind":"video"}""",
            """{"schemaVersion":1,"event":"media_candidate","source":"network","url":"https://cdn.example/v.mp4","isPrivate":false,"sizeBytes":1000000000001}""",
        )) assertNull(raw, MediaMessageProtocol.decode(raw))
    }

    @Test fun repositoryUpgradesWeakPageTitleToConcreteTitleAndPreservesNetworkSize() {
        val repository = MediaRepository()
        val tab = MediaTabState(binding, page, false)
        repository.observe(listOf(tab))
        assertTrue(repository.registerDocument(tab, page, 1).accepted)
        val url = "https://cdn.example/film.mp4?token=secret"
        val net = MediaDetectorMessage.Candidate(url, source = MediaSource.NETWORK,
            sizeBytes = 275000000L, title = "Strona z filmem", titleKind = "page")
        val detected = MediaClassifier.fromNetwork(net, binding)!!
        assertEquals(MediaRepository.InsertResult.ADDED,
            repository.add(tab, page, 1, detected, MediaSource.NETWORK, metadata = net))
        val dom = MediaDetectorMessage.Candidate(url, source = MediaSource.PAGE,
            title = "Konkretny tytuł", titleKind = "video", durationSeconds = 4552)
        assertEquals(MediaRepository.InsertResult.DUPLICATE,
            repository.add(tab, page, 1, detected, MediaSource.PAGE, "t1-g1", dom))
        val result = repository.currentEntries(tab).single()
        assertEquals("Konkretny tytuł", result.title)
        assertEquals("video", result.titleKind)
        assertEquals(275000000L, result.sizeBytes)
        assertEquals(4552, result.durationSeconds)
        assertEquals("t1-g1", result.groupId)
        assertTrue(result.sources.containsAll(listOf(MediaSource.PAGE, MediaSource.NETWORK)))
        assertFalse(result.toString().contains("secret"))
    }

    @Test fun streamManifestCannotCarryDirectVideoSize() {
        val repo = MediaRepository()
        val tab = MediaTabState(binding, page, false)
        repo.observe(listOf(tab))
        repo.registerDocument(tab, page, 12)
        val hint = MediaDetectorMessage.Candidate("https://cdn.example/master.m3u8",
            source = MediaSource.NETWORK, sizeBytes = 1000L)
        val media = MediaClassifier.fromNetwork(hint, binding)!!
        repo.add(tab, page, 12, media, MediaSource.NETWORK, metadata = hint)
        assertEquals(MediaFormat.HLS, repo.currentEntries(tab).single().format)
        assertNull(repo.currentEntries(tab).single().sizeBytes)
    }

    @Test fun newNavigationCannotRecoverPreviousTitleOrSize() {
        val repo = MediaRepository()
        val tab = MediaTabState(binding, page, false)
        repo.observe(listOf(tab))
        repo.registerDocument(tab, page, 4)
        val msg = MediaDetectorMessage.Candidate("https://cdn.example/a.mp4",
            source = MediaSource.NETWORK, sizeBytes = 500000L, title = "Old", titleKind = "page")
        val media = MediaClassifier.fromNetwork(msg, binding)!!
        repo.add(tab, page, 4, media, MediaSource.NETWORK, metadata = msg)
        val next = tab.copy(pageUrl = "https://site.example/next")
        repo.observe(listOf(next))
        assertTrue(repo.currentEntries(next).isEmpty())
        assertEquals(MediaRepository.InsertResult.STALE,
            repo.add(next, page, 4, media, MediaSource.NETWORK, metadata = msg))
    }
}
