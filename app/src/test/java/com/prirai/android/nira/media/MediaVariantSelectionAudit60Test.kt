package com.prirai.android.nira.media

import org.junit.Assert.*
import org.junit.Test

class MediaVariantSelectionAudit60Test {
    private fun media(
        id: Long, group: String = "movie", frame: Long? = null,
        source: MediaSource = MediaSource.NETWORK,
        status: MediaPlaybackStatus = MediaPlaybackStatus.NEEDS_VERIFICATION,
        poster: String? = null, origin: String? = null, posterAmbiguous: Boolean = false,
        duration: Int? = null, live: Boolean = false, bytes: Long? = null,
    ) = MediaEntry(
        id = id, url = "https://cdn.example/variant-$id.m3u8?token=opaque",
        format = MediaFormat.HLS, sources = setOf(source), groupId = group,
        frameKey = frame, playbackStatus = status, posterUrl = poster,
        posterOrigin = origin, posterAmbiguous = posterAmbiguous,
        durationSeconds = duration, isLive = live, sizeBytes = bytes,
        sizeOrigin = if (bytes != null) "HLS_BYTERANGE" else null,
    )

    @Test fun confirmedAlternativeBeatsUnplayableNetworkMaster() {
        val master = media(1, status = MediaPlaybackStatus.AUTH_ERROR)
        val child = media(2, status = MediaPlaybackStatus.CONFIRMED, source = MediaSource.DOM)
        for (ordered in listOf(listOf(master, child), listOf(child, master))) {
            val item = mediaTileItems(ordered).single()
            assertEquals(2L, item.entry.id)
            assertEquals(2, item.variantCount)
            assertTrue(mediaVariantPlayable(item.entry))
            assertEquals(1L, item.selected(1).id)
            assertEquals(2L, item.selected(2).id)
            assertEquals(2L, item.selected(999).id)
            assertFalse(mediaVariantPlayable(item.selected(1)))
        }
    }

    @Test fun posterFallsBackFromSecondVariantWithoutSwitchingPlaybackUrl() {
        val master = media(1, status = MediaPlaybackStatus.CONFIRMED)
        val child = media(2, poster = "https://images.example/cover.jpg", origin = "LOCAL_CARD")
        val item = mediaTileItems(listOf(master, child)).single()
        assertEquals(master.id, item.entry.id)
        assertEquals("https://images.example/cover.jpg", item.posterUrl)
        assertEquals(master.url, item.selected(null).url)
        assertNull(item.entry.posterUrl)
    }

    @Test fun strongerPosterWinsAndConflictingEqualEvidenceIsUnknown() {
        val low = media(1, poster = "https://images.example/structured.jpg", origin = "STRUCTURED_VIDEO")
        val high = media(2, poster = "https://images.example/element.jpg", origin = "VIDEO_ELEMENT")
        assertEquals(high.posterUrl, mediaTileItems(listOf(low, high)).single().posterUrl)
        val conflicting = media(3, poster = "https://images.example/different.jpg", origin = "VIDEO_ELEMENT")
        assertNull(mediaTileItems(listOf(high, conflicting)).single().posterUrl)
        assertEquals(high.posterUrl, mediaTileItems(listOf(high, media(4, poster = high.posterUrl, origin = "VIDEO_ELEMENT"))).single().posterUrl)
    }

    @Test fun groupPosterNeverCrossesFrameOrMovieAndAmbiguousEvidenceIsIgnored() {
        val a = media(1, group = "g1", frame = 11, poster = "https://images.example/a.jpg", origin = "LOCAL_CARD")
        val b = media(2, group = "g1", frame = 22, poster = "https://images.example/b.jpg", origin = "LOCAL_CARD")
        val c = media(3, group = "g2", frame = 11, poster = "https://images.example/c.jpg", origin = "LOCAL_CARD")
        val items = mediaTileItems(listOf(a, b, c))
        assertEquals(3, items.size)
        assertEquals(setOf(a.posterUrl, b.posterUrl, c.posterUrl), items.map { it.posterUrl }.toSet())
        val ambiguous = media(4, poster = "https://images.example/ambiguous.jpg",
            origin = "LOCAL_CARD", posterAmbiguous = true)
        assertNull(mediaTileItems(listOf(ambiguous)).single().posterUrl)
    }

