# AI_HANDOFF — Zavro Clean Video Search Diagnostics 3/7

Previous verified Stage 2 SHA: 1966b95120b4620c2aafa812988a057b24add281.
SOURCE #42569863089 and PR #42569868658 SUCCESS at that SHA.
This is a pre-commit checkpoint; live GitHub HEAD remains authoritative.

```yaml
task_id: ZC-VIDEO-DIAG-3
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-video-diagnostics-20261108
base_sha: 1966b95120b4620c2aafa812988a057b24add281
observed_head_sha: 1966b95120b4620c2aafa812988a057b24add281
status: CI_PENDING
acceptance_source: User "Dalej" after Stage 2 CODE_VERIFIED, authorizes only Stage 3/7; explicitly no APK until separate authorization after Stage 7.
completed: Strict native JSON wire parser, owner-bound diagnostic Gecko port command/ACK/read pump, bounded JS cursor transport, native request/site/worker/run correlation when proven, native queue outcomes, explicit unknown/stale/timeout/lost-event reporting, normal profile noBackup and private epoch RAM lifecycle integration; Robolectric tests and documented limits.
remaining: Verify all SOURCE and PR required CI on final SHA; reconcile actual failures and findings; do not start Stage 4 before separate "dalej".
required_ci: SOURCE process-docs, JS full suite, JVM/Robolectric, AndroidTest Kotlin compilation, Android lint + regression baseline, evidence summary all SUCCESS on same final SHA; PR JS/JVM/AndroidTest/lint/error-learning evidence SUCCESS on final head. Build/release SKIPPED.
device: NOT_TESTED — physical Nothing Phone (1), real GeckoView/live sites, real lifecycle and performance.
apk: NOT_BUILT — forbidden until after Stage 7 and separate approval.
known_failures: None observed in Stage 3 before first CI; record every actual distinct CI/review failure with task coverage in docs/ai/ERROR_CASES.json.
verification: Read AGENTS.md, docs/AI_HANDOFF.md, docs/ai/STARTUP_PREFLIGHT.md, VIDEO_SEARCH_DIAGNOSTICS_STAGE1/2.md, TEST_POLICY.md, QUALITY_GATES.md, ERROR_LEARNING.md and CHECKPOINT_POLICY.md from exact base_sha; inspected Gecko controller, strict bridge protocol, existing queue and private registry, recorder and Robolectric tests.
changed_paths:
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchBridgeController.kt
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchDiagnosticNativeSession.kt
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchDiagnosticWire.kt
  - app/src/test/java/com/prirai/android/nira/videosearch/VideoSearchDiagnosticWireTest.kt
  - docs/ai/VIDEO_SEARCH_DIAGNOSTICS_STAGE3.md
  - docs/AI_HANDOFF.md
  - docs/ai/ERROR_CASES.json
verified_before_push:
  - JS/new native diagnostics dormant unless startDiagnostics explicitly called by future UI.
  - Native request attribution cannot be supplied by page, Room schema and search decisions unchanged.
  - No APK/merge/release/force-push/Secrets/cert/package changes.
blockers:
  - Physical device and live-site tests NOT_TESTED.
  - UI/export/graceful final drain belongs to Stage 5; results funnel to Stage 4.
  - Independent reviewer unavailable; self-review does not equal independent security approval.
evidence_links:
  - https://github.com/laszko14/nira-browser/pull/129
  - https://github.com/laszko14/nira-browser/actions/runs/42569863089
  - https://github.com/laszko14/nira-browser/actions/runs/42569868658
next_action: Verify final source and PR CI, then STOP. Stage 4 only after another user "dalej". Do not ask about APK.
```
