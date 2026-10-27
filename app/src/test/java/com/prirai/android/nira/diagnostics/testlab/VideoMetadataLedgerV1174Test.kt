package com.prirai.android.nira.diagnostics.testlab

import com.prirai.android.nira.media.MediaEntry
import com.prirai.android.nira.media.MediaFormat
import com.prirai.android.nira.media.MediaSessionBinding
import com.prirai.android.nira.media.MediaSource
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class VideoMetadataLedgerV1174Test {
    private val binding = MediaSessionBinding(Any(), "tab", null, false)
    private fun entry(id: Long, duration: Int?=null, size: Long?=null,
                      estimated: Boolean=false, live: Boolean=false) =
        MediaEntry(id, "https://example.invalid/secret-token.mp4",
            MediaFormat.HLS, setOf(MediaSource.NETWORK),
            "group", "Do not export", "player",
            duration, size, sizeEstimated=estimated,
            durationOrigin=if (duration != null) "HLS_EXTINF" else null,
            sizeOrigin=if (estimated) "MANIFEST_BITRATE" else null,
            isLive=live)

    @Test fun acceptedMetadataAndLateUpdatesUseOneIdentity() {
        val ledger = VideoMetadataLedger()
        ledger.observe(binding, entry(1))
        ledger.observe(binding, entry(1, 90, 22_500_000, true))
        ledger.displayed(binding, entry(1, 90, 22_500_000, true))
        val r = ledger.snapshot()
        assertEquals(1, r.observedSources)
        assertEquals(1, r.durationKnown)
        assertEquals(1, r.sizeEstimated)
        assertEquals(1, r.updates)
        assertEquals(1, r.uiDisplayed)
        assertEquals("HLS_EXTINF", r.examples.single().durationSource)
        assertFalse(r.toString().contains("token"))
        assertFalse(r.toString().contains("Do not export"))
    }

    @Test fun liveAndUnknownAreNotFabricated() {
        val ledger = VideoMetadataLedger()
        ledger.observe(binding, entry(1, live=true))
        ledger.observe(binding, entry(2))
        val r=ledger.snapshot()
        assertEquals(1,r.live)
        assertEquals(1,r.durationUnknown)
        assertEquals(1,r.sizeUnknown)
        assertEquals(null,r.examples.first().sizeBytes)
    }

    @Test fun navigationAndEngineSessionSeparateSameIds() {
        val ledger=VideoMetadataLedger()
        ledger.observe(binding,entry(1,10))
        ledger.navigation(binding)
        ledger.observe(binding,entry(1,20))
        ledger.observe(MediaSessionBinding(Any(),"tab",null,true),entry(1,30))
        assertEquals(3,ledger.snapshot().observedSources)
        assertEquals(1,ledger.snapshot().navigations)
    }

    @Test fun boundedAndSerializedReportDoesNotExposeSensitiveStrings() {
        val ledger=VideoMetadataLedger(2)
        (1L..10L).forEach { ledger.observe(binding,entry(it,20,5000000)) }
        val summary=ledger.snapshot()
        assertEquals(2,summary.observedSources)
        assertEquals(8,summary.droppedSources)
        val e=TestLabEngine({0L},LabIdentity(1174,"0.5.22-alpha79-clean"))
        val t=e.start(TestType.VIDEO_METADATA)!!
        e.begin(t)
        e.stop(t)
        assertTrue(e.complete(t,metadata=summary))
        val report=TestLabReportGenerator.generate(e.latest()!!)
        assertTrue(report.text.contains("VIDEO METADATA"))
        assertTrue(report.text.contains("Film #1"))
        assertFalse(report.json.contains("secret-token"))
        assertNotNull(Json.decodeFromString<TestLabReport>(report.json).videoMetadata)
        ledger.clear()
    }
}
