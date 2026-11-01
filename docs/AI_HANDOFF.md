# AI_HANDOFF — Zavro Clean · V3, główna gałąź robocza
> Jedyny bieżący checkpoint tej gałęzi. Wersja aplikacji: Clean v1207 z `feat/zavro-clean-search-redesign-v2-20261101` (`b161b9403425435e106498c382da34b5e3cb3d58`). Proces V3 przejęty z `chore/zavro-clean-codex-v3-20261101`; wcześniejsze materiały: [archiwum](ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md). Handoff jest snapshotem, nie bieżącym HEAD.

```yaml
task_id: ZC-V32-PREFLIGHT-SOURCES
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-v3-20261101
base_sha: ff62a26d3d1259626e042afaff702c8c9c1c6610
observed_head_sha: 75d59b0ba47269b84e4e027556fd59dcbee66287
status: CI_PENDING
acceptance_source: Uzytkownik zamowil V3.2 preflight, wymagane CI, trzy pliki Zrodla oraz kontrole zgodnosci. Bez aplikacji, APK i merge.
required_ci: process-docs, shared-tests (JS, JVM/Robolectric, AndroidTest compile, lint + lint-regression), quality-evidence na dokladnym finalnym SOURCE SHA
device: NOT_TESTED
apk: NOT_BUILT - jawnie bez APK, bez podpisu i bez review trigger
completed: V3.2 wprowadzilo fail-closed lokalny preflight, sciezki selektywnego odczytu, 3 statyczne pliki zrodlowe, ich kontrakt i testy bez ingerencji w app/
remaining: Weryfikacja nowego SOURCE CI i PR CI na finalnym SHA, recenzja niezalezna wysokiego ryzyka gdy dostepna, reczne dodanie plikow do Zrodel Projektu ChatGPT
known_failures: V3.1 ma nadal dwa otwarte zgłoszenia; brak gwarancji pelnego odczytu w kazdym nowym czacie bez kontroli operacyjnej
verification: Nie uznawaj CI_PENDING ani zielonego CI poprzedniego SHA za PASS nowego; AndroidTest compile nie oznacza testu na telefonie
changed_paths:
  - AGENTS.md, docs/ai/STARTUP_PREFLIGHT.md, docs/ai/CHATGPT_PROJECT_INSTRUCTIONS.md
  - docs/ai/DEVELOPMENT_WORKFLOW.md, docs/ai/QUALITY_GATES.md, docs/AI_HANDOFF.md
  - .github/agents.md, .github/copilot-instructions.md, .github/workflows/zavro-ai-process-docs.yml
  - scripts/ai-preflight.cjs i testy; scripts/project-sources.test.cjs
  - docs/ai/project-sources/ (3 informacyjne pliki do recznego zalaczenia)
verified_before_push:
  - Clean v1207 b161b9403425435e106498c382da34b5e3cb3d58; proces V3 ff62a26d3d1259626e042afaff702c8c9c1c6610
  - Wymagane SOURCE CI i PR CI finalnego procesu success 1/1; porownanie z v1207 daje zero zmienionych plikow app/
blockers:
  - Brak niezaleznej recenzji security; self-review nie jest jej substytutem
  - Brak lokalnego checkoutu i fizycznego testu urzadzenia
next_action: Po zielonym finalnym CI odbierz V3.2, dodaj 3 pliki Zrodla w Projekcie ChatGPT; nastepne zadania zaczynaja sie od live ref, AGENTS i handoff
evidence_links:
  - https://github.com/laszko14/nira-browser/actions/runs/41332183638
  - https://github.com/laszko14/nira-browser/actions/runs/41332187317
  - https://github.com/laszko14/nira-browser/pull/118
```
