package com.prirai.android.nira.downloads

import kotlinx.coroutines.*

/** Serializes only successive owners of the same download's files; other downloads remain parallel. */
internal class DownloadTaskRegistry(private val scope: CoroutineScope, private val onIdle: () -> Unit) {
    private data class Entry(val job: Job, val stopping: Boolean, val predecessors: List<Job>)
    private val entries = mutableMapOf<String, Entry>()
    private val lock = Any()

    fun isIdle(): Boolean = synchronized(lock) { entries.isEmpty() }

    fun start(id: String, work: suspend () -> Unit): Job? = synchronized(lock) {
        val previous = entries[id]
        if (previous != null && previous.job.isActive && !previous.stopping) return@synchronized null
        enqueue(id, previous, false, work)
    }

    fun stop(id: String, cleanup: suspend () -> Unit): Job = synchronized(lock) {
        enqueue(id, entries[id], true, cleanup)
    }

    private fun enqueue(id: String, previous: Entry?, stopping: Boolean, work: suspend () -> Unit): Job {
        val predecessors = (previous?.predecessors.orEmpty() + listOfNotNull(previous?.job)).filterNot { it.isCompleted }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            // Even if this queued owner is cancelled, its successor must still wait for the original writer.
            withContext(NonCancellable) { predecessors.joinAll() }
            currentCoroutineContext().ensureActive()
            work()
        }
        entries[id] = Entry(job, stopping, predecessors)
        job.invokeOnCompletion {
            synchronized(lock) {
                if (entries[id]?.job === job) {
                    entries.remove(id)
                    if (entries.isEmpty()) onIdle()
                }
            }
        }
        if (stopping) previous?.job?.cancel()
        job.start() // Registration must precede execution, including immediate completion.
        return job
    }
}
