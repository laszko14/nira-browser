package com.prirai.android.nira.media

import org.junit.Assert.*
import org.junit.Test

class VideoMetadataV1174Test {
    private val binding=MediaSessionBinding(Any(),"tab","profile",false)
    private val page="https://page.example/watch"
    private fun repo(): Pair<MediaRepository,MediaTabState> {
        val repository=MediaRepository()
        val tab=MediaTabState(binding,page,false)
        repository.observe(listOf(tab))
        assertTrue(repository.registerDocument(tab,page,1).accepted)
        return repository to tab
    }

    @Test fun validatedHlsVodCombinesDurationAndPerVariantBitrateWithoutDuplicates() {
        val (repo,tab)=repo()
        val url="https://cdn.example/720p.m3u8"
        val prior=MediaDetectorMessage.Candidate(url,source=MediaSource.NETWORK,
            provenance="derived",bitrate=2_000_000L)
        val detected=MediaClassifier.fromNetwork(prior,binding)!!
        assertEquals(MediaRepository.InsertResult.ADDED,
            repo.add(tab,page,1,detected,MediaSource.NETWORK,metadata=prior))
        val finished=MediaDetectorMessage.Candidate(url,source=MediaSource.NETWORK,
            provenance="observed",verification="manifest_verified",isLive=false,
            durationSeconds=120,bitrate=2_000_000L)
        assertEquals(MediaRepository.InsertResult.DUPLICATE,
            repo.add(tab,page,1,detected,MediaSource.NETWORK,metadata=finished))
        val result=repo.currentEntries(tab).single()
        assertEquals(120,result.durationSeconds)
        assertEquals("HLS_EXTINF",result.durationOrigin)
        assertEquals(30_000_000L,result.sizeBytes)
        assertTrue(result.sizeEstimated)
        assertEquals("~30,0 MB",sizeText(result))
    }

    @Test fun exactCompleteHlsRangesOverrideBitrateApproximation() {
        val (repo,tab)=repo()
        val url="https://cdn.example/video.m3u8"
        val detected=MediaClassifier.fromNetwork(
            MediaDetectorMessage.Candidate(url,source=MediaSource.NETWORK),binding)!!
        val estimate=MediaDetectorMessage.Candidate(url,source=MediaSource.NETWORK,
            isLive=false,durationSeconds=50,bitrate=1_000_000,verification="manifest_verified")
        repo.add(tab,page,1,detected,MediaSource.NETWORK,metadata=estimate)
        val exact=MediaDetectorMessage.Candidate(url,source=MediaSource.NETWORK,
            isLive=false,durationSeconds=50,bitrate=1_000_000,
            sizeBytes=7_000_000,verification="manifest_verified")
        repo.add(tab,page,1,detected,MediaSource.NETWORK,metadata=exact)
        val result=repo.currentEntries(tab).single()
        assertEquals(7_000_000L,result.sizeBytes)
        assertFalse(result.sizeEstimated)
        assertEquals("HLS_BYTERANGE",result.sizeOrigin)
        assertEquals("7,0 MB",sizeText(result))
    }

    @Test fun liveManifestHasNoFabricatedTotalSize() {
        val (repo,tab)=repo()
        val url="https://cdn.example/live.m3u8"
        val candidate=MediaDetectorMessage.Candidate(url,source=MediaSource.NETWORK,
            isLive=true,bitrate=1_000_000,verification="manifest_verified")
        val detected=MediaClassifier.fromNetwork(candidate,binding)!!
        repo.add(tab,page,1,detected,MediaSource.NETWORK,metadata=candidate)
        val result=repo.currentEntries(tab).single()
        assertTrue(result.isLive)
        assertNull(result.durationSeconds)
        assertNull(result.sizeBytes)
        assertNull(sizeText(result))
    }

    @Test fun networkManifestDurationRequiresExplicitLiveBoolean() {
        fun decode(s:String)=MediaMessageProtocol.decode(
            """{"schemaVersion":1,"event":"media_candidate","source":"network",
            "url":"https://cdn.example/movie.m3u8","isPrivate":false,$s}"""
        )
        assertNull(decode(""""durationSeconds":120"""))
        val valid=decode(""""isLive":false,"durationSeconds":120""") as MediaDetectorMessage.Candidate
        assertEquals(120,valid.durationSeconds)
        assertNull(decode(""""isLive":true,"durationSeconds":120"""))
    }
}
