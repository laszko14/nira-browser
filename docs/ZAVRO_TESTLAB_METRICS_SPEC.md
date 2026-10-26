# Zavro TestLab v1165 — kontrakt i plan wdrożenia

## Cel i baza

Jeden dobrowolny, lokalny pomiar60s podczas normalnego używania przeglądarki; siedem trybów tego samego silnika. Baza v1164d890c68bd15d0f79e25ccd2935fb64ee955286b8, gałąźfeat/zavro-x-mpv-v1165-testlab-diagnostics. Bez merge, migracji, resetu, zewnętrznej telemetrii i wykonywania działań na kartach za użytkownika.

Spec użytkownika w załączniku guzik writing-block jest źródłem wymagań. Decyzje techniczne poniżej są decyzjami implementacyjnymi, nie dodatkowymi życzeniami użytkownika. Implementacja w istniejącym module app/Kotlin/Compose, bez nowych modułów Gradle i bez subagentów. Szczegółowy prompt oraz wcześniejsze polecenia upoważniają do standardowych etapów bez dodatkowego zatwierdzania.

## Architektura

`TestLabRuntime → TestLabEngine → bounded aggregates/events → TestLabReportGenerator → TestLabActivity`

- TestLabEngine: czysty, testowalny Kotlin; wspólny monotoniczny Clock, state machine, token sesji i operacji, walidacja danych, limity, wynik bez referencji Androida.
- TestLabRuntime/Collectors: Application context, aktywne wyłącznie od przygotowania do cleanup, rejestracja ActivityLifecycleCallbacks/ComponentCallbacks2, JankStats dla rzeczywiście używanego Window, próbnik zasobów i pojedynczy heartbeat Main.
- TestLab: lekka fasada dla hooków. Nieaktywna fasada nie uruchamia timerów, odczytów zasobów, kolektorów ani zapisu. Przyjmowane kody są enum, nie komunikat/URL/wyjątek.
- Raport schemaVersion1: immutable DTO, JSON/TXT poza callbackami klatek. Brak danych to null/status, nie zero. Wynik oznacza stan pomiaru, nie punktową ocenę zdrowia aplikacji.
- TestLabActivity: Material3/NiraTheme, siedem trybów, przygotowanie, powrót do istniejącej BrowserActivity, raporty/marker/stop/eksport/usuwanie. Brak automatycznego share.

Tryby: COMPLEX, PANELS_GESTURES, TABS_GROUPS, GECKO_PAGES, VIDEO_MPV, RESOURCES, STABILITY. Każdy60_000ms. Wspólne lekkie kolektory; dokładniejszy wybrany obszar w jawnych limitach.

## Czas i lifecycle

IDLE→PREPARING→RUNNING→FINALIZING→COMPLETED; zakończenia PARTIAL/CANCELLED/FAILED. Token sesji odrzuca spóźnione callbacki z poprzedniej sesji. Wspólna baza SystemClock.elapsedRealtimeNanos; długość niezależna od zegara ściennego.

PREPARING nie zbiera klatek ustawień. Countdown3s rozpoczyna się dopiero w rzeczywistym oknie Browser/MPV. Tło podczas przygotowania przerywa countdown do ponownego wejścia do takiego okna. RUNNING w tle nadal ma deadline; okresy bez odpowiedniego Window są jawne. Bez wake locka. Po uśpieniu deadline jest sprawdzany przed dopisaniem danych. Pomiar kończy się logicznie po60s, nawet jeśli Android dostarczy callback później; opóźnienie finalizacji osobno.

Kończenie zamyka zapis przed cleanup. Kolektory odpinane na właściwym wątku; brak nowego startu podczas FINALIZING. Aktywności/Window nie mogą trafić do DTO/raportu. Dialogi paneli mają osobny hook okna. Przy rotacji stary Window odpięty, nowy przypięty; sesja i deadline pozostają.

Supertest40s jest niezależny. Start jednego pełnego pomiaru przy drugim aktywnym jest odrzucany z informacją; nie zmieniamy czasu ani clipboard behavior dotychczasowego Supertestu.

