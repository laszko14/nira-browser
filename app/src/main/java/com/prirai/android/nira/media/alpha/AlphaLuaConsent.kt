package com.prirai.android.nira.media.alpha

import android.content.Context

/** Separate from the imported/undoable panel snapshot. XML never grants execution consent. */
internal class AlphaLuaConsent(context:Context) {
    private val prefs=context.applicationContext.getSharedPreferences("mpv_alpha_lua_execution_v1",Context.MODE_PRIVATE)
    fun enabled():Boolean=runCatching { prefs.getBoolean("user_enabled",false) }.getOrDefault(false)
    fun save(enabled:Boolean):Boolean=prefs.edit().putBoolean("user_enabled",enabled).commit()
}

/** Session-bound aggregate diagnostics, RAM only. No source, button names or IDs. */
internal object AlphaLuaDiagnostics {
    private var owner:Any?=null
    private var current=AlphaLuaState()
    @Synchronized fun update(session:Any?,value:AlphaLuaState) { owner=session;current=value }
    @Synchronized fun report(session:Any?):String {
        val value=if(session!=null && session===owner)current else AlphaLuaState()
        fun yes(flag:Boolean)=if(flag)"TAK" else "NIE"
        return """
            LUA / BRIDGE
            Bridge zainstalowany: ${yes(value.installed)}
            Bridge gotowy: ${yes(value.ready)}
            Lua włączone: ${yes(value.enabled)}
            Wywołania: ${value.sent}
            Potwierdzone sukcesy: ${value.succeeded}
            Błędy składni: ${value.syntaxErrors}
            Błędy wykonania: ${value.runtimeErrors}
            Brak potwierdzenia: ${value.timeouts}
            Zablokowane wywołania: ${value.blocked}
        """.trimIndent()
    }
}
