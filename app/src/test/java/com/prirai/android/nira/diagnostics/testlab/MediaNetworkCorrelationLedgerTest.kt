package com.prirai.android.nira.diagnostics.testlab

import com.prirai.android.nira.media.MediaEntry
import com.prirai.android.nira.media.MediaFormat
import com.prirai.android.nira.media.MediaSessionBinding
import com.prirai.android.nira.media.MediaSource
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class MediaNetworkCorrelationLedgerTest {
    private val session = Any()
    private val binding = MediaSessionBinding(session, "secret-tab", "secret-profile", true)
    private fun media(
        id: Long, url: String = "https://cdn.example/a.mp4?token=PRIVATE",
        sources: Set<MediaSource> = setOf(MediaSource.DOM),
        frame: Long? = 1, group: String? = null,
        format: MediaFormat = MediaFormat.MP4,
        title: String? = null,
    ) = MediaEntry(id, url, format, sources, groupId = group,
        title = title, titleKind = if(title != null) "video" else null,
        frameKey = frame)

    @Test fun nativeExactFrameCoObservationIsCountedWithoutClaimingContentProof() {
        val ledger = MediaNetworkCorrelationLedger()
        ledger.observe(binding,media(1,sources=setOf(MediaSource.DOM,MediaSource.NETWORK),
            title="Private Movie Title"))
        val result = ledger.snapshot()
        assertEquals(1,result.observedRecords)
        assertEquals(1,result.networkAndDomOnOneRecord)
        assertEquals(1,result.trustedFrameCoObserved)
        assertEquals(1,result.coObservedWithIndividualMetadata)
        assertEquals(0,result.networkOnlyRecords)
        assertTrue(result.shadowOnly)
        assertFalse(result.changesMediaOrPlayback)
        assertFalse(result.performedNetworkRequests)
    }

    @Test fun uniqueUrlAcrossTwoKnownIframesIsNeverMergedByShadow() {
        val ledger = MediaNetworkCorrelationLedger()
        ledger.observe(binding,media(1,sources=setOf(MediaSource.NETWORK),frame=100))
        ledger.observe(binding,media(2,sources=setOf(MediaSource.DOM),frame=200))
        val result = ledger.snapshot()
        assertEquals(2,result.observedRecords)
        assertEquals(1,result.exactUrlCrossFramePairs)
        assertEquals(0,result.networkAndDomOnOneRecord)
    }

    @Test fun unknownTrustedFrameIsNotPromotedToDomOwner() {
        val ledger = MediaNetworkCorrelationLedger()
        ledger.observe(binding,media(1,sources=setOf(MediaSource.NETWORK),frame=null))
        ledger.observe(binding,media(2,sources=setOf(MediaSource.DOM),frame=7))
        val result=ledger.snapshot()
        assertEquals(1,result.exactUrlUnknownFramePairs)
        assertEquals(1,result.networkWithoutTrustedFrame)
        assertEquals(0,result.trustedFrameCoObserved)
    }

    @Test fun differentSignedQueriesAreNeverReconciled() {
        val ledger=MediaNetworkCorrelationLedger()
        ledger.observe(binding,media(1,url="https://cdn.example/a.mp4?token=A",
            sources=setOf(MediaSource.NETWORK),frame=7))
        ledger.observe(binding,media(2,url="https://cdn.example/a.mp4?token=B",
            sources=setOf(MediaSource.DOM),frame=7))
        val r=ledger.snapshot()
        assertEquals(0,r.exactUrlCrossFramePairs+r.exactUrlUnknownFramePairs)
        assertEquals(0,r.networkAndDomOnOneRecord)
    }

    @Test fun provisionalSameGroupDifferentUrlsAreNotProofOfSameMovie() {
        val ledger=MediaNetworkCorrelationLedger()
        ledger.observe(binding,media(1,url="https://cdn.example/480.mp4",group="g1"))
        ledger.observe(binding,media(2,url="https://cdn.example/1080.mp4",group="g1",
            sources=setOf(MediaSource.NETWORK)))
        assertEquals(1,ledger.snapshot().sameGroupDifferentUrlPairs)
        assertEquals(0,ledger.snapshot().networkAndDomOnOneRecord)
    }

    @Test fun sameUrlDifferentFormatsAreReportedWithoutMerging() {
        val ledger=MediaNetworkCorrelationLedger()
        ledger.observe(binding,media(1,format=MediaFormat.MP4))
        ledger.observe(binding,media(2,format=MediaFormat.HLS))
        assertEquals(1,ledger.snapshot().exactUrlDifferentFormatPairs)
    }

    @Test fun separateEngineSessionsAndPrivacySwitchAreIsolated() {
        val ledger=MediaNetworkCorrelationLedger()
        ledger.observe(binding,media(1,sources=setOf(MediaSource.DOM)))
        ledger.observe(binding.copy(session=Any()),media(2,sources=setOf(MediaSource.NETWORK)))
        assertEquals(0,ledger.snapshot().exactUrlCrossFramePairs)
        assertEquals(0,ledger.snapshot().exactUrlUnknownFramePairs)
        ledger.observe(binding.copy(contextId="new-private-profile"),
            media(3,sources=setOf(MediaSource.NETWORK)))
        assertEquals(1,ledger.snapshot().partitionResets)
        assertEquals(0,ledger.snapshot().exactUrlUnknownFramePairs)
    }

    @Test fun navigationEpochNeverReusesOldMediaIdentity() {
        val ledger=MediaNetworkCorrelationLedger()
        ledger.observe(binding,media(1,sources=setOf(MediaSource.DOM)))
        ledger.navigation(binding)
        ledger.observe(binding,media(1,sources=setOf(MediaSource.NETWORK)))
        val r=ledger.snapshot()
        assertEquals(2,r.observedRecords)
        assertEquals(0,r.networkAndDomOnOneRecord)
        assertEquals(1,r.navigationEpochs)
    }

    @Test fun repeatedUpdatesDoNotDoubleCountAndCapacityIsBounded() {
        val ledger=MediaNetworkCorrelationLedger(2)
        ledger.observe(binding,media(1,sources=setOf(MediaSource.DOM)))
        ledger.observe(binding,media(1,sources=setOf(MediaSource.DOM,MediaSource.NETWORK)))
        ledger.observe(binding,media(2))
        ledger.observe(binding,media(3))
        val result=ledger.snapshot()
        assertEquals(2,result.observedRecords)
        assertEquals(1,result.networkAndDomOnOneRecord)
        assertEquals(1,result.droppedRecords)
    }

    @Test fun anonymousSnapshotNeverExportsSensitiveUrlsOrTitles() {
        val ledger=MediaNetworkCorrelationLedger()
        ledger.observe(binding,media(1,title="SECRET_MOVIE"))
        val json=Json.encodeToString(ledger.snapshot())
        for(secret in listOf("cdn.example","PRIVATE","SECRET_MOVIE","secret-tab","secret-profile"))
            assertFalse(json.contains(secret))
        ledger.clear()
        assertEquals(0,ledger.snapshot().observedRecords)
    }
}
