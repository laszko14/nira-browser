package com.prirai.android.nira.media

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaTileTitleEvidenceTest {
    @Test fun filenameIsNeverDisplayedAsVerifiedTitle() {
        val entry = MediaEntry(
            id = 11L,
            url = "https://cdn.example.invalid/episode-7-master.m3u8?token=fixture",
            format = MediaFormat.HLS,
            sources = setOf(MediaSource.DOM),
        )
        assertEquals("Film #1", mediaTileItems(listOf(entry)).single().title)
    }

    @Test fun manifestResolutionIsNotCurrentPlayQuality() {
        val entry = MediaEntry(
            id = 21L, url = "https://cdn.example.invalid/master.mpd",
            format = MediaFormat.DASH, sources = setOf(MediaSource.NETWORK),
            quality = "1080p", width = 1920, height = 1080,
            availableQualities = "720p,1080p",
            playbackStatus = MediaPlaybackStatus.NEEDS_VERIFICATION,
        )
        assertEquals(null, qualityText(entry))
    }

    @Test fun distinctFrameGroupsCannotBorrowTitlesOrIdentifiers() {
        val first = MediaEntry(
            id = 1L, url = "https://cdn.example.invalid/a.mp4",
            format = MediaFormat.MP4, sources = setOf(MediaSource.DOM),
            groupId = "same", frameKey = 1L, title = "Film A", titleKind = "player",
        )
        val second = MediaEntry(
            id = 2L, url = "https://cdn.example.invalid/b.mp4",
            format = MediaFormat.MP4, sources = setOf(MediaSource.DOM),
            groupId = "same", frameKey = 2L, title = "Film B", titleKind = "player",
        )
        val tiles = mediaTileItems(listOf(first, second))
        assertEquals(2, tiles.size)
        assertEquals(setOf("Film A", "Film B"), tiles.map { it.title }.toSet())
    }
}
