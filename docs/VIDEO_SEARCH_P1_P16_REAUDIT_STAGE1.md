# Zavro Clean — ponowna weryfikacja audytu Codexa P1–P16 — ETAP 1/10

Data: 2026-10-30. Gałąź `audit/zavro-clean-p1-p16-stage1-20261030`.
Baza: `c31a36ca5789585f32c55a2f3297427194fe5054` (v1195). Oryginalny audyt: `docs/VIDEO_SEARCH_AUDIT.md` na gałęzi `fix/zavro-clean-video-search-audit-20261029`; zamknięcie P16 w CI [40957255307](https://github.com/laszko14/nira-browser/actions/runs/40957255307), commit `08a463ed4cda8b8ca9743eeab8f7c7b5b98cb34d`.

## Reguła dowodowa

- **W kodzie** = mechanizm/naprawa nadal istnieje w aktualnej gałęzi.
- **CI** = istniejące testy jednostkowe, Robolectric lub JS (w tym osobny zestaw P1–P16) zakończyły się sukcesem na podanym SHA. CI nie jest dowodem działania witryn na rzeczywistym GeckoView.
- **URZĄDZENIE NOT_TESTED** = w tej sesji nie uruchomiono testów na Nothing Phone (1), Android 15. Dotyczy wszystkich wierszy.
- Nie cofać nowszych zmian V3.2 do dawnych stałych 200/2000; nie usuwać ani nie migrować danych bez wykazanej konieczności.
- Ten etap przygotowuje bramki i plan reprodukcji. Potwierdzone usterki naprawiać w odpowiadających im etapach 2–9, następnie pełna regresja w 10/10.

## Macierz pokrycia P1–P16

| Pn | Pierwotny problem | Aktualny kontrakt / istniejący dowód | Dalszy test i kryterium |
| --- | --- | --- | --- |
| P1 | Formularz i Dodaj przykryte przez ActionBar/IME | `MyVideoSitesActivity`, `MyVideoSitesLayoutTest` | Android 15: 320×480, landscape, fontScale 2, IME, edycja i drag, wszystkie akcje osiągalne |
| P2 | Prywatna epoka przeciekała między zamknięciem i ponownym otwarciem | `VideoSearchPrivateTabLifecycleMiddleware`, `VideoSearchPrivateSessionRegistryTest` | Close-last/reopen w dwóch szybkich dyspozycjach, rotacja, profile A/B, brak zapisów Room/cache |
| P3 | Worker zamykał kartę przejętą przez użytkownika | `VideoSearchNormalWorkerLease`, `VideoSearchNormalWorkerLeaseTest`; nowy test sticky adoption w `VideoSearchAuditStage1GateTest` | Gecko: użytkownik wybiera kartę workera i odchodzi, worker nie steruje ani nie zamyka karty |
| P4 | Stop → Start podczas cancelled/finally | `VideoSearchNormalRunGate`, `VideoSearchNormalRunGateTest` | NonCancellable finally i natychmiastowy restart, stary callback nie zastępuje runu |
| P5 | Stary automat nadpisywał konfigurację przy kolizji czasu | monotoniczny `updatedAtMs` w `VideoSearchDao`, `VideoSearchConfigurationRevisionTest` | Równoległy picker i auto, cofnięty zegar, zmiana owner po rozpoczęciu skanu |
| P6 | Cursor wracał do 0 po 96 kartach; gubione pakiety | `VideoSearchPaginationCollector`, testy JS P6; nowa bramka >352 kart | Gecko dokument >320 kart, paczki po 16, zmiana DOM i stale callback |
| P7 | Stary stały cap 2000/200 | V3.2: `VideoSearchResultBudgets`, `VideoSearchAggregateBudgetTest`; nowa bramka sumy limitów | Kontrakt obecny: 1..10k na witrynę, suma wybranych limitów w normalnym Room, prywatne 20k RAM-run |
| P8 | Stop tracił częściowe filmy; po kill pozostawało RUNNING | `VideoSearchMultiRunJournal`, `VideoSearchRunRecovery`, `VideoSearchRecoveryAndProgressTest` | Kill/relaunch podczas checkpointu, spójne STOPPED bez samoczynnego wznowienia sieci |
| P9 | Film dostawał URL kategorii / reklamy | `content.js`, `VideoSearchResultCorrelationTest`, JS P9 | Realne różne DOM, link filmu albo jawne pominięcie, bez arbitralnego wyboru kategorii |
| P10 | ACK nowej frazy akceptował stare karty | `VideoSearchWorkerVerification`, JS P10; nowa bramka cosmetic SPA/no-op | Nie wolno zapisać starych filmów przy no-op/preventDefault, tytuły i kolejność nie dowodzą generacji |
| P11 | Niedostępny Next fałszywie kończył paginację | `VideoSearchBridgeProtocol`, JS P11; nowa bramka no_results/STALLED | Dynamiczny spinner/disabled, autentyczny no_results, potwierdzone last page i brak markera |
| P12 | Przekierowany URL blokował konfigurację | `VideoSearchAddressCorrectionTest`, `VideoSearchDao.correctSiteAddressCas` | HTTP→HTTPS, www, query i path, świadoma zgoda, bez utraty selektorów/wyboru |
| P13 | Pole na homepage, karty na osobnym /results | `VideoSearchManualStages`, `VideoSearchManualConfigurationTest`, JS P13 | Przełączenie dokumentu, powrót, save/reopen, anuluje bez utraty częściowego formularza |
| P14 | SPA pushState/hash blokowało żywy port | `VideoSearchSpaBinding`, `VideoSearchSpaBindingTest`, JS P14 | Realne pushState/replaceState/back/forward, opóźnione Hello, obcy origin odrzucony |
| P15 | Dialog znikał po błędzie lub obrocie | `VideoSearchNormalDialogDraft`, `MyVideoSitesDialogRegressionTest` | IME, rotacja, drugi tap, poprawny profil, brak prywatnych szkiców w Bundle |
| P16 | Pusta lista bez instrukcji | `VideoSearchSitesListState`, `VideoSearchSitesEmptyStateTest` | Loading/Empty/HasSites/Error z realnymi przejściami Room, czytelność interfejsu |

## Dodatkowa luka poza szesnastką — do zaplanowanych etapów

**Luka metadanych prywatnych wyników:** w `VideoSearchPrivateRunAccess.checkpointSingle` oraz `VideoSearchPrivateGroupAccess.checkpoint` nowe filmy są filtrowane wyłącznie przez brak URL w `existing/previousUrls`. Nowszy snapshot tego samego URL z lepszym tytułem/miniaturą nie przechodzi ponownego `putFilm`. `VideoSearchPrivateStore.putFilm` potrafi zaktualizować rekord pod tym samym ID, ale wymienione wywołania nie przekazują mu tych aktualizacji. Wymaga dedykowanej reprodukcji i naprawy w **etapie 6/10**, bez zmiany zasad deduplikacji ani prywatnego RAM.

**Znane ograniczenia:** P13/CFG-05 zależy od rzeczywistego DOM; Picker V2 jest ulepszony, lecz nie ma dowodu wsparcia dowolnej strony. Przy dużych limitach stop z powodu zasobów nie oznacza końca dostępnych filmów. Samo CI nie zweryfikuje fizycznego ekranu, gestów, GeckoView ani płynności 120 Hz.

## Nowe regresje w etapie 1

`VideoSearchAuditStage1GateTest`: (1) przejęta normalna karta workera nie wraca pod kontrolę workera; (2) suma 11×200 wynosi 2200, a 200×10000 wynosi 2 mln, przy odrzuceniu nielegalnych limitów; (3) cursor crawl nie wraca do zera po historycznej granicy 96 i >320 kartach; (4) ACK, przestawienie kart i zmiana samego tytułu w SPA nie dowodzą nowych wyników, zaś `no_results` i `STALLED` mają odrębne terminalne statusy.

Workflow `.github/workflows/zavro-p1-p16-stage1-regression.yml` uruchamia pełne Node/JS, JVM/Robolectric i Android lint. **Brak build/assembleDebug/APK i merge.** Zapisać w rozmowie finalny HEAD, run ID i faktyczne PASS/FAIL; nie dopisywać kolejnego commitu wyłącznie po ID runu.

## Wznowienie

Po zielonym CI **Etap 2/10 — prywatność i izolacja sesji**. Najpierw test faktycznej granicy close-last/reopen i orphan worker, następnie minimalna poprawka tylko jeśli odtworzy się błąd. Test urządzenia/Gecko bez fizycznego telefonu pozostaje NOT_TESTED.
