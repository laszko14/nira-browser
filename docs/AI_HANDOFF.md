# AI_HANDOFF — Zavro Clean · V3, główna gałąź robocza
> Jedyny bieżący checkpoint tej gałęzi. Wersja aplikacji: Clean v1207 z `feat/zavro-clean-search-redesign-v2-20261101` (`b161b9403425435e106498c382da34b5e3cb3d58`). Proces V3 przejęty z `chore/zavro-clean-codex-v3-20261101`; wcześniejsze materiały: [archiwum](ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md). Handoff jest snapshotem, nie bieżącym HEAD.

```yaml
task_id: ZAVRO-CLEAN-V3-BOOTSTRAP-20261101
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-v3-20261101
base_sha: ff62a26d3d1259626e042afaff702c8c9c1c6610
observed_head_sha: ff62a26d3d1259626e042afaff702c8c9c1c6610
status: CI_PENDING
acceptance_source: Uzytkownik polecil przygotowac glowna galaz robocza V3 bez APK i merge, bez zmian w kodzie aplikacji
required_ci: process-docs, shared-tests (JS, JVM/Robolectric, AndroidTest compile, lint + lint-regression), quality-evidence na dokladnym finalnym SOURCE SHA
device: NOT_TESTED
apk: NOT_BUILT - jawnie bez APK, bez podpisu i bez review trigger
completed: Wydzielono galaz robocza z weryfikowanego V3 ff62a26d; Clean v1207 bez zmian w app; historyczne workflow podpisu wycofano tylko z V3; poprzednie SOURCE run 41332183638 i PR run 41332187317 zakonczone success, attempt 1
remaining: Koncowy SOURCE CI nowego HEAD, niezalezne review obszaru CI i podpisu gdy bedzie dostepny; pilot A/B nadal NOT_MEASURED
known_failures: Brak stwierdzonych nowych regresji na poprzednim finalnym SHA; historyczne F01-F12 wymagaja ponownej oceny jesli dotycza nowego zadania
verification: Nie uznawaj CI_PENDING ani zielonego CI poprzedniego SHA za PASS nowego; AndroidTest compile nie oznacza testu na telefonie
changed_paths:
  - docs/AI_HANDOFF.md (jedyny checkpoint nowej galezi)
verified_before_push:
  - Clean v1207 b161b9403425435e106498c382da34b5e3cb3d58; proces V3 ff62a26d3d1259626e042afaff702c8c9c1c6610
  - Wymagane SOURCE CI i PR CI finalnego procesu success 1/1; porownanie z v1207 daje zero zmienionych plikow app/
blockers:
  - Brak niezaleznej recenzji security; self-review nie jest jej substytutem
  - Brak lokalnego checkoutu i fizycznego testu urzadzenia
next_action: Po potwierdzeniu SOURCE CI na nowym finalnym HEAD, kolejne zadanie zaczynaj od ponownego odczytu zywego HEAD, AGENTS.md i handoff tej galezi; bez merge ani APK bez nowej zgody
evidence_links:
  - https://github.com/laszko14/nira-browser/actions/runs/41332183638
  - https://github.com/laszko14/nira-browser/actions/runs/41332187317
  - https://github.com/laszko14/nira-browser/pull/118
```
