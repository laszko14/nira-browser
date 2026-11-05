# AI_HANDOFF — Zavro Clean V4 / P2-F scoped DOM context identity
> History: docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md. P2-E completed on b173b47ee4f703b408088057bd9bc01c50175e2f (SOURCE 41876991486 / PR 41877001413, attempt 2 SUCCESS).

```yaml
task_id: ZC-V4-P2-F-SCOPED-CONTEXT-IDENTITY
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: fix/zavro-clean-v4-p2-evidence-runtime-20261103
base_sha: b173b47ee4f703b408088057bd9bc01c50175e2f
observed_head_sha: b173b47ee4f703b408088057bd9bc01c50175e2f
status: CI_PENDING
acceptance_source: User IMPLEMENT next unfinished P2 after P2-E; determine scope from live repo and finish diagnosis, changes, regressions, review, commit/push and green SOURCE/PR CI. Explicitly no APK, merge or MPV.
completed: P2-F chosen from code gap (not a pre-existing named milestone). A zcs1 locator could be rebound after SPA replaced an entire iframe/Shadow DOM host while keeping its CSS selector, or the same host got a different open shadowRoot. Bind host CSS slot identity to observed node per document and open ShadowRoot identity to host. Refuse to confirm/rebind after same-document replacement. CONTEXT_REPLACED reason is validated by native protocol, explained in Polish UI, and blocks legacy fallback. Regular leaf-card AJAX rerender within same root stays valid. Fresh full navigation gets a new content-script scope and can resolve persisted selectors. No new network, clicks, storage schema, DB migration or profile change.
remaining: Final SOURCE and PR integration CI on final pushed SHA; investigate failures if any. Physical GeckoView/Android 15, live third-party sites, closed Shadow DOM and cross-origin iframes NOT_TESTED. A previously unseen host before first observation cannot be correlated with prior runtime identity.
required_ci: SOURCE process-docs and shared-tests JS WebExtension, Kotlin/Robolectric, AndroidTest compile, Android lint regression baseline and evidence summary SUCCESS on exact final SHA; separate PR integration required jobs SUCCESS. Never build APK for this task.
device: NOT_TESTED
apk: NOT_BUILT; user explicitly prohibited APK.
known_failures: 3 independent review defects in this task, all corrected by 25501af603b2c71f0e7b6b6dc831298993a58245, each with a regression in docs/ai/ERROR_CASES.json. No CI failures observed yet for P2-F. Prior P2-E external Maven Central HTTP 429 remains an accepted environmental risk, not an application defect.
verification: Live PR #123 branch/head b173b47ee4f703b408088057bd9bc01c50175e2f with admin/push permission; AGENTS.md, docs/AI_HANDOFF.md, STARTUP_PREFLIGHT.md, DEVELOPMENT_WORKFLOW.md, TASK_TEMPLATE.md, TEST_POLICY.md, QUALITY_GATES.md, CHECKPOINT_POLICY.md, ERROR_LEARNING.md, TEST_PATTERNS.md, EXCEPTIONS_AND_TOOLS.md and ERROR_CASES.json fetched from identical explicit SHA. Source and tests examined at same ref. Remote-only code changes; local checkout, device and site integrations not tested. Code changes in 25501af603b2c71f0e7b6b6dc831298993a58245 with self-reviewed scope.
changed_paths:
  - app/src/main/assets/extensions/zavro-video-search/recognition.js
  - app/src/main/assets/extensions/zavro-video-search/content.js
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchBridgeProtocol.kt
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchRecognitionDiagnostics.kt
  - app/src/test/js/video-search-p1-recognition.test.cjs
  - app/src/test/js/video-search-structural-v4.test.cjs
  - app/src/test/java/com/prirai/android/nira/videosearch/VideoSearchP1RecognitionTest.kt
  - docs/ai/ERROR_CASES.json
  - docs/AI_HANDOFF.md
verified_before_push:
  - P2-E SOURCE and PR workflows both SUCCESS attempt 2 at b173b47ee4f703b408088057bd9bc01c50175e2f; no requirement to rerun completed P2-E audit.
  - P2-F code self-review via compare_commits found only seven scoped source/test files. No signing, workflows, MPV or Room changes.
  - Three regression families: new host with old CSS, swapped ShadowRoot on old host, legacy fallback after replacement. Also unchanged-host AJAX rerender and full document reload.
  - RED execution on historical source not performed; new tests are source-level regression cases. Final CI must prove them.
  - No APK build, merge, release, force-push, certificate or MPV operations.
blockers:
  - Separate independent privacy/lifecycle reviewer unavailable; self-review and CI do not replace independent risk acceptance.
  - Real GeckoView third-party sites, device, runtime navigation, closed Shadow and cross-origin frames remain NOT_TESTED.
evidence_links:
  - https://github.com/laszko14/nira-browser/pull/123
  - https://github.com/laszko14/nira-browser/commit/25501af603b2c71f0e7b6b6dc831298993a58245
  - https://github.com/laszko14/nira-browser/actions/runs/41876991486
  - https://github.com/laszko14/nira-browser/actions/runs/41877001413
security: Per-document WeakMap host and shadow-root identity, only CSS selectors sent to native, no raw DOM or search query logging, no new network or user interaction, no silent automated repair.
next_action: Check final SOURCE and PR integration jobs on the pushed exact SHA; triage and fix failures if any, then stop. No APK.
```
