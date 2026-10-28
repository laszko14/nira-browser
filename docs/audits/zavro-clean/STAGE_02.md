# Zavro Clean — Audyt 2/18

Status etapu: **PARTIAL**. Podetap **2A COMPLETE**; **2B NOT_STARTED**. Data: 2026-10-28 UTC. Jeden agent; wyłącznie analiza i dokumentacja, bez napraw/APK/nowych testów aplikacji.

## Punkt startu i granica podetapu

- Gałąź audytu: `audit/zavro-clean-20261028`; wejściowy HEAD dokumentacji `3777a62574fd9cf992ec7ecbb4b06abfc49f3e4b`, working tree czysty; ten sam SHA potwierdzono na serwerze.
- Baza aplikacji pozostaje `feat/zavro-clean-side-panels-1dm-20261028` @ `b7d62f79f285ac81c29a79b326529d42e9b9fde4` (Clean v1190). Nie pobierano nowej linii funkcjonalnej i nie zmieniono bazy na main.
- Odczytano AGENTS.md v1.5, `.github/agents.md`, właściwy checkpoint i mapę etapu 1. Brak otwartych podetapów etapu 1; nie powtórzono inwentaryzacji PR-ów/gałęzi.
- Podział wynika z dwóch rozległych przepływów: **2A — proces/Gecko/rozszerzenie i cykl życia UI**, **2B — BrowserStore/Room/DataStore, karty, grupy, profile i ich współbieżność**. W 2A wejścia do drugiego przepływu rozpoznano tylko jako zależności; nie oceniono jeszcze jego poprawności.

Pytania kontrolne 2A: kto tworzy runtime/store i na jakim wątku; kto jest właścicielem obserwatorów; jak JS uzgadnia dokument przed przekazaniem źródła; co chroni granice sesji/profilu; jak UI czyta snapshot; czy zwalnianie zasobów widoku odpowiada ich rejestracji; jakie założenia o kolejności i generacjach pozostają niezweryfikowane.

Kryteria ukończenia 2A: prześledzony przepływ od Application do Gecko i IPC oraz od repozytorium do UI, zestawione cykle życia i punkty cleanup, rozdzielone dowody i podejrzenia, selektywna weryfikacja lub jawne NOT_TESTED, dokumentacja i checkpoint wskazujące dokładnie 2B. Ukończenie 2A nie zamyka etapu 2.

## Mapa przepływu i własności

Ścieżki Kotlin poniżej są względne do `app/src/main/java/com/prirai/android/nira/`. Numery linii odnoszą się do bazy kodu, nie commitów dokumentacyjnych.

```text
BrowserApp → Components → GeckoRuntime / GeckoEngine
                      └→ BrowserStore + middleware → SessionFeature → EngineView
BrowserApp → MediaDetectorController → wbudowane rozszerzenie
background ↔ content → native MessageHandler → MediaMessageProtocol → MediaRepository
BrowserStore (wybrana sesja/dokument) + MediaRepository.revision → MediaFloatingUi
BrowserFragment → SidePanelInfrastructure → child Fragmenty / Compose / RecyclerView
```

