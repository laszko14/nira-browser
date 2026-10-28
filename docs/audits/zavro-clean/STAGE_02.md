# Zavro Clean — Audyt 2/18

Status etapu: **COMPLETE**. Podetapy **2A COMPLETE** i **2B COMPLETE**. Data: 2026-10-28 UTC. Jeden agent; wyłącznie analiza i dokumentacja, bez napraw/APK/nowych testów aplikacji.

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

## Zakres podetapu 2B

Poniższą listę przekazano z 2A i przeanalizowano w kolejnej turze po „Dalej”:

- `browser/tabs/compose/TabViewModel.kt`, `TabOrderManager.kt`, `UnifiedTabOrder.kt`, `UnifiedItemBuilder.kt` oraz `browser/tabgroups/UnifiedTabGroupManager.kt`/middleware/Room: źródła prawdy, kolejność zdarzeń, mutexy i przejścia między trwałym a bieżącym stanem.
- `browser/profile/ProfileManager.kt`, `ProfileMiddleware.kt`, `DefaultProfileTabMigration.kt`, interfejsy profilu UI: synchronizacja kontekstu i scope operacji. Nie powtarzać mapy wejść z 2A.
- `middleware/EnhancedStateCaptureMiddleware.kt`, `browser/tabs/sleep/SleepingTabsManager.kt`, async restore i zależności sesji: własność długich zadań, anulowanie i kolejność przy wymianie sesji. Pogłębione scenariusze funkcjonalne należą do późniejszych etapów.
- Po 2B złożono wspólny wynik poniżej; nie rozpoczęto etapu 3.


## 2B — stan kart, grup, profili i współbieżność

Wejściowy checkpoint: `97b91e3dbf5d3f7dadcb9bfb2f54d27e88e211b0`, gałąź `audit/zavro-clean-20261028`, working tree czysty. Baza aplikacji nadal `b7d62f79…`. Nie powtarzano testów ani śledzenia cyklu życia z 2A. Pytania: które warstwy są autorytatywne; jak uzgadniają stan; czy mutacje są serializowane i kończą zapis; co dzieje się po zmianie profilu, anulowaniu, close/Undo i odtworzeniu sesji; kto jest właścicielem pozostałych coroutines i prezentacji.

### Mapa odpowiedzialności i zabezpieczeń

Ścieżki jak w 2A, względem pakietu `com/prirai/android/nira`. To ocena architektury, bez deklaracji poprawności wszystkich gestów lub wszystkich funkcji profilu.

