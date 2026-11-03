# AI_HANDOFF — Zavro Clean V4 / P2 scoped results hardening
> Archiwum historii: docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md.

```yaml
task_id: ZC-V4-P2-A-B-SCOPED-CONTROLS
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: fix/zavro-clean-v4-p2-evidence-runtime-20261103
base_sha: b566546207bed0ad236dc5f5c28a8807cd4ea5a6
observed_head_sha: 661491515e624226240b606db837f141dc786fe8
status: CI_PENDING
acceptance_source: User-approved continuation of V4 P2 on 2026-11-03; preserve verified evidence, profiles, data and old signed update identity.
completed: Preserve P2 evidence checks and scoped result-card extraction; extend existing zcs1 locators to bounded search inputs, submit actions and next controls in open Shadow DOM/same-origin iframe; root-local ARIA, SVG control hints, fresh pre-action resolution and iframe identity revocation; new negative JS regressions; update versionCode 1224 alpha120.
remaining: Exact final SOURCE and PR integration CI; P2-C virtualized list/scroll corpus and P2-D confirmed user-approved rule repair remain incomplete; cross-origin frames/closed shadow roots unsupported by design; physical GeckoView and real-site/device testing NOT_TESTED.
required_ci: SOURCE push process-docs, shared-tests JS/JVM-Robolectric/AndroidTest compile/lint regression, quality-evidence all SUCCESS on exact final source SHA; separate PR integration.
apk: APK_BLOCKED pending green exact final SOURCE CI and owner COMMENT review; expected package com.prirai.android.nira.debug, ABI arm64-v8a, versionCode 1224, versionName 0.5.22-alpha120-clean, certificate SHA256 df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00 unchanged.
device: NOT_TESTED
known_failures: Previous P2 initial five observations recorded; new scoped-card and SVG review gaps recorded; SOURCE/PR runs 41586101551/41586107020 failed a test literal syntax issue; SOURCE/PR 41586333089/41586339373 failed a recognition source escaped-newline syntax issue; all four CI job observations recorded; final re-verification pending.
verification: Existing P2 SOURCE 41575728798 and PR integration 41575733119 were reported green on bbb45f09f78c1ba456b274d5be7cacc653f1c429; not proof of this new change.
blockers:
  - Scoped forms and pagination require separate full protocol and device review, not assumed by scoped result-card extraction.
  - Physical Android GeckoView and cross-site dynamic corpus NOT_TESTED.
changed_paths:
  - app/src/test/js/video-search-p2-scoped-controls.test.cjs
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
  - Live PR #123 HEAD 661491515e624226240b606db837f141dc786fe8 and same-repo draft verified by GitHub connector.
  - Read AGENTS.md, docs/AI_HANDOFF.md, V3.2 STARTUP_PREFLIGHT, TEST_POLICY, QUALITY_GATES, ERROR_LEARNING, APK_CHAT_GITHUB_REVIEW_TRIGGER from exact source SHA.
  - Admin/push permissions confirmed; no local working tree or device was available in the connector-only environment.
evidence_links:
  - https://github.com/laszko14/nira-browser/pull/123
  - https://github.com/laszko14/nira-browser/actions/runs/41575728798
  - https://github.com/laszko14/nira-browser/actions/runs/41575733119
  - https://github.com/laszko14/nira-browser/actions/runs/41576565589
security: Scoped tokens require stable unique host selectors, open shadow roots or same-origin frames, and per-call revalidation; no cross-origin escalation or untrusted page actions. No TinyFish, merge, public release, force-push or certificate change.
next_action: Verify SOURCE CI + PR integration on final exact SHA, repair any failures, then single owner COMMENT review only after all required gates succeed.
```
