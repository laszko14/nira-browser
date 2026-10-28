package com.prirai.android.nira.diagnostics.testlab

import com.prirai.android.nira.media.MediaEntry
import com.prirai.android.nira.media.MediaFormat
import com.prirai.android.nira.media.MediaSessionBinding
import com.prirai.android.nira.media.MediaSource
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class MediaPosterMetadataLedgerTest {
    private val binding=MediaSessionBinding(Any(),"private-tab","private-profile",true)
    private fun entry(id:Long,origin:String?,ambiguous:Boolean=false)=MediaEntry(
        id=id,url="https://private.example/video.mp4?token=SECRET",
        format=MediaFormat.MP4,sources=setOf(MediaSource.DOM),
        posterUrl=if(ambiguous)null else "https://private.example/photo.jpg?token=SECRET",
        posterOrigin=origin,posterAmbiguous=ambiguous,
    )

    @Test fun anonymousOriginsAndConflictsAreCountedWithoutRawUrls() {
        val ledger=VideoMetadataLedger()
        ledger.observe(binding,entry(1,"VIDEO_ELEMENT"))
        ledger.observe(binding,entry(2,"LOCAL_CARD"))
        ledger.observe(binding,entry(3,"LOCAL_CARD",ambiguous=true))
        val result=ledger.snapshot()
        assertEquals(3,result.observedSources)
        assertEquals(2,result.postersAssociated)
        assertEquals(mapOf("VIDEO_ELEMENT" to 1,"LOCAL_CARD" to 1),result.posterOrigins)
        assertEquals(1,result.posterConflicts)
        val serialized=Json.encodeToString(result)
        for(sensitive in listOf("private.example","SECRET","private-tab","private-profile"))
            assertFalse(serialized.contains(sensitive))
    }
}
