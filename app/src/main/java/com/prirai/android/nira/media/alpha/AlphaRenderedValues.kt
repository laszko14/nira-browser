package com.prirai.android.nira.media.alpha

/** Render-time bounds never overwrite imported source values. */
internal class AlphaRenderedValues(private val prefs:AlphaPreferences) {
    fun value(key:String):AlphaPreferenceValue? =
        if(AlphaImportSchema.rules[key]?.group==AlphaSettingGroup.HUD) prefs.hud[key]
        else if(isAlphaLuaLayoutKey(key))prefs.buttonLayout[key] else prefs.luaAppearance[key]
    fun int(key:String,default:Int):Int {
        val stored=(value(key) as? AlphaPreferenceValue.IntValue)?.value
        val fallback=when(key) {
            "lua_button_scale_percent" ->
                (prefs.luaAppearance["three_finger_speed_panel_size_pct"] as? AlphaPreferenceValue.IntValue)?.value?:default
            "lua_button_bg_transparency_pct" ->
                (prefs.luaAppearance["three_finger_speed_panel_transparency_pct"] as? AlphaPreferenceValue.IntValue)?.value?:default
            "lua_button_font_weight_percent" ->
                (prefs.luaAppearance["lua_button_bold_text"] as? AlphaPreferenceValue.Bool)?.let { if(it.value)60 else 0 }?:default
            "lua_button_outline_percent" ->
                (prefs.luaAppearance["lua_button_high_contrast_text"] as? AlphaPreferenceValue.Bool)?.let { if(it.value)50 else 0 }?:default
            else -> default
        }
        return AlphaImportSchema.renderInt(key,stored?:fallback)
    }
    fun bool(key:String,default:Boolean)=(value(key) as? AlphaPreferenceValue.Bool)?.value?:default
    fun text(key:String,default:String)=(value(key) as? AlphaPreferenceValue.Text)?.value?:default
    fun fraction(key:String,default:Float):Float =
        ((value(key) as? AlphaPreferenceValue.FloatValue)?.value?.takeIf { it.isFinite() }?:default).coerceIn(0f,1f)
}
