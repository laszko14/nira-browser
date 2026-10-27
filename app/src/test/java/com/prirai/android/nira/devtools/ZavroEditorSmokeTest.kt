package com.prirai.android.nira.devtools

import org.junit.Assert.assertEquals
import org.junit.Test

/** Sandbox-only smoke test for ChatGPT-controlled, isolated Kotlin edits. */
class ZavroEditorSmokeTest {
    @Test
    fun cleanAndMpvRemainDistinctVariantLabels() {
        val variants = listOf("clean", "mpv")
        assertEquals("clean", variants.first())
        assertEquals("mpv", variants.last())
    }
}
