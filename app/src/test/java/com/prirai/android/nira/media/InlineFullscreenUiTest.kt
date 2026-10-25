package com.prirai.android.nira.media

import org.junit.Assert.assertEquals
import org.junit.Test

class InlineFullscreenUiTest {
    @Test fun seekClampsAndUnknownDurationCannotSeek() {
        assertEquals(90.0,InlineFullscreenUi.seekTarget(750,120.0)!!,0.001)
        assertEquals(120.0,InlineFullscreenUi.seekTarget(1200,120.0)!!,0.001)
        assertEquals(null,InlineFullscreenUi.seekTarget(500,Double.NaN))
    }
    @Test fun timeAndSpeedMatchFullscreenGestures() {
        assertEquals("1:01:01",InlineFullscreenUi.time(3661.0))
        assertEquals("--:--",InlineFullscreenUi.time(Double.NaN))
        assertEquals(1.5,InlineFullscreenUi.doubleTapSpeed(.1f,2.5)!!,0.001)
        assertEquals(3.0,InlineFullscreenUi.doubleTapSpeed(.5f,2.5)!!,0.001)
        assertEquals(null,InlineFullscreenUi.doubleTapSpeed(.9f,2.0))
        assertEquals(2.0,InlineFullscreenUi.cycleSpeed(3.0),0.001)
    }
}
