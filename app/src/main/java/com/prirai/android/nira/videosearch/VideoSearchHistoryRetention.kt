package com.prirai.android.nira.videosearch

/**
 * Stage 9A. Retain the last 30 *complete* days of finished searches.
 * An invalid/pre-epoch or rolled-back wall clock must never remove history.
 * Running/queued/paused runs and saved films/folders are outside this policy.
 */
internal object VideoSearchHistoryRetention {
    const val DURATION_MS = 30L * 24L * 60L * 60L * 1000L

    fun cutoffMs(nowMs: Long): Long? =
        if (nowMs < DURATION_MS) null else nowMs - DURATION_MS
}
