package com.prirai.android.nira.settings.fragment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineSelectionDraftTest {
    @Test fun cancelAndReopenKeepPreviousRegularPreference() {
        val draft = SearchEngineSelectionDraft(false, 1, -1, false)
        assertFalse(draft.select(4, 8))
        assertEquals(4, draft.checkedIndex(8))
        // No persistent write API on a draft; discard it on Cancel/Back.
        val reopened = SearchEngineSelectionDraft(false, 1, -1, false)
        assertEquals(1, reopened.regularChoice)
        assertEquals(1, reopened.checkedIndex(8))
    }

    @Test fun okCanCommitChosenEngineWithoutOverwritingPrivateSelection() {
        val draft = SearchEngineSelectionDraft(false, 1, 5, true)
        assertFalse(draft.select(2, 8))
        assertEquals(2, draft.regularChoice)
        assertEquals(5, draft.privateChoice)
        assertFalse(draft.useCustom)
    }

    @Test fun selectingCustomOpensEditorWithoutChangingCurrentSelection() {
        val existing = SearchEngineSelectionDraft(false, 3, -1, true)
        assertEquals(8, existing.checkedIndex(8))
        assertTrue(existing.select(8, 8))
        assertTrue(existing.useCustom)
        assertEquals(3, existing.regularChoice)

        val regular = SearchEngineSelectionDraft(false, 1, -1, false)
        assertFalse(regular.select(5, 8))
        assertTrue(regular.select(8, 8))
        assertEquals(5, regular.regularChoice)
    }

    @Test fun privateInheritAndExplicitEngineAreIndependentOfRegularAndCustom() {
        val draft = SearchEngineSelectionDraft(true, 2, -1, true)
        assertEquals(0, draft.checkedIndex(8))
        assertFalse(draft.select(4, 8))
        assertEquals(3, draft.privateChoice)
        assertEquals(4, draft.checkedIndex(8))
        assertFalse(draft.select(0, 8))
        assertEquals(-1, draft.privateChoice)
        assertEquals(2, draft.regularChoice)
        assertTrue(draft.useCustom)
        val reopened = SearchEngineSelectionDraft(true, 2, -1, true)
        assertEquals(0, reopened.checkedIndex(8))
    }

    @Test fun allRowsIncludingLastEngineWorkInPrivatePicker() {
        val draft = SearchEngineSelectionDraft(true, 2, -1, false)
        assertFalse(draft.select(8, 8))
        assertEquals(7, draft.privateChoice)
        assertEquals(8, draft.checkedIndex(8))
    }
}
