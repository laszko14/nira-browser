package com.prirai.android.nira.diagnostics.testlab

import org.junit.Assert.*
import org.junit.Test

class TestLabMetricsTest {
    private fun ms(value: Long) = value * 1_000_000L
    @Test fun absentFrameSamplesAreUnavailableRatherThanZero() {
        assertNull(FrameAccumulator().snapshot())
    }
    @Test fun durationAndJankUseMeasuredValuesWithoutAssumingSixtyHertz() {
        val frames = FrameAccumulator()
        listOf(10L,20L,30L,40L).forEachIndexed { index, duration -> frames.add(ms(duration),index%2==0) }
        val summary = frames.snapshot()!!
        assertEquals(4L, summary.count)
        assertEquals(2L, summary.jankCount)
        assertEquals(50.0, summary.jankPercent,0.001)
        assertEquals(25.0, summary.meanMs,0.001)
        assertEquals(40.0, summary.maxMs,0.001)
        assertEquals(20.0, summary.p50Ms!!,0.25)
        assertNull(summary.p95Ms)
        assertNull(summary.meanOverrunMs)
    }
    @Test fun percentileThresholdsAndOverflowAreExplicit() {
        val frames = FrameAccumulator()
        for(i in 1L..20L)frames.add(ms(i),false)
        assertEquals(19.0, frames.snapshot()!!.p95Ms!!,0.25)
        assertNull(frames.snapshot()!!.p99Ms)
        val overflow=FrameAccumulator()
        repeat(100){overflow.add(ms(2000),true,ms(1800))}
        assertNull(overflow.snapshot()!!.p95Ms)
        assertEquals(2000.0,overflow.snapshot()!!.maxMs,0.001)
        assertEquals(1800.0,overflow.snapshot()!!.meanOverrunMs!!,0.001)
    }
    @Test fun invalidFrameValuesNeverEnterMeasuredCounts() {
        val frames=FrameAccumulator()
        frames.add(0,false);frames.add(-1,true);frames.add(Long.MAX_VALUE,true)
        assertNull(frames.snapshot())
        assertEquals(3L,frames.invalidCount)
        frames.add(ms(7),false)
        assertEquals(1L,frames.snapshot()!!.count)
    }
    @Test fun allFramesAreAggregatedWithBoundedStorage() {
        val frames=FrameAccumulator()
        repeat(100_000){frames.add(ms(2),false)}
        frames.add(ms(500),true)
        assertEquals(100_001L,frames.snapshot()!!.count)
        assertEquals(500.0,frames.snapshot()!!.maxMs,0.001)
        assertEquals(2.0,frames.snapshot()!!.p99Ms!!,0.25)
    }
    @Test fun cpuUsesElapsedTimeWeightedPercentOfOneCore() {
        val resources=ResourceAccumulator(180)
        resources.add(ResourceSample(0,cpuTimeMs=0))
        resources.add(ResourceSample(ms(1000),cpuTimeMs=100))
        resources.add(ResourceSample(ms(4000),cpuTimeMs=700))
        val summary=resources.snapshot()
        assertEquals(700L,summary.cpuDeltaMs)
        assertEquals(17.5,summary.cpuMeanOneCorePercent!!,0.001)
        assertEquals(20.0,summary.cpuMaxOneCorePercent!!,0.001)
        val multi=ResourceAccumulator(180)
        multi.add(ResourceSample(0,cpuTimeMs=0));multi.add(ResourceSample(ms(1000),cpuTimeMs=2000))
        assertEquals(200.0,multi.snapshot().cpuMeanOneCorePercent!!,0.001)
    }
    @Test fun resourceSamplesAreBoundedButExtremaAndInitialBaselineSurviveEviction() {
        val resources=ResourceAccumulator(2)
        resources.add(ResourceSample(0,javaHeapBytes=100))
        resources.add(ResourceSample(ms(1),javaHeapBytes=500))
        resources.add(ResourceSample(ms(2),javaHeapBytes=300))
        val summary=resources.snapshot()
        assertEquals(2,summary.samples.size)
        assertEquals(1L,summary.evictedSamples)
        assertEquals(200L,summary.javaHeapDeltaBytes)
        assertEquals(500L,summary.javaHeapMaxBytes)
        assertNull(summary.pssMaxBytes)
        assertNull(summary.cpuMeanOneCorePercent)
    }
    @Test fun negativeResourcesAndOutOfOrderSamplesAreNotConvertedToZero() {
        val resources=ResourceAccumulator(3)
        resources.add(ResourceSample(ms(2),javaHeapBytes=-1,nativeHeapBytes=-1,cpuTimeMs=-1))
        resources.add(ResourceSample(ms(1),javaHeapBytes=999))
        val summary=resources.snapshot()
        assertNull(summary.javaHeapDeltaBytes)
        assertNull(summary.nativeHeapMaxBytes)
        assertNull(summary.samples.single().javaHeapBytes)
        assertEquals(1L,summary.outOfOrderSamples)
        assertTrue(summary.invalidFields>=3)
    }
    @Test fun fullAuditResourceOffsetsAndCpuCoverBothMinutes() {
        val resources=ResourceAccumulator(maxOffsetNanos=TestLabEngine.FULL_DURATION_NS)
        val times=longArrayOf(59_999L,60_000L,60_001L,119_999L,120_000L)
        times.forEachIndexed { index, time ->
            resources.add(ResourceSample(ms(time),javaHeapBytes=1000L+index,
                pssBytes=2000L+index,cpuTimeMs=index*100L))
        }
        val report=resources.snapshot()
        assertEquals(5L,report.totalSamples)
        assertEquals(0L,report.invalidFields)
        assertEquals(0L,report.outOfOrderSamples)
        assertEquals(4L,report.javaHeapDeltaBytes)
        assertEquals(2004L,report.pssMaxBytes)
        assertEquals(400L,report.cpuDeltaMs)
        assertTrue(report.cpuObservedWallMs!! >= 60_000.0)
    }
    @Test fun sixtySecondModeRejectsLateSampleWithoutCallingItOutOfOrder() {
        val resources=ResourceAccumulator()
        resources.add(ResourceSample(ms(59_999),javaHeapBytes=1))
        resources.add(ResourceSample(ms(60_000),javaHeapBytes=2))
        resources.add(ResourceSample(ms(60_001),javaHeapBytes=3))
        resources.add(ResourceSample(ms(59_999),javaHeapBytes=4))
        val result=resources.snapshot()
        assertEquals(2L,result.totalSamples)
        assertEquals(1L,result.invalidFields)
        assertEquals(1L,result.outOfOrderSamples)
    }
    @Test fun fullAuditRejectsOnlyTrulyLateSamplesAndPreservesCpuBaseline() {
        val resources=ResourceAccumulator(maxOffsetNanos=TestLabEngine.FULL_DURATION_NS)
        resources.add(ResourceSample(0,cpuTimeMs=0,javaHeapBytes=1))
        resources.add(ResourceSample(ms(119_999),cpuTimeMs=1000,javaHeapBytes=4))
        resources.add(ResourceSample(ms(120_000),cpuTimeMs=1100,javaHeapBytes=5))
        resources.add(ResourceSample(ms(120_001),cpuTimeMs=1200,javaHeapBytes=6))
        val result=resources.snapshot()
        assertEquals(3L,result.totalSamples)
        assertEquals(1L,result.invalidFields)
        assertEquals(0L,result.outOfOrderSamples)
        assertEquals(1100L,result.cpuDeltaMs)
        assertEquals(4L,result.javaHeapDeltaBytes)
    }
    @Test fun oneMemorySampleHasNoMeasuredChange() {
        val resources=ResourceAccumulator()
        resources.add(ResourceSample(0,javaHeapBytes=100))
        assertEquals(100L,resources.snapshot().javaHeapMaxBytes)
        assertNull(resources.snapshot().javaHeapDeltaBytes)
    }
    @Test fun invalidApiMagnitudeNeverBecomesAnExportedJsonInteger() {
        val resources=ResourceAccumulator()
        resources.add(ResourceSample(0,javaHeapBytes=Long.MAX_VALUE,cpuTimeMs=Long.MAX_VALUE))
        assertNull(resources.snapshot().javaHeapMaxBytes)
        assertNull(resources.snapshot().samples.single().cpuTimeMs)
    }

}
