package com.prirai.android.nira.media

import org.junit.Assert.*
import org.junit.Test

class MediaSessionBindingTest {
    @Test fun bindsByNativeSessionEvenWhenAnotherTabIsSelected() {
        val first = Any()
        val second = Any()
        val tabs = listOf(MediaSessionBinding(first, "first", null, false),
            MediaSessionBinding(second, "second", "profile_work", false))
        assertEquals("first", resolveMediaSession(first, tabs)?.tabId)
        assertNull(resolveMediaSession(first, tabs)?.contextId)
        assertEquals("profile_work", resolveMediaSession(second, tabs)?.contextId)
    }

    @Test fun keepsPrivateContextAndRejectsClosedOrMissingSession() {
        val session = Any()
        val tabs = listOf(MediaSessionBinding(session, "private-tab", "private", true))
        assertTrue(resolveMediaSession(session, tabs)!!.isPrivate)
        assertEquals("private", resolveMediaSession(session, tabs)?.contextId)
        assertNull(resolveMediaSession(session, emptyList()))
        assertNull(resolveMediaSession(Any(), tabs))
        assertNull(resolveMediaSession(null, tabs))
        assertNull(resolveMediaSession(session, tabs + tabs))
    }

    @Test fun comparesIdentityRatherThanEquality() {
        val first = String(charArrayOf('a'))
        val equalButDifferent = String(charArrayOf('a'))
        assertNull(resolveMediaSession(equalButDifferent,
            listOf(MediaSessionBinding(first, "first", null, false))))
    }
}
