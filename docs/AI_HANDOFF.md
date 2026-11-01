# AI_HANDOFF — Zavro Clean · V3, główna gałąź robocza
> Jedyny bieżący checkpoint tej gałęzi. Wersja aplikacji: Clean v1207 z `feat/zavro-clean-search-redesign-v2-20261101` (`b161b9403425435e106498c382da34b5e3cb3d58`). Proces V3 przejęty z `chore/zavro-clean-codex-v3-20261101`; wcześniejsze materiały: [archiwum](ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md). Handoff jest snapshotem, nie bieżącym HEAD.

```yaml
task_id: ZC-V32-CLOSEOUT-REVIEW-SMOKE
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-v3-20261101
base_sha: ff62a26d3d1259626e042afaff702c8c9c1c6610
observed_head_sha: 5458e884ebb5b135db1caa7f2f3f7a0f4ddfe43c
status: CI_PENDING
acceptance_source: Uzytkownik polecil zamknac 3 zweryfikowane techniczne bledy, wykonac security self-review i przygotowac praktyczny test nowej rozmowy. Bez APK i merge.
required_ci: process-docs, shared-tests (JS, JVM/Robolectric, AndroidTest compile, lint + lint-regression), quality-evidence na dokladnym finalnym SOURCE SHA
device: NOT_TESTED
apk: NOT_BUILT - jawnie bez APK, bez podpisu i bez review trigger
completed: Zamkniete 3 potwierdzone regresje testow z fix_sha i dowodami GitHub, przygotowany raport security self-review i instrukcja FRESH_CHAT. Poprzednie SOURCE oraz PR CI na 5458e884 SUCCESS.
remaining: Nowe SOURCE CI i PR CI tego checkpointu; niezalezne review PR 119, test w rzeczywiscie nowym czacie oraz potwierdzenie 3 plikow Zrodla ChatGPT. Pierwotne 2/10 nadal OPEN.
known_failures: ERROR_CASES 5 observed, 5 recorded, 4 fixed, 1 user report OPEN. Brak podstaw do deklaracji 10/10 w kolejnych rozmowach.
verification: SOURCE 41337335979 i PR 41337339338 byly success na poprzednim SHA; nowe wymagane CI do zweryfikowania na nowym finalnym SHA. Fresh chat i niezalezne security review NIE wykonane.
changed_paths:
  - docs/ai/ERROR_CASES.json (3 potwierdzone wpisy FIXED)
  - docs/ai/V32_REVIEW_SMOKE.md (self-review i scenariusz nowej rozmowy)
  - docs/AI_HANDOFF.md (checkpoint)
verified_before_push:
  - Clean v1207 b161b9403425435e106498c382da34b5e3cb3d58; proces V3 ff62a26d3d1259626e042afaff702c8c9c1c6610
  - Wymagane SOURCE CI i PR CI finalnego procesu success 1/1; porownanie z v1207 daje zero zmienionych plikow app/
blockers:
  - Brak niezaleznej recenzji security; self-review nie jest jej substytutem
  - Brak lokalnego checkoutu i fizycznego testu urzadzenia
next_action: Sprawdz CI koncowego SHA i raport docs/ai/V32_REVIEW_SMOKE.md; wykonaj osobna nowa rozmowe jako smoke test, a niezalezna recenzje PR #119 pozostaw otwarta. Nie buduj APK.
evidence_links:
  - https://github.com/laszko14/nira-browser/actions/runs/41337335979
  - https://github.com/laszko14/nira-browser/actions/runs/41337339338
  - https://github.com/laszko14/nira-browser/pull/119
```
