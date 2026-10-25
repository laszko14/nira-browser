package com.prirai.android.nira.media.alpha

/**
 * Staged from Alpha v54 MpvConfigSync.kt, c6107ce14a9f1768c73f975c5d60b1d8bd71aaed
 * (Apache-2.0). No installer/caller in v1149: importing a snapshot cannot execute Lua.
 * Future opt-in must install ONE bridge before the owner's first initialize,
 * never on fullscreen/Back; startup actions must be scoped to the playback session.
 */
internal object AlphaLuaBridge {
    const val REFERENCE_COMMIT="c6107ce14a9f1768c73f975c5d60b1d8bd71aaed"
    data class Plan(val installBeforeInitialization:Boolean=false)
    fun plan()=Plan()

    // Template only. Unlike Alpha, errors expose fixed codes rather than source text.
    val source:String = """
        mp.register_script_message("mpv_alpha_eval", function(source)
            if type(source) ~= "string" or source == "" then return end
            local compile = loadstring or load
            local fn = compile(source, "MPVAlphaButton")
            if not fn then
                mp.set_property("user-data/mpvex/alpha_bridge_error", "syntax")
                return
            end
            local ok = pcall(fn)
            if not ok then
                mp.set_property("user-data/mpvex/alpha_bridge_error", "runtime")
            end
        end)
        pcall(mp.set_property, "user-data/mpvex/alpha_bridge_ready", "1")
    """.trimIndent()
}
