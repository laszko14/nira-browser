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
            Bridge zainstalowany: ${yes(value.installed)} (${if(value.installed)"INSTALL_OK" else "INSTALL_FAILED"})
            Konfiguracja ładowania: ${yes(value.loadConfigured)}
            Bridge załadowany: ${if(value.loaded)"TAK (LOAD_CONFIRMED)" else "NIEPOTWIERDZONY (LOAD_UNCONFIRMED)"}
            Bridge zarejestrowany: ${if(value.registered)"TAK (REGISTERED)" else "NIEPOTWIERDZONY (REGISTER_UNCONFIRMED)"}
            Probe wysłane: ${value.probeSent} (${if(value.probeSent>0)"PROBE_SENT" else "PROBE_UNSENT"})
            Probe ACK: ${yes(value.probeAcknowledged)} (${if(value.probeAcknowledged)"PROBE_ACK" else "PROBE_UNCONFIRMED"})
            Bridge gotowy: ${yes(value.ready)} (${if(value.ready)"READY" else "NOT_READY"})
            Lua włączone: ${yes(value.enabled)}
            Wywołania: ${value.sent} (DISPATCH_SENT)
            Potwierdzone sukcesy: ${value.succeeded} (EXEC_OK)
            Błędy składni: ${value.syntaxErrors} (SYNTAX_ERROR)
            Błędy wykonania: ${value.runtimeErrors} (RUNTIME_ERROR)
            Brak potwierdzenia: ${value.timeouts} (TIMEOUT)
            Zablokowane wywołania: ${value.blocked} (BLOCKED)
            Błędy publikacji ACK: ${value.publishFailures} (PROPERTY_WRITE_FAILED)
            Nieprawidłowe odpowiedzi: ${value.invalidMessages} (PROPERTY_INVALID)
        """.trimIndent()
    }
}
