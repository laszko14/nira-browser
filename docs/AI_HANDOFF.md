# AI_HANDOFF — Zavro Clean stage 9/10 (full regression)

Archive: `docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md`.

```yaml
task_id: ZC-PREVIEW-COMPAT-9
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-preview-regression-stage9-20261110
base_sha: cff8ecaf65b5d78e103f6d8043035ee3c3adcd24
observed_head_sha: cff8ecaf65b5d78e103f6d8043035ee3c3adcd24
status: CI_PENDING
acceptance_source: User authorized stages 7–10 and private signed update only after all required CI.
completed: Stage8 SOURCE 42799348761 and PR 42799376655 SUCCESS on base SHA. All six SOURCE jobs and five required PR jobs passed; PR release and build intentionally skipped. Stage9 local full JS 631/631, process tests 62/62 and error-learning validation PASS.
remaining: Verify final SOURCE and PR CI on stage9 SHA. Only then start stage10.
required_ci: SOURCE process-docs, shared-tests JS, JVM/Robolectric, AndroidTest compile, lint/baseline, quality-evidence; PR Android CI integration JS, JVM, AndroidTest compile, lint, error-learning evidence.
device: NOT_TESTED — no physical Nothing Phone (1), real site or MPV Alpha runtime.
apk: NOT_BUILT — authorized only after stage10 CODE_VERIFIED and update compatibility checks.
known_failures: Historical preview and bg-play site/device reports remain OPEN pending runtime verification; no new stage9 defect observed yet.
verification: Stage8 six SOURCE and five required PR jobs SUCCESS. Stage9 local JS 631/631, process tests 62/62, error-learning 51 tasks / 215 events / 46 open. Final SOURCE and PR CI pending.
changed_paths:
  - docs/ai/PREVIEW_COMPAT_STAGE9.md
  - docs/ai/ERROR_CASES.json
  - docs/AI_HANDOFF.md
verified_before_push:
  - Stage8 full JavaScript, JVM/Robolectric, AndroidTest compile and lint/baseline passed in SOURCE and PR CI.
  - Stage8 foreground-recovery code-level regression is covered by synthetic production-script tests.
blockers:
  - Stage9 final SOURCE/PR CI pending; physical device and real site runtime unavailable.
evidence_links:
  - https://github.com/laszko14/nira-browser/pull/137
  - https://github.com/laszko14/nira-browser/actions/runs/42799348761
  - https://github.com/laszko14/nira-browser/actions/runs/42799376655
next_action: Verify stage9 final SOURCE and PR CI; repair any FAIL before starting stage10.
```
