package com.prirai.android.nira.videosearch

/** Pure stop-and-drain protocol: a send/ACK is never a completed read. */
internal object VideoSearchDiagnosticDrainPolicy {
    enum class Command { START, READ, STOP }
    fun command(phase: String, finalizing: Boolean): Command? = when (phase) {
        "start" -> Command.START
        "reading" -> if (finalizing) Command.STOP else Command.READ
        "draining" -> Command.READ
        else -> null
    }
    fun afterAck(phase: String, status: String): String? = when {
        phase == "start" && status == "active" -> "reading"
        phase == "stopping" && status == "stopped" -> "draining"
        else -> null
    }
    fun afterRead(phase: String, empty: Boolean): String? = when (phase) {
        "reading" -> "reading"
        "draining" -> if (empty) "drained" else "draining"
        else -> null
    }
    fun terminal(phase: String): Boolean = phase == "drained" || phase == "failed"
}
