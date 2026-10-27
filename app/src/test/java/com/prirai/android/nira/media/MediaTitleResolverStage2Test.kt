package com.prirai.android.nira.media

import org.junit.Assert.*
import org.junit.Test

/** Deterministic in-memory decisions. No test claims real-site title accuracy. */
class MediaTitleResolverStage2Test {
    private fun entry(id: Long, title: String?, kind: String? = "player",
                      ambiguous: Boolean = false, group: String = "g") = MediaEntry(
        id, "https://cdn.example/movie" + id + ".mp4", MediaFormat.MP4,
        setOf(MediaSource.PAGE), groupId = group, title = title,
        titleKind = kind, titleAmbiguous = ambiguous,
    )

    @Test fun separateTextQualityFromSourceAssociation() {
        val player = MediaTitleResolver.candidate("  Odcinek 12: Żółw!  ", "player")!!
        assertEquals("Odcinek 12: Żółw!", player.text)
        assertEquals(MediaTitleResolver.Association.PLAYER_HINT, player.association)
        assertEquals(MediaTitleResolver.TextQuality.SPECIFIC, player.quality)
        val generic = MediaTitleResolver.candidate("1080p", "structured")!!
        assertEquals(MediaTitleResolver.Association.STRUCTURED_URL_HINT, generic.association)
        assertEquals(MediaTitleResolver.TextQuality.TECHNICAL, generic.quality)
    }

    @Test fun strongerHintUpgradesUnknownOrWeakAndWeakerCannotDowngrade() {
        val state = MediaTitleResolver.State()
        assertEquals(MediaTitleResolver.Reason.NO_CANDIDATE, state.offer(null, null))
        assertEquals(MediaTitleResolver.Reason.ACCEPTED, state.offer("Site title", "page"))
        assertEquals(MediaTitleResolver.Reason.UPGRADED, state.offer("Film właściwy", "player"))
        assertEquals(MediaTitleResolver.Reason.REJECTED_WEAKER, state.offer("Other page", "page"))
        assertEquals("Film właściwy", state.snapshot().text)
        assertEquals("player", state.snapshot().kind)
    }

    @Test fun equalStrengthDifferentNamesStayAmbiguousUntilStrongerEvidence() {
        val state = MediaTitleResolver.State()
        state.offer("Tytuł A", "video")
        assertEquals(MediaTitleResolver.Reason.CONFLICT, state.offer("Tytuł B", "video"))
        assertEquals("Tytuł A", state.snapshot().text) // kept in memory, never presented
        assertTrue(state.snapshot().ambiguous)
        assertEquals(MediaTitleResolver.Reason.REPEATED, state.offer("Tytuł A", "video"))
        assertTrue(state.snapshot().ambiguous)
        assertEquals(MediaTitleResolver.Reason.UPGRADED, state.offer("Tytuł z playera", "player"))
        assertFalse(state.snapshot().ambiguous)
        assertEquals("Tytuł z playera", state.snapshot().text)
    }

    @Test fun rejectTechnicalValuesWithoutDestroyingAcceptedTitle() {
        val state = MediaTitleResolver.State()
        state.offer("Życie (odc. 10)", "video")
        for (value in listOf("Play", "VIDEO", "1080p", "master.m3u8", "stream")) {
            assertEquals(MediaTitleResolver.Reason.REJECTED_TECHNICAL, state.offer(value, "player"))
        }
        assertEquals("Życie (odc. 10)", state.snapshot().text)
        assertNull(MediaTitleResolver.candidate("A".repeat(301), "player"))
        assertNull(MediaTitleResolver.candidate("Film", "untrusted"))
        assertFalse(state.snapshot().toString().contains("Życie"))
    }

    @Test fun conflictInsideOneRecordPreventsDisplayingItsHighRankedText() {
        val resolved = MediaTitleResolver.selectGroup(listOf(
            entry(1, "First", ambiguous = true), entry(2, "Lower", kind = "video")
        ), false)
        assertTrue(resolved.ambiguous)
        assertNull(resolved.text)
    }

    @Test fun equalGroupVariantTitlesAreAllowedButDisagreementIsNot() {
        val same = MediaTitleResolver.selectGroup(listOf(
            entry(1, "Odcinek"), entry(2, "Odcinek")), false)
        assertEquals("Odcinek", same.text)
        val conflict = MediaTitleResolver.selectGroup(listOf(
            entry(1, "Odcinek A"), entry(2, "Odcinek B")), false)
        assertNull(conflict.text)
        assertTrue(conflict.ambiguous)
    }

    @Test fun pageFallbackIsDisallowedWhenMultipleMaterialsExist() {
        assertNull(MediaTitleResolver.selectGroup(listOf(
            entry(1, "Site heading", "page")), false).text)
        assertEquals("Site heading", MediaTitleResolver.selectGroup(listOf(
            entry(1, "Site heading", "page")), true).text)
    }

    @Test fun decodesHtmlAndUrlTextWithoutDestroyingUnicodeOrPlus() {
        assertEquals("A – B & C", MediaTitleResolver.normalizeText("A &#8211; B &amp; C"))
        assertEquals("A – B", MediaTitleResolver.normalizeText("A%20%E2%80%93%20B"))
        assertEquals("A+B", MediaTitleResolver.normalizeText("A+B"))
        assertEquals("A+B", MediaTitleResolver.normalizeText("A%2BB"))
        assertEquals("Żółw", MediaTitleResolver.candidate("Żółw", "player")?.text)
        assertEquals("A — B", MediaTitleResolver.candidate("A &mdash; B", "video")?.text)
    }

    @Test fun noTitleOrForeignGroupReturnsUnknown() {
        val empty = MediaTitleResolver.selectGroup(listOf(entry(1, null)), false)
        assertNull(empty.text)
        assertFalse(empty.ambiguous)
    }
}
