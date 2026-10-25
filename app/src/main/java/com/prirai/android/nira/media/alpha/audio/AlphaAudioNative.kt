package com.prirai.android.nira.media.alpha.audio

import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode
import kotlinx.serialization.json.*

/** Checked JNI command result + independent native AF readback. No lifecycle or observer. */
internal object AlphaAudioNative:AlphaAudioBackend {
    override fun read():List<AlphaAfFilter>?=runCatching {
        val nodes=MPVLib.getPropertyNode("af")?.asArray()?:return null
        if(nodes.size>64)return null
        nodes.map {node->
            val raw=json(node,0) as? JsonObject?:return null
            if((raw["name"] as? JsonPrimitive)?.isString!=true)return null
            AlphaAfFilter(raw)
        }
    }.getOrNull()
    // Use JSON primitives for correct escaping of opaque foreign parameters.
    // MPVNode.toJson() is unsuitable for arbitrary strings with backslashes.
    private fun json(node:MPVNode,depth:Int):JsonElement {
        require(depth<16)
        return when(node) {
            is MPVNode.MapNode-> {require(node.value.size<=128);JsonObject(node.value.mapValues {json(it.value,depth+1)})}
            is MPVNode.ArrayNode-> {require(node.value.size<=128);JsonArray(node.value.map {json(it,depth+1)})}
            is MPVNode.StringNode->{require(node.value.length<=262144);JsonPrimitive(node.value)}
            is MPVNode.BooleanNode->JsonPrimitive(node.value)
            is MPVNode.IntNode->JsonPrimitive(node.value)
            is MPVNode.DoubleNode->{require(node.value.isFinite());JsonPrimitive(node.value)}
            else->JsonNull
        }
    }
    // AAR commandNode returns null for negative mpv status, MPVNode.None for success.
    override fun replace(spec:String)=MPVLib.commandNode("change-list","af","append",spec)!=null
    override fun remove()=MPVLib.commandNode("change-list","af","remove","@rex_visual_eq")!=null
}
