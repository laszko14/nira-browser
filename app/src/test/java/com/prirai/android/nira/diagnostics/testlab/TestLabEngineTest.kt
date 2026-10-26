package com.prirai.android.nira.diagnostics.testlab

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class TestLabEngineTest {
    private var now=0L
    private fun advance(ms:Long){now+=ms*1_000_000L}
    private fun engine(maxEvents:Int=1024)=TestLabEngine({now},LabIdentity(1165,"0.5.22-alpha71-mpv39"),maxEvents)
    private fun running(e:TestLabEngine,type:TestType=TestType.COMPLEX):SessionToken {
        val token=e.start(type)!!;assertTrue(e.begin(token));assertTrue(e.foreground(token,true));return token
    }
    private fun finish(e:TestLabEngine,t:SessionToken):TestLabReport {
        assertTrue(e.stop(t));assertEquals(LabPhase.FINALIZING,e.phase)
        assertTrue(e.complete(t));return e.latest()!!
    }
    @Test fun preparingDoesNotMeasureTheSettingsScreen() {
        val e=engine();val t=e.start(TestType.COMPLEX)!!
        assertEquals(LabPhase.PREPARING,e.phase)
        assertFalse(e.frame(t,10_000_000,false))
        assertFalse(e.note(t,LabCode.MENU_OPEN))
        advance(3000);assertTrue(e.begin(t));advance(10)
        val report=finish(e,t)
        assertEquals(10.0,report.durationMs,0.001)
        assertNull(report.frameMetrics)
    }
    @Test fun sixtySecondDeadlineUsesOnlyMonotonicTimeAndRequiresCleanup() {
        val e=engine();val t=running(e);advance(59_999)
        assertFalse(e.poll(t));advance(1);assertTrue(e.poll(t))
        assertEquals(LabPhase.FINALIZING,e.phase)
        assertNull(e.start(TestType.VIDEO_MPV))
        assertFalse(e.note(t,LabCode.MPV_START_FILE))
        assertTrue(e.complete(t));val report=e.latest()!!
        assertEquals(LabPhase.COMPLETED,report.status)
        assertEquals(60_000.0,report.durationMs,0.001)
    }
    @Test fun earlyFinishHasActualDurationAndPartialStatus() {
        val e=engine();val t=running(e);advance(15_012)
        val report=finish(e,t)
        assertEquals(LabPhase.PARTIAL,report.status)
        assertEquals(15_012.0,report.durationMs,0.001)
    }
    @Test fun lateTimerCannotExtendTheMeasurementWindow() {
        val e=engine();val t=running(e);advance(65_000)
        assertFalse(e.note(t,LabCode.MPV_START_FILE));assertTrue(e.poll(t));e.complete(t)
        assertEquals(60_000.0,e.latest()!!.durationMs,0.001)
        assertNull(e.latest()!!.eventCounts[LabCode.MPV_START_FILE])
    }
    @Test fun noParallelStartOrDoubleFinishEvenUnderConcurrentCallers() {
        val e=engine();val pool=Executors.newFixedThreadPool(8);val gate=CountDownLatch(1)
        try {
            val futures=(1..8).map{pool.submit<SessionToken?>{gate.await();e.start(TestType.COMPLEX)}}
            gate.countDown();val tokens=futures.mapNotNull{it.get(5,TimeUnit.SECONDS)}
            assertEquals(1,tokens.size);val t=tokens.single();e.begin(t);advance(10);e.stop(t)
            assertTrue(e.complete(t));val report=e.latest()
            assertFalse(e.complete(t));assertSame(report,e.latest())
        } finally {pool.shutdownNow()}
    }
    @Test fun cancelledPreparationPreservesAZeroDurationUntestedReport() {
        val e=engine();val t=e.start(TestType.COMPLEX)!!;advance(1000)
        assertTrue(e.cancel(t));assertTrue(e.complete(t))
        assertEquals(LabPhase.CANCELLED,e.latest()!!.status)
        assertEquals(0.0,e.latest()!!.durationMs,0.001)
        assertNull(e.latest()!!.frameMetrics)
        assertEquals(Coverage.NOT_TESTED,e.latest()!!.testCoverage[LabOperation.LEFT_PANEL_OPEN])
    }
    @Test fun oldSessionCallbacksCannotAppendToNewSession() {
        val e=engine();val old=running(e);advance(20);val oldReport=finish(e,old)
        val fresh=running(e)
        assertFalse(e.note(old,LabCode.MPV_START_FILE));assertFalse(e.frame(old,10_000_000,true))
        assertTrue(e.note(fresh,LabCode.MENU_OPEN));advance(20);val current=finish(e,fresh)
        assertNull(current.eventCounts[LabCode.MPV_START_FILE]);assertEquals(1L,current.eventCounts[LabCode.MENU_OPEN])
        assertNull(oldReport.eventCounts[LabCode.MENU_OPEN])
    }
    @Test fun backgroundIsAnExplicitCoverageGap() {
        val e=engine();val t=running(e);advance(10_000);e.foreground(t,false)
        assertFalse(e.frame(t,100_000_000,true));advance(15_000);e.foreground(t,true);advance(5000)
        val report=finish(e,t)
        assertEquals(15_000.0,report.foregroundMs,0.001)
        assertEquals(15_000.0,report.noCoverageMs,0.001)
    }
    @Test fun cancelledGestureIsNotASuccessFailureOrTimeout() {
        val e=engine();val t=running(e);val gesture=e.operation(t,LabOperation.LEFT_PANEL_OPEN)!!
        advance(50);assertTrue(e.end(gesture,OperationOutcome.CANCELLED));advance(50)
        val report=finish(e,t);val op=report.operationMetrics.single{it.operation==LabOperation.LEFT_PANEL_OPEN}
        assertEquals(1L,op.started);assertEquals(1L,op.cancelled);assertEquals(0L,op.succeeded)
        assertNull(op.latency);assertTrue(report.anomalies.none{it.kind==ObservationKind.CONFIRMED_INVARIANT_FAILURE})
    }
    @Test fun panelStartAloneCannotBeReportedAsAnOpenedPanel() {
        val e=engine();val t=running(e);val op=e.operation(t,LabOperation.LEFT_PANEL_OPEN)!!
        advance(10);assertFalse(e.end(op,OperationOutcome.SUCCESS));advance(10)
        val report=finish(e,t)
        assertEquals(0L,report.operationMetrics.single{it.operation==LabOperation.LEFT_PANEL_OPEN}.succeeded)
        assertEquals(1L,report.errorCounts[LabError.INSTRUMENTATION_PROTOCOL])
    }
    @Test fun acceptedPanelCompletionMeasuresRealInterval() {
        val e=engine();val t=running(e);val op=e.operation(t,LabOperation.LEFT_PANEL_OPEN)!!
        advance(20);assertTrue(e.accept(op));advance(80);assertTrue(e.end(op,OperationOutcome.SUCCESS))
        advance(1);val stats=finish(e,t).operationMetrics.single{it.operation==LabOperation.LEFT_PANEL_OPEN}
        assertEquals(1L,stats.succeeded);assertEquals(100.0,stats.latency!!.meanMs,0.001)
        assertNull(stats.latency.p95Ms)
    }
    @Test fun cappedTimelineKeepsAggregateCountsAndDisclosesEvictions() {
        val e=engine(3);val t=running(e)
        repeat(10){advance(1);e.note(t,LabCode.MENU_OPEN)}
        advance(1);val report=finish(e,t)
        assertTrue(report.timeline.size<=3);assertTrue(report.evictedEvents>0)
        assertEquals(10L,report.eventCounts[LabCode.MENU_OPEN])
    }
    @Test fun noVideoIsNotTestedAndNotHealthyPlayback() {
        val e=engine();val t=running(e);advance(100);val report=finish(e,t)
        assertEquals(Coverage.NOT_TESTED,report.testCoverage[LabOperation.MPV_PLAYBACK])
        assertEquals(Availability.NOT_EXPOSED_BY_PLATFORM,report.metricAvailability[LabMetric.MPV_PRESENTED_FRAMES])
        assertEquals(Availability.NOT_EXPOSED_BY_PLATFORM,report.metricAvailability[LabMetric.GECKO_COMPOSITOR_FRAMES])
    }
    @Test fun correlationAndMeasuredStallAreNotConfirmedAnr() {
        val e=engine();val t=running(e);e.operation(t,LabOperation.LEFT_PANEL_OPEN);advance(500)
        e.heartbeat(t,500_000_000);e.mark(t);advance(500)
        val report=finish(e,t)
        assertTrue(report.anomalies.any{it.kind==ObservationKind.OBSERVED_PERFORMANCE_ANOMALY})
        assertTrue(report.anomalies.none{it.kind==ObservationKind.CONFIRMED_INVARIANT_FAILURE})
        assertEquals(1,report.anomalyMarkers.size)
        assertEquals(500_000_000L,report.anomalyMarkers.single().offsetNanos)
    }
    @Test fun onlyExplicitInvariantEvidenceIsAConfirmedFailure() {
        val e=engine();val t=running(e)
        e.invariant(t,LabInvariant.ORDER_NOT_PERSISTED);advance(100)
        assertTrue(finish(e,t).anomalies.any{it.kind==ObservationKind.CONFIRMED_INVARIANT_FAILURE})
    }
    @Test fun specialistModeDoesNotPretendToHaveTestedOtherOperations() {
        val e=engine();val t=running(e,TestType.PANELS_GESTURES)
        assertNull(e.operation(t,LabOperation.TAB_SWITCH));advance(100)
        assertEquals(Coverage.NOT_SELECTED,finish(e,t).testCoverage[LabOperation.TAB_SWITCH])
    }
    @Test fun environmentCannotBeMutatedAfterCompletionByACollector() {
        val e=engine();val t=e.start(TestType.COMPLEX)!!;val rates=mutableListOf(60.0)
        e.begin(t,LabEnvironment(35,8,rates));advance(10);val report=finish(e,t)
        rates.add(120.0)
        assertEquals(listOf(60.0),report.environment.refreshRatesHz)
    }
    @Test fun everyMetricHasAnExplicitSourceEvenWhenUnsupported() {
        val e=engine();val t=running(e);advance(1);val report=finish(e,t)
        assertEquals(LabMetric.entries.toSet(),report.metricSources.keys)
        assertEquals(MetricSource.UNAVAILABLE,report.metricSources[LabMetric.DUAL_AUDIO])
    }
    @Test fun appIdentityCannotCreateAnUnboundedReportHeader() {
        assertThrows(IllegalArgumentException::class.java){LabIdentity(1165,"0."+"1".repeat(1000)+"-alpha71-mpv39")}
    }
    @Test fun aFrameCanCorrelateWithTwoOperationsWithoutBeingCountedTwiceGlobally() {
        val e=engine();val t=running(e)
        val left=e.operation(t,LabOperation.LEFT_PANEL_OPEN)!!;e.accept(left)
        val right=e.operation(t,LabOperation.RIGHT_PANEL_OPEN)!!;e.accept(right)
        e.frame(t,20_000_000,true,tags=(1L shl LabOperation.LEFT_PANEL_OPEN.ordinal) or (1L shl LabOperation.RIGHT_PANEL_OPEN.ordinal))
        advance(10);val report=finish(e,t)
        assertEquals(1L,report.frameMetrics!!.count)
        assertEquals(1L,report.operationMetrics.single{it.operation==LabOperation.LEFT_PANEL_OPEN}.taggedJank)
        assertEquals(1L,report.operationMetrics.single{it.operation==LabOperation.RIGHT_PANEL_OPEN}.taggedJank)
    }
    @Test fun nonFiniteEventPayloadIsRejectedBeforeStorage() {
        val e=engine();val t=running(e)
        assertFalse(e.note(t,LabCode.LOAD_PROGRESS,Double.NaN))
        advance(10);val report=finish(e,t)
        assertNull(report.eventCounts[LabCode.LOAD_PROGRESS])
        assertEquals(1L,report.errorCounts[LabError.INVALID_SAMPLE])
    }

}
