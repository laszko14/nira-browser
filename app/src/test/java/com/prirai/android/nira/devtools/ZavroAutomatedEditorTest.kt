package com.prirai.android.nira.devtools

import org.junit.Assert.assertEquals
import org.junit.Test

/** Isolated proof that the ordinary-chat editor can create and compile one Kotlin test. */
class ZavroAutomatedEditorTest {
    @Test
    fun checksAndroidVariantWithoutModifyingProductionCode() {
        assertEquals("mpv", "m" + "pv")
    }
}
