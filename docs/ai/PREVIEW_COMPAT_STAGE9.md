# Zavro Clean — etap 9/10: pełne regresje

Baza: `cff8ecaf65b5d78e103f6d8043035ee3c3adcd24` (etap 8). Zmiany produkcyjne w tym etapie: **brak**. Rejestr błędów uzgodniono z potwierdzonymi SOURCE/PR runami etapów 6 i 8; historyczne przypadki zależne od prawdziwej witryny lub urządzenia pozostają otwarte.

## Dostępna weryfikacja

- Cały zestaw `app/src/test/js/*.test.cjs`: **631/631 PASS**. Obejmuje produkcyjne WebExtension oraz testy wyszukiwania, wyników i linków wideo, podglądów, autoplay, background play, gestów, kart, UI i kontraktów wydania.
- Cały zestaw `scripts/*.test.cjs`: **62/62 PASS**. Obejmuje checkpoint, preflight, polityki CI, evidence, lint baseline i error-learning.
- `node scripts/error-learning.cjs validate`: PASS, 51 zadań / 215 zapisanych przypadków / 46 otwartych.
- Pełny `:app:testDebugUnitTest` w CI obejmuje istniejące testy Kotlin/Robolectric wyszukiwarki filmów, pobierania, kart i grup, historii, sesji, profili normalnych/prywatnych, ustawień, MediaSession, AudioFocus, WebExtension, allowlist, gestów, UI i zewnętrznych nagłówków MPV Alpha. `:app:compileDebugAndroidTestKotlin`, `:app:lintDebug` i `scripts/lint-regression.cjs` są odrębnymi obowiązkowymi jobami SOURCE i PR CI.

Nie było lokalnej dystrybucji Gradle 9.7.1; lokalny wrapper otrzymał `Connection refused` podczas jej pobierania. JVM, AndroidTest compile i lint są więc potwierdzane przez GitHub Actions na końcowym SHA. Kompilacja AndroidTest nie wykonuje testu na telefonie. **DEVICE: NOT_TESTED**; konkretne witryny, iframe i zewnętrzny MPV Alpha na Nothing Phone (1) wymagają późniejszej weryfikacji runtime.
