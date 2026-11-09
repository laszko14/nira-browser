# Zavro Clean — diagnostyka wyników, etap 4/7

Task `ZC-VIDEO-DIAG-4`. Produkt: Zavro Clean (nie x MPV).
Gałąź: `feat/zavro-clean-video-diagnostics-20261108`.
Potwierdzony SHA przed etapem: `0c7be9c457a25b969303827f53554f9adb07c17d`.
Upoważnienie: kolejne "Dalej", wyłącznie IMPLEMENT etapu 4.
Brak zgody na APK, merge, release, force-push lub zmianę podpisu.

## Obserwowalny lejek

Istniejący Room `resultsSnapshot()` czyta atomowo historię danego
normalnego profilu/uruchomienia. Istniejący `VideoSearchResultsPresenter`
i `VideoSearchResultsMerge` nadal podejmują identyczne decyzje.

Nowy `VideoSearchResultsDiagnostics.kt` jest **wyłącznie obserwatorem**.
Działa przez `VideoSearchDiagnosticResultsHost`, który otrzymuje
referencję do już autoryzowanego rejestratora wyłącznie przy ręcznym
`beginDiagnostics(owner)`; wyrejestrowanie następuje przy stopie
i cofnięciu autoryzacji profilu. Samo otwieranie wyników nie uruchamia
nagrywania ani nie zapisuje żadnych nowych preferencji.

Etapy mierzone osobno:
1. `SAVED_FILMS`: unikalne, zapisane w Room wiersze dla jednej witryny i
   dokładnego URL (liczba istniejących wierszy nie musi oznaczać liczby
   kandydatów rozpoznanych na stronie).
2. `UNIQUE_FILMS`: licznik deklarowany przez zadanie oraz suma z projektu
   prezentera; ich rozbieżność jest weryfikowalnym lokalnym objawem.
3. `CANDIDATES`: po wyborze witryny i ewentualnym scaleniu, **liczba
   pozycji interfejsu**, nie nowych filmów ani kandydatów z DOM.
4. Po `take(visibleCount)`: liczba pozycji przygotowanych do LazyColumn/
   LazyGrid. "Pokaż kolejne 150" to celowe ograniczenie, nie utrata danych.
5. `DISPLAYED_FILMS`: filmowe wiersze gotowe do renderowania po
   zwinięciu grup. W osobnej obserwacji
   `VIEWPORT_COMPOSED_FILM_ROWS` liczba filmowych elementów aktualnie
   skomponowanych przez Compose w widocznym oknie. To **nie jest test
   rzeczywistych pikseli** ani zweryfikowane wyświetlenie miniatur.
6. Stan sortowania, filtrowania, trybu witryn, scalania po nazwie/długości,
   trybu smart, siatki/listy/compact, zaznaczania, zwinięć i paginacji
   interfejsu zapisywany w enumach/licznikach bez tytułów/URL.
   Wznowienie karty z tym samym wynikiem i niezmienionymi opcjami nie
   generuje powtarzanych identycznych zestawów zdarzeń.

Rozdzielone są zapisy `OBSERVATION` (`RESULTS_SCREEN_COUNTS`,
`VIEWPORT_COMPOSED_FILM_ROWS`) i `INFERENCE` (pierwsza wykryta
rozbieżność lokalna, w tym `STORAGE_TO_PROJECTED_DIVERGENCE`,
`PROJECTED_TO_MERGE_DIVERGENCE`, `MERGE_TO_PAGE_DIVERGENCE`,
`PAGE_TO_ELIGIBLE_DIVERGENCE`). Żaden znacznik JS ani UI nie ogłasza
samodzielnie "witryna działa na pewno".

## Ocena na witrynę

`OK` oznacza tylko spójność **lokalnego** snapshotu/zapisu/projektu
dla zakończonego zadania z niepustymi wynikami; nie potwierdza strony.
`PROBLEM`: brak oczekiwanego tasku, konflikt licznika Room albo
nieprawidłowy projekt wyników.
`INTERRUPTED`: FAILED / STOPPED / NEEDS_ATTENTION (nie błędne 0).
`UNCERTAIN`: praca w toku, pauza, brak wierszy mimo statusu COMPLETED
(gdy brak niezależnego dowodu realnie pustej strony).
Wyłączenie źródła filtrem, scalenie różnych witryn, zwinięte grupy i
stronicowanie UI są osobno oznaczonymi *celowymi* przyczynami
mniejszej liczby pozycji. Wszelkie nierozpoznane zdarzenia lub utracony
pakiet JS nadal oznaczają częściową wiedzę, nie PASS.

## Właściciel, bezpieczeństwo, ograniczenia

Tylko normalny profil, którego `VideoSearchScope.Normal` zgadza się
zarówno z rejestratorem i aktualnym `ProfileManager`, jest uprawniony
do snapshotu Room. Profil prywatny nie jest rzutowany na zwykły:
jego przyszły osobny widok wymaga osobnej integracji przed końcowym
audytem etapu 7. Prywatny rejestrator z etapu 3 nadal jest RAM-only.

Nie kopiuje się zawartości filmów, nazw witryn, zapytań, adresów stron,
tokenów ani identyfikatorów zaznaczonych filmów do zdarzeń lokalnego UI.
Dopuszczone są natywne `runId`, `siteId`, enumy i liczniki.
Rozdzielenie od danych użytkownika: brak modyfikacji Room,
wyników, zaznaczenia, scrolla, profili, sesji lub automatycznego
eksportu. Analiza wykonuje się poza głównym wątkiem po projekcji
odpowiedniego snapshotu; viewport jest obserwowany przez
`snapshotFlow` z limitem 800 ms. Własny kolejkujący zapis nadal
ogranicza pamięć/IO i wykazuje straty.

## Testy i kryteria zamknięcia

Nowy syntetyczny Robolectric/JVM test
`VideoSearchResultsDiagnosticsTest.kt` obejmuje: spójny lokalny
lejek, filtr, zwinięcie grupy, scalenie między stronami, limit 150 z
200, konflikt task–Room, brak osobnej poprawnej witryny, zatrzymanie
i brak dowodu zera, brak dostępu obcego profilu, domyślnie wyłączony
rejestrator, brak tytułu/URL/hasła w JSONL, deduplikację obserwacji
i osobny licznik viewport.

Wymagane bezpieczne CI: SOURCE process-docs, shared-tests JS,
Kotlin/Robolectric, AndroidTest Kotlin compilation, lint z bazą,
required-summary; PR shared-tests JS/JVM/AndroidTest/lint i
error-learning. Wszystkie wymagane joby mają być SUCCESS na końcowym
head_sha. Skipped build/release jest celowe. APK nie jest upoważnione.

NOT_TESTED: fizyczny Nothing Phone (1), prawdziwe witryny, zmiany trybu
prywatnego na urządzeniu, faktyczne piksele i FPS/RAM. Uruchamianie,
zatrzymanie i eksport raportu to zakres etapu 5. Syntetyczne testy
nie dowodzą prawdziwego wyglądu ani kompletności wyników na stronach.