| Obiekt / zasób | Właściciel i czas życia | Wątek / synchronizacja | Dowód i granica wniosku |
| --- | --- | --- | --- |
| Components, runtime, engine, store | BrowserApp / proces główny; lazy singletony grafu | Engine jest wymuszony na Main **przed** IO restore | `BrowserApp.kt:64–70`, `:126`, `:140`; `Components.kt:292`, `:361`, `:683–719`. Podejrzenie inicjalizacji GeckoRuntime na IO zostało odrzucone po odczytaniu `warmUp()` |
| Odtworzenie i AutoSave | applicationScope, nie scope Activity | IO: odczyt/restore; przejście na Main przy podpinaniu AutoSave | `BrowserApp.kt:388–404`; nie przeprowadzono ponownego uruchomienia aplikacji ani kontroli atomowości zapisu (2B/10) |
| Bootstrap efektów store | Components, flaga `storeBootstrapped` | Wywołanie z Main; włącza ikony, notyfikacje, MediaSessionFeature i sleeping tabs | `Components.kt:487–525`, `BrowserApp.kt:157–178`. Idempotencja zakłada wywołania na Main. W razie wyjątku w połowie flaga pozostaje true; nie wykazano osiągalnego błędu tego typu i nie zgłoszono usterki |
| MediaDetectorController i jego repozytorium | Proces; instalacja z BrowserApp, referencje przez handlery/collector i udostępniony repository | `flowScoped(Dispatchers.Main)`, natywne callbacki; mutacje repozytorium pod monitorem | `media/MediaDetectorController.kt:22–58`, `:74–135`; nie jest to obiekt Activity. Brak `stop()` sam w sobie nie dowodzi wycieku zasobów widoku |
| Mapy attached/rate i stan mediów | Klucze rzeczywistych EngineSession; repository dodatkowo wiąże tabId/contextId/private | Mapy kontrolera bez własnego monitora — założenie callbacków Gecko na UI; repository ma `@Synchronized` | Kontroler `:104–120`; repozytorium `:261–325`. Usunięte/zastąpione sesje wypadają z map i scopes. Zachowania rejestru handlerów wewnątrz AC po close nie zmierzono |
| UI repository i revision | Jeden MediaRepository procesu; StateFlow publikuje referencję i rewizję | Natywny stan sesji pochodzi z BrowserStore, nie z JS ani z wybranej karty odgadywanej w handlerze | Kontroler `:232–236`, repozytorium `:262–264`; to dwa strumienie z różnymi rolami, nie dwa repozytoria mediów |
| SessionFeature i pozostałe features widoku | `ViewBoundFeatureWrapper`, owner Fragment + przekazany View | Integracja z cyklem życia AC | `BaseBrowserFragment.kt:119–148`, `:367–377`; `_binding` i activityContext zerowane w `onDestroyView:1212–1221`. Nie wykonano testu odtwarzania widoku na realnych bibliotekach |
| Toolbar, ViewModelStore i collectory paska | Właściciel `viewLifecycleOwner` przekazany przez BrowserFragment | Scope Main paska anulowany, modelStore czyszczony na destroy właściciela | `BrowserFragment.kt:670–681` → `UnifiedToolbar.kt:388`; `ComposeTabBarWithProfileSwitcher.kt:91–92`, `:158–162`. Stan i zapisy TabViewModel są zakresem 2B |
| Integracje adresu i reload/stop | UnifiedToolbar podpięty do tego samego właściciela widoku | Jawny start/stop; stop anuluje lokalne collectory | `UnifiedToolbar.kt:504–512`, `:538–546`; `ToolbarIntegration.kt:71–95`, `ReloadStopButtonIntegration.kt:55–68`. Zidentyfikowano pary wywołań, nie wykonano heap dump |
| Listener ustawień toolbaru | Rejestracja w SharedPreferences, wyrejestrowanie przy destroy | UI przez post | `UnifiedToolbar.kt:874–884`; nie stwierdzono brakującej pary cleanup w sprawdzonym wejściu BrowserFragment |
| FAB i Compose w oknie kafelków | BrowserFragment/view; arkusz ma własny Dialog Window | Kolektor `viewLifecycleOwner.lifecycleScope`; oddzielna kompozycja okna | `BrowserFragment.kt:402–437`, `MediaFloatingUi.kt:137–144`, `:195–196`, `:276–285`; jawne dismiss, usunięcie listenerów i disposeComposition |
| Host paneli i child Fragmenty | Cykl życia widoku BrowserFragment; lazy content, callbacks nie są stanem trwałym | UI; transakcje po zamknięciu odkładane przez post i ponownie sprawdzane | `SidePanelInfrastructure.kt:135–166`, `:182–301`, `:317–326`; `BrowserFragment.kt:425–437` czyści bindingi przed dispose hosta |
| Animacja i pomiary paneli | SidePanelInfrastructure | Generation token + anulowanie drivera | `SidePanelMotionController.kt:158–175`, `ValueAnimatorSidePanelDriver.kt:35–41`; `SidePanelMotionProbe.kt:134–148`, host dispose `:472–494` usuwa listenery i host. Nie oceniono fizyki gestów (etapy 4/5/8) |
| Insety / pozycja treści | WebContentPositionManager przy widoku | Android UI | `browser/WebContentPositionManager.kt:133–136`, wywołanie destroy w BaseBrowserFragment; szczegółowe konflikty insetów są poza 2A |
| Diagnostyka | Procesowe agregaty; osobny czas życia okna pomiarowego TestLab | Liczniki/ledgery synchronizowane; cleanup runtime jest oddzielny od mediów | `MediaDetectorDiagnostics.kt:15–26`, `:174–185`, `TestLab.kt:69–80`, `TestLabRuntime.kt:331–347`. Agregaty diagnostyczne nie sterują przynależnością kafelków; kompletna analiza TestLab/wydajności w etapie 15 |

