# AI_HANDOFF — Zavro Clean V4 / Adaptive Site Intelligence P1
> Archive: docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md

```yaml
task_id: ZC-V4-ADAPTIVE-SITE-INTELLIGENCE-P1
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: fix/zavro-clean-v4-p1-adaptive-site-intelligence-20261103
base_sha: d48fdff8a6e5e46e68ec4b2c329e32927a6c10a4
observed_head_sha: e8b13afdf46d756ec9a8ff89a4ec2a90d9f2d6d3
status: CI_PENDING
acceptance_source: P1 structural multi-signal film cards; safe icon/search control recognition; dynamic SPA retry; read-only Shadow/iframe detection; typed bounded bridge; remembered verified rule evidence without manual overwrite; diagnostic reasons.
completed: Added independent bounded recognition.js with local film-link+title+thumbnail evidence, negative ad/category link filters, structural scoring, open-shadow/same-origin iframe read-only surface detection; connected to existing WebExtension content.js. SVG accessible-icon search hints; typed owner-bound recognition bridge; SPA hydration read-only retry; backward-compatible optional Room JSON recognition metadata and CAS-based memory of verified user searches; diagnostics and JS/Kotlin/Room tests. Incremented signed arm64 debug update from v1214 alpha116 to v1218 alpha117.
remaining: Verify all SOURCE and PR integration CI, fix actual failures. P1 deep extraction from Shadow DOM/iframe remains deliberately unsupported until typed scoped locator and safe frame ownership is implemented; do not treat surface detection as full support. No actual 15-site Gecko verification.
required_ci: SOURCE push process-docs, shared-tests JS/JVM-Robolectric/AndroidTest compile/lint-regression, quality-evidence on final source SHA; separate PR integration. No pass without live evidence.
apk: APK_BLOCKED until authorized exact-SHA owner COMMENT review after completed green final SOURCE CI; then verify package com.prirai.android.nira.debug, ABI arm64-v8a, versionCode 1218, versionName 0.5.22-alpha117-clean and unchanged cert SHA256 df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00.
device: NOT_TESTED
known_failures: SOURCE run 41569421409 process-docs failed because P1 handoff omitted mandatory verified_before_push, evidence_links and archive reference. Fix in this checkpoint; tests await final SHA.
verification: GitHub connector verified P0 HEAD d48fdff8a6e5e46e68ec4b2c329e32927a6c10a4, AGENTS.md and handoff at exact SHA and V3.2 policies, APK review trigger. P1 code not yet CI verified.
blockers:
  - Shadow/iframe extraction needs additional scoped locator and lifecycle security proof.
  - Physical Android GeckoView real-world sites NOT_TESTED.
changed_paths:
  - app/src/main/assets/extensions/zavro-video-search/recognition.js
  - app/src/main/assets/extensions/zavro-video-search/manifest.json
  - app/src/main/assets/extensions/zavro-video-search/content.js
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchRecognitionMemory.kt
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchRecognitionDiagnostics.kt
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchModels.kt
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchRowCodec.kt
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchRepository.kt
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchBridgeProtocol.kt
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchBridgeController.kt
  - app/src/main/java/com/prirai/android/nira/videosearch/MyVideoSitesActivity.kt
  - app/src/test/js/video-search-p1-recognition.test.cjs
  - app/src/test/java/com/prirai/android/nira/videosearch/VideoSearchP1RecognitionTest.kt
  - app/src/test/js/release-contract-stage19a.test.cjs
  - app/src/test/js/video-search-neon-redesign.test.cjs
  - app/build.gradle
  - .github/workflows/apk-release.yml
verified_before_push:
  - GitHub P0 live head d48fdff8a6e5e46e68ec4b2c329e32927a6c10a4; read AGENTS.md and handoff from exactly that SHA.
  - Read V3.2 STARTUP_PREFLIGHT, TEST_POLICY, QUALITY_GATES, ERROR_LEARNING and approved APK owner-review trigger.
  - PR 122 draft stacked on P0; repository permissions admin, pull and push confirmed, no merge.
evidence_links:
  - https://github.com/laszko14/nira-browser/pull/122
  - https://github.com/laszko14/nira-browser/actions/runs/41569421409
security: No TinyFish, merge, public release, force-push, destructive migration or certificate changes.
next_action: Open stacked draft PR targeting P0 and check SOURCE + integration tests. Repair all failures before any APK request.
```
