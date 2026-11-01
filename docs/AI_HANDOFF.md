# AI_HANDOFF — bieżący checkpoint (Zavro Clean / proces AI)

> Jedno źródło bieżącego stanu **gałęzi procesu**. Historyczne materiały: [archiwum](ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md).

```yaml
task_id: T01-GECKO-V1196
workflow_mode: staged
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: improve/zavro-clean-chatgpt-github-process-20261101
base_sha: deb05f4b7e5a1e23625aa02dc5096cf0a91d53ad
observed_head_sha: deb05f4b7e5a1e23625aa02dc5096cf0a91d53ad
status: CI_PENDING
acceptance_source: ETAP 9/9 pilot T01/16 — docs/ai/T01_V1196_NONDESTRUCTIVE_SMOKE.md
required_ci: zavro-ai-process-docs.yml / process-docs + shared-tests/{javascript,unit,android-test-compile,lint} + quality-evidence
device: AWAITING_USER_OBSERVATIONS
apk: NOT_BUILT
pilot: T01/16 ACTIVE; completed 14-day followups 0/16; measurements NOT_MEASURED
changed_paths:
  - docs/ai/PILOT_16.json
  - docs/ai/PILOT_MEASUREMENT_9.md
  - docs/ai/T01_V1196_NONDESTRUCTIVE_SMOKE.md
  - scripts/ai-pilot-metrics.test.cjs
  - docs/AI_HANDOFF.md
verified_before_push:
  - Stage 9 final SHA deb05f4b7e5a1e23625aa02dc5096cf0a91d53ad run 41257993904 completed/success; six jobs
  - G4C v1196 build source 717608d1af23638614499d25dae7682f16a7fd8a run 41250929267 completed/success
  - User previously confirmed in-place install and preserved visible settings; no new device test in T01 yet
  - T01 registered ACTIVE; counters remain unknown; no forced stop, erase or separate native Gecko probe
blockers:
  - Physical steps require a user-operated device; no remote phone access
next_action: Verify final CI SHA then ask for brief v1196 smoke results; do not close T01 without real observations; no other task before STOP
evidence_links:
  - https://github.com/laszko14/nira-browser/actions/runs/41257993904
  - https://github.com/laszko14/nira-browser/actions/runs/41250929267
```

## Wznowienie

Odczytaj `AGENTS.md` i ten checkpoint na jawnym SHA, porównaj żywy HEAD. `observed_head_sha` i `CI_PENDING` to migawka sprzed push, nie aktualny werdykt po runie; **wszystkie** wymagane joby muszą mieć ukończony SUCCESS na finalnym SHA. Zapis T01 w [rejestrze](ai/PILOT_16.json), instrukcja dla telefonu w [T01](ai/T01_V1196_NONDESTRUCTIVE_SMOKE.md); nie zmieniaj audytu 60/60 na podstawie podstawowego smoke.

Nie twórz osobnego commitu wyłącznie dla numeru CI. Po zielonym CI i przygotowaniu T01 STOP w oczekiwaniu na wyniki użytkownika. Nie wykonano APK, PR, merge, zmian telefonu, profili, sesji ani usuwania danych.
