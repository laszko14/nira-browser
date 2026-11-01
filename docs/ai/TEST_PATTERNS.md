# Wzorce testów, przegląd i zakończenie — Zavro Clean

## Działające wzorce z repozytorium

- **Robolectric:** `app/src/test/java/com/prirai/android/nira/audit/AppBoundariesTest.kt` i `videosearch/VideoSearchV32IntegrationTest.kt` używają `@RunWith(RobolectricTestRunner::class)` i `@Config(sdk = [35], application = Application::class, manifest = Config.NONE)`. Pobieraj Application przez `RuntimeEnvironment.getApplication()` **bez parametru typu**. Sprawdź zależności i wersje przed kopiowaniem testu; obecny katalog ma AGP 9.3.1, Kotlin 2.4.10, Room 2.8.4. Dla Room preferuj `Room.inMemoryDatabaseBuilder` z zamknięciem `try/finally`; dla migracji użyj izolowanego pliku i testu `VideoSearchDatabaseMigrationV2Test.kt`. Nie zakładaj, że `ApplicationProvider` działa bez osobnej zależności.
- **JS/WebExtension:** `app/src/test/js/video-search-p11-pagination-evidence.test.cjs` uruchamia rzeczywisty `content.js` z kontrolowanym DOM; `media-detector.test.cjs` uruchamia prawdziwy `background.js` z atrapą protokołu. `helpers/transport-snapshot.cjs` rejestruje JSON-kompatybilne payloady **w momencie wysłania**; testuje go `transport-snapshot-contract.test.cjs` i HLS 17C. Snapshot JSON nie emuluje pełnej semanty browser structured clone, binariów ani fizycznego Gecko.
- **Kontrole strukturalne to nie test zachowania:** `toolbar-panel-ownership-audit60.test.cjs` czyta regexy Kotlin. Prawdziwy scenariusz gestu wykonuje `AppBoundariesTest.cancelledEdgeGestureNeverCommitsPanelOpening`. Zmiana nazwy metody może zepsuć regex; pozostaw go jako pomocniczy do chwili zastąpienia gwarancją zachowania.

## Sprawdzanie poprawki

1. Zanotuj minimalny przykład błędu (stan/wejście/zły wynik), a następnie test działającego API/stanów, nie nazw funkcji. Dla zmiany API wyszukaj **wszystkie** wywołania w produkcji/testach/fixture i skompiluj testy; błąd 18D dotyczył 4 wywołań `downloadChunk`.
2. Odtwórz RED na starej implementacji, jeśli możliwe. Po poprawce harnessu potwierdź, że nadal wykrywa stary błąd. **W tym etapie:** negatywne kontrole wykazują uszkodzenie płytkiej kopii IPC, lecz nie wykonano historycznej wersji produkcyjnej sprzed 17C z nowym harness — ten RED pozostaje `NOT_TESTED`.
3. Dla asynchronii: owner, epoka, anulowanie, Stop/Start i spóźniony callback; dla danych: atomowość, restart, rollback i profile; dla parserów: poprawne, błędne i graniczne dane. Deterministyczny scheduler zamiast `sleep`.
4. Szybkie testy modułu poprzedzają niezbędny końcowy CI. `CODE_VERIFIED` wyłącznie gdy wymagane joby końcowego SHA są SUCCESS; `DEVICE_VERIFIED` wymaga wykonania na urządzeniu.

## Checklista zamknięcia

- [ ] Dowód dla każdej akceptacji lub jawne `NOT_TESTED`.
- [ ] Wszystkie zależne callsite, sąsiednia ścieżka i wyścigi sprawdzone.
- [ ] Test zachowania z poprawnym oracle; RED na starym kodzie lub ograniczenie jawne.
- [ ] Finalny HEAD, checkout SHA, workflow/run/attempt i required jobs sprawdzone; `skipped` nie jest PASS.
- [ ] Urządzenie, Gecko, APK i podpis ocenione osobno; bez merge i publicznego release; prywatny APK po IMPLEMENT i wymaganym CI, nie po analizie.
- [ ] Checkpoint przed końcowym pushem, raport krótki, bez commita tylko dla wyniku CI.

## Delegowanie i niezależne review

**Dobór narzędzi:** zwykły ChatGPT + GitHub jest preferowany; Codex jako rezerwa, gdy to realnie potrzebne lub zwykłe narzędzia nie wystarczą. Przekaż jeden task_id, branch i SHA, diff, kontrprzykład i log, nie powtarzaj audytu.

**Niezależne review:** wymagane dla prywatności, trwałych danych, lifecycle, źródeł mediów i podpisywania. Recenzent czyta *oryginalne wymaganie*, diff i testy, nie tylko opis autora. Pyta o brakujący negatywny przypadek oraz granice dowodu. Opinia innego modelu nie zastępuje regresji.
