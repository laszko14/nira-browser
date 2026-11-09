# Zavro Clean — współpraca ChatGPT, Codex i GitHub (V3.2)

**Mapa systemu, nie druga instrukcja nadrzędna.**

**ChatGPT**: ustala zakres pytania i upoważnienie, odczytuje bieżący HEAD oraz `AGENTS.md` i `docs/AI_HANDOFF.md` bezpośrednio z GitHuba; wybiera odpowiednie źródła przez `docs/ai/STARTUP_PREFLIGHT.md`. Projektowe Źródła mogą pomóc z kontekstem, ale nie zastępują repozytorium.

**Codex**: jeśli używany do implementacji, startuje z aktualnego checkoutu i `AGENTS.md`; lokalna kontrola `scripts/ai-preflight.cjs` wykrywa niezgodny SHA, branch i brakujące pliki. To potwierdza źródła na dysku, nie zrozumienie ich przez model.

**GitHub Actions**: `.github/workflows/zavro-ai-process-docs.yml` sprawdza źródłowy commit, instrukcje, V3.2, skrypty, testy JS/JVM, kompilację AndroidTest i lint. `pull_request` to dodatkowe testy integracyjne, nie substytut SOURCE CI. Po FAIL stosuj `docs/ai/TEST_POLICY.md`: diagnozuj i naprawiaj w tym samym zadaniu; wymagane kontrole muszą przejść na końcowym SHA przed następnym etapem. Nie oznaczaj `CI_PENDING` jako PASS.

**Pamięć błędów V3.2**: `docs/ai/ERROR_CASES.json` i `docs/ai/ERROR_LEARNING.md` są jedynym kanonem. Zapisuj istotne incydenty z dowodami; łącz duplikaty po fingerprint i nie twórz osobnej reguły dla chwilowego ponowienia. Artefakty `v31-ci-errors-*` są tymczasowym dowodem, same nie edytują repo. Błędy rozmowy zapisuje agent, bez sekretów i danych prywatnych.

**APK i bezpieczeństwo**: prywatne aktualizacje tylko przez zatwierdzony mechanizm w `docs/ai/APK_CHAT_GITHUB_REVIEW_TRIGGER.md`, po wymaganym SOURCE CI i za wyraźną zgodą. Zwykły push lub PR nie podpisuje APK. Bez samowolnego merge, release, force-push ani zmiany certyfikatu.

**Przekazanie**: `docs/AI_HANDOFF.md` to jeden checkpoint na gałęzi; w nowej rozmowie porównaj go z live HEAD, nie przyjmuj historii za stan bieżący.

Plik do ręcznego dodania do Źródeł Projektu; sama obecność na GitHubie nie zmienia ustawień ChatGPT.
