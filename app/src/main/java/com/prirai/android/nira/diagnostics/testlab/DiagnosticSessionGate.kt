package com.prirai.android.nira.diagnostics.testlab

internal enum class DiagnosticOwner { TESTLAB, SUPERTEST }
/** One lock, never held while invoking collectors or either engine. */
internal class DiagnosticSessionGate {
    @Volatile var owner: DiagnosticOwner? = null; private set
    @Synchronized fun acquire(request: DiagnosticOwner): Boolean {
        if(owner!=null)return false
        owner=request;return true
    }
    @Synchronized fun release(request: DiagnosticOwner) {if(owner==request)owner=null}
    companion object {val shared=DiagnosticSessionGate()}
}
