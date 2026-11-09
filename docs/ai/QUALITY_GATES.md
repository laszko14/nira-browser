# Zavro Clean V3 — bramki jakości G0–G7

Jedno miejsce decyzji o przyjęciu. Priorytety: nienaruszalność danych → poprawność → dowody → utrzymanie → wydajność.

- **G0 / Zakres:** ANALYZE i doc-only bez aplikacji/APK. IMPLEMENT tylko w zatwierdzonym zakresie. Bez merge, release, force-push, destrukcji ani zmiany certyfikatu.
- **G1 / Stan V3.2:** właściwy live branch, pełny SHA, AGENTS/handoff z jawnego identycznego ref, selektywne instrukcje według `docs/ai/STARTUP_PREFLIGHT.md` i brak konfliktu równoległych zapisów. Brak dowodów = `PREFLIGHT_BLOCKED`.
- **G2 / Przyczyna:** kontrprzykład, poprawna semantyka z TASK_TEMPLATE, dowód ryzyka i diagnoza. Dla CI odczytaj konkretny run/attempt/job/krok/log; oddziel potwierdzone fakty od hipotez.
- **G3 / Zmiana:** najmniejszy kompletny diff, testy celowane i regresje; brak osłabienia oracle lub lint. Po FAIL diagnozuj i napraw w ramach tego samego zadania. Powtórz run tylko dla potwierdzonego problemu przejściowego; błąd kodu/testu wymaga nowego SHA i odpowiedniej kontroli.
- **G4 / Review:** obowiązkowy krytyczny self-review. Dla wysokiego ryzyka odrębne review, gdy recenzent dostępny; brak recenzenta jawnie ogranicza ocenę.
- **G5 / SOURCE CI:** wszystkie wymagane joby procesu, JS, JVM/Robolectric, AndroidTest compile, lint + lint-regression i dowód checkoutu są SUCCESS na dokładnym finalnym źródłowym SHA. PR CI/integration jest odrębna bramka, jeśli wymagana. Skipped, failed, cancelled, missing, stale SHA, niepełna paginacja i CI_PENDING nie są PASS. Historyczny czerwony run dla starego SHA nie wymaga sztucznego ponawiania.
- **G5a / Pamięć błędów:** utrwalaj istotne odrębne incydenty w `docs/ai/ERROR_CASES.json`; duplikaty łącz fingerprintem i odwołaniem `duplicate_of`, nie twórz osobnych reguł dla chwilowych ponowień. Nie deklaruj kompletności bez porównania źródeł.
- **G6 / APK:** tylko przy upoważnieniu i bezpiecznej recenzji PR według `docs/ai/APK_CHAT_GITHUB_REVIEW_TRIGGER.md`, po G5. Doc-only: NOT_BUILT.
- **G7 / Stop:** `CODE_VERIFIED` oznacza kod/proces i wszystkie wymagane CI potwierdzone na końcowym SHA. `CI_PENDING` oznacza run trwający, nigdy PASS. `NOT_TESTED` oznacza brak wykonanej weryfikacji; realną blokadę raportuj jako `CI_BLOCKED` z dowodem i brakującą czynnością. `APK_VERIFIED` wymaga zbudowanego artefaktu i zweryfikowanego podpisu; `DEVICE_VERIFIED` wymaga rzeczywistego testu urządzenia. Brak testu to `NOT_TESTED`.
