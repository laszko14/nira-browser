# AI_HANDOFF — Zavro Clean · V3, główna gałąź robocza
> Jedyny bieżący checkpoint tej gałęzi. Wersja aplikacji: Clean v1207 z `feat/zavro-clean-search-redesign-v2-20261101` (`b161b9403425435e106498c382da34b5e3cb3d58`). Proces V3 przejęty z `chore/zavro-clean-codex-v3-20261101`; wcześniejsze materiały: [archiwum](ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md). Handoff jest snapshotem, nie bieżącym HEAD.

```yaml
task_id: ZC-V32-CHAT-ERROR-SMOKE
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-v3-20261101
base_sha: ff62a26d3d1259626e042afaff702c8c9c1c6610
observed_head_sha: c39c4edea3d598e050c134b166b9153a23c57e89
status: CI_PENDING
acceptance_source: V3.2 etap 2/3; praktyczny smoke z rzeczywistej rozmowy: zapis negatywnego wyniku prosby o niezalezna recenzje PR #119 do ERROR_CASES. Bez APK, merge i zmian aplikacji.
required_ci: process-docs, shared-tests (JS, JVM/Robolectric, AndroidTest compile, lint + lint-regression), quality-evidence na dokladnym finalnym SOURCE SHA
device: NOT_TESTED
apk: NOT_BUILT - jawnie bez APK, bez podpisu i bez review trigger
completed: Preflight G1 przez GitHub na live c39c4ede, SOURCE 41341275888 oraz PR 41341280189 completed/success na tym SHA. Próba zlecenia review Copilot nie przyniosla obserwowalnego przypisania ani recenzji; zarejestrowano prawdziwe zdarzenie REVIEW ERR-20261101-0003 jako OPEN, z dowodem PR. Nie symulowano bledow.
remaining: Potwierdzic odczyt nowego wpisu z live SHA, SOURCE oraz PR CI po komicie. Security review PR #119 nadal bez recenzenta; pierwotny raport 2/10 nadal OPEN. Jedna proba z rozmowy nie dowodzi pelnego wychwytywania przyszlych bledow.
known_failures: ERROR_CASES 7 observed, 7 recorded, 5 FIXED, 2 OPEN (niepelny zapis historyczny i brak potwierdzonego review). W biezacym tescie 1 observed / 1 recorded / 1 OPEN; pozostale 10/10 nieudowodnione.
verification: SOURCE 41341275888 6/6 success i PR 41341280189 success na c39c4ede przed tym etapem. Finalny nowy SHA i nowe CI do weryfikacji; nie ma niezaleznej recenzji, APK ani testu urzadzenia.
changed_paths:
  - docs/ai/ERROR_CASES.json (prawdziwe zdarzenie REVIEW z rozmowy)
  - docs/AI_HANDOFF.md (checkpoint etapu 2/3)
verified_before_push:
  - Clean v1207 b161b9403425435e106498c382da34b5e3cb3d58; proces V3 ff62a26d3d1259626e042afaff702c8c9c1c6610
  - Wymagane SOURCE CI i PR CI finalnego procesu success 1/1; porownanie z v1207 daje zero zmienionych plikow app/
blockers:
  - Brak niezaleznej recenzji security; self-review nie jest jej substytutem
  - Brak lokalnego checkoutu i fizycznego testu urzadzenia
next_action: Zweryfikowac zapis z nowego live SHA i 6/6 SOURCE CI; potem etap 3/3 - koncowa ocena V3.2 z jawnymi ograniczeniami G4 i 2/10. Nie budowac APK.
evidence_links:
  - https://github.com/laszko14/nira-browser/actions/runs/41341275888
  - https://github.com/laszko14/nira-browser/actions/runs/41341280189
  - https://github.com/laszko14/nira-browser/pull/119
```
