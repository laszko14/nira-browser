package com.prirai.android.nira.media.alpha.audio

/** Aggregate state only, never raw AF parameters or custom labels. */
internal object AlphaAudioDiagnostics {
    private var owner:Any?=null
    private var current=AlphaAudioState()
    @Synchronized fun update(session:Any?,state:AlphaAudioState) {owner=session;current=state}
    @Synchronized fun report(session:Any?):String {
        val s=if(session!=null && owner===session)current else AlphaAudioState()
        return """
            AUDIO / ALPHA
            Audio controller: ${if(s.ready)"READY" else "UNAVAILABLE"}
            Szum: ${if(s.noiseExternal)"EXTERNAL_UNVERIFIED" else "UNAVAILABLE_MODEL"}
            Głos Lua: ${if(s.voiceExternal)"EXTERNAL_UNVERIFIED" else "UNAVAILABLE_DEFINITION"}
            EQ: ${if(!s.consistent)"UNCONFIRMED" else s.eq?.preset()?:"OFF"}
            Aktywne filtry kontrolera: ${if(s.eq!=null)1 else 0}
            Polecenia wysłane: ${s.sent}
            Operacje potwierdzone: ${s.accepted}
            Operacje odrzucone: ${s.rejected}
            Błędy filtrów: ${s.errors}
            Zmiany parametrów: ${s.changes}
            Konflikty łańcucha: ${s.conflicts}
            Zgodność z odczytem MPV: ${if(s.consistent)"TAK" else "NIEPOTWIERDZONA"}
        """.trimIndent()
    }
}
