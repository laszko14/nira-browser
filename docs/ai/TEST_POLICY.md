# Zavro Clean v2.0 — automatyczna polityka testów
- Przed naprawą zbierz obserwowalny kontrprzykład, prawidłowy wynik i test RED, jeśli możliwy. Przy zmianie harnessu sprawdź, czy test nadal wykrywa historyczny błąd.
- W trakcie wykonuj szybkie testy modułu, a przed zakończeniem pełne wymagane JS, JVM/Robolectric, AndroidTest compile, lint i lint-regression oraz dodatkowe testy danego zadania.
- Test strukturalny/regex to pomocnicze sprawdzenie kontraktu, nie test działania funkcji. Uruchomione testy raportuj z realnym wynikiem i liczbą. NO-SOURCE, skipped i NOT_TESTED nie są PASS.
- Kombinacje opcji, brak danych, błędny input, obciążenie, przerywanie, race, orientacja i restart według ryzyka. Dla danych: atomowość, migracja, profil, rollback; dla lifecycle: owner/stop/cancel/late callback.
- Nigdy nie usuwaj testów, nie obniżaj asercji ani nie resetuj lint baseline tylko dla zielonego CI. Porównuj wyniki przed i po zmianie na wiarygodnej bazie.
- AndroidTest compile nie oznacza testu na telefonie. Finalne CI musi być GREEN na dokładnym końcowym SHA. Osobno oznacz DEVICE_NOT_TESTED.
Wzorce techniczne: `docs/ai/TEST_PATTERNS.md`; krytyczne bramki: `docs/ai/QUALITY_GATES.md`.
