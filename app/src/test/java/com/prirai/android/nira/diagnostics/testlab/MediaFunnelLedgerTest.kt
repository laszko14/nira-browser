package com.prirai.android.nira.diagnostics.testlab

import com.prirai.android.nira.media.MediaEntry
import com.prirai.android.nira.media.MediaFormat
import com.prirai.android.nira.media.MediaSessionBinding
import com.prirai.android.nira.media.MediaSource
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class MediaFunnelLedgerTest {
    private val normal=MediaSessionBinding(Any(),"private-runtime-key",null,false)
    private fun media(
        id:Long, title:String?=null, poster:String?=null,
        frameAmbiguous:Boolean=false
    )=MediaEntry(id=id,url="https://private.example/stream?secret=TOKEN",
        format=MediaFormat.MP4,sources=setOf(MediaSource.DOM),
        title=title,titleKind=if(title==null)null else "video",
        posterUrl=poster,frameAmbiguous=frameAmbiguous)

    @Test fun stageCountsAreScopedToRecordsAndCandidateEventsAreDistinct() {
        val funnel=MediaFunnelLedger()
        funnel.candidate();funnel.candidate()
        funnel.observe(normal,media(1))
        funnel.observe(normal,media(1,title="Private sample title",poster="https://private.example/poster"))
        funnel.displayed(normal,media(1))
        val snapshot=funnel.snapshot()
        assertEquals(2L,snapshot.stages[MediaFunnelStage.CANDIDATE])
        assertEquals(1L,snapshot.stages[MediaFunnelStage.DISCOVERED])
        assertEquals(1L,snapshot.stages[MediaFunnelStage.ASSOCIATED])
        assertEquals(1L,snapshot.stages[MediaFunnelStage.LABEL_PRESENT])
        assertEquals(1L,snapshot.stages[MediaFunnelStage.NATIVE_ACCEPTED])
        assertEquals(1L,snapshot.stages[MediaFunnelStage.TILE_SNAPSHOT_ASSOCIATED])
        assertEquals(0L,snapshot.stages[MediaFunnelStage.VERIFIED])
        assertEquals(1L,snapshot.stages[MediaFunnelStage.STORED])
        assertEquals(1L,snapshot.stages[MediaFunnelStage.DISPLAYED])
        assertEquals(0L,snapshot.unknownReasons[MediaUnknownReason.TITLE_UNKNOWN])
        assertEquals(1L,snapshot.unknownReasons[MediaUnknownReason.SIZE_UNKNOWN])
    }

    @Test fun independentSessionsAndNavigationsNeverCoalesceIdenticalIds() {
        val funnel=MediaFunnelLedger()
        val privateTab=MediaSessionBinding(Any(),"private-runtime-key",null,true)
        funnel.observe(normal,media(7))
        funnel.observe(privateTab,media(7))
        funnel.navigation(normal)
        funnel.observe(normal,media(7))
        assertEquals(3L,funnel.snapshot().stages[MediaFunnelStage.DISCOVERED])
        assertEquals(3L,funnel.snapshot().stages[MediaFunnelStage.NATIVE_ACCEPTED])
        assertEquals(0L,funnel.snapshot().stages[MediaFunnelStage.TILE_SNAPSHOT_ASSOCIATED])
    }

    @Test fun conflictsAndUnknownsAreCountersWithoutRawMediaData() {
        val funnel=MediaFunnelLedger()
        funnel.observe(normal,media(1,frameAmbiguous=true),verifiedEvidence=true)
        funnel.conflict(MediaFunnelConflict.TITLE)
        funnel.conflict(MediaFunnelConflict.POSTER_ALTERNATIVE)
        funnel.conflict(MediaFunnelConflict.VARIANT_GROUP_MISMATCH)
        val snapshot=funnel.snapshot()
        assertEquals(1L,snapshot.stages[MediaFunnelStage.VERIFIED])
        assertEquals(1L,snapshot.unknownReasons[MediaUnknownReason.OWNER_AMBIGUOUS])
        assertEquals(1L,snapshot.titleConflicts)
        assertEquals(1L,snapshot.posterAlternatives)
        assertEquals(1L,snapshot.variantGroupMismatches)
        assertFalse(snapshot.extendedNetworkProbesEnabled)
        assertNull(snapshot.probeBytes)
        assertNull(snapshot.probeElapsedMs)
        val json=Json.encodeToString(snapshot)
        for (sensitive in listOf("private.example","TOKEN","Private sample title","private-runtime-key"))
            assertFalse(json.contains(sensitive))
    }

    @Test fun boundedLedgerDoesNotAccumulateUnboundedRecords() {
        val funnel=MediaFunnelLedger(capacity=2)
        repeat(4){funnel.observe(normal,media(it.toLong()))}
        val snapshot=funnel.snapshot()
        assertEquals(2L,snapshot.stages[MediaFunnelStage.STORED])
        assertEquals(2L,snapshot.droppedRecords)
        funnel.clear()
        assertEquals(0L,funnel.snapshot().stages[MediaFunnelStage.STORED])
    }
}
