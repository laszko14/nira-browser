# AI_HANDOFF — Zavro Clean stage 8/10 (HTML5 and site compatibility)

Archive: `docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md`.

```yaml
task_id: ZC-PREVIEW-COMPAT-8
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-preview-html5-stage8-20261110
base_sha: 20fe4681be775d8108eabcf392bc558513971425
observed_head_sha: 487dada2ea8cbf436df151c92f7801daa6cb2b0b
status: CI_PENDING
acceptance_source: User authorized stages 7–10 and private signed update only after all required CI.
completed: Stage7 SOURCE 42797481205 and PR 42797503257 SUCCESS on base SHA. Stage8 RED reproduced foreground unintended restart; content script fix and two regression tests pass. Full local JS 631/631 PASS.
remaining: Verify final SOURCE and PR CI on stage8 documentation and error-ledger SHA; only then start stage9.
required_ci: SOURCE process-docs, shared-tests JS, JVM/Robolectric, AndroidTest compile, lint/baseline, quality-evidence; PR Android CI integration on final source SHA.
device: NOT_TESTED — no real affected site, GeckoView runtime or Nothing Phone (1).
apk: NOT_BUILT — authorized only after stage10 CODE_VERIFIED and update compatibility checks.
known_failures: Historical preview and bg-play site/device reports remain OPEN pending runtime verification. Stage8 synthetic foreground recovery bug is FIXED in code/tests; CI pending.
verification: Stage8 test RED 2 play calls instead of 1; GREEN targeted and full JS 631/631. Local Gradle wrapper download unavailable; JVM/Android/lint will be verified by CI. Manual self-review performed; no separate reviewer available.
changed_paths:
  - app/src/main/assets/extensions/nira-bg-play/content.js
  - app/src/test/js/preview-bgplay-stage5.test.cjs
  - docs/ai/PREVIEW_COMPAT_STAGE8.md
  - docs/ai/ERROR_CASES.json
  - docs/AI_HANDOFF.md
verified_before_push:
  - Foreground pause and late recovery cannot restart a site player in synthetic production-script tests.
  - Native hidden state still permits background recovery of prior playing full media.
  - Full JavaScript suite and error-learning validation pass.
blockers:
  - Final SOURCE/PR CI pending; physical device and real site runtime unavailable.
evidence_links:
  - https://github.com/laszko14/nira-browser/pull/136
  - https://github.com/laszko14/nira-browser/actions/runs/42797481205
  - https://github.com/laszko14/nira-browser/actions/runs/42797503257
next_action: Verify stage8 final SOURCE and PR CI; repair any FAIL before starting stage9.
```