## Metryki i źródła

| Metryka | Źródło / jednostka | Ograniczenie |
|---|---|---|
| Android UI frames/jank | AndroidX metrics-performance1.0.0/JankStats | Nie Gecko compositor ani klatki SurfaceView wideo |
| UI duration | FrameData.frameDurationUiNanos | nanosekundy, raport ms |
| CPU frame / deadline overrun | FrameDataApi24/Api31, jeśli rzeczywiście dostępne | null dla niedostępnej klasy/API; bez równoległego FrameMetrics |
| Refresh rate | Display, Hz, historia zmian ograniczona | Jank określa biblioteka; brak stałego progu16.7ms |
| Operacja | START/ACCEPT/CANCEL/FINISH w rzeczywistym przepływie | START nie dowodzi otwarcia/zapisu; cancellation nie jest failure |
| Tab/group correctness | Wynik reducer/Room/DataStore po zakończeniu, tylko booleans/kody | ID wyłącznie chwilowo w adapterze, nie w storage/raporcie |
| Java/native heap | Runtime/Debug, bytes | Nie sumować nakładających się obszarów |
| PSS/USS | Debug.MemoryInfo, KiB→bytes | Własny proces; źródło i dostępność jawne |
| CPU | Process.getElapsedCpuTime, delta ms / elapsed ns | % jednego rdzenia, może >100%; nie CPU wszystkich subprocess Gecko |
| Main heartbeat | Jedna oczekująca wiadomość Main co1s | Stall nie jest ANR; tło/gapy wyłączają interpretację |
| Battery/thermal/power save | Android system APIs | Bez deklaracji dokładnej energii zużytej w60s |
| Gecko | BrowserStore/EngineObserver/istniejąca diagnostyka rozszerzenia | Load complete≠responsive compositor |
| MPV | Istniejący allowlisted MpvDebugTrace + position samples | Żadnego Play/seek/reload; brak pomiaru obu torów audio |
| Stability/exits | Obsłużone kategorie i ApplicationExitInfo API30+ | Bez exception.message, description, trace stream i logów innych aplikacji |

JankStats zwraca obiekt wielokrotnego użytku; callback kopiuje wyłącznie skalary do agregatorów. API24+ dostarcza callback na wątku FrameMetrics. Zachowujemy domyślną heurystykę opartą na odświeżaniu. PerformanceMetricsState tagi są statycznymi nazwami operacji; kilka tagów może współistnieć. Przypisanie jest korelacją, nie automatyczną przyczyną.

Percentyle: jawny minimalny rozmiar próby (P50≥2/P90≥10/P95≥20/P99≥100). Stały histogram0.25ms z ograniczoną liczbą kubełków dla wszystkich próbek; wynik jest przybliżeniem z podaną metodą. Overflow nie może dawać pozornie dokładnego percentyla. Sumy i maxima obejmują wszystkie poprawne próbki.

CPU średnie ważone rzeczywistym czasem między próbkami, a nie średnia nierównych interwałów. Niepoprawne/ujemne/nieskończone dane odrzucane i liczone; niedostępne pola pozostają null.

## Prywatność i limity

Wyłącznie enum/kod i ograniczone liczby przed zapisem; brak API event(message:String). Nie przyjmujemy URL, hostów/IP, nazw profili/tytułów/ID kart, headerów/cookies/tokenów, ścieżek prywatnych plików ani Throwable. Chwilowe porównania ID pozostają w adapterze i są usuwane po zakończeniu operacji.

Początkowe limity:1024 timeline events,128 równoczesnych operacji,32 markers/anomalies,180 resource samples,4096 histogram bins; report≤256KiB. Zdarzenia odrzucone/evicted raportowane. Kolektor nie zapisuje na dysk per-frame. Brak danych pamięci w danym API nie jest zerowym użyciem.

Raport domyślnie RAM. Recovery opt-in domyślnie OFF: atomic private file, minimalny schema, max64KiB, ostatnia sesja, retencja24h, zapis najwyżej co5s, usuwanie/wyłączenie. Bez globalnego uncaught handler. Eksport tylko na żądanie, SAF/istniejący FileProvider; ograniczone pliki udostępnienia i retencja.

