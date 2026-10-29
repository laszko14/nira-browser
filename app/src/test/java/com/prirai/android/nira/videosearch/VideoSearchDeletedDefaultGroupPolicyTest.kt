package com.prirai.android.nira.videosearch

import org.junit.Assert.*
import org.junit.Test

/** ETAP 7C: a default group is cleared only after an initialized
 * group manager confirms its exact ID and Gecko partition are missing. */
class VideoSearchDeletedDefaultGroupPolicyTest {
    private val normal = VideoSearchScope.Normal("default")
    private val work = VideoSearchScope.Normal("work")

    @Test fun missingGroupAfterLoadMustClear() {
        assertTrue(VideoSearchDefaultGroupPolicy.shouldClearDeletedDefault(
            "deleted", normal, true, listOf("other" to null)
        ))
    }

    @Test fun emptyLoadedGroupsMeanPreviouslySelectedGroupWasRemoved() {
        assertTrue(VideoSearchDefaultGroupPolicy.shouldClearDeletedDefault(
            "gone", work, true, emptyList()
        ))
    }

    @Test fun emptyGroupsDuringStartupAreNotProofOfDeletion() {
        assertFalse(VideoSearchDefaultGroupPolicy.shouldClearDeletedDefault(
            "maybe", normal, false, emptyList()
        ))
    }

    @Test fun startupFailureCannotErasePreferenceEvenWithOtherGroups() {
        assertFalse(VideoSearchDefaultGroupPolicy.shouldClearDeletedDefault(
            "maybe", work, false, listOf("unrelated" to "profile_work")
        ))
    }

    @Test fun existingMatchingGroupIsAlwaysPreserved() {
        assertFalse(VideoSearchDefaultGroupPolicy.shouldClearDeletedDefault(
            "chosen", normal, true,
            listOf("other" to "profile_work", "chosen" to null)
        ))
    }

    @Test fun existingCustomProfileGroupIsPreserved() {
        assertFalse(VideoSearchDefaultGroupPolicy.shouldClearDeletedDefault(
            "chosen", work, true, listOf("chosen" to "profile_work")
        ))
    }

    @Test fun sameDisplayedNameDoesNotSubstituteDeletedId() {
        assertTrue(VideoSearchDefaultGroupPolicy.shouldClearDeletedDefault(
            "deleted-id", normal, true, listOf("same-name-new-id" to null)
        ))
    }

    @Test fun onlyOtherProfileGroupWithSameIdIsNotAValidDestination() {
        assertTrue(VideoSearchDefaultGroupPolicy.shouldClearDeletedDefault(
            "moved", work, true, listOf("moved" to null)
        ))
        assertTrue(VideoSearchDefaultGroupPolicy.shouldClearDeletedDefault(
            "moved", normal, true, listOf("moved" to "profile_work")
        ))
    }

    @Test fun privateGroupNeverBecomesFallback() {
        assertTrue(VideoSearchDefaultGroupPolicy.shouldClearDeletedDefault(
            "private", normal, true, listOf("private" to "private")
        ))
    }

    @Test fun explicitNoGroupIsNotAnOrphan() {
        assertFalse(VideoSearchDefaultGroupPolicy.shouldClearDeletedDefault(
            null, normal, true, emptyList()
        ))
        assertNull(VideoSearchDefaultGroupPolicy.destination(
            null, normal, emptyList()
        ))
    }

    @Test fun blankPreferenceDoesNotCauseUnnecessaryCleanup() {
        assertFalse(VideoSearchDefaultGroupPolicy.shouldClearDeletedDefault(
            "", work, true, emptyList()
        ))
        assertFalse(VideoSearchDefaultGroupPolicy.shouldClearDeletedDefault(
            "   ", work, true, emptyList()
        ))
    }

    @Test fun deletedGroupNeverCausesAutomaticGroupRecreation() {
        val groups = listOf("survivor" to "profile_work")
        assertNull(VideoSearchDefaultGroupPolicy.destination(
            "deleted", work, groups
        ))
        assertTrue(VideoSearchDefaultGroupPolicy.shouldClearDeletedDefault(
            "deleted", work, true, groups
        ))
        assertEquals(1, groups.size)
    }

    @Test fun allProfilesKeepIndependentGroupChoices() {
        val available = listOf("default-grp" to null, "work-grp" to "profile_work")
        assertFalse(VideoSearchDefaultGroupPolicy.shouldClearDeletedDefault(
            "default-grp", normal, true, available
        ))
        assertFalse(VideoSearchDefaultGroupPolicy.shouldClearDeletedDefault(
            "work-grp", work, true, available
        ))
    }
}
