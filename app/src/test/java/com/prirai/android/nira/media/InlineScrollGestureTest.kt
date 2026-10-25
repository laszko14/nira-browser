package com.prirai.android.nira.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineScrollGestureTest {
    @Test fun tapAndJitterStayWithButtons() {
        assertFalse(InlineScrollGesture.starts(0f,0f,8))
        assertFalse(InlineScrollGesture.starts(2f,8f,8))
    }
    @Test fun verticalScrollWorksInBothDirections() {
        assertTrue(InlineScrollGesture.starts(3f,9f,8))
        assertTrue(InlineScrollGesture.starts(-3f,-9f,8))
    }
    @Test fun horizontalAndDiagonalMotionDoNotBecomePageScroll() {
        assertFalse(InlineScrollGesture.starts(20f,9f,8))
        assertFalse(InlineScrollGesture.starts(9f,9f,8))
    }
}
