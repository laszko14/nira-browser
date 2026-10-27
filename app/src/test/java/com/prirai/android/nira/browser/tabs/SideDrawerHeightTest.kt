package com.prirai.android.nira.browser.tabs

import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests the shared numeric contract without launching a browser or producing an APK.
 * Gesture, TalkBack and real-device layout checks remain for the authorized device stage.
 */
class SideDrawerHeightTest {
    @Test fun validatesOnlyEvenHeightsWithinInclusiveRange() {
        assertNull(SideDrawerHeight.validated(null))
        assertNull(SideDrawerHeight.validated(-1))
        assertNull(SideDrawerHeight.validated(23))
        assertNull(SideDrawerHeight.validated(25))
        assertNull(SideDrawerHeight.validated(113))
        assertNull(SideDrawerHeight.validated(114))
        for (value in 24..112 step 2) {
            assertEquals(value, SideDrawerHeight.validated(value))
        }
    }

    @Test fun snapsAndClampsEverySliderBoundary() {
        assertEquals(24, SideDrawerHeight.snapped(-99f))
        assertEquals(24, SideDrawerHeight.snapped(24f))
        assertEquals(36, SideDrawerHeight.snapped(36f))
        assertEquals(48, SideDrawerHeight.snapped(48f))
        assertEquals(64, SideDrawerHeight.snapped(64f))
        assertEquals(112, SideDrawerHeight.snapped(112f))
        assertEquals(112, SideDrawerHeight.snapped(999f))
        for (value in 24..112) {
            val result = SideDrawerHeight.snapped(value.toFloat())
            assertTrue(result in 24..112)
            assertEquals(0, (result - 24) % 2)
        }
    }

    @Test fun orientationHasDistinctDefaultButOneExplicitHeight() {
        assertEquals(48, SideDrawerHeight.base(landscape = false))
        assertEquals(36, SideDrawerHeight.base(landscape = true))
        for (height in listOf(24, 36, 48, 64, 112)) {
            assertEquals(height.dp, SideDrawerHeight.geometry(height, false, 1f).height)
            assertEquals(height.dp, SideDrawerHeight.geometry(height, true, 1f).height)
        }
    }

    @Test fun ultraCompactActuallyShrinksAllDecorations() {
        for (landscape in listOf(false, true)) {
            val compact = SideDrawerHeight.geometry(24, landscape, 1f)
            val default = SideDrawerHeight.geometry(SideDrawerHeight.base(landscape), landscape, 1f)
            assertEquals(24.dp, compact.height)
            assertFalse(compact.showSubtitle)
            assertTrue(compact.imageFrame < default.imageFrame)
            assertTrue(compact.favicon < default.favicon)
            assertTrue(compact.folderFrame < default.folderFrame)
            assertTrue(compact.action < default.action)
            assertTrue(compact.imageFrame <= compact.height)
            assertTrue(compact.action <= compact.height)
        }
    }

    @Test fun entireHeightRangeAndFontScaleKeepVisualActionsInsideRows() {
        for (landscape in listOf(false, true)) {
            for (fontScale in listOf(1f, 1.3f, 2f)) {
                for (height in 24..112 step 2) {
                    val row = SideDrawerHeight.geometry(height, landscape, fontScale)
                    assertEquals(height.dp, row.height)
                    for (size in listOf(
                        row.radius, row.accent, row.imageFrame, row.favicon,
                        row.folderFrame, row.folder, row.chevron,
                        row.action, row.actionIcon, row.leadingGap, row.textGap
                    )) {
                        assertTrue("height=$height landscape=$landscape font=$fontScale", size > 0.dp)
                        assertTrue("height=$height landscape=$landscape font=$fontScale", size < row.height)
                    }
                    assertTrue(row.favicon <= row.imageFrame)
                    assertTrue(row.folder <= row.folderFrame)
                    assertTrue(row.actionIcon <= row.action)
                    if (height == 24) assertFalse(row.showSubtitle)
                }
            }
        }
    }

    @Test fun narrowestDrawerKeepsTitleBudgetAtMaximumHeight() {
        // Existing dialog minimum 220dp, 20dp edge grip and LazyColumn
        // content padding 11dp start + 8dp end.
        val usableWidth = 220f - 20f - 11f - 8f
        for (landscape in listOf(false, true)) {
            for (height in 24..112 step 2) {
                val row = SideDrawerHeight.geometry(height, landscape, 1f)
                val tabBudget = usableWidth - row.accent.value -
                    (row.leadingGap.value + 2f) - row.imageFrame.value -
                    row.textGap.value - row.action.value - 2f
                val groupBudget = usableWidth - row.leadingGap.value * 2f -
                    row.chevron.value - row.folderFrame.value -
                    row.textGap.value - row.action.value - 1f
                assertTrue("tab title space at H=$height landscape=$landscape: $tabBudget",
                    tabBudget >= 60f)
                assertTrue("group title space at H=$height landscape=$landscape: $groupBudget",
                    groupBudget >= 60f)
            }
        }
    }

    @Test fun subtitlesFitOnlyWhenSpaceAndFontScaleAllow() {
        assertTrue(SideDrawerHeight.geometry(48, false, 1f).showSubtitle)
        assertTrue(SideDrawerHeight.geometry(36, true, 1f).showSubtitle)
        assertFalse(SideDrawerHeight.geometry(24, false, 1f).showSubtitle)
        assertFalse(SideDrawerHeight.geometry(36, true, 2f).showSubtitle)
        assertTrue(SideDrawerHeight.geometry(112, true, 2f).showSubtitle)
    }
}
