package com.prirai.android.nira.media

import kotlin.math.roundToInt

/** CSS visual viewport to GeckoView physical pixels; never substitute Android dp. */
internal object InlineMpvGeometry {
    fun pixels(x:Double,y:Double,w:Double,h:Double,viewportWidth:Double,
               engineWidth:Int,dpr:Double):IntArray {
        val scale=if (viewportWidth>0.0) engineWidth/viewportWidth else dpr
        return intArrayOf((x*scale).roundToInt(),(y*scale).roundToInt(),
            (w*scale).roundToInt(),(h*scale).roundToInt())
    }
}
