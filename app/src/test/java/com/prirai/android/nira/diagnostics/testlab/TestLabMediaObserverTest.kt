package com.prirai.android.nira.diagnostics.testlab

import org.junit.Assert.*
import org.junit.Test

class TestLabMediaObserverTest {
    private var now=0L
    private fun capture():Triple<TestLabEngine,SessionToken,TestLabMediaObserver> {
        val e=TestLabEngine({now},LabIdentity(1165,"0.5.22-alpha71-mpv39"));val token=e.start(TestType.VIDEO_MPV)!!
        e.begin(token);return Triple(e,token,TestLabMediaObserver(LabCapture(e,token)))
    }
    @Test fun ackMustMatchActualPrepareOwnerAndUnknownStringsNeverReachReport() {
        val(e,t,m)=capture();val owner=Any()
        m.trace("prepare_sent",owner);now=500_000_000;m.trace("ack_ok",Any())
        assertEquals(1,m.pendingHandoffs)
        m.trace("https://private.invalid/SECRET?token=PRIVATE",owner);m.trace("ack_ok",owner)
        assertEquals(0,m.pendingHandoffs);m.close();e.stop(t);e.complete(t)
        val handoff=e.latest()!!.operationMetrics.first{it.operation==LabOperation.HANDOFF_ACK}
        assertEquals(1L,handoff.succeeded);assertEquals(500.0,handoff.latency!!.meanMs,0.0)
        assertFalse(TestLabReportGenerator.generate(e.latest()!!).json.contains("private.invalid"))
    }
    @Test fun fileLoadedAloneDoesNotProvePlaybackAndLegitimateNewFileIsNotUnexpectedReload() {
        val(e,t,m)=capture();val owner=Any()
        m.trace("mpv_start_file",owner);m.trace("mpv_file_loaded",owner)
        assertTrue(m.waitingForPlayback)
        m.trace("mpv_playback_started",owner);assertFalse(m.waitingForPlayback)
        m.trace("mpv_start_file",owner);m.trace("mpv_playback_started",owner)
        m.close();e.stop(t);e.complete(t)
        assertEquals(2L,e.latest()!!.operationMetrics.first{it.operation==LabOperation.MPV_PLAYBACK}.succeeded)
        assertNull(e.latest()!!.eventCounts[LabCode.MPV_UNEXPECTED_RELOAD])
    }
    @Test fun existingPausedPlayerHasSamplesButNoInventedProgressAndSeeksAreSeparate() {
        val(e,t,m)=capture();val owner=Any()
        m.position(owner,100.0);m.position(owner,100.0);m.position(owner,101.5)
        m.position(owner,500.0);m.position(owner,499.0);m.position(owner,Double.NaN)
        m.close();e.stop(t);e.complete(t)
        val playback=e.latest()!!.playbackMetrics!!
        assertEquals(5L,playback.positionSamples);assertEquals(1.5,playback.incrementalSeconds,0.0)
        assertEquals(2L,playback.discontinuities)
        assertNull(e.latest()!!.operationMetrics.first{it.operation==LabOperation.MPV_PLAYBACK}.latency)
        assertEquals(Availability.NOT_EXPOSED_BY_PLATFORM,e.latest()!!.metricAvailability[LabMetric.DUAL_AUDIO])
    }
    @Test fun oneDiscoveryBatchCountsCandidatesAndDoesNotCreateTwentyTimelineRows() {
        val(e,t,m)=capture();m.discovery("before_play",20);m.discovery("SECRET",100)
        m.close();e.stop(t);e.complete(t)
        assertEquals(20L,e.latest()!!.eventCounts[LabCode.DETECTOR_BEFORE_PLAY])
        assertEquals(1,e.latest()!!.timeline.size)
    }
    @Test fun oldMediaObserverCannotWriteIntoRestartedMeasurement() {
        val(e,t,m)=capture();val owner=Any();m.trace("prepare_sent",owner);e.stop(t);e.complete(t)
        val next=e.start(TestType.COMPLEX)!!;e.begin(next)
        m.trace("ack_ok",owner);m.position(owner,2.0);m.discovery("before_play",5);m.close()
        e.stop(next);e.complete(next)
        assertTrue(e.latest()!!.timeline.isEmpty());assertNull(e.latest()!!.playbackMetrics)
    }
    @Test fun fullscreenTraceFlagAloneDoesNotPretendThatTheWindowWasDrawn() {
        val(e,t,m)=capture();m.trace("inline_fullscreen",Any());m.close();e.stop(t);e.complete(t)
        assertEquals(1L,e.latest()!!.eventCounts[LabCode.MPV_FULLSCREEN])
        assertEquals(0L,e.latest()!!.operationMetrics.first{it.operation==LabOperation.MPV_FULLSCREEN}.succeeded)
    }
}
