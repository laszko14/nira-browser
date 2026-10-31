package com.prirai.android.nira.media

import com.prirai.android.nira.diagnostics.testlab.VideoMetadataLedger
import org.junit.Assert.*
import org.junit.Test

/** ZC-AUD-042: size estimates belong to a current VOD average bitrate, never peak/declared. */
class MediaSizeEstimateAudit60Test {
    private val page = "https://site.example/watch"
    private fun fixture(): Pair<MediaRepository, MediaTabState> {
        val repository = MediaRepository()
        val tab = MediaTabState(MediaSessionBinding(Any(), "tab", "profile", false), page, false)
        repository.observe(listOf(tab))
        assertTrue(repository.registerDocument(tab, page, 1).accepted)
        return repository to tab
    }

    private fun offer(
        repo: MediaRepository, tab: MediaTabState,
        url: String = "https://cdn.example/child.m3u8",
        duration: Int? = null, live: Boolean? = null,
        rate: Long? = null, kind: String? = null,
        exact: Long? = null, verification: String? = "manifest_verified",
    ): MediaEntry {
        val candidate = MediaDetectorMessage.Candidate(
            url = url, source = MediaSource.NETWORK, pageUrl = page,
            documentKey = 1, privateHint = false, verification = verification,
            durationSeconds = duration, isLive = live,
            bitrate = rate, bitrateKind = kind, sizeBytes = exact
        )
        val detected = MediaClassifier.fromNetwork(candidate, tab.binding)!!
        val result = repo.add(tab, page, 1, detected, MediaSource.NETWORK, metadata = candidate)
        assertTrue(result == MediaRepository.InsertResult.ADDED ||
            result == MediaRepository.InsertResult.DUPLICATE)
        return repo.forTab(tab.binding).single { it.url == url }
    }

    @Test fun averageToPeakDeclaredOrUnspecifiedClearsOldSizeAndLedgerEvidence() {
        for (replacementKind in listOf("peak", "declared", null)) {
            val (repo, tab) = fixture()
            val ledger = VideoMetadataLedger()
            val initial = offer(repo, tab, duration = 100, live = false,
                rate = 800_000L, kind = "average")
            assertEquals(10_000_000L, initial.sizeBytes)
            assertTrue(initial.sizeEstimated)
            assertEquals("MANIFEST_BITRATE", initial.sizeOrigin)
            assertEquals("~10,0 MB", sizeText(initial))
            ledger.observe(tab.binding, initial)
            val changed = offer(repo, tab, rate = 1_200_000L, kind = replacementKind)
            assertEquals(1_200_000L, changed.bitrate)
            assertEquals(replacementKind, changed.bitrateKind)
            assertNull(changed.sizeBytes)
            assertFalse(changed.sizeEstimated)
            assertNull(changed.sizeOrigin)
            assertNull(sizeText(changed))
            assertEquals(100, changed.durationSeconds)
            assertEquals("HLS_EXTINF", changed.durationOrigin)
            ledger.observe(tab.binding, changed)
            val summary = ledger.snapshot()
            assertEquals(0, summary.sizeEstimated)
            assertEquals(1, summary.sizeUnknown)
            assertEquals("UNKNOWN", summary.examples.single().sizeStatus)
            assertEquals("NO_VARIANT_BITRATE", summary.examples.single().sizeSource)
        }
    }

    @Test fun currentAverageReestimatesOnDurationAndBitrateChanges() {
        val (repo, tab) = fixture()
        offer(repo, tab, duration = 100, live = false, rate = 800_000, kind = "average")
        val durationUpdate = offer(repo, tab, duration = 120)
        assertEquals(12_000_000L, durationUpdate.sizeBytes)
        assertTrue(durationUpdate.sizeEstimated)
        val rateUpdate = offer(repo, tab, rate = 600_000, kind = "average")
        assertEquals(9_000_000L, rateUpdate.sizeBytes)
        assertTrue(rateUpdate.sizeEstimated)
        assertEquals("MANIFEST_BITRATE", rateUpdate.sizeOrigin)
        assertEquals("~9,0 MB", sizeText(rateUpdate))
        val peak = offer(repo, tab, rate = 900_000, kind = "peak")
        assertNull(peak.sizeBytes)
        val resumed = offer(repo, tab, rate = 700_000, kind = "average")
        assertEquals(10_500_000L, resumed.sizeBytes)
        assertEquals("~10,5 MB", sizeText(resumed))
    }

    @Test fun liveTransitionDoesNotResurrectAverageFromPreviousVod() {
        val (repo, tab) = fixture()
        offer(repo, tab, duration = 100, live = false, rate = 800_000, kind = "average")
        val live = offer(repo, tab, live = true)
        assertTrue(live.isLive)
        assertNull(live.sizeBytes)
        assertFalse(live.sizeEstimated)
        assertNull(live.durationSeconds)
        val vod = offer(repo, tab, duration = 110, live = false)
        assertFalse(vod.isLive)
        assertEquals(110, vod.durationSeconds)
        assertNull(vod.sizeBytes)
        assertNull(sizeText(vod))
        val fresh = offer(repo, tab, rate = 800_000, kind = "average")
        assertEquals(11_000_000L, fresh.sizeBytes)
        assertTrue(fresh.sizeEstimated)
    }

    @Test fun exactHlsByteRangeSurvivesLossOfAverageAndLaterDurationUpdates() {
        val (repo, tab) = fixture()
        offer(repo, tab, duration = 100, live = false, rate = 800_000, kind = "average")
        val exact = offer(repo, tab, exact = 12_345_678L)
        assertEquals(12_345_678L, exact.sizeBytes)
        assertFalse(exact.sizeEstimated)
        assertEquals("HLS_BYTERANGE", exact.sizeOrigin)
        val peak = offer(repo, tab, rate = 1_300_000, kind = "peak")
        assertEquals(12_345_678L, peak.sizeBytes)
        assertEquals("HLS_BYTERANGE", peak.sizeOrigin)
        assertFalse(peak.sizeEstimated)
        assertFalse(sizeText(peak)!!.startsWith("~"))
        val later = offer(repo, tab, duration = 120, rate = 1_500_000, kind = "average")
        assertEquals(12_345_678L, later.sizeBytes)
        assertEquals("HLS_BYTERANGE", later.sizeOrigin)
    }

    @Test fun refreshedMasterAndChildKeepIndependentExactUrlSizes() {
        val (repo, tab) = fixture()
        val masterUrl = "https://cdn.example/master.m3u8"
        val childUrl = "https://cdn.example/child.m3u8"
        val master = offer(repo, tab, url = masterUrl, duration = 100, live = false,
            rate = 800_000, kind = "average")
        val child = offer(repo, tab, url = childUrl, duration = 100, live = false,
            rate = 600_000, kind = "average")
        assertEquals(10_000_000L, master.sizeBytes)
        assertEquals(7_500_000L, child.sizeBytes)
        val refreshedMaster = offer(repo, tab, url = masterUrl, rate = 1_200_000, kind = "peak")
        assertNull(refreshedMaster.sizeBytes)
        val unchangedChild = repo.forTab(tab.binding).single { it.url == childUrl }
        assertEquals(7_500_000L, unchangedChild.sizeBytes)
        assertTrue(unchangedChild.sizeEstimated)
        val childPeak = offer(repo, tab, url = childUrl, rate = 900_000, kind = "peak")
        assertNull(childPeak.sizeBytes)
        assertEquals(2, repo.forTab(tab.binding).size)
    }
}
