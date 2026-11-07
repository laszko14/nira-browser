# AI_HANDOFF — Zavro Clean V4 / P4-A1 realistic recognition regressions

> Current branch snapshot below was prepared before the P4-A1 commit/CI.
> P3-E was separately CODE_VERIFIED and signed APK v1224 was APK_VERIFIED on SHA
> `94090f7dd91b51794f69b065b4c6f1737f737e0c`. Neither SOURCE CI nor this document is a physical-device test.

```yaml
task_id: ZC-V4-P4-A1-RECOGNITION-FIXTURES
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-v4-p4-20261107
base_sha: 94090f7dd91b51794f69b065b4c6f1737f737e0c
observed_head_sha: 94090f7dd91b51794f69b065b4c6f1737f737e0c
status: CI_PENDING
acceptance_source: User said "Dalej" after agreeing the 12-step P4 plan. P4-A1 only expands realistic, sanitized structural recognition and safe search-form tests. No APK, merge, release, force-push, certificate, profile or session changes.
completed: Added source-backed JS/VM regression fixtures testing mixed legitimate results vs sponsored/recommendations, homepage featured shelves, equal-scoring ambiguous grids, unsafe/external/category/media URLs, hidden/nav items and semantic GET vs unsafe POST/reset/ambiguous search controls. Existing production recognition.js/content.js used unmodified. Kept P3-E full integration tests.
remaining: Verify SOURCE process-docs, JS, Kotlin/Robolectric, AndroidTest Kotlin compilation, lint regression, summary; verify stacked Draft PR JS, Kotlin, AndroidTest compilation, lint and error-learning evidence on the exact P4-A1 final SHA. On a test failure, register distinct CI evidence in ERROR_CASES.json, fix and repeat mandatory gates. Next P4-A2 is dynamic-page failure and context-replacement regression coverage, separately authorized by "Dalej".
required_ci: SOURCE six distinct logical jobs completed/success at exact P4-A1 HEAD; PR shared regression JS/Kotlin/AndroidTest compile/lint plus learning evidence success on exact PR HEAD.
device: NOT_TESTED — physical Android and external live-site acceptance remain user/device tasks.
apk: NOT_BUILT_FOR_P4 — previous P3-E v1224 APK_VERIFIED via pull_request_review run 42346801018, existing signing key; no P4 APK authorization.
known_failures: At checkpoint 0 known P4-A1 CI errors; P3-D earlier known 6 FIXED; exact post-push counts must be reconciled.
verification: At base 94090f7dd91b51794f69b065b4c6f1737f737e0c read AGENTS.md, docs/AI_HANDOFF.md, docs/ai/STARTUP_PREFLIGHT.md, TASK_TEMPLATE, DEVELOPMENT_WORKFLOW, TEST_POLICY, TEST_PATTERNS, QUALITY_GATES, ERROR_LEARNING, CHECKPOINT_POLICY, EXCEPTIONS_AND_TOOLS and P3-E device acceptance via direct GitHub connector. P3-E PR #127 was open/draft, SHA matched live branch. SOURCE #42345574085 and PR #42345592736 both completed SUCCESS; signed APK build #42346801018 success. No device or live external site tests.
changed_paths:
  - app/src/test/js/video-search-p4a1-realistic-fixtures.test.cjs
  - app/src/test/js/video-search-structural-v4.test.cjs
  - docs/ai/ERROR_CASES.json
  - docs/AI_HANDOFF.md
verified_before_push:
  - No production code, manifest, schema, ABI, cert or permissions changes.
  - Fixtures use actual shipped recognition and content sources, not algorithm copies.
  - Explicit assertions for rejected cases, fail-closed ambiguity and no unsolicited page click.
  - Regression coverage added rather than relaxing prior tests.
blockers:
  - Real-site/physical-device cases E01-E18 are NOT_TESTED.
  - No independent code reviewer, self-review plus GitHub CI only.
evidence_links:
  - https://github.com/laszko14/nira-browser/pull/127
  - https://github.com/laszko14/nira-browser/actions/runs/42345574085
  - https://github.com/laszko14/nira-browser/actions/runs/42345592736
  - https://github.com/laszko14/nira-browser/actions/runs/42346801018
next_action: Complete exact-source SOURCE and Draft PR CI for P4-A1, repair failures and reconcile ERROR_CASES; then STOP. Next separate user "Dalej" begins P4-A2 dynamic DOM replacement regressions, NOT a new audit or signed APK.
```
