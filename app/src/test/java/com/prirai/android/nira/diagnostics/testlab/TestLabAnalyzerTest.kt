package com.prirai.android.nira.diagnostics.testlab

import org.junit.Assert.*
import org.junit.Test

class TestLabAnalyzerTest {
    private fun base():TestLabReport {
        var now=0L;val e=TestLabEngine({now},LabIdentity(1168,"0.5.22-alpha73-clean"));val t=e.start(TestType.COMPLEX)!!
        e.begin(t);now=1_000_000_000;e.stop(t);e.complete(t);return e.latest()!!
    }
    @Test fun slowCompletedPanelHasExplicitThresholdEvidenceAndCorrelationIsNotACause() {
        val report=base();val original=report.operationMetrics.first{it.operation==LabOperation.LEFT_PANEL_OPEN}
        val changed=original.copy(started=1,accepted=1,succeeded=1,latency=LatencySummary(1,900.0,900.0,null))
        val r=TestLabAnalyzer.analyze(report.copy(operationMetrics=listOf(changed)))
        val finding=r.anomalies.single()
        assertEquals(ObservationKind.OBSERVED_PERFORMANCE_ANOMALY,finding.kind)
        assertEquals(LabOperation.LEFT_PANEL_OPEN,finding.operation)
        assertEquals(LabUnit.MILLISECONDS,finding.unit)
        assertNotNull(finding.threshold);assertEquals(1L,finding.evidenceCount)
        assertNull(finding.invariant)
    }
    @Test fun cancelledGestureWithNoSuccessLatencyDoesNotCreateASlowOperationFinding() {
        val r=base();val cancelled=r.operationMetrics.first().copy(started=1,cancelled=1,latency=null)
        assertTrue(TestLabAnalyzer.analyze(r.copy(operationMetrics=listOf(cancelled))).anomalies.isEmpty())
    }
    @Test fun memoryGrowthAndQueueLimitAreSuspicionsWhileConfirmedInvariantKeepsPriority() {
        val r=base();val confirmed=Observation(ObservationKind.CONFIRMED_INVARIANT_FAILURE,LabCode.INVARIANT_FAILURE,0,invariant=LabInvariant.ORDER_NOT_PERSISTED)
        val result=TestLabAnalyzer.analyze(r.copy(resourceMetrics=r.resourceMetrics.copy(javaHeapDeltaBytes=32L*1024*1024,totalSamples=2),
            eventCounts=mapOf(LabCode.DETECTOR_QUEUE_LIMIT to 2L),anomalies=listOf(confirmed)))
        assertEquals(confirmed,result.anomalies.first())
        assertTrue(result.anomalies.drop(1).all{it.kind==ObservationKind.SUSPECTED_CORRELATION&&it.invariant==null})
        assertTrue(result.anomalies.any{it.code==LabCode.DETECTOR_QUEUE_LIMIT&&it.evidenceCount==2L})
    }
}
