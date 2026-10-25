package com.prirai.android.nira.media.alpha

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Isolated, inert snapshot, not Android default SharedPreferences.
 * Typed values retain original Alpha key names/types for a future explicit mapper.
 * The snapshot has no I/O or playback side effects; the v1151 panel store owns private persistence.
 */
@Serializable
internal sealed interface AlphaPreferenceValue {
    @Serializable data class Bool(val value:Boolean):AlphaPreferenceValue
    @Serializable data class IntValue(val value:Int):AlphaPreferenceValue
    @Serializable data class FloatValue(val value:Float):AlphaPreferenceValue
    @Serializable data class Text(val value:String):AlphaPreferenceValue
}
@Serializable
internal data class AlphaPreferences(
    val schemaVersion:Int=1,
    val hud:Map<String,AlphaPreferenceValue> = emptyMap(),
    val luaAppearance:Map<String,AlphaPreferenceValue> = emptyMap(),
    val buttonLayout:Map<String,AlphaPreferenceValue> = emptyMap(),
    val speed:Map<String,AlphaPreferenceValue> = emptyMap(),
    val gestures:Map<String,AlphaPreferenceValue> = emptyMap(),
    val slots:List<AlphaLuaButton?> = List(43) { null },
) {
    // Not deserializable as true: imported data cannot enable code execution.
    val luaExecutionEnabled:Boolean get()=false
    init { require(schemaVersion==1 && slots.size==43) }
}

/**
 * Alpha v54 CustomButton wire fields, including nullable per-button overrides.
 * Source: REX-Player c6107ce, CustomButtonScreen.kt (Apache-2.0).
 * Unknown fields are rejected by the strict decoder rather than silently lost.
 */
@Serializable
internal data class AlphaLuaButton(
    val id:String,
    val title:String,
    val content:String,
    val longPressContent:String="",
    val onStartup:String="",
    val enabled:Boolean=true,
    val alwaysVisible:Boolean?=null,
    val backgroundColor:Int?=null,
    val textColor:Int?=null,
    val activeBackgroundColor:Int?=null,
    val activeTextColor:Int?=null,
    val icon:String?=null,
) {
    // Prevent accidental source disclosure through generic diagnostics.
    override fun toString()="AlphaLuaButton(<redacted>)"
}

/** Preserves all holes and IDs; migrates only historical 30..34 to FREE visible slots. */
internal object AlphaSlots {
    const val MAIN=30
    const val HISTORICAL=5
    const val RIGHT=8
    const val RIGHT_START=MAIN+HISTORICAL
    const val COUNT=RIGHT_START+RIGHT

    @Serializable private data class Envelope(val slots:List<AlphaLuaButton?> = List(COUNT){null})
    fun normalize(source:List<AlphaLuaButton?>):List<AlphaLuaButton?> {
        require(source.size<=COUNT) { "Unsupported slot count" }
        val result=MutableList(COUNT) { source.getOrNull(it) }
        for (old in MAIN until RIGHT_START) {
            val button=result[old] ?: continue
            val free=(RIGHT_START until COUNT).firstOrNull { result[it]==null }
                ?: (0 until MAIN).firstOrNull { result[it]==null }
            if (free!=null) { result[free]=button; result[old]=null }
        }
        return result.toList()
    }
    fun decode(serialized:String):List<AlphaLuaButton?> {
        // Synthetic button JSON only. No XML, script dispatch or settings writes.
        require(serialized.length<=1_048_576) { "Slot snapshot too large" }
        val slots=if (serialized.trimStart().startsWith("["))
            Json.decodeFromString<List<AlphaLuaButton?>>(serialized)
        else Json.decodeFromString<Envelope>(serialized).slots
        return normalize(slots)
    }
    fun encode(slots:List<AlphaLuaButton?>):String =
        Json.encodeToString(Envelope(normalize(slots)))
}
