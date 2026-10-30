package com.prirai.android.nira.browser.profile

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Global addon/search settings belong to one active profile at a time.
 * A new selection invalidates queued work; the mutex also prevents an older
 * in-flight enable/disable from racing the next profile's reconciler.
 */
internal object ProfilePolicySequencer {
    private val generation = AtomicLong()
    private val mutationLock = Mutex()

    fun issue(): Long = generation.incrementAndGet()
    fun isCurrent(ticket: Long): Boolean = generation.get() == ticket

    suspend fun reconcile(ticket: Long, operation: suspend (() -> Boolean) -> Unit) {
        mutationLock.withLock {
            if (isCurrent(ticket)) {
                operation { isCurrent(ticket) }
            }
        }
    }
}
