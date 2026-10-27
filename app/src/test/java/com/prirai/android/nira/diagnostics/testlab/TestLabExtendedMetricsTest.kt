package com.prirai.android.nira.diagnostics.testlab

import org.junit.Assert.*
import org.junit.Test

class TestLabExtendedMetricsTest {
    @Test fun cpuFrameDurationIsSeparateFromUiDurationAndUnsupportedFieldsStayNull() {
        val frames=FrameAccumulator()
        frames.addMeasured(10_000_000,false,0,false,4_000_000,true)
        frames.addMeasured(20_000_000,true,0,false,8_000_000,true)
        val f=frames.snapshot()!!
        assertEquals(15.0,f.meanMs,0.0);assertEquals(6.0,f.meanCpuMs!!,0.0)
        assertEquals(8.0,f.maxCpuMs!!,0.0);assertEquals(2L,f.cpuSamples)
        assertNull(f.meanOverrunMs)
    }
    @Test fun nativePssAndUssDeltasSurviveRingEvictionWithoutAddingOverlappingMemory() {
        val r=ResourceAccumulator(1)
        r.add(ResourceSample(0,nativeHeapBytes=10,pssBytes=100,ussBytes=50))
        assertNull(r.snapshot().nativeHeapDeltaBytes)
        r.add(ResourceSample(1_000_000_000,nativeHeapBytes=25,pssBytes=90,ussBytes=60))
        val v=r.snapshot()
        assertEquals(15L,v.nativeHeapDeltaBytes);assertEquals(-10L,v.pssDeltaBytes);assertEquals(10L,v.ussDeltaBytes)
        assertEquals(1,v.samples.size);assertEquals(100L,v.pssMaxBytes)
    }
    @Test fun outstandingMainMessageIsEvidenceBeforeAckAndOldWindowCannotInflateIt() {
        var now=0L;val e=TestLabEngine({now},LabIdentity(1168,"0.5.22-alpha73-clean"))
        val t=e.start(TestType.STABILITY)!!;e.begin(t);e.foreground(t,true)
        val epoch=e.foregroundGeneration;now=500_000_000
        assertTrue(e.pendingHeartbeat(t,500_000_000,epoch))
        e.foreground(t,false);e.foreground(t,true)
        assertFalse(e.pendingHeartbeat(t,500_000_000,epoch))
        e.stop(t);now=750_000_000;e.complete(t)
        val r=e.latest()!!
        assertEquals(0L,r.heartbeatSamples);assertEquals(500.0,r.maxPendingHeartbeatMs!!,0.0)
        assertEquals(250.0,r.finalizationDelayMs,0.0)
        assertEquals(Availability.MEASURED,r.metricAvailability[LabMetric.MAIN_HEARTBEAT_PENDING_AGE])
        assertEquals(Availability.NOT_OBSERVED,r.metricAvailability[LabMetric.MAIN_HEARTBEAT])
        assertTrue(r.anomalies.any{it.code==LabCode.MAIN_STALL_PENDING&&it.kind==ObservationKind.OBSERVED_PERFORMANCE_ANOMALY})
    }
}
