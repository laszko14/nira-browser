package com.prirai.android.nira.media.alpha.audio

import com.prirai.android.nira.media.alpha.*
import kotlinx.serialization.Serializable

/** Originals live only in the private, atomic panel snapshot, independently of XML undo. */
@Serializable
internal data class AlphaAudioRepairBackup(val kind:String,val content:String,val longPressContent:String) {
    override fun toString()="AlphaAudioRepairBackup(<redacted>)"
}

/** Trusted, shipped revision, not a heuristic interpreting private code or button titles. */
internal class AlphaAudioRepairSource(private val template:String) {
    init {require(template.length in 100..65536)}
    fun code(kind:String,long:Boolean):String {
        require(kind in setOf("drc","de"))
        return "-- Zavro audio repair v1 $kind ${if(long)"off" else "cycle"}\n"+template+
            "\nreturn zavro_audio_repair('$kind','${if(long)"off" else "cycle"}',zavro_repair_request)\n"
    }
    fun kind(code:String):String?=listOf("drc","de").firstOrNull {code==code(it,false) || code==code(it,true)}
    fun wrap(code:String,request:String):String {
        require(kind(code)!=null && Regex("[a-zA-Z0-9-]{1,96}").matches(request))
        return "local zavro_repair_request='$request'\n$code"
    }
    fun apply(current:AlphaPreferences,expected:AlphaLuaButton,kind:String):AlphaPreferences {
        require(current.slots.count {it?.id==expected.id}==1)
        require(current.slots.single {it?.id==expected.id}==expected)
        require(expected.id !in current.audioRepairs)
        return current.copy(
            slots=current.slots.map {if(it?.id==expected.id)it.copy(content=code(kind,false),longPressContent=code(kind,true)) else it},
            audioRepairs=current.audioRepairs+(expected.id to AlphaAudioRepairBackup(kind,expected.content,expected.longPressContent)),
        )
    }
    fun restore(current:AlphaPreferences,expected:AlphaLuaButton):AlphaPreferences {
        require(current.slots.count {it?.id==expected.id}==1 && current.slots.single {it?.id==expected.id}==expected)
        val backup=current.audioRepairs.getValue(expected.id)
        return current.copy(slots=current.slots.map {
            if(it?.id==expected.id)it.copy(content=backup.content,longPressContent=backup.longPressContent) else it
        },audioRepairs=current.audioRepairs-expected.id)
    }
}
