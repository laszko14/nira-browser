# Zavro Clean V3 — bramki jakości G0–G7
Jedno miejsce decyzji o przyjęciu. Priorytety: nienaruszalność danych → poprawność → dowody → utrzymanie → wydajność.
- **G0 / Zakres:** ANALYZE i doc-only bez aplikacji/APK. IMPLEMENT tylko w zatwierdzonym zakresie. Bez merge, release, force-push, destrukcji ani zmiany certyfikatu.
- **G1 / Stan:** właściwy live branch, pełny SHA, AGENTS/handoff z jawnego ref, brak konfliktu równoległych zapisów.
- **G2 / Przyczyna:** kontrprzykład, poprawna semantyka z TASK_TEMPLATE, dowód ryzyka i diagnoza.
- **G3 / Zmiana:** najmniejszy kompletny diff, RED/GREEN gdy wykonalne, wszystkie callsite i regresje; brak osłabienia oracle lub lint.
- **G4 / Review:** obowiązkowy krytyczny self-review. Dla wysokiego ryzyka (prywatność, dane, lifecycle, współbieżność, podpis, CI) wymagane odrębne review, gdy recenzent jest dostępny; jeśli wymagany recenzent niedostępny, oznacz ograniczenie i nie ogłaszaj pełnej akceptacji ryzyka.
- **G5 / SOURCE CI:** wymagane joby procesu, JS, JVM/Robolectric, AndroidTest compile, lint + lint-regression i dowód checkoutu są SUCCESS na **dokładnym finalnym źródłowym SHA**. PR merge-ref to osobne INTEGRATION, nie SOURCE. Skipped, failed, missing, cancelled, stale SHA, niepełna paginacja i CI_PENDING nie są PASS. Build/release SKIPPED w tests-only są poprawne jako niewymagane.
- **G6 / APK:** tylko przy upoważnieniu i bezpiecznej recenzji PR według APK_CHAT_GITHUB_REVIEW_TRIGGER.md, po G5. Verifier i niezmienione signing Secrets; brak drogi lub zgodności = APK_BLOCKED. Doc-only V3: NOT_BUILT.
- **G7 / Stop:** oddzielnie CODE_VERIFIED (proces/kod i wymagane CI), APK_VERIFIED (wyłącznie dowód zbudowanego i zweryfikowanego podpisu), DEVICE_VERIFIED (faktyczny test telefonu). Nieobecny test = NOT_TESTED. Nie myl kompilacji AndroidTest z urządzeniem.
