package com.prirai.android.nira.components.toolbar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolbarActionConfigurationTest {
    @Test fun orderIgnoresUnknownsAndDuplicatesWhileMigratingOldSettings() {
        val ordered = ToolbarActionConfiguration.normalizedOrder(
            "forward,settings,forward,unknown,new"
        )
        assertEquals(
            listOf("forward", "settings", "new", "close", "back", "previous", "next"),
            ordered
        )
    }

    @Test fun emptyOrderHasReferenceScreenshotDefaultsAndTwoTabActions() {
        assertEquals(
            listOf("new", "close", "settings", "back", "forward", "previous", "next"),
            ToolbarActionConfiguration.normalizedOrder("")
        )
    }

    @Test fun tabNavigationActionsCanBeReorderedWithoutDuplication() {
        assertEquals(
            listOf("next", "new", "previous", "close", "settings", "back", "forward"),
            ToolbarActionConfiguration.normalizedOrder("next,new,previous,next")
        )
    }

    @Test fun hiddenIncludesBothTabActionsButIgnoresInternalButtons() {
        assertEquals(
            setOf("close", "previous", "next", "forward"),
            ToolbarActionConfiguration.normalizedHidden(
                "close,previous,next,forward,refresh_0,previous"
            )
        )
        assertTrue(ToolbarActionConfiguration.normalizedHidden("").isEmpty())
    }
}
