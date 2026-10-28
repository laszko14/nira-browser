package com.prirai.android.nira.media

import org.junit.Assert.*
import org.junit.Test

class MediaPosterResolverTest {
    @Test fun titleKindDoesNotAffectImageRanking() {
        val s=MediaPosterResolver.State()
        assertTrue(s.offer("https://poster.invalid/structured.jpg","structured"))
        assertTrue(s.offer("https://poster.invalid/local.jpg","card"))
        assertEquals("https://poster.invalid/local.jpg",s.snapshot().url)
        assertEquals(MediaPosterResolver.Origin.LOCAL_CARD,s.snapshot().origin)
    }
    @Test fun equalRankDifferentImagesAreNotGuessed() {
        val s=MediaPosterResolver.State()
        assertTrue(s.offer("https://poster.invalid/first.jpg","card"))
        assertTrue(s.offer("https://poster.invalid/second.jpg","card"))
        assertNull(s.snapshot().url)
        assertTrue(s.snapshot().ambiguous)
        assertFalse(s.offer("https://poster.invalid/first.jpg","card"))
        assertNull(s.snapshot().url)
    }
    @Test fun strongerVideoElementResolvesConflictedCardSources() {
        val s=MediaPosterResolver.State()
        s.offer("https://poster.invalid/a.jpg","card")
        s.offer("https://poster.invalid/b.jpg","card")
        assertTrue(s.offer("https://poster.invalid/element.jpg","element"))
        assertEquals("https://poster.invalid/element.jpg",s.snapshot().url)
        assertFalse(s.snapshot().ambiguous)
        assertFalse(s.offer("https://poster.invalid/weak.jpg","structured"))
        assertEquals("https://poster.invalid/element.jpg",s.snapshot().url)
    }
    @Test fun repeatAndNullDoNotManufactureConflicts() {
        val s=MediaPosterResolver.State()
        assertFalse(s.offer(null,"element"))
        assertTrue(s.offer("https://poster.invalid/a.jpg","player"))
        assertFalse(s.offer("https://poster.invalid/a.jpg","player"))
        assertFalse(s.offer("https://poster.invalid/b.jpg","structured"))
        assertFalse(s.snapshot().ambiguous)
    }
    @Test fun olderV1CandidatesAreSupportedAtLowestRank() {
        val s=MediaPosterResolver.State()
        assertTrue(s.offer("https://poster.invalid/legacy.jpg",null))
        assertEquals(MediaPosterResolver.Origin.LEGACY,s.snapshot().origin)
        assertTrue(s.offer("https://poster.invalid/declared.jpg","structured"))
        assertEquals("https://poster.invalid/declared.jpg",s.snapshot().url)
    }
    @Test fun unknownWireOriginIsIgnoredAndCannotBeLeaked() {
        val s=MediaPosterResolver.State()
        assertFalse(s.offer("https://private.invalid/secret?token=abc","forged"))
        assertNull(s.snapshot().url)
        assertFalse(s.snapshot().toString().contains("secret"))
        assertNull(MediaPosterResolver.origin("forged"))
    }
}
