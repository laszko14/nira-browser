package com.prirai.android.nira.videosearch

import org.junit.Assert.*
import org.junit.Test

/** Pure policy checks: 30 complete days, no premature or overflow expiry. */
class VideoSearchHistoryRetentionPolicyTest {
    private val day = 24L * 60L * 60L * 1000L

    @Test fun exactThirtyDaysIsEligibleBoundary() {
        assertEquals(10L * day,
            VideoSearchHistoryRetention.cutoffMs(40L * day))
    }
    @Test fun oneMillisecondBeforeThirtyDaysNeverPrunes() {
        assertNull(VideoSearchHistoryRetention.cutoffMs(30L * day - 1L))
    }
    @Test fun durationIsExactlyThirtyDaysInMilliseconds() {
        assertEquals(2_592_000_000L, VideoSearchHistoryRetention.DURATION_MS)
    }
    @Test fun newInstallOrClockRollbackDoesNotExpireHistory() {
        assertNull(VideoSearchHistoryRetention.cutoffMs(-1L))
        assertNull(VideoSearchHistoryRetention.cutoffMs(0L))
        assertNull(VideoSearchHistoryRetention.cutoffMs(123456L))
    }
    @Test fun exactlyThirtyDaysAfterEpochHasZeroCutoff() {
        assertEquals(0L, VideoSearchHistoryRetention.cutoffMs(30L * day))
    }
    @Test fun maximumLongTimestampDoesNotOverflow() {
        assertEquals(Long.MAX_VALUE - VideoSearchHistoryRetention.DURATION_MS,
            VideoSearchHistoryRetention.cutoffMs(Long.MAX_VALUE))
    }
}
