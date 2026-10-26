package com.prirai.android.nira.diagnostics.testlab

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class TestLabReportTest {
    private var now=0L
    private fun sample():TestLabReport {
        val e=TestLabEngine({now},LabIdentity(1165,"0.5.22-alpha71-mpv39"))
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
        assertTrue(output.text.contains("MPV — BRAK PRÓBY ODTWARZANIA"))
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
        val safe=setOf("0.5.22-alpha71-mpv39")+
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
}
