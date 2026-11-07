# AI_HANDOFF — Zavro Clean V4 / P3-E integration + real-site/device readiness
> Legacy history: docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md. P3-D CODE_VERIFIED on `abfeadc263646bbb443b1c49b294b303a4dbbf9c`: SOURCE #42275003159 and PR #42275012306 completed SUCCESS, attempt 1. Those are previous stage receipts, NOT P3-E CI.

```yaml
task_id: ZC-V4-P3-E-INTEGRATION-ACCEPTANCE
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-v4-p3e-integration-20261107
base_sha: abfeadc263646bbb443b1c49b294b303a4dbbf9c
observed_head_sha: abfeadc263646bbb443b1c49b294b303a4dbbf9c
status: CI_PENDING
acceptance_source: User said "Dalej" after verified P3-D. Next agreed P3-E is integration of entire film search pipeline, regression test scenarios and preparation for real-site/device acceptance. No authorized merge, public release, force-push, APK signing, certificate changes or destructive actions.
completed: Added real-source integration tests for recognition.js + content.js sharing one mocked native transport (explicit GET search, bounded recognition, structured exact card metadata, contiguous 16-card packets, stale cursor on recycled SPA DOM, category and cross-origin filtering). Added Kotlin/Robolectric Room integration for P2 query evidence + P3-C virtual replacement + exact film URL metadata correlation + profile-scoped transactional snapshot + P3-D optional smart grouping versus unchanged legacy duration OR, preserving partial terminal statuses and negative cross-origin tests. Added docs/ai/ZAVRO_V4_P3E_REAL_SITE_DEVICE_ACCEPTANCE.md with 18 executable real-device/site cases and evidence/status capture. Tests use synthetic DOM, not live websites or actual device. Existing production code, Room schema, GeckoView protocol and profile/session features remain unchanged.
remaining: Verify full SOURCE and stacked PR required CI on P3-E final SHA. If a new regression is found, record run/job/attempt/error and fix test or production before fresh CI. Physical real-site/Android acceptance and signed APK still separate and NOT_TESTED/NOT_BUILT.
required_ci: SOURCE process-docs + shared-tests JS WebExtension, JVM Kotlin/Robolectric, AndroidTest Kotlin compilation, Android lint baseline, checkout/source SHA evidence and summarizer all SUCCESS on final SHA; PR integration JavaScript/Kotlin/AndroidTest/lint and error-learning evidence SUCCESS on same final PR-head SHA. Expected build/release SKIPPED must not be reported as APK built.
device: NOT_TESTED — Android 15 / Nothing Phone (1) physical test, GeckoView site navigation, orientation and screen rate, real-site search, pagination, private browsing, long lists must be executed separately with approved APK.
apk: NOT_BUILT — no explicit signed APK authorization. Ordinary push and Draft PR must never trigger signing. Approved reviewer trigger requires separate confirmed request and signing prerequisites.
known_failures: Zero P3-E CI failures observed before first push. Prior P3-D 6/6 CI failure occurrences recorded FIXED in docs/ai/ERROR_CASES.json; do not mistake earlier SOURCE failures for current P3-E checks.
verification: Before starting, directly verified P3-D live branch and open Draft PR #126 at full base SHA; P3-D SOURCE #42275003159 and PR #42275012306 completed/success. At that exact SHA read AGENTS.md, docs/AI_HANDOFF.md, docs/ai/STARTUP_PREFLIGHT.md, TASK_TEMPLATE, DEVELOPMENT_WORKFLOW, TEST_POLICY, TEST_PATTERNS, QUALITY_GATES, ERROR_LEARNING, CHECKPOINT_POLICY, EXCEPTIONS_AND_TOOLS, APK_CHAT_GITHUB_REVIEW_TRIGGER and VIDEO_SEARCH_AUDIT. Direct GitHub access confirmed push/admin; no local checkout, emulator, ADB or Gradle used. Full source tests delegated to GitHub CI, not falsely labeled local tests.
changed_paths:
  - app/src/test/js/video-search-p3e-integration.test.cjs
  - app/src/test/java/com/prirai/android/nira/videosearch/VideoSearchV4P3EIntegrationTest.kt
  - docs/ai/ZAVRO_V4_P3E_REAL_SITE_DEVICE_ACCEPTANCE.md
  - docs/AI_HANDOFF.md
  - docs/ai/ERROR_CASES.json
verified_before_push:
  - No edits to production code, Room schema, existing profiles, APK, extension manifest, native bridge protocol or permissions.
  - P3-E tests invoke actual recognition and content scripts against a single synthetic native port, not duplicated algorithms.
  - Kotlin integration invokes actual collector, query proof, metadata correlator, repository, presenter and smartGroup.
  - Strict original URL identity, profile isolation and partial-versus-complete status have negative regression tests; no test assertions relaxed.
  - Real-site acceptance remains NOT_TESTED and all 18 cases start unverified.
blockers:
  - No attached physical Android device or authorized signed update APK; cannot claim DEVICE_VERIFIED.
  - Synthetic scripted DOM/Room tests do not prove that unknown external websites work end-to-end; manual acceptance matrix required.
  - No independent reviewer; self-review plus CI, no merge/release.
evidence_links:
  - https://github.com/laszko14/nira-browser/pull/126
  - https://github.com/laszko14/nira-browser/actions/runs/42275003159
  - https://github.com/laszko14/nira-browser/actions/runs/42275012306
next_action: Complete SOURCE and PR CI on exact P3-E final SHA, repair failures and reconcile ERROR_CASES.json; report CODE_VERIFIED if all mandatory jobs SUCCESS. Then stop, leaving device/APK acceptance as NOT_TESTED/NOT_BUILT pending explicit owner authorization.
```
