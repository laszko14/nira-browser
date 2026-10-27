package com.prirai.android.nira.diagnostics.testlab

import com.prirai.android.nira.media.MediaEntry
import com.prirai.android.nira.media.MediaFormat
import com.prirai.android.nira.media.MediaSessionBinding
import com.prirai.android.nira.media.MediaSource
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class MediaIdentityShadowGraphTest {
    private val session=Any()
    private val normal=MediaSessionBinding(session,"test-tab","ordinary",false)
    private fun entry(
        id: Long, group: String? = null, frame: Long? = 1,
        title: String? = null, kind: String? = null,
        ambiguous: Boolean = false, hint: Int? = null,
        url: String = "https://example.invalid/${id}.mp4?token=PRIVATE_SECRET",
    )=MediaEntry(id=id,url=url,format=MediaFormat.MP4,
        sources=setOf(MediaSource.DOM),groupId=group,title=title,
        titleKind=kind,titleAmbiguous=ambiguous,frameKey=frame,
        variantCount=hint,posterUrl="https://example.invalid/private-poster-$id.jpg")

    @Test fun sameGroupWithinFrameProducesProvisionalVariantEdges() {
        val graph=MediaIdentityShadowGraph()
        graph.observe(normal,entry(1,group="movie-1",title="A",kind="player"))
        graph.observe(normal,entry(2,group="movie-1",title="A",kind="video"))
        graph.observe(normal,entry(3,group="movie-2",title="B",kind="structured"))
        graph.comparePanel(normal,listOf(entry(1,"movie-1"),entry(2,"movie-1"),entry(3,"movie-2")))
        val result=graph.snapshot()
        assertEquals(3,result.sourceNodes)
        assertEquals(2,result.filmNodes)
        assertEquals(1,result.groupedFilms)
        assertEquals(1,result.provisionalVariantEdges)
        assertEquals(0,result.groupsWithConflictingTitles)
        assertEquals(1,result.panelSnapshotsCompared)
        assertEquals(0,result.panelGroupCountDisagreements)
        assertFalse(result.changesPlaybackOrUi)
    }

    @Test fun repeatedGroupIdInTwoIframesNeverMerges() {
        val graph=MediaIdentityShadowGraph()
        graph.observe(normal,entry(1,group="g1",frame=12))
        graph.observe(normal,entry(2,group="g1",frame=13))
        val result=graph.snapshot()
        assertEquals(2,result.filmNodes)
        assertEquals(1,result.crossFrameGroupIdCollisions)
        assertEquals(0,result.groupedFilms)
    }

    @Test fun conflictingSpecificTitlesAreFlaggedAndNeverAppliedToUi() {
        val graph=MediaIdentityShadowGraph()
        graph.observe(normal,entry(1,group="g",title="Different movie A",kind="player"))
        graph.observe(normal,entry(2,group="g",title="Different movie B",kind="player"))
        graph.comparePanel(normal,listOf(entry(1,"g",title="Different movie A",kind="player"),
            entry(2,"g",title="Different movie B",kind="player")))
        assertEquals(1,graph.snapshot().groupsWithConflictingTitles)
        assertEquals(1,graph.snapshot().panelShadowUnsafeGroups)
        assertEquals(0,graph.snapshot().panelGroupCountDisagreements)
    }

    @Test fun higherQualityTitleWinsOverWeakerWithoutFalseConflict() {
        val graph=MediaIdentityShadowGraph()
        graph.observe(normal,entry(1,group="g",title="Owner title",kind="player"))
        graph.observe(normal,entry(2,group="g",title="Wrong weak hint",kind="page"))
        assertEquals(0,graph.snapshot().groupsWithConflictingTitles)
        assertEquals(0,graph.snapshot().groupsWithoutIndividualTitles)
    }

    @Test fun thirtyAndFiftyIndependentMoviesNeverInheritPageWideTitle() {
        for (total in listOf(30,50)) {
            val graph=MediaIdentityShadowGraph()
            val observed=(1L..total.toLong()).map { id ->
                entry(id,group="g$id",title="One page title",kind="page")
            }
            observed.forEach { graph.observe(normal,it) }
            graph.comparePanel(normal,observed)
            val result=graph.snapshot()
            assertEquals(total,result.sourceNodes)
            assertEquals(total,result.filmNodes)
            assertEquals(total,result.groupsWithoutIndividualTitles)
            assertEquals(0,result.provisionalVariantEdges)
            assertEquals(0,result.panelGroupCountDisagreements)
        }
    }

    @Test fun profileSwitchAndNavigationUseNewDocumentEpoch() {
        val graph=MediaIdentityShadowGraph()
        val privateBinding=normal.copy(contextId="private-context",isPrivate=true)
        graph.observe(normal,entry(1,group="same",title="Original",kind="video"))
        graph.observe(privateBinding,entry(1,group="same",title="Private",kind="video"))
        graph.navigation(privateBinding)
        graph.observe(privateBinding,entry(1,group="same",title="After navigation",kind="video"))
        val result=graph.snapshot()
        assertEquals(3,result.filmNodes)
        assertEquals(1,result.partitionResets)
        assertEquals(1,result.navigationEpochs)
    }

    @Test fun differentEngineSessionsCannotCombineGroups() {
        val graph=MediaIdentityShadowGraph()
        val separate=MediaSessionBinding(Any(),"tab-2","ordinary",false)
        graph.observe(normal,entry(1,group="same",frame=null))
        graph.observe(separate,entry(1,group="same",frame=null))
        assertEquals(2,graph.snapshot().filmNodes)
    }

    @Test fun anonymousSourcesStaySeparateEvenWithIdenticalUrlsAndExposeMismatch() {
        val graph=MediaIdentityShadowGraph()
        val one=entry(1,group=null,url="https://example.invalid/identical.mp4")
        val two=entry(2,group=null,url="https://example.invalid/identical.mp4")
        graph.observe(normal,one);graph.observe(normal,two)
        graph.comparePanel(normal,listOf(one,two))
        assertEquals(2,graph.snapshot().filmNodes)
        assertEquals(1,graph.snapshot().panelGroupCountDisagreements)
    }

    @Test fun unknownFrameGroupAndMissingManifestEdgesStayExplicitlyUncertain() {
        val graph=MediaIdentityShadowGraph()
        graph.observe(normal,entry(1,group="playlist",frame=null,hint=8))
        graph.observe(normal,entry(2,group="playlist",frame=null,hint=8))
        val summary=graph.snapshot()
        assertEquals(1,summary.groupsWithAmbiguousOwners)
        assertEquals(1,summary.manifestVariantHintsWithoutLinkedSources)
        assertFalse(summary.trackLinkageAvailable)
        assertEquals(0,summary.audioTracksLinked)
        assertEquals(0,summary.subtitleTracksLinked)
    }

    @Test fun sourceGroupAndFrameReassignmentsDoNotSilentlyRewriteShadowIdentity() {
        val graph=MediaIdentityShadowGraph()
        graph.observe(normal,entry(1,group="initial",frame=10))
        graph.observe(normal,entry(1,group="conflicting",frame=11))
        val summary=graph.snapshot()
        assertEquals(1,summary.filmNodes)
        assertEquals(1,summary.sourceGroupReassignments)
        assertEquals(1,summary.sourceFrameReassignments)
    }

    @Test fun boundedMemoryAndPrivacySafeExport() {
        val graph=MediaIdentityShadowGraph(capacity=2)
        for (id in 1L..4L) graph.observe(normal,entry(id,group="SECRET_GROUP_$id",
            title="SECRET_MOVIE_$id",kind="player"))
        val summary=graph.snapshot()
        assertEquals(2,summary.sourceNodes)
        assertEquals(2,summary.droppedSources)
        val json=Json.encodeToString(summary)
        for (word in listOf("PRIVATE_SECRET","SECRET_MOVIE","SECRET_GROUP",
                "example.invalid","ordinary","test-tab")) assertFalse(json.contains(word))
        graph.clear()
        assertEquals(0,graph.snapshot().sourceNodes)
    }
}
