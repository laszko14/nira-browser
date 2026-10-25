package com.prirai.android.nira.media.alpha.scripts

import `is`.xyz.mpv.MPVNode

internal data class FeedbackCount(val sent:Int=0,val accepted:Int=0,val errors:Int=0,
    val openFailed:Int=0,val opened:Int=0,val removed:Int=0,val shaderFailed:Int=0,val viewFailed:Int=0,val viewRestored:Boolean=false) {
    companion object {
        fun parse(node:MPVNode?):FeedbackCount {
            fun n(k:String)=((node?.get(k) as? MPVNode.IntNode)?.value?:0).coerceIn(0,1000000).toInt()
            return FeedbackCount(n("sent"),n("accepted"),n("errors"),n("open_failed"),n("opened"),n("removed"),n("shader_failed"),n("view_failed"),(node?.get("view_restored") as? MPVNode.BooleanNode)?.value==true)
        }
    }
}
internal data class AmbientFeedback(val renderer:String="unknown",val file:String="UNCONFIRMED",
    val list:Boolean=false,val compilation:String="UNCONFIRMED",val cleanup:String="UNCONFIRMED",
    val condition:String="UNCONFIRMED",val stage:String="UNCONFIRMED")
internal object AmbientReadback {
    fun renderer(value:String?)=when(value){"gpu"->"gpu";"gpu-next"->"gpu-next";null,""->"unknown";else->"other"}
    fun condition(renderer:String,width:Double?,height:Double?,videoAspect:Double?):String=when {
        renderer !in setOf("gpu","unknown")->"UNSUPPORTED_RENDERER"
        width==null || height==null || videoAspect==null || !width.isFinite() || !height.isFinite() ||
            !videoAspect.isFinite() || width<=0 || height<=0 || videoAspect<=0->"WAIT_VIDEO"
        kotlin.math.abs(width/height/videoAspect-1.0)<0.001->"NO_BARS"
        else->"BARS_PRESENT"
    }
    /** A recorded native render pass is stronger evidence than merely accepting a GLSL list. */
    fun executedPass(node:MPVNode?,depth:Int=0):Boolean {
        if(depth>6)return false
        return when(node) {
            is MPVNode.MapNode->node.value.entries.take(256).any {(key,v)->
                key=="desc" && (v as? MPVNode.StringNode)?.value?.take(1024)?.contains("REX Ambient+")==true || executedPass(v,depth+1)}
            is MPVNode.ArrayNode->node.value.take(256).any {executedPass(it,depth+1)}
            else->false
        }
    }
}
