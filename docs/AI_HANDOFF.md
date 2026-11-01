# AI_HANDOFF — bieżący checkpoint (Zavro Clean / proces AI)

> Jedno źródło aktualnego stanu **tej gałęzi**. Starszą historię pozostawiono w [archiwum](ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md).

```yaml
task_id: ZC-PROC-07
workflow_mode: staged
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: improve/zavro-clean-chatgpt-github-process-20261101
base_sha: 5215b217fd70a9094b5ee75b11751670e3a0b45c
observed_head_sha: 5215b217fd70a9094b5ee75b11751670e3a0b45c
status: CI_PENDING
acceptance_source: ETAP 7/9 — docs/ai/GECKO_DEVICE_TEST_PLAN.md
required_ci: zavro-ai-process-docs.yml / process-docs + shared-tests/{javascript,unit,android-test-compile,lint} + quality-evidence
device: NOT_TESTED
apk: NOT_BUILT
changed_paths:
  - docs/ai/GECKO_DEVICE_TEST_PLAN.md
  - scripts/check-gecko-device-plan.test.cjs
  - .github/workflows/zavro-ai-process-docs.yml
  - docs/AI_HANDOFF.md
verified_before_push:
  - Stage 6 final SHA 5215b217fd70a9094b5ee75b11751670e3a0b45c run 41255508287 completed/success; 6/6 jobs
  - Read 19B matrix, G2/G3B/G3C/G4D; no real device verification of 002/030/031/043
  - Plan and negative structural tests prepared; no phone operation or APK
blockers: []
next_action: Verify process-docs, four shared jobs and quality-evidence at final HEAD; then await Dalej for stage 8/9
evidence_links:
  - https://github.com/laszko14/nira-browser/actions/runs/41255508287
```

## Wznowienie

Odczytaj `AGENTS.md` i ten checkpoint na jawnym SHA oraz żywy HEAD gałęzi. `observed_head_sha` jest migawką **sprzed** push, a `CI_PENDING` nie jest końcowym werdyktem po wykonaniu GitHub Actions. Wszystkie wymagane joby i próba muszą mieć `completed/success` na końcowym HEAD. Nie uznawaj `skipped` za PASS. Po zakończeniu procesu ZC-PROC-07 STOP; kolejny etap 8/9 dotyczy odbioru wyników GitHub Actions i integracji zdarzeniowej, dopiero po osobnym „Dalej”.

Granice: bez APK, PR, merge, zmiany podpisu, danych, telefonicznych działań i statusów 60 ustaleń. Testy offline dokumentów nie są testami GeckoView na telefonie. Zobacz [plan](ai/GECKO_DEVICE_TEST_PLAN.md) i [archiwum](ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md).
