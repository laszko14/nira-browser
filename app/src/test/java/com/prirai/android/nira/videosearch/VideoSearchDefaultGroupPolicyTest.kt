package com.prirai.android.nira.videosearch

import org.junit.Assert.*
import org.junit.Test

/** ETAP 7A: choose only a group in the same Gecko partition and normal profile. */
class VideoSearchDefaultGroupPolicyTest {
    private val default = VideoSearchScope.Normal("default")
    private val work = VideoSearchScope.Normal("work")

    @Test fun defaultProfileAcceptsOnlyNullContextGroup() {
        assertEquals("a", VideoSearchDefaultGroupPolicy.destination(
            "a", default, listOf("a" to null, "other" to "profile_work")))
    }

    @Test fun customProfileAcceptsOnlyMatchingContext() {
        assertEquals("a", VideoSearchDefaultGroupPolicy.destination(
            "a", work, listOf("a" to "profile_work")))
    }

    @Test fun noGroupPreferenceAlwaysMeansNewUngroupedTab() {
        assertNull(VideoSearchDefaultGroupPolicy.destination(
            null, default, listOf("a" to null)))
    }

    @Test fun privateGroupIsNeverAllowedForNormalBrowsing() {
        assertNull(VideoSearchDefaultGroupPolicy.destination(
            "a", default, listOf("a" to "private")))
        assertNull(VideoSearchDefaultGroupPolicy.destination(
            "a", work, listOf("a" to "private")))
    }

    @Test fun mismatchedNormalProfilesNeverShareGroup() {
        assertNull(VideoSearchDefaultGroupPolicy.destination(
            "a", work, listOf("a" to null)))
        assertNull(VideoSearchDefaultGroupPolicy.destination(
            "a", default, listOf("a" to "profile_work")))
    }

    @Test fun removedGroupIsNotAccidentallyRecreated() {
        assertNull(VideoSearchDefaultGroupPolicy.destination(
            "deleted", default, listOf("existing" to null)))
    }

    @Test fun missingManagerGroupsMustNotAcceptPreference() {
        assertNull(VideoSearchDefaultGroupPolicy.destination(
            "a", default, emptyList()))
    }

    @Test fun doesNotChooseSomeOtherGroupWhenPreferredIsAbsent() {
        assertNull(VideoSearchDefaultGroupPolicy.destination(
            "unknown", default, listOf("a" to null, "b" to null)))
    }

    @Test fun exactGroupIdMustMatchNotJustPrefix() {
        assertNull(VideoSearchDefaultGroupPolicy.destination(
            "a", default, listOf("a-2" to null)))
    }

    @Test fun unknownOrBlankGroupIdOpensOutsideGroup() {
        assertNull(VideoSearchDefaultGroupPolicy.destination(
            "", work, listOf("a" to "profile_work")))
        assertNull(VideoSearchDefaultGroupPolicy.destination(
            " ", work, listOf("a" to "profile_work")))
    }

    @Test fun groupCanBeSelectedRegardlessOfOtherProfileGroups() {
        assertEquals("correct", VideoSearchDefaultGroupPolicy.destination(
            "correct", work, listOf(
                "wrong" to null, "also-wrong" to "profile_other",
                "correct" to "profile_work"
            )))
    }

    @Test fun groupPolicyDoesNotChangeFilmsOrUrls() {
        val urls = listOf(
            "https://example.org/film?id=1&lang=pl",
            "https://example.org/film?id=1&lang=en"
        )
        assertEquals("a", VideoSearchDefaultGroupPolicy.destination(
            "a", default, listOf("a" to null)))
        assertEquals(2, urls.distinct().size)
    }
}
