# AI_HANDOFF — Zavro Clean · V3, główna gałąź robocza
> Jedyny bieżący checkpoint tej gałęzi. Wersja aplikacji: Clean v1207 z `feat/zavro-clean-search-redesign-v2-20261101` (`b161b9403425435e106498c382da34b5e3cb3d58`). Proces V3 przejęty z `chore/zavro-clean-codex-v3-20261101`; wcześniejsze materiały: [archiwum](ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md). Handoff jest snapshotem, nie bieżącym HEAD.

```yaml
task_id: ZC-V32-CI-CAPTURE-FIDELITY
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-v3-20261101
base_sha: ff62a26d3d1259626e042afaff702c8c9c1c6610
observed_head_sha: ccbed9f4c4ffcf7cbaaaa3959ab7079bfc2affbd
status: CI_PENDING
acceptance_source: Kontynuacja V3.2; sprawdzono rzeczywiste artefakty CI, wykryto i naprawiono bledne oznaczanie anulowanych jobow jako CI_FAILURE. Bez APK i merge.
required_ci: process-docs, shared-tests (JS, JVM/Robolectric, AndroidTest compile, lint + lint-regression), quality-evidence na dokladnym finalnym SOURCE SHA
device: NOT_TESTED
apk: NOT_BUILT - jawnie bez APK, bez podpisu i bez review trigger
completed: Nowa rozmowa G1 odczytala z GitHub wspolny live SHA, SOURCE i PR CI wczesniej byly SUCCESS na c387013e. Cztery artefakty historyczne zawieraly 4 rzeczywiste failure oraz 12 cancelled blednie oznaczonych CI_FAILURE. Naprawiono klasyfikator, dodano 2 regresje i wpis ERR-20261101-0002 z fix_sha ccbed9f4.
remaining: SOURCE CI i PR CI po koncowym commicie; odrebna recenzja security PR 119 oraz praktyczna obserwacja kompletnosci zapisywania bledow z rozmow. Pierwotne 2/10 nadal OPEN.
known_failures: ERROR_CASES 6 observed, 6 recorded, 5 fixed, 1 user report OPEN. Anulowane joby sa INTERRUPTIONS a nie rzeczywistymi CI_FAILURE. Brak podstaw do deklaracji 10/10 w kolejnych rozmowach.
verification: SOURCE 41338925494 i PR 41338929652 SUCCESS na c387013e, poprzednim SHA. Artefakty 41334916448, 41335018971, 41337049270, 41337163423 zweryfikowane. Nowe SOURCE i PR CI na finalnym SHA pending; niezalezne security review NIE wykonane.
changed_paths:
  - scripts/capture-ci-errors.cjs (odroznienie failure i interruptions)
  - scripts/error-learning.test.cjs (historyczny RED/GREEN i same interruptions)
  - docs/ai/ERROR_CASES.json (nowe zdarzenie, test i fix_sha)
  - docs/AI_HANDOFF.md (checkpoint)
verified_before_push:
  - Clean v1207 b161b9403425435e106498c382da34b5e3cb3d58; proces V3 ff62a26d3d1259626e042afaff702c8c9c1c6610
  - Wymagane SOURCE CI i PR CI finalnego procesu success 1/1; porownanie z v1207 daje zero zmienionych plikow app/
blockers:
  - Brak niezaleznej recenzji security; self-review nie jest jej substytutem
  - Brak lokalnego checkoutu i fizycznego testu urzadzenia
next_action: Sprawdz finalne SOURCE i PR CI oraz komplet 6 zadan na tym samym HEAD. Następnie odrebne security review PR #119; user report 2/10 zostaw OPEN. Bez APK.
evidence_links:
  - https://github.com/laszko14/nira-browser/actions/runs/41338925494
  - https://github.com/laszko14/nira-browser/actions/runs/41338929652
  - https://github.com/laszko14/nira-browser/actions/runs/41334916448
  - https://github.com/laszko14/nira-browser/pull/119
```
