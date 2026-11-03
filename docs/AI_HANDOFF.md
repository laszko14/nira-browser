# AI_HANDOFF — Zavro Clean V4 / P2-D safe verified rule repair
> Historia: docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md.

```yaml
task_id: ZC-V4-P2-D-RULE-REPAIR
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: fix/zavro-clean-v4-p2-evidence-runtime-20261103
base_sha: ed3adb4abec37291ec1b257153cae6aeabf23c12
observed_head_sha: a713bb60afe5436f3467268719dcd202afbeeb35
status: CI_PENDING
acceptance_source: User IMPLEMENT P2-D diagnostics, evidence-only reversible repairs, provenance, profile isolation and regressions; absolutely NO APK.
completed: Implemented P2-D1 results-only/AJAX/human-gate diagnostics; P2-D2 bounded structural confidence ranking and unique previously verified backup candidate only with new query evidence, unstable CSS filtering and ambiguity rejection; P2-D3 explicit user-phrase-search proof bound to session/document/tab before CAS; P2-D4 one-row Room CAS with monotonic revision, rollback of configuration AND original state in existing JSON, manual provenance and no migration; P2-D5 JS and Robolectric negative/positive/restart/profile/revision regression tests.
remaining: Verify exact-final-SHA SOURCE and PR CI, repair if needed; real GeckoView sites, async iframe/Shadow replacement, rotation, physical device and real private runs remain NOT_TESTED. Next P2-E after CI.
required_ci: SOURCE push process-docs, shared-tests JS/JVM-Robolectric/AndroidTest compile/lint and quality-evidence all SUCCESS at exact final SHA, plus separate PR integration for same head_sha.
apk: NOT_BUILT; do not trigger private workflow or create any APK before whole P2 is complete.
device: NOT_TESTED
known_failures: 17 P2-D events observed and recorded, all FIXED by code commits 5991/5a25/a713 (original safety findings, 14 legacy test assertions in SOURCE and PR, navigation race, compileDebugKotlin nullable Results access causing seven failed jobs including dependent SOURCE summary, rollback state and learned-backup candidate review). Final CI on documentation checkpoint SHA is pending.
verification: Read live GitHub PR #123, head SHA and AGENTS.md, AI_HANDOFF.md, V3.2 STARTUP_PREFLIGHT, DEVELOPMENT_WORKFLOW, TEST_POLICY, QUALITY_GATES, ERROR_LEARNING, CHECKPOINT_POLICY, TASK_TEMPLATE, TEST_PATTERNS, EXCEPTIONS_AND_TOOLS from the original exact SHA. No local working tree or phone test.
blockers:
  - Physical GeckoView/real site corpus and user-device interactions are untested.
  - Cross-origin iframe and closed Shadow DOM are intentionally unsupported; ambiguous evidence fails closed.
  - Independent security review is not yet confirmed.
changed_paths:
  - app/src/main/assets/extensions/zavro-video-search/content.js
  - app/src/main/assets/extensions/zavro-video-search/recognition.js
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchRecognitionMemory.kt
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchRecognitionDiagnostics.kt
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchRepairEvidence.kt
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchRowCodec.kt
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchDao.kt
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchRepository.kt
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchBridgeProtocol.kt
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchBridgeController.kt
  - app/src/main/java/com/prirai/android/nira/videosearch/MyVideoSitesActivity.kt
  - app/src/test/js/video-search-p2d-diagnostics.test.cjs
  - app/src/test/java/com/prirai/android/nira/videosearch/VideoSearchP2DRepairTest.kt
  - app/src/test/java/com/prirai/android/nira/videosearch/VideoSearchConfigurationHealthTest.kt
  - app/src/test/java/com/prirai/android/nira/videosearch/VideoSearchConfigurationRevisionTest.kt
  - app/src/test/java/com/prirai/android/nira/videosearch/VideoSearchManualConfigurationTest.kt
  - docs/ai/ERROR_CASES.json
  - docs/AI_HANDOFF.md
verified_before_push:
  - Live PR 123 open draft, correct branch, HEAD ed3adb4abec37291ec1b257153cae6aeabf23c12; source and integration CI for P2-C SUCCESS on that SHA.
  - Existing Room schema v3 is unchanged; all repair and rollback state uses existing configurationJson.
  - P2-D SOURCE and PR on 5991: process-docs, JS, AndroidTest compile and lint PASS; JVM failed 14 old provenance/inspection-contract assertions. Fixed in 5a25.
  - SOURCE and PR on 0c214: Kotlin compileDebugKotlin failed on nullable Results?.page. Fixed in a713, with rollback state protection, unique previously VERIFIED fallback ranking, and new explicit Room regression cases.
  - Pending: repeat SOURCE and PR integration on FINAL SHA before marking CODE_VERIFIED.
  - No APK, merge, release, force-push, secret/certificate change or MPV edits.
evidence_links:
  - https://github.com/laszko14/nira-browser/pull/123
  - https://github.com/laszko14/nira-browser/actions/runs/41706574420
  - https://github.com/laszko14/nira-browser/actions/runs/41706587032
  - https://github.com/laszko14/nira-browser/actions/runs/41714819448
  - https://github.com/laszko14/nira-browser/actions/runs/41714825819
  - https://github.com/laszko14/nira-browser/actions/runs/41716281572
  - https://github.com/laszko14/nira-browser/actions/runs/41716291555
security: Search evidence requires exact query/new film identity; no automatic CAPTCHA/login bypass; no raw user query in Room logs or repair metadata; normal-only Room with owner, session, tab and revision rechecks.
next_action: Verify and repair SOURCE and PR CI on exact final SHA; then P2-E wider real-site evidence.
```