Procesowe coroutines na SupervisorJob nie są automatycznie błędem: sam graf Components nie przechowuje Activity jako kontekstu. Scope widoku i procesu mają inne obowiązki. Nie wykazano w 2A potwierdzonego wycieku pamięci ani deadlocku; nie oznacza to wyniku pomiaru pamięci lub kompletnej analizy wszystkich blokad.

## Kontrakt JavaScript ↔ Android i kolejność zdarzeń

1. `BrowserApp.kt:178` instaluje detektor przez `MediaDetectorController.start`. Kontroler publikuje repository przed wynikiem instalacji. `started` chroni przed drugim startem tej samej instancji; błędy instalacji/rejestracji są diagnozowane, lecz nie ma tu jawnego mechanizmu ponawiania. Nie wykazano rzeczywistego transient failure; to ograniczenie ścieżki odzyskiwania do uwzględnienia w późniejszych testach.
2. Background (`assets/extensions/nira-media-detector/background.js:249–272`) przydziela klucz dokumentu, rozróżnia root i frame oraz weryfikuje kontekst nadawcy. Content (`content.js:65–112`) oczekuje odpowiedzi native `true` na `media_document`/`media_frame`, a dopiero potem wysyła `document_ready`/`frame_ready`. Background nie powinien routować kandydatów do nieuzgodnionego dokumentu.
3. Native handler (`MediaDetectorController.kt:137–183`) sprawdza tożsamość obiektu EngineSession, aktualny BrowserStore i jednoznaczne przypisanie sesji. Rekoncyliacja jest wykonywana również bezpośrednio w callbacku, więc samo opóźnienie collectora store nie daje dostępu do starej karty. URL/dokument/privacy są walidowane przed dopisaniem źródła.
4. Schemat v1 jest zamknięty: `MediaMessageProtocol.kt:88–111`, `:138–149`; limit wielkości wiadomości i zagnieżdżenia, typy oraz lista pól. Kontrole stricte bezpieczeństwa i wszystkich formatów nie są zamykane tym podetapem.
5. `MediaRepository.observe:275–325` usuwa stan nieistniejących sesji i resetuje wpisy po zmianie powiązania/nawigacji. Celowo zachowuje maksymalny klucz dokumentu, aby opóźnione zdarzenia nie wskrzeszały poprzedniej strony. `registerDocument:344–362` i `registerFrame:365–390` tworzą kolejną granicę akceptacji. Zakresy są ograniczone (`perTabLimit=128`, `globalLimit=384`, `scopeLimit=128`), nie są trwałym magazynem sesji.
6. `currentEntries:557–563` wymaga zgodności sesji, tabId, profilu/prywatności, URL i rejestracji dokumentu. `BrowserFragment.kt:403–421` łączy rewizję repozytorium z wybranym dokumentem w store. Lokalny snapshot arkusza nie staje się osobnym źródłem prawdy; kliknięcia ponownie pobierają bieżący stan (`:337–342`, MediaFloatingUi `:211–216`).
7. Content odłącza MutationObserver, anuluje kolejki i czyści timery po pagehide (`content.js:1189–1198`). Dwa przeanalizowane modele czasu życia **nie mają jawnie uzgodnionej generacji restartu background**: [ZC-AUD-002](FINDINGS.md#zc-aud-002--możliwy-brak-uzgodnienia-nowej-generacji-rozszerzenia), SUSPECTED.

Nie wykazano podwójnego aktywnego MediaRepository w normalnej inicjalizacji aplikacji. Współistnienie BrowserStore, repozytorium mediów, UI snapshotu i historycznych liczników diagnostycznych jest uzasadnione ich odmiennymi rolami. Spójność źródeł stanu kart/grup/profili pozostaje do 2B.

## Wyniki i weryfikacja

Nowe potwierdzone błędy 2A: **P0=0, P1=0, P2=0, P3=0**. Nowe podejrzenia: **1, potencjalny P2 — ZC-AUD-002**. Warunek zachowania tej samej EngineSession po restarcie background jest niezweryfikowany; nie przedstawiono go jako potwierdzonego zachowania GeckoView.

| Kontrola | Wynik | Co rzeczywiście sprawdzono |
| --- | --- | --- |
| Statyczne śledzenie inicjalizacji, własności i cleanup | PASS w zakresie odczytu i par wywołań | Źródła z tabeli; brak wyniku testu cyklu życia urządzenia |
| Selektywne testy JS komunikacji | **PASS 7/7**, Node v24.19.0 | Realny JS wykonywany w istniejącym harnessie VM z mockowanymi API rozszerzenia; routing, stare dokumenty/SPA, ramki, native backpressure i pagehide |
| Kotlin/Robolectric, lint, rzeczywiste AC/GeckoView | NOT_TESTED | Nie uruchamiano Gradle i nie instalowano zależności. Istniejące Kotlin testy MediaRepository/MediaSessionBinding/MediaMessageProtocol oraz paneli odczytano wyłącznie jako specyfikację pokrycia; nie nadano im nowego PASS |
| Restart background, heap dump, pomiary wyścigów/ANR i telefon | NOT_TESTED | Potrzebna osobna weryfikacja; brak podłączonego urządzenia w tym podetapie |
| Nowe CI i APK | NOT_TESTED | Nie uruchamiano żadnych workflow ani buildów. Nie powielano odczytu historycznego CI z etapu 1 |

Wykonane polecenie selektywnych testów (bez nowych plików testowych w aplikacji):

```bash
node --test --test-name-pattern='^network routes only to known tab|^stage 3.3: stale response|^stage 3.3: document key mismatches|^stage 3.3: SPA route changes|^stage 4.0.1: iframe keys bind|^v1164: native candidate backpressure|^v1164: accepted native batches' app/src/test/js/media-detector.test.cjs
```

Brak FAIL i CI_PENDING w uruchomionych kontrolach 2A. PASS mockowanych testów JS nie potwierdza działania native Kotlin ani wewnętrznego rejestru handlerów GeckoView. Nie przyjęto niezweryfikowanej semantyki `consumeFlow`/`ViewBoundFeatureWrapper` za dowód testu odtworzenia widoku.

## Pozostały zakres i bezpieczne wznowienie

**2B — najbliższy niewykonany fragment, dopiero po „Dalej”:**

- `browser/tabs/compose/TabViewModel.kt`, `TabOrderManager.kt`, `UnifiedTabOrder.kt`, `UnifiedItemBuilder.kt` oraz `browser/tabgroups/UnifiedTabGroupManager.kt`/middleware/Room: źródła prawdy, kolejność zdarzeń, mutexy i przejścia między trwałym a bieżącym stanem.
- `browser/profile/ProfileManager.kt`, `ProfileMiddleware.kt`, `DefaultProfileTabMigration.kt`, interfejsy profilu UI: synchronizacja kontekstu i scope operacji. Nie powtarzać mapy wejść z 2A.
- `middleware/EnhancedStateCaptureMiddleware.kt`, `browser/tabs/sleep/SleepingTabsManager.kt`, async restore i zależności sesji: własność długich zadań, anulowanie i kolejność przy wymianie sesji. Pogłębione scenariusze funkcjonalne należą do późniejszych etapów.
- Złożyć wspólny wynik etapu 2 dopiero po zbadaniu 2B; w razie rzeczywistej dodatkowej złożoności jawnie ustalić następny podetap. Nie rozpoczynać etapu 3 w tej samej turze.

Zapis obejmuje wyłącznie ten raport, aktualizację indeksu/rejestru i zastąpienie bieżącego checkpointu audytu w `docs/AI_HANDOFF.md`. Stary raport etapu 1 pozostaje bez zmian. Nowy raport wymaga `git add -f` z powodu istniejącego ignorowania `docs/`; bez zmiany `.gitignore`. Przed push ponownie sprawdzono triggery obu workflow: sama gałąź audytu bez PR/tag nie uruchamia tych workflow; nie stosować `[skip ci]` i nie otwierać PR-a dla zapisu.

Punkt wznowienia: `audit/zavro-clean-20261028`, ostatni commit dokumentacyjny tej gałęzi (SHA i faktyczny wynik push podaje raport końcowy rozmowy); wejściowy checkpoint `3777a625…`, kod niezmienny `b7d62f79…`. Odczytać wpis „ZC-AUD — ETAP 2/18 PARTIAL, 2A COMPLETE”, sprawdzić HEAD/working tree/remote i rozpocząć dokładnie 2B.

**ETAP 2/18 NIEUKOŃCZONY — STOP. Zapisano punkt wznowienia. Polecenie „Dalej” kontynuuje bieżący etap.**
