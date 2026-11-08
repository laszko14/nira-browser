# AI_HANDOFF — Zavro Clean V4 / P4-F2 final cross-module regression

> Historical archive: docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md. Pre-commit snapshot; source of truth is live GitHub branch HEAD, not observed_head_sha. P4-F1 CODE_VERIFIED at 5e91b74cf44ba8c0a82a228294534e396bd88fa0, SOURCE #42497040362 and PR #42497045614 SUCCESS.

```yaml
task_id: ZC-V4-P4-F2-FINAL-REGRESSION
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-v4-p4-20261107
base_sha: 5e91b74cf44ba8c0a82a228294534e396bd88fa0
observed_head_sha: 5e91b74cf44ba8c0a82a228294534e396bd88fa0
status: CI_PENDING
acceptance_source: User "dalej" after P4-F1 CODE_VERIFIED. F2 is final P4 cross-module regression, normal/private profile data and session safety, no APK or merge.
completed: Added five Robolectric integration tests with real in-memory Room repository and synthetic pages. Contracts cover normal scopes sharing run ID but never data, smart season/episode grouping from saved metadata and exact page URLs, merged bulk selecting all sources with indexed ownership, 340 saved films with 150-card lazy window/3 source headers/site filters, restored selection/scroll/view within one tab-run-profile and discarded selection on close, private RAM retirement never entering normal Room, E1 responsive size and scroll anchors. Existing JS/JVM full suite still runs in mandatory SOURCE and PR CI. Full matrix documented in docs/ai/P4_FINAL_REGRESSION.md.
remaining: Run and verify all required SOURCE and PR CI jobs on identical final SHA; review and reconcile real CI failures and fix regressions in the same stage. Do not claim live-site/device test or APK.
required_ci: SOURCE process-docs, shared-tests (JS, Kotlin/Robolectric JVM, AndroidTest Kotlin compile, Android lint baseline), evidence summary all SUCCESS; PR shared-tests JS, Kotlin, AndroidTest compile, lint baseline and error-learning evidence all SUCCESS; check source checkout HEAD equals final SHA.
device: NOT_TESTED — no Nothing Phone (1), GeckoView, real sites, actual rotation/touch or RAM/FPS measurement.
apk: NOT_BUILT_FOR_P4 — no trigger, no private build/signing requested.
known_failures: SOURCE #42499752653 attempt 1 at initial 34a66aeb7d81db6ed2adbe5e7b305dc59068d276: process-docs failed checkpoint missing exact archive path and shared-tests literal; required summary failed as expected because process-docs was red. Both recorded in docs/ai/ERROR_CASES.json with actual repair SHA in separate commit; rerun final SOURCE/PR on new SHA.
verification: Live Draft PR #128 on feat/zavro-clean-v4-p4-20261107 at exact 5e91b74cf44ba8c0a82a228294534e396bd88fa0; AGENTS.md, AI_HANDOFF.md, preflight, quality gates, test, checkpoint, workflow, error-learning and task docs read from this SHA. Reviewed P3-E and V3.2 integrations, results Room, profile private store, smart grouping, bulk index, tab memory, lazy page keys.
changed_paths:
  - app/src/test/java/com/prirai/android/nira/videosearch/VideoSearchV4P4F2IntegrationTest.kt
  - docs/ai/P4_FINAL_REGRESSION.md
  - docs/AI_HANDOFF.md
  - docs/ai/ERROR_CASES.json
verified_before_push:
  - No production app code changed unless real new failure requires it; no existing test weakened.
  - In-memory Room and private RAM test fixtures do not touch external profile data.
  - No signing, APK workflow, MPV, manifest permissions, release or merge changes.
blockers:
  - Actual website and device integration NOT_TESTED; JVM fixtures are not GeckoView proof.
  - Independent high-risk review unavailable.
evidence_links:
  - https://github.com/laszko14/nira-browser/pull/128
  - https://github.com/laszko14/nira-browser/actions/runs/42497040362
  - https://github.com/laszko14/nira-browser/actions/runs/42497045614
next_action: Verify mandatory CI on exact final SHA, triage and register any failure, then STOP. F2 is the last agreed P4 substage.
```
