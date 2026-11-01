# AI_HANDOFF — bieżący checkpoint (Zavro Clean / proces AI)

> Jedno źródło aktualnego stanu **tej gałęzi**. Historia pozostała w [archiwum](ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md).

```yaml
task_id: ZC-PROC-06
workflow_mode: staged
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: improve/zavro-clean-chatgpt-github-process-20261101
base_sha: f2bc94a042beb488ef736d9d8b802bc631499777
observed_head_sha: f2bc94a042beb488ef736d9d8b802bc631499777
status: CI_PENDING
acceptance_source: ETAP 6/9 — docs/ai/REVIEW_STAGE6.md
required_ci: zavro-ai-process-docs.yml / process-docs + shared-tests/{javascript,unit,android-test-compile,lint} + quality-evidence
device: NOT_TESTED
apk: NOT_BUILT
changed_paths:
  - scripts/lint-regression.cjs
  - scripts/lint-regression.test.cjs
  - .github/workflows/apk-release.yml
  - scripts/check-ci-workflows.test.cjs
  - docs/ai/REVIEW_STAGE6.md
  - docs/AI_HANDOFF.md
verified_before_push:
  - Stage 5 final SHA f2bc94a042beb488ef736d9d8b802bc631499777; CI 41254614107 completed SUCCESS from prior checkpoint
  - Reviewed 8 process commits and 25 changed paths, without modifying app runtime
  - Regression tests R6-01 / R6-02, CI execution pending
blockers: []
next_action: Verify process-docs, four shared-tests jobs and quality-evidence at final HEAD; then await Dalej for stage 7/9
evidence_links:
  - https://github.com/laszko14/nira-browser/actions/runs/41254614107
```

## Jak wznowić

Odczytaj `AGENTS.md` i powyższy checkpoint na jawnym SHA, a potem sprawdź żywy HEAD. `observed_head_sha` to migawka **sprzed** pusha, nie SHA finalnego commita. `CI_PENDING` jest stanem zapisanym przed CI; sprawdź aktualny run, próbę, SHA i wszystkie obowiązkowe joby. `skipped` nie oznacza PASS.

Granice: wyłącznie gałąź procesu; bez APK, PR, merge, force-push i zmian danych. Po zielonym CI etap 6/9 kończy się STOP. Kolejny etap 7/9 dopiero po „Dalej”. Review: [REVIEW_STAGE6.md](ai/REVIEW_STAGE6.md). Pełna historia: [AI_HANDOFF_LEGACY_2026-11-01.md](ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md).
