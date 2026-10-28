package com.prirai.android.nira.media

import org.junit.Assert.*
import org.junit.Test

class VideoTilesMetadataTest {
    private fun entry(format: MediaFormat, size: Long?) = MediaEntry(
        1, "https://fixture.invalid/movie", format, setOf(MediaSource.NETWORK), sizeBytes = size,
    )

    @Test fun absentOrInvalidMetadataStaysUnknown() {
        assertNull(durationText(null))
        assertNull(durationText(0))
        assertNull(durationText(-1))
        assertNull(sizeText(entry(MediaFormat.MP4, null)))
        assertNull(sizeText(entry(MediaFormat.MP4, -5)))
    }

    @Test fun manifestSizeNeverPretendsToBeMovieSize() {
        assertNull(sizeText(entry(MediaFormat.HLS, 2048)))
        assertNull(sizeText(entry(MediaFormat.DASH, 4096)))
        assertEquals("2,0 MB", sizeText(entry(MediaFormat.MP4, 2_000_000)))
    }

    @Test fun currentQualityRequiresPlaybackConfirmationRatherThanManifestDimensions() {
        val base = entry(MediaFormat.HLS, null)
        assertNull(qualityText(base))
        val advertised = MediaEntry(1, "https://fixture.invalid/video.m3u8",
            MediaFormat.HLS, setOf(MediaSource.DOM), width = 1280, height = 720)
        assertNull(qualityText(advertised))
        val unconfirmed = MediaEntry(2, "https://fixture.invalid/video.m3u8",
            MediaFormat.HLS, setOf(MediaSource.NETWORK),
            quality = "Auto", width = 1920, height = 1080)
        assertNull(qualityText(unconfirmed))
        val confirmed = MediaEntry(3, "https://fixture.invalid/video.mp4",
            MediaFormat.MP4, setOf(MediaSource.DOM), quality = "720p",
            playbackStatus = MediaPlaybackStatus.CONFIRMED)
        assertEquals("720p", qualityText(confirmed))
    }

    @Test fun durationUsesRealBoundedMetadata() {
        assertEquals("1:05", durationText(65))
        assertEquals("1:01:01", durationText(3661))
    }
}
