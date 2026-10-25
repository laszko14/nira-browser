package com.prirai.android.nira.media

import org.junit.Assert.assertEquals
import org.junit.Test

class InlineMpvGeometryTest {
    @Test fun visualViewportOverridesDensityAndKeepsAspect() {
        val r=InlineMpvGeometry.pixels(10.0,20.0,900.0,506.0,980.0,1080,3.0)
        assertEquals(11,r[0])
        assertEquals(22,r[1])
        assertEquals(992,r[2])
        assertEquals(558,r[3])
    }
    @Test fun scrollChangesPositionWithoutResizingVideo() {
        val a=InlineMpvGeometry.pixels(0.0,-100.0,400.0,225.0,400.0,1080,3.0)
        assertEquals(-270,a[1])
        assertEquals(1080,a[2])
        assertEquals(608,a[3])
    }
}
