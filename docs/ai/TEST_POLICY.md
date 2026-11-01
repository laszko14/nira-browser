# Zavro Clean V3 — testy, triage i regresje
## Pętla diagnoza → test → naprawa
1. Zapisz źródło: HEAD SHA, workflow/run/attempt/job, komendę i **pierwszy istotny FAIL**. Najpierw status/steps, potem właściwy fragment logu, zamiast ponownego pobierania całości.
2. Przypisz przyczynę na podstawie dowodu: `production`, `test_harness`, `workflow`, `environment`, `intentional_red` lub `undetermined`. Odróżnij błąd aplikacji od fixture i ograniczenia JDK.
3. Postaw hipotezę odróżniającą co najmniej dwie możliwe przyczyny. Test RED dla rzeczywistego kontrprzykładu, gdy wykonalny.
4. Napraw minimalny właściwy element, bez tłumienia błędów i osłabiania oracle. Po zmianie fixture sprawdź, czy historyczny wadliwy wariant nadal jest wykrywany.
5. Uruchom test bezpośredni i zależne; dla Kotlin API wszystkie callsite, fixture i kompilacja dotkniętych testów.
6. Dwa FAIL z tym samym fingerprintem (typ + test + pierwszy błąd + źródło) bez nowej informacji: zmień reproduktor, odczyt lub metodę, zamiast kolejnego zgadywanego pushu.
7. Brak JDK/narzędzia nie uzasadnia zmiany poprawnego kodu; raportuj blokadę i korzystaj z właściwego CI.
8. Po stabilizacji: self-review, wymagane CI końcowego źródła i checkpoint. Kolejna istotna poprawka = nowe źródłowe SHA i ponowna kontrola.

## V3.1 — przypisanie błędu do rejestru
Po każdym nowym FAIL lub zgłoszeniu przypisz identyfikator w `docs/ai/ERROR_CASES.json`; nie pomijaj powtórek, a brak przyczyny pozostaw `undetermined`. Gdy zapisy w rejestrze są poza zakresem bieżącego commita, zaznacz `NOT_RECONCILED` i nie twierdź, że nauka została ukończona. Prawdziwe zakończenie wymaga porównania liczby odrębnych obserwacji z liczbą wpisów i analizy artefaktów CI.

## Dobór testów według ryzyka
- JS/parser/WebExtension: `node --test app/src/test/js/*.test.cjs` i negatywne warianty protokołu.
- Kotlin/Room/UI: `./gradlew :app:testDebugUnitTest`, Robolectric, zmiany sygnatur/fixture; profile, prywatność, migracje, atomowość, rollback, restart.
- Lifecycle/Gecko/asynchronia: owner, cancel, Stop/Start, spóźniony callback, zmiana profilu, ponowna inicjalizacja.
- Orientacja/scroll/gesty: pion i poziom, widoczność/dotyk, zwijanie, powrót, puste dane; test struktury nie zastępuje testu zachowania.
- AndroidTest: `./gradlew :app:compileDebugAndroidTestKotlin` oznacza tylko kompilację, bez testu urządzenia.
- Lint: `./gradlew :app:lintDebug` i obowiązkowy `scripts/lint-regression.cjs` z zachowanym baseline. Liczniki lint nie dowodzą tożsamości każdej usterki.
W iteracji uruchamiaj testy trafione, przed zamknięciem wszystkie **obowiązkowe** bramki na finalnym source SHA. NO-SOURCE, skipped i NOT_TESTED nie są PASS. Wzorce: TEST_PATTERNS.md. Statusy: QUALITY_GATES.md.
