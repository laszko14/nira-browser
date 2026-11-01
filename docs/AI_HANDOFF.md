# AI_HANDOFF — bieżący checkpoint (Zavro Clean / proces AI)

> Jedno źródło aktualnego stanu tej gałęzi; historia bez zmian w [archiwum](ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md).

```yaml
task_id: ZC-PROC-08
workflow_mode: staged
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: improve/zavro-clean-chatgpt-github-process-20261101
base_sha: 81c290f40376dc93fabf9b6f21dce6e2bd4a0095
observed_head_sha: 81c290f40376dc93fabf9b6f21dce6e2bd4a0095
status: CI_PENDING
acceptance_source: ETAP 8/9 — docs/ai/CI_EVENTS_AND_RECEIPTS.md
required_ci: zavro-ai-process-docs.yml / process-docs + shared-tests/{javascript,unit,android-test-compile,lint} + quality-evidence
device: NOT_TESTED
apk: NOT_BUILT
changed_paths:
  - scripts/ci-run-receipt.cjs
  - scripts/ci-run-receipt.test.cjs
  - .github/workflows/zavro-ai-process-docs.yml
  - docs/ai/CI_EVENTS_AND_RECEIPTS.md
  - docs/AI_HANDOFF.md
verified_before_push:
  - Stage 7 final SHA 81c290f40376dc93fabf9b6f21dce6e2bd4a0095 run 41256300980 completed/success, 6/6 jobs
  - One-shot read-only CI evidence tool; tests for pagination, skipped, stale SHA and attempt
  - No event hook on default branch, no external integration or extra access
blockers: []
next_action: Verify process-docs, four shared child jobs and quality-evidence on final SHA; then await Dalej for stage 9/9
evidence_links:
  - https://github.com/laszko14/nira-browser/actions/runs/41256300980
```

## Wznowienie

Odczytaj `AGENTS.md` i aktualny checkpoint na jawnym SHA. `observed_head_sha` to migawka sprzed końcowego pusha, a `CI_PENDING` nie jest oceną po wykonaniu Actions. Sprawdź żywy HEAD, run ID/próbę/event, cztery potomne joby oraz dwa pozostałe; `skipped` i brak joba nie oznaczają PASS. Przy końcowym sukcesie zakończ etap 8/9 i STOP; etap 9/9 (pilotaż i pomiar) dopiero po kolejnym „Dalej”.

Zakres: proces ChatGPT/GitHub bez APK, PR, merge, kasowania danych, webhooków ani dodatkowych uprawnień. Dokumentacja: [CI_EVENTS_AND_RECEIPTS.md](ai/CI_EVENTS_AND_RECEIPTS.md). Historia: [AI_HANDOFF_LEGACY_2026-11-01.md](ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md).
