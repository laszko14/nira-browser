package com.prirai.android.nira.downloads

import kotlinx.coroutines.Job
import mozilla.components.browser.state.state.content.DownloadState

/**
 * Owns the delayed Content-Length probe, separate from the download services.
 * An old result can never claim a newer attempt using the same download ID.
 * No lock is held while waiting for network data.
 */
internal class DownloadProbeGate {
    class Ticket internal constructor(val id: String) {
        internal var job: Job? = null
    }

    private val lock = Any()
    private val active = mutableMapOf<String, Ticket>()

    fun open(id: String): Ticket = synchronized(lock) {
        active.remove(id)?.job?.cancel()
        Ticket(id).also { active[id] = it }
    }

    fun attach(ticket: Ticket, job: Job) = synchronized(lock) {
        if (active[ticket.id] === ticket) {
            ticket.job = job
        } else {
            job.cancel()
        }
    }

    fun invalidate(id: String) = synchronized(lock) {
        active.remove(id)?.job?.cancel()
    }

    /** Protect the initial serial-service start against concurrent pause/cancel. */
    fun runIfCurrent(ticket: Ticket, action: () -> Unit): Boolean = synchronized(lock) {
        if (active[ticket.id] !== ticket) return@synchronized false
        action()
        true
    }

    /** Exactly one handoff may claim a live attempt; pause/cancel uses the same lock. */
    fun claim(ticket: Ticket, action: () -> Unit): Boolean = synchronized(lock) {
        if (active[ticket.id] !== ticket) return@synchronized false
        active.remove(ticket.id)
        action()
        true
    }

    fun release(ticket: Ticket) = synchronized(lock) {
        if (active[ticket.id] === ticket) active.remove(ticket.id)
    }
}

/** A late probe cannot resurrect removed, completed, failed or paused downloads. */
internal fun DownloadState?.isEligibleForProbeHandoff(expected: DownloadState): Boolean {
    val current = this ?: return false
    return current.id == expected.id &&
        current.url == expected.url &&
        current.private == expected.private &&
        current.referrerUrl == expected.referrerUrl &&
        current.status in setOf(DownloadState.Status.INITIATED, DownloadState.Status.DOWNLOADING)
}
