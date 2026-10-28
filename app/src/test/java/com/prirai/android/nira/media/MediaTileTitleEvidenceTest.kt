package com.prirai.android.nira.media

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaTileTitleEvidenceTest {
    @Test fun filenameFallbackIsExplicitlyUnverified() {
        val entry = MediaEntry(
            id = 11L,
            url = "https://cdn.example.invalid/episode-7-master.m3u8?token=fixture",
            format = MediaFormat.HLS,
            sources = setOf(MediaSource.DOM),
        )
        assertEquals("Plik (niepotwierdzony): episode 7 master",
            mediaTileItems(listOf(entry)).single().title)
        assertEquals("episode 7 master", friendlyFileName(entry.url))
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
    @Test fun pageTitleBelongsToOneLogicalMovieRegardlessOfVariantCount() {
        val variants = (1L..3L).map { id -> MediaEntry(
            id = id, url = "https://cdn.example.invalid/$id.m3u8", format = MediaFormat.HLS,
            sources = setOf(MediaSource.PAGE), groupId = "manifest-1-1",
            title = "Jedyny film", titleKind = "page",
        ) }
        assertEquals("Jedyny film", mediaTileItems(variants).single().title)
        val other = MediaEntry(id = 4L, groupId = "other",
            url = "https://cdn.example.invalid/other/master.m3u8", format = MediaFormat.HLS,
            sources = setOf(MediaSource.PAGE), title = "Jedyny film", titleKind = "page")
        assertEquals(listOf("Film #1", "Film #2"),
            mediaTileItems(variants + other).map { it.title })
    }

    @Test fun technicalAndOpaqueFilenamesDoNotBecomeFallbackNames() {
        for (name in listOf("master", "playlist", "index", "stream", "1080p", "video_720p",
                "a8f74b30bc2040fdac63032433bd68ad", "550e8400-e29b-41d4-a716-446655440000",
                "aB3dE7fG9hJ2kL5mN8pQ0rS6tU1vW4xY")) {
            assertEquals(null, friendlyFileName("https://cdn.example.invalid/$name.m3u8"))
        }
        assertEquals("Żółw nad rzeką", friendlyFileName(
            "https://cdn.example.invalid/%C5%BB%C3%B3%C5%82w_nad-rzek%C4%85.mp4?private=fixture"))
    }

}
