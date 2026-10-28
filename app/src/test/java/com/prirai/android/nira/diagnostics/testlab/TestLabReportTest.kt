package com.prirai.android.nira.diagnostics.testlab

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class TestLabReportTest {
    private var now=0L
    private fun sample():TestLabReport {
        val e=TestLabEngine({now},LabIdentity(1168,"0.5.22-alpha73-clean"))
        val t=e.start(TestType.COMPLEX)!!;e.begin(t);e.foreground(t,true)
        now+=12_345_000_000;e.stop(t);e.complete(t);return e.latest()!!
    }
    @Test fun jsonIsVersionedTypedAndRoundTripsMissingData() {
        val report=sample();val output=TestLabReportGenerator.generate(report)
        val root=Json.parseToJsonElement(output.json).jsonObject
        assertEquals(1,root.getValue("schemaVersion").jsonPrimitive.int)
        assertTrue(root.getValue("frameMetrics") is JsonNull)
        assertEquals("NOT_OBSERVED",root.getValue("metricAvailability").jsonObject.getValue("ANDROID_UI_FRAMES").jsonPrimitive.content)
        assertEquals(report,Json.decodeFromString<TestLabReport>(output.json))
    }
    @Test fun noPanelOrPlaybackIsExplicitlyUntested() {
        val output=TestLabReportGenerator.generate(sample())
        assertTrue(output.text.contains("NIEPRZETESTOWANY"))
        assertTrue(output.text.contains("Zewnętrzny odtwarzacz:"))
        assertFalse(output.text.contains("FPS:"))
        assertFalse(output.text.contains("Brak błędów całej aplikacji"))
    }
    @Test fun partialReportUsesActualSecondsAndExplainsWindowAndProcessScope() {
        val output=TestLabReportGenerator.generate(sample())
        assertTrue(output.text.contains("12.345 s"))
        assertTrue(output.text.contains("PARTIAL"))
        assertTrue(output.text.contains("Android UI Window"))
        assertTrue(output.text.contains("własny proces"))
    }
    @Test fun onlyVersionAndClosedEnumValuesAreExportedAsStrings() {
        val root=Json.parseToJsonElement(TestLabReportGenerator.generate(sample()).json)
        val safe=setOf("0.5.22-alpha73-clean")+
            listOf(TestType.entries,LabPhase.entries,LabMetric.entries,LabOperation.entries,LabCode.entries,
                Availability.entries,MetricSource.entries,Coverage.entries,Limitation.entries,LabError.entries,
                LabInvariant.entries,ObservationKind.entries,CompletionProof.entries,PercentileMethod.entries,LabUnit.entries)
                .flatten().map{it.name}
        val forbidden=setOf("url","domain","host","ip","cookies","authorization","title","profileid","profilename","tabid","path","script","exception","stacktrace","message")
        fun check(node:JsonElement){when(node){
            is JsonObject->node.forEach{(key,value)->assertFalse(key.lowercase() in forbidden);check(value)}
            is JsonArray->node.forEach(::check)
            is JsonPrimitive->if(node.isString)assertTrue("Unexpected free-form value",node.content in safe)
        }}
        check(root)
    }
    @Test fun shadowGraphJsonAndTxtArePrivacyFilteredAndExplicitlyNonAuthoritative() {
        val graph=MediaIdentityShadowGraph()
        val summary=graph.snapshot()
        val report=sample().copy(testType=TestType.FULL_AUDIT,
            mediaIdentityShadow=summary)
        val output=TestLabReportGenerator.generate(report)
        assertEquals(summary,Json.decodeFromString<TestLabReport>(output.json).mediaIdentityShadow)
        assertTrue(output.text.contains("MEDIA IDENTITY"))
        assertTrue(output.text.contains("SHADOW MODE"))
        assertTrue(output.text.contains("UI/Play niezmienione"))
        assertTrue(output.text.contains("NOT_AVAILABLE"))
        assertFalse(output.json.contains("cookie"))
        assertFalse(output.json.contains("https://"))
    }
    @Test fun networkCorrelationReportIsAnonymousAndExplicitlyShadowOnly() {
        val binding=com.prirai.android.nira.media.MediaSessionBinding(Any(),"secret-tab","secret-profile",true)
        val entry=com.prirai.android.nira.media.MediaEntry(
            1,"https://secret.example/movie.mp4?token=private",
            com.prirai.android.nira.media.MediaFormat.MP4,
            setOf(com.prirai.android.nira.media.MediaSource.DOM,
                com.prirai.android.nira.media.MediaSource.NETWORK),frameKey=7)
        val ledger=MediaNetworkCorrelationLedger()
        ledger.observe(binding,entry)
        val summary=ledger.snapshot()
        val report=sample().copy(testType=TestType.FULL_AUDIT,
            mediaNetworkCorrelation=summary)
        val output=TestLabReportGenerator.generate(report)
        assertEquals(summary,
            Json.decodeFromString<TestLabReport>(output.json).mediaNetworkCorrelation)
        assertTrue(output.text.contains("MEDIA NETWORK CORRELATION"))
        assertTrue(output.text.contains("SHADOW MODE"))
        assertTrue(output.text.contains("NIEŁĄCZONE"))
        for(secret in listOf("secret.example","token=private","secret-profile","secret-tab"))
            assertFalse(output.json.contains(secret))
    }
    @Test fun boundedExportPreservesAggregatesAndMarksOmittedTimeline() {
        val report=sample().copy(timeline=(1L..1024L).map{LabEvent(it,it*1000,LabCode.SLOW_FRAME,
            value=300.0,operationTags=LabOperation.entries.toList())},eventCounts=mapOf(LabCode.SLOW_FRAME to 1024L),
            anomalyMarkers=listOf(AnomalyMarker(1_024_000,(1L..1024L).toList())))
        val output=TestLabReportGenerator.generate(report,32_768)
        assertTrue(output.json.toByteArray(Charsets.UTF_8).size<=32_768)
        assertTrue(output.text.toByteArray(Charsets.UTF_8).size<=32_768)
        val decoded=Json.decodeFromString<TestLabReport>(output.json)
        assertTrue(decoded.exportTruncated)
        assertTrue(decoded.timeline.size<1024)
        assertEquals(1024L,decoded.eventCounts[LabCode.SLOW_FRAME])
        val retained=decoded.timeline.map{it.serial}.toSet()
        assertTrue(decoded.anomalyMarkers.all{marker->marker.nearbyEventSerials.all{it in retained}})
        assertTrue(output.text.contains("OGRANICZONY"))
    }
    @Test fun confirmedInvariantAppearsBeforePerformanceCorrelationInTopFindings() {
        val report=sample().copy(anomalies=listOf(
            Observation(ObservationKind.OBSERVED_PERFORMANCE_ANOMALY,LabCode.MAIN_STALL,1000,500.0),
            Observation(ObservationKind.CONFIRMED_INVARIANT_FAILURE,LabCode.INVARIANT_FAILURE,2000,invariant=LabInvariant.ORDER_NOT_PERSISTED)))
        val output=TestLabReportGenerator.generate(report)
        assertTrue(output.text.indexOf("CONFIRMED_INVARIANT_FAILURE")<output.text.indexOf("OBSERVED_PERFORMANCE_ANOMALY"))
        assertTrue(output.text.contains("korelacja"))
    }

    @Test fun panelPerformanceSectionDoesNotInvent120HzOrJankWhenNoFrames() {
        val base=sample().copy(testType=TestType.PANELS_GESTURES)
        val output=TestLabReportGenerator.generate(base).text
        assertTrue(output.contains("Cel 120 Hz: 8.333 ms"))
        assertTrue(output.contains("NIEPOTWIERDZONY"))
        assertTrue(output.contains("JankStats: NIEPRZETESTOWANY"))
        assertTrue(output.contains("LEFT_PANEL_SETTLE: NIEPRZETESTOWANY"))
        assertTrue(output.contains("RIGHT_PANEL_SETTLE: NIEPRZETESTOWANY"))
        assertFalse(output.contains("FPS:"))
    }

    @Test fun panelPerformanceSectionUsesActualRefreshAndExistingWindowSamplesOnly() {
        val base=sample()
        val frames=FrameAccumulator().apply {
            addMeasured(8_000_000,false,0,false)
            addMeasured(16_000_000,true,0,false)
        }.snapshot()
        // The initial sample has no panel gestures, so fabricate one explicit
        // anonymous completed/interrupt pair rather than mapping an empty list.
        val ops=base.operationMetrics.filterNot {
            it.operation == LabOperation.LEFT_PANEL_SETTLE
        } + OperationSummary(
            operation=LabOperation.LEFT_PANEL_SETTLE,
            started=2, accepted=0, succeeded=1, cancelled=0, failed=0,
            timedOut=0, interrupted=1, unknown=0,
            latency=LatencySummary(1,196.0,196.0,null),
            taggedFrames=2, taggedJank=1, maxTaggedFrameMs=16.0,
            completionProofs=mapOf(CompletionProof.ANIMATOR_END to 1L)
        )
        val output=TestLabReportGenerator.generate(base.copy(
            environment=LabEnvironment(35,8,listOf(60.0,90.0,119.88),119.88,120.0),
            frameMetrics=frames,operationMetrics=ops
        ))
        assertTrue(output.text.contains("Tryb odczytany: 119.880 Hz"))
        assertTrue(output.text.contains("budżet cyklu: 8.342 ms"))
        assertEquals(1, ops.count { it.operation == LabOperation.LEFT_PANEL_SETTLE })
        assertTrue(output.text.contains("LEFT_PANEL_SETTLE: próby=2; ukończone=1"))
        assertTrue(output.text.contains("jank w oknie operacji=1/2"))
        assertTrue(output.text.contains("RIGHT_PANEL_SETTLE: NIEPRZETESTOWANY"))
        assertFalse(output.text.contains("FPS:"))
        assertFalse(output.json.contains("https://"))
    }

    @Test fun generalVideoDiscoveryReportDoesNotAddUnrelatedPanelBudgetSection() {
        val output=TestLabReportGenerator.generate(sample().copy(testType=TestType.VIDEO_DISCOVERY))
        assertFalse(output.text.contains("PANELE — płynność animacji"))
    }

}
