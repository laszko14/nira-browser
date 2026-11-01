# AI_HANDOFF — Zavro Clean · V3, główna gałąź robocza
> Jedyny bieżący checkpoint tej gałęzi. Wersja aplikacji: Clean v1207 z `feat/zavro-clean-search-redesign-v2-20261101` (`b161b9403425435e106498c382da34b5e3cb3d58`). Proces V3 przejęty z `chore/zavro-clean-codex-v3-20261101`; wcześniejsze materiały: [archiwum](ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md). Handoff jest snapshotem, nie bieżącym HEAD.

```yaml
task_id: ZC-V31-ERROR-LEARNING
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-v3-20261101
base_sha: ff62a26d3d1259626e042afaff702c8c9c1c6610
observed_head_sha: 7ea23d0de749a59f322d1735f12507b218b3c725
status: CI_PENDING
acceptance_source: Uzytkownik polecil wdrozyc V3.1 automatyczne dowody bledow CI i trwaly rejestr zgłoszen, bez zmian w app i bez APK
required_ci: process-docs, shared-tests (JS, JVM/Robolectric, AndroidTest compile, lint + lint-regression), quality-evidence na dokladnym finalnym SOURCE SHA
device: NOT_TESTED
apk: NOT_BUILT - jawnie bez APK, bez podpisu i bez review trigger
completed: Uruchomiono V3.1 na bazie Clean v1207; dodano rejestr ERROR_CASES, automatyczny capture CI, walidacje i testy; stare zdarzenia z niedostepnych rozmow pozostaja nieodtworzone
remaining: Koncowy SOURCE CI dla V3.1, odrebne review workflow wysokiego ryzyka, weryfikacja zbierania artefaktow w realnym CI; A/B NOT_MEASURED
known_failures: Brak stwierdzonych nowych regresji na poprzednim finalnym SHA; historyczne F01-F12 wymagaja ponownej oceny jesli dotycza nowego zadania
verification: Nie uznawaj CI_PENDING ani zielonego CI poprzedniego SHA za PASS nowego; AndroidTest compile nie oznacza testu na telefonie
changed_paths:
  - docs/AI_HANDOFF.md (checkpoint)
  - docs/ai/ERROR_CASES.json i ERROR_LEARNING.md
  - scripts/error-learning* i capture-ci-errors.cjs
  - .github/workflows/ artefakty odczytu CI bez APK
  - AGENTS.md, QUALITY_GATES.md, TEST_POLICY.md, CHECKPOINT_POLICY.md
verified_before_push:
  - Clean v1207 b161b9403425435e106498c382da34b5e3cb3d58; proces V3 ff62a26d3d1259626e042afaff702c8c9c1c6610
  - Wymagane SOURCE CI i PR CI finalnego procesu success 1/1; porownanie z v1207 daje zero zmienionych plikow app/
blockers:
  - Brak niezaleznej recenzji security; self-review nie jest jej substytutem
  - Brak lokalnego checkoutu i fizycznego testu urzadzenia
next_action: Zweryfikuj wymagane SOURCE CI i artefakt V3.1 na koncowym SHA, rozlicz kazde nowe CI FAIL i zgłoszenie; bez APK i merge
evidence_links:
  - https://github.com/laszko14/nira-browser/actions/runs/41332183638
  - https://github.com/laszko14/nira-browser/actions/runs/41332187317
  - https://github.com/laszko14/nira-browser/pull/118
```
