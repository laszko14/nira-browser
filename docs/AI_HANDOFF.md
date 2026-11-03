# AI_HANDOFF — Zavro Clean V4 / P2 scoped results hardening
> Archiwum historii: docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md.

```yaml
task_id: ZC-V4-P2-COMPLETE
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: fix/zavro-clean-v4-p2-evidence-runtime-20261103
base_sha: b566546207bed0ad236dc5f5c28a8807cd4ea5a6
observed_head_sha: bbb45f09f78c1ba456b274d5be7cacc653f1c429
status: CI_PENDING
acceptance_source: User-approved continuation of V4 P2 on 2026-11-03; preserve verified evidence, profiles, data and old signed update identity.
completed: P2 initial evidence checks and TreeWalker/SPA bounds preserved; P2 extension scoped result-card locator for unique open Shadow DOM or same-origin iframe host; revalidation before each card extraction and selector health check; same-origin frame-relative film links; icon-only SVG/control accessible hints; JS and Kotlin negative regression fixtures; update versionCode 1223 alpha119 for private arm64 debug update.
remaining: Exact final SOURCE CI and separate PR integration evidence; full scoped input/submit and pagination controls (only scoped result cards wired); inaccessible frames and closed roots remain unsupported; safe user-confirmed automatic replacement of broken rules not implemented; physical GeckoView and real-site/device corpus NOT_TESTED.
required_ci: SOURCE push process-docs, shared-tests JS/JVM-Robolectric/AndroidTest compile/lint regression, quality-evidence all SUCCESS on exact final source SHA; separate PR integration.
apk: APK_BLOCKED pending green exact final SOURCE CI and owner COMMENT review; expected package com.prirai.android.nira.debug, ABI arm64-v8a, versionCode 1223, versionName 0.5.22-alpha119-clean, certificate SHA256 df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00 unchanged.
device: NOT_TESTED
known_failures: Previous P2 initial five observations recorded; new scoped-card and SVG review gaps recorded; SOURCE 41586101551 and PR 41586107020 failed the same new JS test syntax error; two distinct jobs logged, fix staged; final CI pending.
verification: Existing P2 SOURCE 41575728798 and PR integration 41575733119 were reported green on bbb45f09f78c1ba456b274d5be7cacc653f1c429; not proof of this new change.
blockers:
  - Scoped forms and pagination require separate full protocol and device review, not assumed by scoped result-card extraction.
  - Physical Android GeckoView and cross-site dynamic corpus NOT_TESTED.
changed_paths:
  - app/src/main/assets/extensions/zavro-video-search/recognition.js
  - app/src/main/assets/extensions/zavro-video-search/content.js
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchBridgeProtocol.kt
  - app/src/test/js/video-search-p1-recognition.test.cjs
  - app/src/test/java/com/prirai/android/nira/videosearch/VideoSearchP1RecognitionTest.kt
  - app/build.gradle
  - .github/workflows/apk-release.yml
  - app/src/test/js/release-contract-stage19a.test.cjs
  - app/src/test/js/video-search-neon-redesign.test.cjs
  - docs/AI_HANDOFF.md
  - docs/ai/ERROR_CASES.json
verified_before_push:
  - Live PR #123 HEAD bbb45f09f78c1ba456b274d5be7cacc653f1c429 and same-repo draft verified by GitHub connector.
  - Read AGENTS.md, docs/AI_HANDOFF.md, V3.2 STARTUP_PREFLIGHT, TEST_POLICY, QUALITY_GATES, ERROR_LEARNING, APK_CHAT_GITHUB_REVIEW_TRIGGER from exact source SHA.
  - Admin/push permissions confirmed; no local working tree or device was available in the connector-only environment.
evidence_links:
  - https://github.com/laszko14/nira-browser/pull/123
  - https://github.com/laszko14/nira-browser/actions/runs/41575728798
  - https://github.com/laszko14/nira-browser/actions/runs/41575733119
  - https://github.com/laszko14/nira-browser/actions/runs/41576565589
security: Scoped tokens require stable unique host selectors, open shadow roots or same-origin frames, and per-call revalidation; no cross-origin escalation or untrusted page actions. No TinyFish, merge, public release, force-push or certificate change.
next_action: Commit once on same P2 branch, verify SOURCE CI + PR integration at exact final SHA; fix failures; then single owner COMMENT review for private signed update only after all required CI succeeds.
```
