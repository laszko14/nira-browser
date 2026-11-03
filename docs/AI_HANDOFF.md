# AI_HANDOFF — Zavro Clean V4 / P2-D safe verified rule repair
> Historia: docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md.

```yaml
task_id: ZC-V4-P2-D-RULE-REPAIR
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: fix/zavro-clean-v4-p2-evidence-runtime-20261103
base_sha: ed3adb4abec37291ec1b257153cae6aeabf23c12
observed_head_sha: ed3adb4abec37291ec1b257153cae6aeabf23c12
status: CI_PENDING
acceptance_source: User IMPLEMENT P2-D diagnostics, evidence-only reversible repairs, provenance, profile isolation and regressions; absolutely NO APK.
completed: Prepared P2-D1 results-only/AJAX/human-gate diagnostics; P2-D2 bounded candidate and ambiguity policy with unstable CSS filtering; P2-D3 user-phrase-search proof and context-bound controller refusing inspect-only writes; P2-D4 one-row Room CAS with monotonic revision, complete rollback snapshot in existing configuration JSON and manual-origin tagging; P2-D5 JS and Robolectric negative/positive regression tests.
remaining: Verify the exact final SOURCE and PR CI, repair any failures; real GeckoView sites, async iframe/Shadow replacement, rotation, device and real private runs remain NOT_TESTED; after green CI proceed to P2-E separately.
required_ci: SOURCE push process-docs, shared-tests JS/JVM-Robolectric/AndroidTest compile/lint and quality-evidence all SUCCESS at exact final SHA, plus separate PR integration for same head_sha.
apk: NOT_BUILT; do not trigger private workflow or create any APK before whole P2 is complete.
device: NOT_TESTED
known_failures: 4 P2-D observations (1 user report and 3 review findings) recorded in ERROR_CASES.json as OPEN until verified final fix SHA; no P2-D CI result before push.
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
  - docs/ai/ERROR_CASES.json
  - docs/AI_HANDOFF.md
verified_before_push:
  - Live PR 123 open draft, correct branch, HEAD ed3adb4abec37291ec1b257153cae6aeabf23c12; source and integration CI for P2-C SUCCESS on that SHA.
  - Existing Room schema v3 is unchanged; all repair and rollback state uses existing configurationJson.
  - No APK, merge, release, force-push, secret/certificate change or MPV edits.
evidence_links:
  - https://github.com/laszko14/nira-browser/pull/123
  - https://github.com/laszko14/nira-browser/actions/runs/41706574420
  - https://github.com/laszko14/nira-browser/actions/runs/41706587032
security: Search evidence requires exact query/new film identity; no automatic CAPTCHA/login bypass; no raw user query in Room logs or repair metadata; normal-only Room with owner, session, tab and revision rechecks.
next_action: Verify and repair SOURCE and PR CI on exact final SHA; then P2-E wider real-site evidence.
```
