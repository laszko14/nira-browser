package com.prirai.android.nira.diagnostics.testlab

import java.util.Locale
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One immutable, privacy-filtered snapshot feeds both representations. No storage or sharing here. */
data class TestLabReportBundle(val report: TestLabReport, val text: String, val json: String)

object TestLabReportGenerator {
    private val codec = Json { encodeDefaults = true; explicitNulls = true }
    const val MAX_BYTES = 262_144

    fun generate(input: TestLabReport, maxBytes: Int = MAX_BYTES): TestLabReportBundle {
        require(maxBytes in 32_768..MAX_BYTES)
        var report = input.copy(anomalies = input.anomalies.sortedWith(
            compareBy<Observation> { it.kind.ordinal }.thenBy { it.offsetNanos ?: Long.MAX_VALUE }))
        while (true) {
            val json = codec.encodeToString(report)
            val text = render(report)
            if (json.toByteArray(Charsets.UTF_8).size <= maxBytes && text.toByteArray(Charsets.UTF_8).size <= maxBytes)
                return TestLabReportBundle(report, text, json)
            val remaining = report.timeline.drop((report.timeline.size + 1) / 2)
            val serials = remaining.mapTo(HashSet()) { it.serial }
            report = when {
                report.timeline.isNotEmpty() -> report.copy(timeline = remaining,
                    anomalyMarkers = report.anomalyMarkers.map { marker ->
                        marker.copy(nearbyEventSerials = marker.nearbyEventSerials.filter { it in serials }) },
                    exportTruncated = true, limitations = (report.limitations + Limitation.EXPORT_TRUNCATED).distinct())
                report.resourceMetrics.samples.isNotEmpty() -> report.copy(resourceMetrics = report.resourceMetrics.copy(
                    samples = report.resourceMetrics.samples.drop((report.resourceMetrics.samples.size + 1) / 2)),
                    exportTruncated = true, limitations = (report.limitations + Limitation.EXPORT_TRUNCATED).distinct())
                else -> error("Bounded report aggregates exceed export limit")
            }
        }
    }