| Warstwa / przepływ | Ustalenie z rzeczywistego kodu |
| --- | --- |
| BrowserStore i sesje | Store jest źródłem bieżących kart, wyboru, EngineState i UndoHistory. `ProfileManager.migrateTabToProfile:248–267` zachowuje ID i UI metadata, ale zeruje EngineState, aby nie podpiąć starej sesji do nowej partycji. `ProfileMiddleware:29–86` respektuje jawny kontekst i pochodzenie parentId; nie utożsamia parentId z członkostwem grupy |
| Dokładny kontrakt AC 156.0.1 | Odczytano oficjalne [lib-state-156.0.1-sources.jar](https://maven.mozilla.org/maven2/org/mozilla/components/lib-state/156.0.1/lib-state-156.0.1-sources.jar), plik `mozilla/components/lib/state/Store.kt:71–95`: `dispatch` jest synchroniczny, synchronizowany na Store i zwraca Unit. Dlatego nie zgłoszono rzekomego błędu ThreadLocal ProfileMigrationGuard wynikającego ze starszej, asynchronicznej semantyki. To odczyt źródła biblioteki, nie wykonanie Androida. SHA256 archiwum: `2d66ed6968910fcaaeb04a3f5f9342cc1e66233e55768f8fe09de34daa3cc546` |
| Profile — trwałe dane i UI | `ProfileManager:42`, `:99–155`, `:218–233` przechowuje profile JSON, aktywny ID i private flag w SharedPreferences; nie w Room, wbrew historycznej ogólnej mapie. `BrowserProfile.contextIdFor` ustala default→null, private→private, nazwany→profile_ID. `BrowsingModeManager` przechowuje UI state; callbacki `BrowserActivity:421–440` zapisują oba wybory. Nie zmieniano ogólnej dokumentacji projektu |
| Skutki zmiany profilu | `ProfileManager:153–155` uruchamia niezależną pracę IO polityki dodatków i wyboru wyszukiwarki. Profilowe listy dodatków są lokalnymi preferencjami, ale enable/disable dotyczy jednego silnika rozszerzeń. Brak kontroli generacji po zawieszeniu jest usterką **ZC-AUD-003** |
| Room i grupy | `UnifiedTabGroupManager:63–109` czeka na inicjalizację, bierze mutationMutex, obejmuje logiczną zmianę `database.withTransaction`, po commicie publikuje jeden groupsState i zdarzenia. W razie błędu odtwarza cache. Publiczne getGroup/getAllGroups czytają opublikowany snapshot (`:565–580`), nie pośredni cache transakcji |
| Repozytorium grup i DAO | `TabGroupDatabase` schema 3 ma migracje 1→2 i 2→3, bez destrukcyjnego fallback. `TabGroupDao` przechowuje członkostwo i position; `persistPositions:392–398` przelicza pozycje w transakcji. `moveTabRelative`, tworzenie/merge i reassign walidują aktualne konteksty. Przeanalizowano granice operacji, nie wszystkie kombinacje drag/drop z etapu 4 |
| DataStore — kolejność | `TabOrderManager:61–85` serializuje operacje przez operationMutex, reentrant context Operation i NonCancellable po przyjęciu operacji. Profil jest przechwycony; publish nie nadpisuje widocznego porządku innego aktywnego profilu. `loadOrder:115–143` czeka na inicjalizację Room przed uzgadnianiem. `saveOrder:149–165` publikuje po zapisie |
| Granica Room/DataStore | Room ustala członkostwo i kolejność wewnątrz grupy; DataStore — kolejność kontenerów i widoczny stan rozwinięcia. `rebuildOrderForProfile:478–513` scala snapshot zamiast zapisywać członkostwo z UI wstecz do Room. `pending_root_<profile>` zapisuje zamiar przeniesienia przed zmianą członkostwa i jest konsumowane z zapisem order w jednej operacji DataStore. Nie jest to jedna transakcja obejmująca Room, BrowserStore i DataStore; scenariusze awarii podczas przenoszenia wymagają dalszych testów |
| ViewModel i reaktywność | `TabViewModel:90–114`, `:155–177`: viewModelScope, groupsState, anulowanie starszego loadJob, czyszczenie UI przy zmianie profilu, filtrowanie i sprawdzenie profilu przed publikacją. `launchMutation` wiąże mutację z profilem. Podwójna projekcja grup i kolejności ma mechanizm uzgadniania; sama obecność kilku StateFlow nie dowodzi dwóch źródeł prawdy |
| Renderer i close/Undo | `UnifiedItemBuilder:95–192` filtruje żywe karty i uzupełnia snapshot nowej grupy przed dotarciem order. `closeTab:900–924` pozostawia zachowanie odzyskiwania w UndoMiddleware. `UnifiedTabOrder.reconciled:31–64` zachowuje stare sloty również po ostatecznym zamknięciu, a rebuild zapisuje je ponownie — **ZC-AUD-004** |
| Zdarzenia jednorazowe | `TabViewModel:827–850` emituje DuplicateTabRequest przez SharedFlow; konsument `ComposeTabBarWithProfileSwitcher:305–331` tworzy tab, ewentualnie ustala grupę, potem wybiera kartę. groupEvents jest dodatkowym strumieniem zdarzeń z DROP_OLDEST; główna projekcja ViewModel obserwuje groupsState, więc utrata pojedynczego eventu nie jest dowodem utraty trwałego członkostwa |
| Globalny komunikat Cofnij | Singleton GlobalSnackbarManager przyjmuje scope od dwóch kompozycji. Panel może zastąpić scope paska własnym, a po zamknięciu pozostawić go anulowanym — **ZC-AUD-005**. To konflikt własności prezentacji, nie błąd samego natywnego UndoHistory |
| Capture i usypianie | `EnhancedStateCaptureMiddleware:40–46` zleca checkForFormData wybranej sesji w przekazanym scope Main; nie zawiesza jej. `BrowserActivity:182–188` osobno wywołuje manager usypiania. `SleepingTabsManager:60–88`, `:143–188` ma collectora wyboru i okresowy sweeper Main; pomija wybraną kartę, odtwarzanie, download i prompty, używa SuspendEngineSessionAction. Nie wykonano pomiaru wpływu na formularze ani realnego zwalniania pamięci |
| Migracja default i async restore | Migracja przechwytuje RestoreAction przed reducerem; zachowuje kolejność listy. Marker migracji jest zapisywany przed asynchronicznym przepisaniem DAO (`DefaultProfileTabMigration:138–159`), a cache grup jest ładowany jednorazowo (`UnifiedTabGroupManager:176–226`). Aliasy null/profile_default są uwzględnione w głównych filtrach. Odnotowano rozdzielone cykle życia; nie zgłoszono z tego samego faktu utraty danych ani powtórnego zgłoszenia bez dowodu skutku |
| LiveData i pozostałe granice | LiveData znaleziono w `webapp/PwaSuggestionManager:19–24`; główny przepływ kart/grup używa StateFlow/Flow. Wyszukanie observeForever w kodzie aplikacji nie dało wyników. Nie nadano PWA wyniku testu funkcjonalnego; pełne scenariusze tego modułu pozostają do etapów 9/10. Singletony menedżerów używają applicationContext; scope ViewModel należy do właściciela UI wskazanego w 2A |

Nie zgłoszono nieużywanego `closeOtherTabs` jako czynnej usterki: wyszukanie nie wykazało produkcyjnego wywołania. Podobnie sam fakt istnienia metody stop bez bieżącego wywołania nie potwierdza wycieku. To nie zastępuje oceny osiągalności w etapie 3.

### Wyniki 2B i granice dowodów

Potwierdzono **3 nowe P2**: ZC-AUD-003 (nieaktualna polityka profilu), ZC-AUD-004 (nieograniczona retencja slotów) i ZC-AUD-005 (anulowany scope komunikatu Cofnij). Każde ma osobny mechanizm, dokładne lokalizacje i wymagane regresje w [FINDINGS.md](FINDINGS.md). Nie wprowadzono poprawek.

| Kontrola 2B | Wynik | Faktyczny zakres |
| --- | --- | --- |
| Kontrakt Store 156.0.1, mapa Room/DataStore/ViewModel/profili i właścicieli zadań | PASS odczytu źródeł | Odrzucono niezgodną ze współczesną wersją hipotezę asynchronicznego dispatch. Nie jest to PASS wszystkich funkcji |
| Polityka dodatków, rzeczywisty ProfileAddonPolicy.kt | FAIL inwariantu aktualnego profilu; kontrola B PASS | Zawieszony odczyt A, zakończenie B, dopiero potem A: wynik B oczekiwany true, rzeczywisty false; AddonManager, Android i Main są fixture |
| Retencja, rzeczywisty UnifiedTabOrder.kt | FAIL inwariantu końcowego sprzątania; kontrola partial restore PASS | 500 różnych zamkniętych ID + 1 żywa karta daje 501 slotów. Nie zmierzono rozmiaru ani czasu zapisu DataStore |
| Komunikat Cofnij, rzeczywisty GlobalSnackbarManager.kt | FAIL inwariantu dostarczenia żądania; kontrola żywego scope PASS | Prawdziwe coroutines, fixture Compose/Log; po anulowaniu scope panelu zero nowych wywołań hosta |
| Pełny Gradle unit/lint, instrumentacja Android/Gecko, telefon, heap/jank | NOT_TESTED | Brak podstaw do powielania pełnego CI; próby nie uruchamiają APK ani Gradle i nie instalują zależności |
| Nowe CI | NOT_TESTED | Nie uruchamiano workflow i nie odpytywano istniejących runów |

Trzy izolowane programy kończą się kodem 0, bo ich `check` potwierdza **reprodukcję usterki**. Nie nazywać tego PASS zachowania aplikacji. Kompilacja używa istniejącego Kotlin 2.4.0 z dystrybucji Gradle, a nie całego projektu na Kotlin 2.4.10. Źródła fixture, komendy i pełne wyniki są utrwalone w [STAGE_02B_PROBES.md](STAGE_02B_PROBES.md). Próby nie są nowymi plikami w app/src/test i nie modyfikują danych użytkownika.

### Wspólny wynik etapu 2 i punkt wznowienia

**Etap 2 COMPLETE: 2A i 2B wykonane.** W etapie 2 potwierdzone P0=0, P1=0, P2=3, P3=0; SUSPECTED=1 (ZC-AUD-002 z 2A, potencjalny P2). W całym dotychczasowym audycie CONFIRMED=4 P2, SUSPECTED=1. Nie ma otwartego podetapu 2C.

Kryteria: mapa zależności i właścicieli kompletna dla architektonicznego zakresu etapu; zbadano przejścia JS/native/UI oraz BrowserStore/Room/DataStore/profili, serializację mutacji, kolektory i ich czas życia; znalezione usterki i jedna niepotwierdzona hipoteza sklasyfikowane; kontrole wykonane albo jawnie NOT_TESTED; raport/rejestr/checkpoint zapisane. Nie oznacza to ukończenia późniejszych szczegółowych audytów funkcjonalnych.

Ryzyka do kolejnych etapów: rzeczywisty restart rozszerzenia (002), prawdziwe callbacki AddonManager, awarie procesu podczas wielowarstwowej migracji/przenosin, cykl życia prezentacji na Androidzie, fizyczne zużycie pamięci i płynność. Nie deklaruje się wykluczenia wszystkich wyścigów, wycieków ani potwierdzonej wydajności 120 Hz.

Zapis tylko dokumentacyjny: STAGE_02.md, STAGE_02B_PROBES.md, FINDINGS.md, AUDIT_INDEX.md i bieżący wpis w docs/AI_HANDOFF.md. STAGE_01.md oraz kod aplikacji, build i wersje pozostają bez zmian. Triggery dwóch workflow sprawdzono ponownie; zwykły push gałęzi audytu bez PR/tag nie uruchamia tych workflow. Bez [skip ci], PR-a i dodatkowego zapisu tylko dla własnego SHA.

Checkpoint: ostatni commit dokumentacyjny `audit/zavro-clean-20261028`, którego rzeczywisty SHA i wynik push podaje raport końcowy rozmowy; wejściowy commit 2B `97b91e3d…`, baza kodu `b7d62f79…`. Po „Dalej” odczytać wpis **ZC-AUD — ETAP 2/18 COMPLETE**, potwierdzić HEAD/working tree i rozpocząć **etap 3 — pozostałości starych implementacji**. Nie powtarzać etapów 1/2 bez nowego powodu.

**ETAP 2/18 ZAKOŃCZONY — STOP. Następny etap wymaga polecenia „Dalej”.**
