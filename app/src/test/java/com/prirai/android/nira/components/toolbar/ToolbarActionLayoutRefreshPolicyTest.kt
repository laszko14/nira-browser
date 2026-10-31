package com.prirai.android.nira.components.toolbar

import android.content.res.Configuration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolbarActionLayoutRefreshPolicyTest {
    private val portrait = Configuration.ORIENTATION_PORTRAIT
    private val landscape = Configuration.ORIENTATION_LANDSCAPE

    @Test fun sameWidthOrientationChangeRefreshesActionsInBothDirections() {
        assertTrue(ToolbarActionLayoutRefreshPolicy.needsRefresh(
            width = 410, previousLayoutWidth = 410, lastWidth = 410,
            orientation = landscape, lastOrientation = portrait
        ))
        assertTrue(ToolbarActionLayoutRefreshPolicy.needsRefresh(
            width = 410, previousLayoutWidth = 410, lastWidth = 410,
            orientation = portrait, lastOrientation = landscape
        ))
    }

    @Test fun widthChangeStillRefreshesWhileOrientationRemainsConstant() {
        assertTrue(ToolbarActionLayoutRefreshPolicy.needsRefresh(
            width = 640, previousLayoutWidth = 410, lastWidth = 410,
            orientation = landscape, lastOrientation = landscape
        ))
    }

    @Test fun firstLayoutRefreshesAndUnchangedGeometryDoesNot() {
        assertTrue(ToolbarActionLayoutRefreshPolicy.needsRefresh(
            width = 410, previousLayoutWidth = 410, lastWidth = -1,
            orientation = portrait, lastOrientation = Configuration.ORIENTATION_UNDEFINED
        ))
        assertFalse(ToolbarActionLayoutRefreshPolicy.needsRefresh(
            width = 410, previousLayoutWidth = 410, lastWidth = 410,
            orientation = portrait, lastOrientation = portrait
        ))
    }

    @Test fun changedOldWidthTriggersLayoutReconciliation() {
        assertTrue(ToolbarActionLayoutRefreshPolicy.needsRefresh(
            width = 410, previousLayoutWidth = 430, lastWidth = 410,
            orientation = portrait, lastOrientation = portrait
        ))
    }
}
