package com.prirai.android.nira.videosearch

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory process bootstrap guard. A normal worker calls this before its
 * first run write, so opening history later never stops a living worker.
 * On process death the set disappears and previous work is reconciled.
 */
internal object VideoSearchRunRecovery {
    private val lock = Mutex()
    private val seen = HashSet<String>()

    suspend fun once(repo: VideoSearchRepository, scope: VideoSearchScope.Normal): Int =
        lock.withLock {
            if (scope.profileId in seen) return@withLock 0
            val count = repo.reconcileOrphanedRuns(scope)
            seen.add(scope.profileId) // only after successful Room transaction
            count
        }
}
