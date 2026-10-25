package com.prirai.android.nira.media

import com.prirai.android.nira.media.alpha.ui.*
import org.junit.Assert.*
import org.junit.Test

class AlphaComposeUiTest {
    @Test fun originalHudCalculationsNeverInventDuration() {
        assertEquals(HudRemainingTimes(80,40),hudRemainingTimes(100f,20f,2f))
        assertEquals(HudRemainingTimes(null,null),hudRemainingTimes(Float.NaN,20f,2f))
        assertNull(miniProgressFraction(10f,Float.NaN))
        assertEquals(1f,miniProgressFraction(120f,100f)!!,0f)
    }
    @Test fun unknownPositionNeverReachesSliderAsNaN() {
        assertEquals(0f,finiteAlphaSeekPosition(Float.NaN,Float.NaN),0f)
        assertEquals(100f,finiteAlphaSeekPosition(120f,100f),0f)
    }
    @Test fun originalLayoutPreservesOrderAndDeduplicates() {
        val layout=parseHudLayout("clock,speed,clock|remaining,unknown")!!
        assertEquals(listOf(HudElement.CLOCK,HudElement.SPEED),layout.first)
        assertEquals("clock,speed|remaining",layout.encode())
        assertEquals(listOf(HudElement.CLOCK),layout.without(HudElement.SPEED).first)
    }
}
