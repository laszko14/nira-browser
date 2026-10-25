package com.prirai.android.nira.media

/** Pure display/gesture arithmetic; owns no player or playback lifecycle. */
internal object InlineFullscreenUi {
    fun seekTarget(progress:Int,duration:Double):Double? =
        if (duration.isFinite() && duration>0) progress.coerceIn(0,1000)/1000.0*duration else null
    fun time(seconds:Double):String {
        if (!seconds.isFinite() || seconds<0) return "--:--"
        val s=seconds.toLong()
        return if (s>=3600) "%d:%02d:%02d".format(s/3600,s/60%60,s%60)
            else "%d:%02d".format(s/60,s%60)
    }
    fun cycleSpeed(speed:Double):Double = when {
        speed<2.0 -> 2.0
        speed<2.5 -> 2.5
        speed<3.0 -> 3.0
        else -> 2.0
    }
    fun doubleTapSpeed(x:Float,speed:Double):Double? = when {
        x<.34f -> 1.5
        x<.67f -> cycleSpeed(speed)
        else -> null
    }
}
