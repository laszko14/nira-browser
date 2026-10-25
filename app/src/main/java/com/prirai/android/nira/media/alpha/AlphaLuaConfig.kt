package com.prirai.android.nira.media.alpha

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

internal interface AlphaLuaStorage {
    fun read():String?
    fun backup():String?=null
    fun importBackup():String?=null
    fun writeImport(value:String,before:String):Boolean=false
    fun restoreImport(value:String):Boolean=false
    fun write(value:String):Boolean
}

/** Only private UI data. Read/migrate never writes, runs code or changes MPV. */
internal class AlphaLuaConfig(private val storage:AlphaLuaStorage) {
    private val mutableError=MutableStateFlow<String?>(null)
    val error=mutableError.asStateFlow()
    private val mutableState=MutableStateFlow(load())
    val state=mutableState.asStateFlow()
    private val mutableImportAvailable=MutableStateFlow(
        runCatching { storage.importBackup()?.let(::decode)!=null }.getOrDefault(false))
    val importBackupAvailable=mutableImportAvailable.asStateFlow()

    private fun load():AlphaPreferences {
        return try {
            storage.read()?.let(::decode) ?: defaults()
        } catch (_:Exception) {
            try {
                val restored=storage.backup()?.let(::decode)
                mutableError.value=if(restored!=null) "recovered_previous" else "corrupt_snapshot"
                restored?:defaults()
            } catch (_:Exception) { mutableError.value="corrupt_snapshot";defaults() }
        }
    }
    @Synchronized fun update(change:(AlphaPreferences)->AlphaPreferences):Boolean {
        return try {
            val changed=change(mutableState.value)
            val next=changed.copy(slots=AlphaSlots.normalize(changed.slots))
            val text=Json.encodeToString(next)
            require(text.length<=1_048_576)
            if (!storage.write(text)) { mutableError.value="save_failed"; return false }
            mutableState.value=next
            mutableError.value=null
            true
        } catch (_:Exception) { mutableError.value="invalid_snapshot";false }
    }
    @Synchronized fun applyImport(plan:AlphaImportPlan,scope:AlphaImportScope):Boolean {
        return try {
            val next=decode(Json.encodeToString(plan.applyTo(mutableState.value,scope)))
            if(next==mutableState.value) {
                mutableError.value=null
                return true // Keep the original undo point on repeat import.
            }
            val before=Json.encodeToString(mutableState.value)
            if(!storage.writeImport(Json.encodeToString(next),before)) {
                mutableError.value="save_failed";return false
            }
            mutableState.value=next
            mutableImportAvailable.value=true
            mutableError.value=null
            true
        } catch (_:Exception) { mutableError.value="invalid_snapshot";false }
    }
    @Synchronized fun restoreBeforeImport():Boolean {
        return try {
            val previous=storage.importBackup()?.let(::decode)?:return false
            if(!storage.restoreImport(Json.encodeToString(previous))) {
                mutableError.value="save_failed";return false
            }
            mutableState.value=previous
            mutableImportAvailable.value=false
            mutableError.value=null
            true
        } catch (_:Exception) { mutableError.value="invalid_snapshot";false }
    }
    fun reset()=update { defaults() }

    companion object {
        fun decode(raw:String):AlphaPreferences {
            require(raw.length<=1_048_576)
            val element=Json.parseToJsonElement(raw)
            if (element is JsonArray) return AlphaPreferences(slots=AlphaSlots.decode(raw))
            val obj=element.jsonObject
            val slots=obj["slots"]?:JsonArray(emptyList())
            val normalized=AlphaSlots.decode(JsonObject(mapOf("slots" to slots)).toString())
            return Json.decodeFromJsonElement<AlphaPreferences>(
                JsonObject(obj+("slots" to Json.encodeToJsonElement(normalized))))
        }
        fun defaults():AlphaPreferences {
            val slots=MutableList<AlphaLuaButton?>(43){null}
            listOf(1.0,1.25,1.5,1.75,2.0,2.25,2.5,3.0).forEachIndexed { i,v ->
                slots[i]=AlphaLuaButton("native-speed-$i","${v}×","mp.set_property_number(\"speed\", $v)")
            }
            slots[35]=AlphaLuaButton("native-pause","⏯","mp.commandv(\"cycle\", \"pause\")")
            slots[36]=AlphaLuaButton("native-back","−10 s","mp.commandv(\"seek\", -10, \"relative\")")
            slots[37]=AlphaLuaButton("native-forward","+10 s","mp.commandv(\"seek\", 10, \"relative\")")
            return AlphaPreferences(slots=slots)
        }
    }
}

/** Conservative single-statement native recognizer, NOT a Lua interpreter. */
internal object AlphaNativeButton {
    private val speed=Regex("""^mp\.set_property(?:_number)?\s*\(\s*(['"])speed\1\s*,\s*([0-9]+(?:\.[0-9]+)?)\s*\)$""")
    private val seek=Regex("""^mp\.commandv\s*\(\s*(['"])seek\1\s*,\s*(['"]?)([+-]?[0-9]+(?:\.[0-9]+)?)\2\s*,\s*(['"])(relative|absolute)\4\s*\)$""")
    private val pause=Regex("""^mp\.set_property(?:_bool)?\s*\(\s*(['"])pause\1\s*,\s*(true|false)\s*\)$""")
    private val toggle=Regex("""^mp\.commandv\s*\(\s*(['"])cycle\1\s*,\s*(['"])pause\2\s*\)$""")
    fun command(code:String,state:AlphaPlaybackState):AlphaCommand? {
        if (code.length>1024) return null
        val text=code.trim()
        speed.matchEntire(text)?.let {
            val value=it.groupValues[2].toDoubleOrNull()?:return null
            return if(value in .5..3.0) AlphaCommand.Speed(value) else null
        }
        seek.matchEntire(text)?.let {
            val value=it.groupValues[3].toDoubleOrNull()?:return null
            if (!value.isFinite()) return null
            return if(it.groupValues[5]=="relative" && value in -600.0..600.0)
                AlphaCommand.SeekRelative(value)
            else if(it.groupValues[5]=="absolute" && value>=0) AlphaCommand.SeekAbsolute(value)
            else null
        }
        pause.matchEntire(text)?.let { return AlphaCommand.Pause(it.groupValues[2]=="true") }
        if(toggle.matches(text)) return state.paused?.let { AlphaCommand.Pause(!it) }
        return null
    }
    fun active(button:AlphaLuaButton,state:AlphaPlaybackState):Boolean {
        if(toggle.matches(button.content.trim())) return state.paused==true
        return when(val action=command(button.content,state)) {
            is AlphaCommand.Speed -> state.speed?.let { kotlin.math.abs(it-action.value)<.015 }==true
            is AlphaCommand.Pause -> state.paused==action.paused
            else -> false
        }
    }
}
