package com.prirai.android.nira.media.alpha

/** Async failures have one completion notification; transport/success are silent. */
internal object AlphaFeedbackPolicy {
    fun immediate(status:AlphaLuaStatus)=status in setOf(AlphaLuaStatus.DISABLED,AlphaLuaStatus.UNAVAILABLE,
        AlphaLuaStatus.EMPTY,AlphaLuaStatus.MISSING_BUTTON,AlphaLuaStatus.SEND_FAILED,AlphaLuaStatus.BUSY,AlphaLuaStatus.NATIVE_FAILED)
    fun completion(status:AlphaLuaStatus)=status in setOf(AlphaLuaStatus.SYNTAX_ERROR,AlphaLuaStatus.RUNTIME_ERROR,AlphaLuaStatus.TIMEOUT)
    fun clientStatus(loaded:Boolean,syntax:Boolean,top:Boolean,callback:Boolean,conflict:Boolean,logErrors:Int):String=when {
        conflict->"CLIENT_CONFLICT";syntax->"SYNTAX_ERROR";top->"TOP_LEVEL_ERROR";callback->"CALLBACK_ERROR"
        loaded->"LOADED";logErrors>0->"LOAD_ERROR";else->"LOAD_UNCONFIRMED"
    }
    fun warning(level:Int)=level==30
    fun error(level:Int)=level in 0..20
    fun ambientFile(name:String)=Regex("rex_ambient_plus_v4_[0-9]+_[0-9]+\\.glsl").matches(name)
}


/** libass uses a 720-high reference canvas. Keep native feedback physical size stable. */
internal object OsdGeometry {
    fun options(height:Int,density:Double,hudBottom:Double):Map<String,String> {
        if(height<=0 || !density.isFinite() || density<=0 || !hudBottom.isFinite())return emptyMap()
        val scale=720.0/height
        val margin=if(hudBottom>0)hudBottom+16*density else 10*density
        return mapOf("osd-margin-y" to (margin*scale).toInt().coerceAtLeast(0).toString(),
            "osd-margin-x" to (10*density*scale).toInt().coerceAtLeast(0).toString(),
            "osd-font-size" to (14*density*scale).coerceIn(1.0,9000.0).toString())
    }
}
