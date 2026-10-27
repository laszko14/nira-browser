package com.prirai.android.nira.diagnostics.testlab

import com.prirai.android.nira.media.MediaEntry
import com.prirai.android.nira.media.MediaFormat
import com.prirai.android.nira.media.MediaSessionBinding
import com.prirai.android.nira.media.MediaSource
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class VideoDiscoveryLedgerV1172Test {
    private val session = MediaSessionBinding(Any(), "test-tab", null, false)
    private fun media(id: Int, title: String? = null, kind: String? = null,
                      group: String? = "g$id") = MediaEntry(
        id.toLong(), "https://cdn.example/private-token$id.mp4", MediaFormat.MP4,
        setOf(MediaSource.DOM), group, title, kind
    )

    @Test fun twelveDifferentVideosHaveIndependentAnonymousIdentities() {
        val ledger = VideoDiscoveryLedger()
        (1..12).forEach { ledger.observe(session, media(it, "Episode $it", "player")) }
        val report = ledger.snapshot()
        assertEquals(12, report.sources)
        assertEquals(12, report.groups)
        assertEquals(12, report.initialTitles)
        assertEquals(0, report.groupsWithoutTitle)
        assertEquals((1..12).toList(), report.examples.map { it.film })
    }

    @Test fun twelveUntitledVideosRemainUnknownWithoutCopyingPageHeading() {
        val ledger = VideoDiscoveryLedger()
        (1..12).forEach { ledger.observe(session, media(it)) }
        assertEquals(12, ledger.snapshot().groupsWithoutTitle)
        assertEquals(12, ledger.snapshot().titleKinds["UNKNOWN"])
    }

    @Test fun variantsAreOneGroupButTwoSourcesAndDifferentSameTitlesStaySeparate() {
        val ledger = VideoDiscoveryLedger()
        ledger.observe(session, media(1, "Shared caption", "player", "a"))
        ledger.observe(session, media(2, null, null, "a"))
        ledger.observe(session, media(3, "Shared caption", "player", "b"))
        val result = ledger.snapshot()
        assertEquals(3, result.sources)
        assertEquals(2, result.groups)
        assertEquals(1, result.groupsWithVariants)
        assertEquals(0, result.groupTitleConflicts)
    }

    @Test fun lateTitleAndStrongerProvenanceUpdateSameFilm() {
        val ledger = VideoDiscoveryLedger()
        ledger.observe(session, media(1))
        ledger.observe(session, media(1, "Loose name", "link"))
        ledger.observe(session, media(1, "Bound name", "player"))
        assertEquals(1, ledger.snapshot().sources)
        assertEquals(1, ledger.snapshot().initialTitles)
        assertEquals(1, ledger.snapshot().improvements)
        assertEquals("PLAYER", ledger.snapshot().examples.single().titleSource)
    }

    @Test fun conflictingVariantTitlesAreFlaggedAndNotExported() {
        val ledger = VideoDiscoveryLedger()
        ledger.observe(session, media(1, "Sensitive film one", "player", "same"))
        ledger.observe(session, media(2, "Sensitive film two", "player", "same"))
        val summary = ledger.snapshot()
        assertEquals(1, summary.groupTitleConflicts)
        assertEquals("CONFLICT", summary.examples.single().titleSource)
        assertFalse(summary.toString().contains("Sensitive"))
        assertFalse(summary.toString().contains("private-token"))
        ledger.rejectedTitle(session, true)
        ledger.rejectedTitle(session, false)
        assertEquals(1, ledger.snapshot().rejectedOrConflictingTitles)
        assertEquals(1, ledger.snapshot().weakerRejected)
    }

    @Test fun navigationAndPrivacySessionCannotMergeSameGroupId() {
        val ledger = VideoDiscoveryLedger()
        ledger.observe(session, media(1, "First", "player", "same"))
        ledger.navigation(session)
        ledger.observe(session, media(1, "Next", "player", "same"))
        val privateBinding = MediaSessionBinding(Any(), "test-tab", null, true)
        ledger.observe(privateBinding, media(1, "Private", "player", "same"))
        assertEquals(3, ledger.snapshot().groups)
        assertEquals(1, ledger.snapshot().navigations)
    }

    @Test fun recordCapacityBoundsMemoryButPreservesDroppedCount() {
        val ledger = VideoDiscoveryLedger(2)
        repeat(30) { ledger.observe(session, media(it + 1)) }
        assertEquals(2, ledger.snapshot().sources)
        assertEquals(28, ledger.snapshot().droppedSources)
        ledger.clear()
        assertEquals(0, ledger.snapshot().sources)
    }

    @Test fun titleOriginIsTrackedAnonymouslyFromAcceptedSourceToPanel() {
        val ledger = VideoDiscoveryLedger()
        val named = media(1, "Private episode title", "video")
        val unknown = media(2)
        ledger.observe(session, named)
        ledger.observe(session, unknown)
        ledger.displayed(session, named)
        ledger.displayed(session, unknown)
        val summary = ledger.snapshot()
        assertEquals(2, summary.uiObservedSources)
        assertEquals(1, summary.uiObservedTitledSources)
        assertEquals("SCOPED_HINT", summary.examples.first().status)
        assertEquals("FALLBACK", summary.examples.last().status)
        assertFalse(summary.toString().contains("Private episode title"))
        assertFalse(summary.toString().contains("private-token"))
    }

    @Test fun exportedReportIsBoundedAndContainsNoRawTitleOrUrl() {
        val ledger = VideoDiscoveryLedger()
        ledger.observe(session, media(1, "Secret program", "player"))
        val engine = TestLabEngine({ 1_000_000_000L }, LabIdentity(1172,"0.5.22-alpha77-clean"))
        val token = engine.start(TestType.VIDEO_DISCOVERY)!!
        engine.begin(token)
        engine.stop(token)
        assertTrue(engine.complete(token, ledger.snapshot()))
        val report = TestLabReportGenerator.generate(engine.latest()!!)
        assertTrue(report.text.contains("VIDEO DISCOVERY"))
        assertTrue(report.text.contains("Film #1"))
        assertFalse(report.text.contains("Secret program"))
        assertFalse(report.json.contains("Secret program"))
        assertFalse(report.json.contains("cdn.example"))
        assertNotNull(Json.decodeFromString<TestLabReport>(report.json).videoDiscovery)
    }
}
