package com.prirai.android.nira.media

/** MPVLib is process-global: a second Activity must never initialize it twice. */
internal object MpvEngineGate {
    private var owner: String? = null

    @Synchronized
    fun claim(id: String): Boolean {
        if (owner != null) return false
        owner = id
        return true
    }

    @Synchronized
    fun owns(id: String): Boolean = owner == id

    @Synchronized
    fun release(id: String) {
        if (owner == id) owner = null
    }
}
