# AI_HANDOFF — Zavro Clean · migracja Codex V3
> Ta gałąź procesu jest odseparowana od linii funkcjonalnej v1207. Checkpoint redesignu pozostaje na `feat/zavro-clean-search-redesign-v2-20261101`. Historia: [archiwum](ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md).

```yaml
task_id: ZAVRO-CLEAN-CODEX-V3-20261101
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: chore/zavro-clean-codex-v3-20261101
base_sha: b161b9403425435e106498c382da34b5e3cb3d58
observed_head_sha: b161b9403425435e106498c382da34b5e3cb3d58
status: CI_PENDING
acceptance_source: Zatwierdzony plan Zavro Clean Codex V3 z 2026-11-01; doc-only IMPLEMENT, bazą aplikacji jest v1207, bez APK, bez zmian aplikacji
required_ci: proces dokumentów, shared JS/JVM/AndroidTest compile/lint + lint-regression, SOURCE SHA i evidence na końcowym commicie
device: NOT_TESTED
apk: NOT_BUILT - jawny zakres BEZ APK
completed: Rozpoznano stan v1207 na b161b940; izolowana gałąź procesu; przygotowano komplet polityk i kontrakty V3
remaining: Walidacja i poprawki CI, neutralność testów wobec task_id i brancha, safety review, finalny push/PR i końcowe required CI, pomiar pilotażu tylko gdy możliwy
known_failures: Historyczne F01-F12 wymagają ponownej oceny względem finalnego SHA; pilot A/B pozostaje NOT_MEASURED
verification: Realny wynik każdego required joba oraz checkout SOURCE ma być sprawdzony na końcowym SHA; nie wyciągaj PASS z tego planowanego checkpointu
changed_paths:
  - AGENTS.md i docs/ai/* polityki i bootstrap
  - docs/AI_HANDOFF.md
  - scripts/* testy procesu
verified_before_push:
  - Bazowy HEAD v1207 b161b9403425435e106498c382da34b5e3cb3d58 odczytany przez bezpośredni GitHub connector
  - Plan Work przeanalizował F01-F12 i R01-R48; raport nie jest implementacją
blockers:
  - Brak lokalnego checkoutu/urządzenia; testy runtime wyłącznie przez CI, brak modelowego replay
next_action: Zweryfikuj pełny diff V3 i uruchom required SOURCE CI na finalnym SHA; bez żadnego APK
evidence_links:
  - https://github.com/laszko14/nira-browser/commit/b161b9403425435e106498c382da34b5e3cb3d58
  - https://github.com/laszko14/nira-browser/pull/117
```
