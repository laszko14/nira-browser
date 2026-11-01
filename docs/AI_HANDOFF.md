# AI_HANDOFF — bieżący checkpoint (Zavro Clean / proces AI)

> Jedno źródło bieżącego stanu **gałęzi procesu**. Historyczne materiały: [archiwum](ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md).

```yaml
task_id: T01-GECKO-V1196
workflow_mode: staged
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: improve/zavro-clean-chatgpt-github-process-20261101
base_sha: 414b861d14ab03e769dca9b82727644e07a89169
observed_head_sha: 414b861d14ab03e769dca9b82727644e07a89169
status: CI_PENDING
acceptance_source: pilot T01/16 — docs/ai/T01_V1196_NONDESTRUCTIVE_SMOKE.md
required_ci: zavro-ai-process-docs.yml / process-docs + shared-tests/{javascript,unit,android-test-compile,lint} + quality-evidence
device: USER_REPORTED_SMOKE_3_OF_3_OK; ZC-AUD-002/030/031/043 NOT_TESTED
apk: NOT_BUILT
pilot: T01/16 FOLLOWUP_PENDING; completed 14-day followups 0/16; metrics NOT_MEASURED
reported_at: 2026-11-01T01:05:55.350Z
followup_not_before: 2026-11-15T01:05:55.350Z
app_under_test_source_sha: 717608d1af23638614499d25dae7682f16a7fd8a
changed_paths:
  - docs/ai/PILOT_16.json
  - docs/ai/T01_V1196_NONDESTRUCTIVE_SMOKE.md
  - docs/ai/PILOT_MEASUREMENT_9.md
  - scripts/ai-pilot-metrics.test.cjs
  - docs/AI_HANDOFF.md
verified_before_push:
  - Previous process SHA 414b861d14ab03e769dca9b82727644e07a89169 run 41258788736 completed/success all 6 required jobs attempt 1
  - G4C 1196 source 717608d1af23638614499d25dae7682f16a7fd8a run 41250929267 completed/success; installed APK digest not inspected
  - User confirmed three non-destructive basic smoke checks worked; no native Gecko evidence collected
  - T01 observation window 14 days; metrics unknown, no false zero; no change to audit 60 findings
blockers: []
next_action: Verify final SHA in CI (all six jobs) then STOP; T01 stays FOLLOWUP_PENDING until later real observation, no automatic check promised
evidence_links:
  - https://github.com/laszko14/nira-browser/actions/runs/41258788736
  - https://github.com/laszko14/nira-browser/actions/runs/41250929267
```

## Wznowienie

Przed zadaniem porównaj bieżący HEAD, czytaj `AGENTS.md` i checkpoint na **jawnym SHA**. `status: CI_PENDING` oraz `observed_head_sha` to zapis sprzed push, nie wynik końcowego CI. Nie dopisuj run ID po pushu osobnym commitem. Stwierdzenie użytkownika **„Wszystko działa”** dotyczy tylko trzech podstawowych prób, a nie czterech otwartych audytowych ID. Nie wykonano dodatkowych prób Gecko, APK, merge ani czyszczenia danych. Po ukończeniu i zielonym CI **STOP**, kolejne zadanie wymaga osobnego polecenia.