    private fun number(value: Double?): String = value?.let { String.format(Locale.ROOT, "%.3f", it) } ?: "BRAK DANYCH"
    private fun render(r: TestLabReport): String = buildString {
        appendLine("Zavro TestLab • ${r.testType} • ${r.appVersion.versionName} (${r.appVersion.versionCode})")
        appendLine("Stan: ${r.status}; rzeczywisty pomiar: ${number(r.durationMs / 1000)} s")
        appendLine("Opóźnienie finalizacji poza logicznym pomiarem: ${number(r.finalizationDelayMs)} ms")
        appendLine("Okno na pierwszym planie: ${number(r.foregroundMs)} ms; brak pokrycia: ${number(r.noCoverageMs)} ms")
        appendLine("COMPLETED oznacza zakończenie pomiaru, nie potwierdzenie sprawności całej aplikacji.")
        if(r.testType==TestType.FULL_AUDIT) appendLine("JEDEN TEST / DWA MINUTY: równoległe kolektory UI, kart, GeckoView, wideo, zasobów i stabilności; sprawdzono tylko wykonane czynności. Metadane DOM/JSON-LD/manifests odczytywane lokalnie przez detektor, raport bez treści stron i adresów.")
        if (r.exportTruncated) appendLine("EKSPORT OGRANICZONY: pominięto starsze szczegóły; agregaty zachowane.")
        appendLine("\nNajważniejsze obserwacje (korelacja czasowa nie dowodzi przyczyny):")
        if (r.anomalies.isEmpty()) appendLine("BRAK ZAREJESTROWANYCH OBSERWACJI; brak próby nie oznacza poprawności.")
        r.anomalies.take(3).forEach {
            appendLine("${it.kind}: ${it.code} ${it.invariant ?: ""} ${it.operation ?: ""}; t=${number(it.offsetNanos?.div(1_000_000.0))} ms; wartość=${number(it.value)} ${it.unit ?: ""}; próg=${number(it.threshold)}, n=${it.evidenceCount ?: "BRAK DANYCH"}")
            appendLine(when(it.kind){
                ObservationKind.CONFIRMED_INVARIANT_FAILURE->"Dowód porównania stanu po operacji; wymaga odtworzenia scenariusza, nie automatycznej naprawy danych."
                ObservationKind.OBSERVED_PERFORMANCE_ANOMALY->"Zmierzony czas przekroczył jawny próg diagnostyczny. Bez niezależnego pomiaru nie wskazuje przyczyny."
                ObservationKind.SUSPECTED_CORRELATION->"Zaobserwowany sygnał, nie ustalona przyczyna. Wzrost pamięci nie dowodzi wycieku."
                ObservationKind.INSUFFICIENT_DATA->"Za mało danych do oceny."
                ObservationKind.NOT_TESTED->"Nie zaobserwowano wymaganej próby."
            })
        }
        appendLine("\nKlatki Android UI Window (JankStats):")
        appendLine("Liczba klatek jest sumą próbek przypiętych Window; nie oznacza unikalnych klatek ekranu ani FPS urządzenia.")
        appendLine("To nie klatki kompozytora Gecko ani odtwarzania wideo. PopupWindow menu nie udostępnia Window dla JankStats i nie jest objęty pomiarem klatek.")
        r.frameMetrics?.let { f ->
            appendLine("n=${f.count}, jank=${f.jankCount} (${number(f.jankPercent)}%), średnia=${number(f.meanMs)} ms, max=${number(f.maxMs)} ms")
            appendLine("P50=${number(f.p50Ms)}, P90=${number(f.p90Ms)}, P95=${number(f.p95Ms)}, P99=${number(f.p99Ms)} ms; histogram górnej granicy 0.25 ms")
            appendLine("Czas CPU klatki z JankStats (nie CPU procesu): n=${f.cpuSamples}, średnia=${number(f.meanCpuMs)}, max=${number(f.maxCpuMs)} ms")
            appendLine("Przekroczenie budżetu: n=${f.overrunSamples}, średnia=${number(f.meanOverrunMs)}, max=${number(f.maxOverrunMs)} ms")
        } ?: appendLine("NIEPRZETESTOWANY: brak prawidłowych próbek klatek.")
        appendLine("Obsługiwane tryby Hz: ${r.environment.refreshRatesHz}; aktualny: ${r.environment.currentRefreshHz ?: "BRAK DANYCH"}; żądany: ${r.environment.requestedRefreshHz ?: "BRAK DANYCH"}")
        appendLine("\nŹródła i dostępność:")
        LabMetric.entries.forEach { appendLine("$it: ${r.metricAvailability[it]} / ${r.metricSources[it]}") }
        appendLine("FIRST_PREDRAW potwierdza widoczny układ, nie koniec animacji okna systemowego. Tego końca nie zmierzono.")
        appendLine("PROCESS_EXIT oznacza odczyt historycznej przyczyny zakończenia własnego procesu, nie awarię podczas tego testu.")
        appendLine("\nOperacje: START ≠ ACCEPT ≠ zakończenie. Anulowanie gestu nie jest błędem.")
        r.testCoverage.forEach { (operation, coverage) ->
            appendLine("$operation: ${if (coverage == Coverage.NOT_TESTED) "NIEPRZETESTOWANY" else coverage}")
        }
        r.operationMetrics.forEach { o ->
            appendLine("${o.operation}: start=${o.started}, accept=${o.accepted}, sukces=${o.succeeded}, anulowane=${o.cancelled}, błędy=${o.failed}, timeout=${o.timedOut}, przerwane=${o.interrupted}, nieznane=${o.unknown}")
            o.latency?.let { appendLine("Czas tylko zakończonych prób: n=${it.samples}, średnia=${number(it.meanMs)}, max=${number(it.maxMs)}, P95=${number(it.p95Ms)} ms") }
            appendLine("Dowód zakończenia: ${o.completionProofs}; skorelowane klatki=${o.taggedFrames}, jank=${o.taggedJank}, max=${number(o.maxTaggedFrameMs)} ms")
        }
        appendLine("Zewnętrzny odtwarzacz: zarejestrowane żądanie otwarcia nie dowodzi rozpoczęcia odtwarzania.")
        if(r.testType in setOf(TestType.VIDEO_DISCOVERY,TestType.FULL_AUDIT)) {
            appendLine("\nVIDEO DISCOVERY — anonimowa korelacja:")
            r.videoDiscovery?.let { v ->
                appendLine("Zaobserwowane źródła: ${v.sources}; grupy: ${v.groups}; grupy wielowariantowe: ${v.groupsWithVariants}")
                appendLine("Brak tytułu: ${v.groupsWithoutTitle}; niepewne grupy: ${v.uncertainGroups}; konflikty grup: ${v.groupTitleConflicts}")
                appendLine("Pierwsze tytuły: ${v.initialTitles}; ulepszenia: ${v.improvements}; konflikty/odrzucone alternatywy: ${v.rejectedOrConflictingTitles}; słabsze odrzucone: ${v.weakerRejected}")
                appendLine("Przekazane panelowi źródła: ${v.uiObservedSources}; z indywidualnym tytułem: ${v.uiObservedTitledSources}; pozostałe: ${v.uiObservedSources-v.uiObservedTitledSources} (nie dowodzi narysowania kafelka)")
                appendLine("Grupy przekazane panelowi: ${v.uiObservedGroups}; z jednoznacznym tytułem: ${v.uiObservedTitledGroups}; bez jednoznacznego tytułu: ${v.uiObservedGroups-v.uiObservedTitledGroups}; konfliktowe: ${v.uiObservedConflictedGroups} (nie dowodzi narysowania kafelków)")
                appendLine("Nawigacje: ${v.navigations}; odrzucone przez limit ewidencji: ${v.droppedSources}; pochodzenie: ${v.titleKinds}; formaty: ${v.formats}; kanały wykrycia: ${v.detectionOrigins}")
                v.examples.forEach { appendLine("Film #${it.film} | warianty=${it.variants} | tytuł=${it.titleSource} | ${it.status} | grupa niepewna=${it.groupingUncertain}") }
                if(v.groups>v.examples.size) appendLine("Pominięto szczegóły ${v.groups-v.examples.size} grup, zachowano agregaty.")
                appendLine("Zaobserwowana metadana ≠ zweryfikowana zgodność tytułu z treścią filmu.")
            } ?: appendLine("NOT_MEASURED — nie zaobserwowano zaakceptowanych rekordów.")
            appendLine("DOM, iframe, SPA i konfiguracje — liczniki zdarzeń:")
            listOf(LabCode.DETECTOR_SCAN_ELEMENTS,LabCode.DETECTOR_SCAN_YIELD,
                LabCode.DETECTOR_IFRAME,LabCode.DETECTOR_FRAME_REGISTERED,
                LabCode.DETECTOR_SPA_NAVIGATION,LabCode.DETECTOR_PLAYER_CONFIG,
                LabCode.DETECTOR_NO_CANDIDATE,LabCode.DETECTOR_UNAVAILABLE,
                LabCode.DETECTOR_HLS,LabCode.DETECTOR_DASH,LabCode.DETECTOR_MP4,
                LabCode.DETECTOR_EARLY_REQUEST,LabCode.DETECTOR_EARLY_BOUND,
                LabCode.DETECTOR_FRAME_MISMATCH).forEach { appendLine("$it=${r.eventCounts[it] ?: 0}") }
            appendLine("Limity, odrzucenia i próby ponowienia według mechanizmu:")
            listOf(LabCode.DETECTOR_QUEUE_NATIVE_CAPACITY,LabCode.DETECTOR_QUEUE_NATIVE_STALE,
                LabCode.DETECTOR_QUEUE_NATIVE_RETRY,LabCode.DETECTOR_QUEUE_NATIVE_REJECT,
                LabCode.DETECTOR_QUEUE_DOM_ROOTS,LabCode.DETECTOR_QUEUE_EARLY_EVICT,
                LabCode.DETECTOR_QUEUE_NETWORK_RATE,LabCode.DETECTOR_QUEUE_MANIFEST_CAPACITY,
                LabCode.DETECTOR_QUEUE_ANDROID_RATE,LabCode.DETECTOR_QUEUE_TITLE_CANDIDATE_DROPPED).forEach { appendLine("$it=${r.eventCounts[it] ?: 0}") }
            appendLine("QUEUE_LIMIT jest sumą wielu przyczyn. Odmowa wysłania ≠ potwierdzona utrata tytułu; ograniczenia webRequest nie pozwalają przypisać wszystkich zdarzeń do filmu.")
        }
        if(r.testType in setOf(TestType.VIDEO_DISCOVERY,TestType.VIDEO_METADATA,TestType.FULL_AUDIT)) {
            appendLine("\nMME NEXTGEN — liczniki przyczyn; to zdarzenia, nie unikalne filmy:")
            listOf(LabCode.MME_TITLE_NO_LABEL, LabCode.MME_TITLE_NO_DIRECT_SOURCE, LabCode.MME_TITLE_NO_OWNER, LabCode.MME_TITLE_IPC_REJECTED, LabCode.MME_TITLE_NATIVE_REJECTED, LabCode.MME_MANIFEST_FETCH_FAILED, LabCode.MME_MANIFEST_AUTH_FAILED, LabCode.MME_MANIFEST_TOO_LARGE, LabCode.MME_MANIFEST_BODY_UNAVAILABLE, LabCode.MME_MANIFEST_PARSE_FAILED, LabCode.MME_MANIFEST_VALID_MASTER, LabCode.MME_MANIFEST_VALID_MEDIA, LabCode.MME_MANIFEST_NO_TECHNICAL, LabCode.MME_MANIFEST_KIB, LabCode.MME_SCAN_SLICE_MS, LabCode.MME_SCAN_ROOT).forEach { appendLine("$it=${r.eventCounts[it] ?: 0}") }
            appendLine("Konflikty rozdzielczości dla tego samego źródła=${r.eventCounts[LabCode.MME_RESOLUTION_CONFLICT] ?: 0}; konflikt → UNKNOWN.")
            appendLine("Miniatury: powodzenie ładowania=${r.eventCounts[LabCode.MME_POSTER_LOADED] ?: 0}; błąd ładowania=${r.eventCounts[LabCode.MME_POSTER_FAILED] ?: 0}; callback loadera nie potwierdza poprawności miniatury ani wszystkich pikseli.")
            appendLine("MANIFEST_KIB i SCAN_SLICE_MS są sumami jednostek diagnostycznych; zero nie dowodzi poprawności rozpoznawania metadanych.")
        }
        if(r.testType in setOf(TestType.VIDEO_METADATA,TestType.FULL_AUDIT)) {
            appendLine("\nVIDEO METADATA — anonimowe obserwacje zaakceptowanych filmów:")
            r.videoMetadata?.let { v ->
                appendLine("Źródła=${v.observedSources}; czas znany=${v.durationKnown}; czas nieznany=${v.durationUnknown}; LIVE=${v.live}")
                appendLine("Rozmiar dokładny=${v.sizeExact}; szacowany=${v.sizeEstimated}; nieznany=${v.sizeUnknown}")
                appendLine("Tytuły powiązane=${v.titlesAssociated}; fallback=${v.titlesFallback}; konflikty=${v.titleConflicts}")
                appendLine("Miniatury przypisane=${v.postersAssociated}; rozdzielczość znana=${v.resolutionKnown}; jakość znana=${v.qualityKnown}; z samej rozdzielczości=${v.qualityDerived}")
                appendLine("FPS=${v.fpsKnown}; kodeki=${v.codecsKnown}; bitrate=${v.bitrateKnown}; wiele wariantów=${v.variantsKnown}")
                appendLine("Pochodzenie miniaturek=${v.posterOrigins}; sprzeczne miniatury o tej samej sile=${v.posterConflicts}")
                appendLine("Pochodzenie=deklaracja, a nie dowód poprawności obrazu. Konflikt → UNKNOWN; brak miniatury nie oznacza błędu odtwarzania.")
                appendLine("Miniatura przypisana nie oznacza załadowanej miniatury; jakość z rozdzielczości nie potwierdza przełączania wariantów.")
                appendLine("Kafelki w otwartym panelu=${v.uiDisplayed}; aktualizacje=${v.updates}; nawigacje=${v.navigations}; limit ewidencji=${v.droppedSources}")
                v.examples.forEach {
                    appendLine("Film #${it.film} | format=${it.format} | czas=${it.durationSeconds ?: "BRAK"} s (${it.durationStatus}/${it.durationSource}) | rozmiar=${it.sizeBytes ?: "BRAK"} B (${it.sizeStatus}/${it.sizeSource}) | panel=${it.uiDisplayed} | aktualizacje=${it.updates}")
                }
                if(v.observedSources>v.examples.size)
                    appendLine("Pominięto szczegóły ${v.observedSources-v.examples.size} źródeł; zachowano agregaty.")
                if(v.observedSources==0)
                    appendLine("Nie zaobserwowano zaakceptowanych rekordów — liczniki rozszerzenia nie zastępują repozytorium.")
                appendLine("Widoczność w panelu nie dowodzi wyrenderowania piksela ani odtworzenia filmu.")
            } ?: appendLine("NOT_MEASURED — kolektor metadanych nie zwrócił snapshotu.")
        }
        if(r.testType in setOf(TestType.FULL_AUDIT,TestType.VIDEO_DISCOVERY,TestType.VIDEO_METADATA)) {
            appendLine("\nMEDIA FUNNEL — anonimowe zdarzenia native, bez dowodu poprawności materiału:")
            r.mediaFunnel?.let { funnel ->
                appendLine("Etapy: ${funnel.stages} (CANDIDATE=próby zaakceptowane w repo; DISCOVERED/STORED=unikalne rekordy, także baza sprzed testu)")
                appendLine("ASSOCIATED=indywidualny tytuł lub miniatura przy rekordzie, bez niezależnego potwierdzenia zgodności filmu.")
                appendLine("VERIFIED=zweryfikowany manifest/strumień lub potwierdzony stan źródła; DISPLAYED=przekazano panelowi, nie wyrenderowano pikseli.")
                appendLine("Braki: ${funnel.unknownReasons}")
                appendLine("Konflikty: tytuł=${funnel.titleConflicts}; różne miniatury=${funnel.posterAlternatives}; rozbieżne grupy wariantów=${funnel.variantGroupMismatches}; limit=${funnel.droppedRecords}")
                appendLine("Powody zmian groupId: NETWORK wobec DOM/PAGE=${funnel.variantGroupDomNetwork}; pozostale=${funnel.variantGroupOther}; zdarzenia, nie dowod blednego scalenia.")
                appendLine("Rozszerzone sondy sieciowe: ${if(funnel.extendedNetworkProbesEnabled) "ON" else "OFF"}; liczba prób=${funnel.probeRequests ?: "NOT_MEASURED"}, bajty=${funnel.probeBytes ?: "NOT_MEASURED"}, czas=${funnel.probeElapsedMs ?: "NOT_MEASURED"} ms")
                appendLine("Brak pomiarów sieciowych oznacza NOT_MEASURED, a nie zero żądań ani zero kosztu.")
            } ?: appendLine("NOT_MEASURED — brak snapshotu kolektora.")
        }
        if (r.testType in setOf(TestType.FULL_AUDIT,TestType.VIDEO_DISCOVERY,TestType.VIDEO_METADATA)) {
            appendLine("\nMEDIA IDENTITY — SHADOW MODE (bez wpływu na kafelki i Play):")
            r.mediaIdentityShadow?.let { g ->
                appendLine("Shadow=ON, UI/Play niezmienione; źródła=${g.sourceNodes}, kandydaci na filmy=${g.filmNodes}, grupy wieloźródłowe=${g.groupedFilms}, prowizoryczne krawędzie wariantów=${g.provisionalVariantEdges}")
                appendLine("Konflikty indywidualnych tytułów w grupach=${g.groupsWithConflictingTitles}; niepewni właściciele=${g.groupsWithAmbiguousOwners}; grupy bez indywidualnego tytułu=${g.groupsWithoutIndividualTitles}")
                appendLine("Wskazówki manifestu bez powiązanych źródeł=${g.manifestVariantHintsWithoutLinkedSources}; zbieżne ID grup w różnych ramkach (niełączone)=${g.crossFrameGroupIdCollisions}")
                appendLine("Zmiany grupy źródła=${g.sourceGroupReassignments}; zmiany ramki=${g.sourceFrameReassignments}; reset partycji=${g.partitionResets}; epoki nawigacji=${g.navigationEpochs}; odrzucone z limitu=${g.droppedSources}")
                appendLine("Porównane panele=${g.panelSnapshotsCompared}; rozbieżna liczba grup shadow/UI=${g.panelGroupCountDisagreements}; podejrzane grupy w panelach=${g.panelShadowUnsafeGroups}")
                appendLine("Audio/subtitle track linkage: ${if(g.trackLinkageAvailable) "SUPPORTED" else "NOT_AVAILABLE"}; nie dopisuj niezaobserwowanych ścieżek.")
                appendLine("To są hipotezy powiązań z zaakceptowanych rekordów natywnych, nie potwierdzona identyczność filmu; kolizje nazw i brak ramki nie są automatycznie naprawiane.")
            } ?: appendLine("NOT_MEASURED — brak snapshotu shadow graph.")
        }
        if (r.testType in setOf(TestType.FULL_AUDIT,TestType.VIDEO_DISCOVERY,TestType.VIDEO_METADATA)) {
            appendLine("\nMEDIA NETWORK CORRELATION — SHADOW MODE (bez ingerencji w Play):")
            r.mediaNetworkCorrelation?.let { g ->
                appendLine("Rekordy native=${g.observedRecords}; DOM/PAGE+NETWORK w jednym rekordzie=${g.networkAndDomOnOneRecord}; ze znaną ramką=${g.trustedFrameCoObserved}; z indywidualnymi metadanymi=${g.coObservedWithIndividualMetadata}")
                appendLine("Tylko sieć=${g.networkOnlyRecords}; tylko DOM/PAGE=${g.domOrPageOnlyRecords}; sieć bez zaufanej ramki=${g.networkWithoutTrustedFrame}")
                appendLine("Ten sam pełny URL, inne ramki (NIEŁĄCZONE)=${g.exactUrlCrossFramePairs}; nieznana ramka (NIEŁĄCZONE)=${g.exactUrlUnknownFramePairs}")
                appendLine("Jedno ID grupy i różne pełne URL (hipotezy wariantów)=${g.sameGroupDifferentUrlPairs}; identyczny URL w różnych formatach=${g.exactUrlDifferentFormatPairs}")
                appendLine("Nawigacje=${g.navigationEpochs}; resety partycji=${g.partitionResets}; limit=${g.droppedRecords}; własne żądania sieciowe=${g.performedNetworkRequests}; zmienia Play/UI=${g.changesMediaOrPlayback}")
                appendLine("Wspólny adres lub grupa nie są dowodem, że chodzi o ten sam film. Brak pewnego ownera oznacza brak przypisania tytułu lub miniatury.")
            } ?: appendLine("NOT_MEASURED — brak danych korelacji.")
        }
        appendLine("\nZasoby: własny proces aplikacji; podprocesy Gecko nie są objęte pomiarem.")
        val m = r.resourceMetrics
        appendLine("Próbki=${m.totalSamples}; odrzucone ze starszego bufora=${m.evictedSamples}")
        appendLine("Java delta=${m.javaHeapDeltaBytes}, max=${m.javaHeapMaxBytes}; native max=${m.nativeHeapMaxBytes}; PSS max=${m.pssMaxBytes}; USS max=${m.ussMaxBytes} B")
        appendLine("Zmiany niezależne: native=${m.nativeHeapDeltaBytes}, PSS=${m.pssDeltaBytes}, USS=${m.ussDeltaBytes} B")
        appendLine("Pamięci nie sumujemy; wzrost pamięci w trakcie pomiaru nie dowodzi wycieku.")
        appendLine("CPU ważone czasem: ${number(m.cpuMeanOneCorePercent)}% jednego rdzenia (może przekroczyć 100%); max=${number(m.cpuMaxOneCorePercent)}%; czas CPU=${m.cpuDeltaMs} ms / ścienny=${number(m.cpuObservedWallMs)} ms")
        appendLine("Heartbeat głównego Looper: n=${r.heartbeatSamples}, max=${number(r.maxHeartbeatDelayMs)} ms. Opóźnienie nie jest potwierdzeniem ANR.")
        appendLine("Oczekująca wiadomość Main: n=${r.pendingHeartbeatSamples}, max wiek=${number(r.maxPendingHeartbeatMs)} ms; nie jest ACK ani potwierdzeniem ANR.")
        appendLine("Błędy: ${r.errorCounts}; nieukończone operacje=${r.incompleteOperations}; pominięte operacje=${r.droppedOperations}")
        appendLine("\nAgregaty zdarzeń:")
        r.eventCounts.forEach { (code, count) -> appendLine("$code=$count") }
        appendLine("\nZnaczniki użytkownika (czas zgłoszenia, kontekst ±3 s):")
        r.anomalyMarkers.forEach { appendLine("${number(it.offsetNanos / 1_000_000.0)} ms: ${it.nearbyEventSerials}") }
        appendLine("\nOś czasu (względna monotoniczna; ograniczona; wcześniejsze usunięte=${r.evictedEvents}):")
        r.timeline.forEach { appendLine("#${it.serial} ${number(it.offsetNanos / 1_000_000.0)} ms ${it.code} ${it.operation ?: ""} ${it.value ?: ""} ${it.operationTags}") }
        appendLine("\nOgraniczenia: ${r.limitations}")
        appendLine("Brak bezpośredniego pomiaru klatek kompozytora Gecko, obrazu wideo ani dokładnego zużycia energii. Brak awarii w tym teście nie dowodzi stabilności.")
    }
}
