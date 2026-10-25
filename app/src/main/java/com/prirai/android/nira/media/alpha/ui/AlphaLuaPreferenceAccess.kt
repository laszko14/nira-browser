package com.prirai.android.nira.media.alpha.ui

import androidx.compose.runtime.*
import com.prirai.android.nira.media.alpha.*

internal class AlphaLuaSetting<T>(
    val config:AlphaLuaConfig,
    val read:(AlphaPreferences)->T,
    private val write:(AlphaPreferences,T)->AlphaPreferences,
) {
    fun get():T=read(config.state.value)
    fun set(value:T) { config.update { write(it,value) } }
}
@Composable
internal fun <T> AlphaLuaSetting<T>.collectAsState():State<T> {
    val snapshot by config.state.collectAsState()
    return rememberUpdatedState(read(snapshot))
}

/** Only UI keys are adapted; all other snapshot groups and button fields survive writes. */
internal class AlphaLuaPreferenceAccess(private val config:AlphaLuaConfig) {
    private fun group(p:AlphaPreferences,key:String)=if(isLayout(key))p.buttonLayout else p.luaAppearance
    private fun isLayout(key:String)=key.contains("position") || key.contains("columns") ||
        key.contains("row_gap") || key.contains("column_gap") || key.contains("empty_slots")
    private fun put(p:AlphaPreferences,key:String,v:AlphaPreferenceValue)=
        if(isLayout(key))p.copy(buttonLayout=p.buttonLayout+(key to v))
        else p.copy(luaAppearance=p.luaAppearance+(key to v))
    fun getInt(key:String,default:Int)=AlphaLuaSetting(config,
        { (group(it,key)[key] as? AlphaPreferenceValue.IntValue)?.value?:default },
        { p,v -> put(p,key,AlphaPreferenceValue.IntValue(v)) })
    fun getBoolean(key:String,default:Boolean)=AlphaLuaSetting(config,
        { (group(it,key)[key] as? AlphaPreferenceValue.Bool)?.value?:default },
        { p,v -> put(p,key,AlphaPreferenceValue.Bool(v)) })
    fun getFloat(key:String,default:Float)=AlphaLuaSetting(config,
        { (group(it,key)[key] as? AlphaPreferenceValue.FloatValue)?.value?:default },
        { p,v -> put(p,key,AlphaPreferenceValue.FloatValue(v)) })
    fun getString(key:String,default:String):AlphaLuaSetting<String> =
        if(key=="custom_buttons_json") AlphaLuaSetting(config,
            { AlphaSlots.encode(it.slots) },
            { p,v -> p.copy(slots=AlphaSlots.decode(v)) })
        else AlphaLuaSetting(config,
            { (group(it,key)[key] as? AlphaPreferenceValue.Text)?.value?:default },
            { p,v -> put(p,key,AlphaPreferenceValue.Text(v)) })
}

internal data class AlphaLuaButtonState(
    val id:String,val label:String,val isLeft:Boolean,
    val alwaysVisible:Boolean?=null,
    val backgroundColor:Int?=null,val textColor:Int?=null,
    val activeBackgroundColor:Int?=null,val activeTextColor:Int?=null,
    val active:Boolean=false,
)
