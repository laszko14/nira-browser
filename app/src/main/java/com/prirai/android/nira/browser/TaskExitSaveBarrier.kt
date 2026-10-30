package com.prirai.android.nira.browser

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A single, asynchronous final-save barrier for an explicitly finishing root task.
 *
 * Work must be launched in an Application-owned scope (never Activity.lifecycleScope).
 * The process may be terminated only after [persist] has returned true. A failed,
 * cancelled or never-completing save must never forcibly terminate the process.
 */
internal class TaskExitSaveBarrier(
    private val scope: CoroutineScope,
    private val persist: suspend () -> Boolean,
    private val onCommitted: () -> Unit,
    private val onFailure: (Throwable) -> Unit,
) {
    private val requested = AtomicBoolean(false)

    fun request() {
        if (!requested.compareAndSet(false, true)) return

        scope.launch {
            try {
                if (persist()) {
                    onCommitted()
                } else {
                    onFailure(IllegalStateException("Final session save was not acknowledged"))
                }
            } catch (e: CancellationException) {
                onFailure(e)
                throw e
            } catch (t: Throwable) {
                onFailure(t)
            }
        }
    }
}
