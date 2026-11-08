# AI_HANDOFF — Zavro Clean V4 / P4-F1 large searches and cancellation

> Historical archive: docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md. Snapshot recorded before push; the live branch HEAD overrides observed_head_sha. P4-E2 CODE_VERIFIED at 717eb29495a5adeb0285bab06cf768e7d0280eff, SOURCE #42494203233 and PR #42494208399 SUCCESS.

```yaml
task_id: ZC-V4-P4-F1-LARGE-SEARCH-PERF
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-v4-p4-20261107
base_sha: 717eb29495a5adeb0285bab06cf768e7d0280eff
observed_head_sha: 717eb29495a5adeb0285bab06cf768e7d0280eff
status: CI_PENDING
acceptance_source: User "dalej" after verified P4-E2. Approved original 12-step P4 plan specifies F1 performance of thousands of results, memory, scrolling, cancellation and parallel websites; F2 is a separate full regression stage. No APK or merge.
completed: One run/snapshot-owned selection index built only when the Room snapshot changes; recomposition and 200 bulk interactions use indexed exact-URL identity instead of rescanning 12,000 film records repeatedly. Selected results recompute only when selection or snapshot changes, in original saved order. No cache shared across profiles or runs; stale URL, foreign run/site/profile fail closed. Coroutine cancellation now stops pending sites after structured concurrent child cancellation, without falsely declaring external leases completed. Memory-budget and worker-parallelism contracts preserved. Added four indexed-selection JVM tests including deterministic backing-list read counts and two 50-site cancellation/memory-pressure tests.
remaining: SOURCE and PR exact-final-SHA mandatory CI; reconcile any actual failure in ERROR_CASES.json. Next P4-F2 full cross-module regression only on separate user "Dalej".
required_ci: SOURCE process-docs and shared-tests JS WebExtension, Kotlin/Robolectric, AndroidTest Kotlin compilation, Android lint regression baseline and evidence summary all SUCCESS on final SHA; PR shared-tests JS/Kotlin/AndroidTest/lint and error-learning evidence all SUCCESS on same head SHA.
device: NOT_TESTED — no real Nothing Phone (1), GeckoView, device memory profiler, touch-scroll FPS or actual live websites.
apk: NOT_BUILT_FOR_P4 — no P4 signed APK approved.
known_failures: Two code-review findings to be recorded with fix_sha: full-snapshot scans on selection, and unmarked pending sites after coroutine cancellation. No F1 CI failure observed before push.
verification: GitHub Draft PR #128, correct branch and exact live 717eb29495a5adeb0285bab06cf768e7d0280eff verified; AGENTS.md, AI_HANDOFF.md and scoped test/quality/preflight/checkpoint/error policies read from this SHA. Inspected pure selection, 12,000-item path, MultiSiteExecutor and synchronized queue status handling. No local checkout or physical device tests.
changed_paths:
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchBulkSelection.kt
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchResultsActivity.kt
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchMultiSiteExecutor.kt
  - app/src/test/java/com/prirai/android/nira/videosearch/VideoSearchBulkSelectionTest.kt
  - app/src/test/java/com/prirai/android/nira/videosearch/VideoSearchMultiSiteExecutorTest.kt
  - docs/AI_HANDOFF.md
  - docs/ai/ERROR_CASES.json
verified_before_push:
  - Indexed state belongs to one immutable snapshot; no cross-profile persistence, Room schema, tab/session mutation or URL rewriting.
  - Cancellation stops waiting work only after structured children finish; no faked successful search, no resource-permit increase.
  - Retains existing worker fairness/parallelism, per-site result limit, legacy merge options and scroll state.
blockers:
  - No device memory/FPS numbers, real-site or live GeckoView acceptance; deterministic JVM work counter is not physical profiling.
  - Independent high-risk review unavailable; self-review and CI are not a substitute.
evidence_links:
  - https://github.com/laszko14/nira-browser/pull/128
  - https://github.com/laszko14/nira-browser/actions/runs/42494203233
  - https://github.com/laszko14/nira-browser/actions/runs/42494208399
next_action: Verify all required SOURCE and PR CI at exact final SHA, triage any failures; stop after F1. Proceed to F2 only on separate "Dalej".
```
