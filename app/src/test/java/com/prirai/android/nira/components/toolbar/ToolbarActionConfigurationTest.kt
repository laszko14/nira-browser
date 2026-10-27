package com.prirai.android.nira.components.toolbar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolbarActionConfigurationTest {
    @Test fun orderIgnoresUnknownsAndDuplicates() {
        val ordered = ToolbarActionConfiguration.normalizedOrder(
            "forward,settings,forward,unknown,new"
        )
        assertEquals(listOf("forward", "settings", "new", "close", "back"), ordered)
    }

    @Test fun emptyOrderHasReferenceScreenshotDefaults() {
        assertEquals(listOf("new", "close", "settings", "back", "forward"),
            ToolbarActionConfiguration.normalizedOrder(""))
    }

    @Test fun hiddenNeverIncludesNonEditableButtons() {
        assertEquals(setOf("close", "forward"),
            ToolbarActionConfiguration.normalizedHidden("close,previous,forward,refresh_0"))
        assertTrue(ToolbarActionConfiguration.normalizedHidden("").isEmpty())
    }
}