    @Test fun durationAndLiveFallbackNeverTransferOtherVariantSizeOrBitrate() {
        val master = media(1, bytes = null)
        val child = media(2, duration = 91, bytes = 6_000_000)
        val item = mediaTileItems(listOf(master, child)).single()
        assertEquals(91, item.durationFor(master))
        assertEquals("1:31", durationText(item.durationFor(master)))
        assertNull(sizeText(item.entry))
        assertEquals("6,0 MB", sizeText(child))
        val live = mediaTileItems(listOf(master, media(3, live = true))).single()
        assertTrue(live.liveFor(master))
        assertNull(live.durationFor(master))
        val contradictory = mediaTileItems(listOf(media(4, duration = 91), media(5, duration = 105))).single()
        assertNull(contradictory.groupDurationSeconds)
        assertNull(contradictory.durationFor(media(6)))
        val mixedLive = mediaTileItems(listOf(media(7, live = true), media(8, duration = 91))).single()
        assertFalse(mixedLive.groupIsLive)
    }

    @Test fun variantsStaySeparateAcrossGroupsAndPrivacyAndActionsCannotCrossTab() {
        val sameTitleButOtherGroup = mediaTileItems(listOf(media(1, group = "g1"), media(2, group = "g2")))
        assertEquals(2, sameTitleButOtherGroup.size)
        val session = Any()
        fun tab(s: Any = session, id: String = "one", profile: String? = "work",
                privateMode: Boolean = false) = MediaTabState(
            MediaSessionBinding(s, id, profile, privateMode), "https://site.example/watch/one", false)
        val shownEntry = media(1)
        val shown = MediaUiSnapshot(tab(), listOf(shownEntry))
        val latest = MediaUiSnapshot(tab(), listOf(shownEntry))
        assertNotNull(mediaCurrentActionEntry(shown, latest, 1))
        assertNull(mediaCurrentActionEntry(shown, MediaUiSnapshot(tab(Any()), listOf(shownEntry)), 1))
        assertNull(mediaCurrentActionEntry(shown, MediaUiSnapshot(tab(privateMode = true), listOf(shownEntry)), 1))
        assertNull(mediaCurrentActionEntry(shown, MediaUiSnapshot(tab(id = "other"), listOf(shownEntry)), 1))
        assertNull(mediaCurrentActionEntry(shown, MediaUiSnapshot(tab(profile = "other"), listOf(shownEntry)), 1))
        assertNull(mediaCurrentActionEntry(shown, MediaUiSnapshot(tab(), listOf(media(1, group = "moved"))), 1))
        assertNull(mediaCurrentActionEntry(shown, MediaUiSnapshot(tab(), listOf(media(1, frame = 77))), 1))
        assertNull(mediaCurrentActionEntry(shown, MediaUiSnapshot(tab(), emptyList()), 1))
        assertNull(mediaCurrentActionEntry(shown, latest, 999))
        // An exact record may renew its URL without retaining the old signed link.
        val renewed = MediaEntry(
            id = shownEntry.id,
            url = "https://cdn.example/new-token.m3u8?sig=renewed",
            format = shownEntry.format,
            sources = shownEntry.sources,
            groupId = shownEntry.groupId,
            frameKey = shownEntry.frameKey,
            playbackStatus = shownEntry.playbackStatus,
        )
        assertEquals(renewed.url, mediaCurrentActionEntry(shown,
            MediaUiSnapshot(tab(), listOf(renewed)), 1)?.url)
        assertFalse(mediaVariantLabel(shownEntry, 1).contains("opaque"))
    }
}