## Punkty integracji — zweryfikowane A

- settings/fragment/HeadersSettingsFragment.kt rzeczywiście definiuje klasę SettingsFragment; preferences_headers.xml jest używany. Dodać osobny wpis bez przebudowy ustawień.
- BrowserFragment.setupEdgePanelGestures rozpoznaje kierunek, osobno próg zatwierdzenia; .show() nie oznacza końca animacji. Nie zmieniać zwracanych Boolean ani przechwytywania MotionEvent.
- SwipeGestureLayout zawiera faktyczne cancellation i pionowy pasywny sygnał; tylko hooki, bez modyfikowania gestu.
- TabsBottomSheetFragment i BookmarksBottomSheetFragment: Window/first pre-draw/onStart/onDismiss/onCancel; osobne attach/detach i outcome.
- TabGroupMiddleware.invoke w v1164 wyłącznie next(action); jawne grupowanie ma osobną metodę. Passive observer po next nie może uruchamiać Room dla zwykłych linków przy wyłączonym TestLab.
- UnifiedTabGroupManager serializuje operacje mutate i persistPositions; walidować dopiero po zakończeniu, nie po launch z ViewModel.
- TabOrderManager/UnifiedTabOrder/UnifiedDragSystem: potwierdzenie zapisanej kolejności i pojedynczy commit; nie wymuszać zapisów.
- MediaDetectorDiagnostics ma już allowlist/kategorie, MpvDebugTrace.note odrzuca nieznane kody. Nowy odbiorca używa tych źródeł, nie kopiuje loggera.
- MpvSupertest aktywny40s, sample ma własny session-bound guard i clipboard na końcu; zachować zachowanie, dodać jedynie bierny tap i konflikt startu.

## Plan implementacji i weryfikacji

- [x] A: baza/status + doc/API + mapa integracji, bez powtarzania testów v1164 (świeże CI40317975227/40317975157 zielone).
- [x] B1: model/safe schema i agregatory: testy RED→GREEN dla null/units/percentiles/bounds/privacy.
- [x] B2: TestLabEngine state machine/token/deadline/operations/coverage/anomalies: kontrolowany zegar, równoległe wywołania, stop60s/partial/cancel/no late writes.
- [ ] C: faktyczne hooki paneli/gestów/tab/group/order; testy skutków i odrzuconych/starych callbacków; bez zmian danych użytkownika.
- [ ] D: adapter Gecko i istniejące trace MPV/Lua/DSP; konflikt Supertest40s, bez nowego playera/JS detektora.
- [ ] E: JankStats/lifecycle/OS probes/heartbeat/exit info/recovery; runtime tests z fake scheduler i Robolectric, potem prawdziwy emulator.
- [ ] F: Material3 UI+7 przycisków/marker/countdown/passywny licznik/SAF/share/delete; rotacja i accessibility/insets.
- [ ] G: testy A/B narzutu (host i emulator osobno), regresje Kotlin/JS/Lua/DSP/v1164Gecko; review, integralność chronionych plików i lint delta.
- [ ] H:165/1165/alpha71-mpv39; draftPR do v1164; podpisujący CI/verifier ARM64/cert; bez merge/pobieraniaAPK doCodex.

Każdy większy etap kończy się aktualnym checkpointem i w razie potrzeby WIP push, bez przedstawiania częściowej implementacji jako gotowego APK. Finalne raporty zawierają zakres naprawdę zaobserwowanych czynności i NOT_TESTED dla pozostałych.

## Źródła

[Metrics stable1.0.0,08.10.2025](https://developer.android.com/jetpack/androidx/releases/metrics), [JankStats lifecycle/threading/reused FrameData](https://developer.android.com/topic/performance/jankstats), [FrameMetrics](https://developer.android.com/reference/android/view/FrameMetrics). Zależność nie występuje bezpośrednio w obecnym katalogu app; przed dodaniem sprawdzić transitive resolution.
