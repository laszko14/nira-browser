# AI_HANDOFF — Zavro Clean V4 / P2 Search Evidence and Runtime
> Archiwum historii: docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md.

```yaml
task_id: ZC-V4-P2-SEARCH-EVIDENCE-RUNTIME
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: fix/zavro-clean-v4-p2-evidence-runtime-20261103
base_sha: b566546207bed0ad236dc5f5c28a8807cd4ea5a6
observed_head_sha: b566546207bed0ad236dc5f5c28a8807cd4ea5a6
status: CI_PENDING
acceptance_source: V4 P2 user-approved audit of 2026-11-03; strengthen proof of searched results, bound DOM costs, avoid stale SPA handling, preserve profile isolation and signed update compatibility.
completed: P2 query-evidence verification for normal, private and worker searches; bounded candidate TreeWalker and selector caching; fail-closed ambiguous result fallback; coalesced SPA observation; regression tests; arm64 debug versionCode 1222 alpha118.
remaining: Verify final SOURCE and PR integration CI, triage and repair failures; scoped Shadow/iframe extraction, dynamic list validation and physical-device measurements not completed in this checkpoint.
required_ci: SOURCE push process-docs, reusable JS/JVM-Robolectric/AndroidTest compile/lint regression, quality-evidence all SUCCESS on final source SHA; separate PR integration.
apk: APK_BLOCKED until green final SOURCE CI and authorized exact SHA owner COMMENT review; expected package com.prirai.android.nira.debug, ABI arm64-v8a, versionCode 1222, versionName 0.5.22-alpha118-clean, unchanged cert SHA256 df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00.
device: NOT_TESTED
known_failures: Two P2 review risks registered; P2 CI not yet run.
verification: P1 SOURCE 41570599754, PR 41570607241 and signed APK 41571433323 PASS at base; P2 CI_PENDING.
blockers:
  - Scoped Shadow/iframe extraction is not yet supported; do not treat read-only surface recognition as full extraction.
  - Physical Android GeckoView and 15-site corpus NOT_TESTED.
changed_paths:
  - app/src/main/assets/extensions/zavro-video-search/recognition.js
  - app/src/main/assets/extensions/zavro-video-search/content.js
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchWorkerVerification.kt
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchBridgeController.kt
  - app/src/test/java/com/prirai/android/nira/videosearch/VideoSearchWorkerVerificationTest.kt
  - app/src/test/js/video-search-p1-recognition.test.cjs
  - app/build.gradle
  - .github/workflows/apk-release.yml
  - app/src/test/js/release-contract-stage19a.test.cjs
  - app/src/test/js/video-search-neon-redesign.test.cjs
  - docs/AI_HANDOFF.md
  - docs/ai/ERROR_CASES.json
verified_before_push:
  - Live P1 HEAD b566546207bed0ad236dc5f5c28a8807cd4ea5a6 verified using direct GitHub connector, AGENTS.md and docs/AI_HANDOFF.md read from that exact SHA.
  - Read V3.2 STARTUP_PREFLIGHT, TEST_POLICY, QUALITY_GATES, ERROR_LEARNING and APK_CHAT_GITHUB_REVIEW_TRIGGER at source SHA.
  - Created isolated P2 branch from P1, without merge, destructive migration or changing certificate.
evidence_links:
  - https://github.com/laszko14/nira-browser/pull/122
  - https://github.com/laszko14/nira-browser/actions/runs/41570599754
  - https://github.com/laszko14/nira-browser/actions/runs/41571433323
security: Same-owner Gecko sessions, no TinyFish, no merge, public release, force-push or APK through unapproved triggers.
next_action: Open stacked draft PR, verify SOURCE CI + PR integration, repair any failure; then if green, issue a single owner COMMENT review and verify private update APK log and artifact.
```
