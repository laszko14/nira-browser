package com.prirai.android.nira.browser.tabs

import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveTabVisibilityTest {
    private fun delta(left: Float, right: Float, group: TabStripBounds? = null,
                      count: Int = 12, back: Boolean = true, forward: Boolean = true) =
        ActiveTabVisibility.scrollDelta(count, 0f, 300f, TabStripBounds(left, right),
            group, back, forward, 12f)

    @Test fun singleTabReturnsToStartAndFittingContentDoesNotMove() {
        assertEquals(0f, delta(10f, 44f, count = 1, back = false, forward = false))
        assertEquals(0f, delta(0f, 34f, back = false, forward = false))
    }
    @Test fun wholeVisibleGroupWinsForEveryMemberEvenWithOtherGroupsOffscreen() {
        for (left in listOf(0f, 50f, 250f))
            assertEquals(0f, delta(left, left + 34f, TabStripBounds(0f, 300f)))
    }
    @Test fun comfortableTabAndTinyEdgeDifferencesDoNotMove() {
        assertEquals(0f, delta(40f, 74f))
        assertEquals(0f, delta(11.5f, 45.5f))
        assertEquals(0f, delta(0f, 34f, back = false))
        assertEquals(0f, delta(266f, 300f, forward = false))
    }
    @Test fun clippedOffscreenAndEdgeTabsMoveTowardsCenter() {
        assertEquals(-143f, delta(-10f, 24f))
        assertEquals(267f, delta(400f, 434f))
        assertEquals(131f, delta(264f, 298f))
    }
    @Test fun oversizedGroupTargetsActualIconAndRespectsMeasuredSize() {
        assertEquals(267f, delta(400f, 434f, TabStripBounds(-100f, 800f)))
        assertEquals(0f, delta(40f, 92f, TabStripBounds(-100f, 800f)))
        assertEquals(276f, delta(400f, 452f, TabStripBounds(-100f, 800f)))
    }
    @Test fun collapsedGroupHeaderUsesSameVisibilityPolicy() {
        assertEquals(300f, delta(420f, 480f))
        assertEquals(0f, delta(100f, 160f))
    }
}
