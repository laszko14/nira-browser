# AI_HANDOFF — Zavro Clean V4 / P2-G3 end-to-end integration
> History: docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md. P2-G2 completed at dfef9c383a5a347bbb226585605305fe6fa86c1c; SOURCE 42092884956 / PR 42092896183 SUCCESS, attempt 1. This task finishes the three-level P2-G implementation; the live CI result belongs to final workflow runs, not this source snapshot.

```yaml
task_id: ZC-V4-P2-G3-END-TO-END-INTEGRITY
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: fix/zavro-clean-v4-p2-evidence-runtime-20261103
base_sha: dfef9c383a5a347bbb226585605305fe6fa86c1c
observed_head_sha: dfef9c383a5a347bbb226585605305fe6fa86c1c
status: CI_PENDING
acceptance_source: User said "dalej" after verified P2-G2; implement last P2-G3 level, end-to-end search, pagination, persistence and security verification, with code, tests, review, commit/push and final CI. No merge, public release, APK, signing changes or MPV.
completed: Code commit ef711d573459f3a5eb689b1f0d1cf961ecca48b6 adds safe same-origin result-link classification for account/login/checkout/payment/download paths; rejects a shrunk/shifted DOM continuation with stale rather than false empty completion; independently tracks extract_batch/extract_crawl cursor node identity and resets on route change or new page generation. Test commit dde2b5f24fba336a6e8dc63b6df027c0050c4f35 adds a native bridge decode + collector + queue-status end-to-end regression for stale continuation. Seven JS behavior tests cover shrink, insertion, append, fresh generation, preview/crawl independence, non-film links and retained safe film identity. Existing worker search verification, revision-based Room CAS, private profile isolation, multi-site queue, cancellation, memory/repair and pagination tests remain intact and are required in full CI.
remaining: Run SOURCE and PR CI on exact final pushed SHA. Investigate any failure; no PASS before job success. Physical GeckoView/real websites, device orientations, background suspension and live search interactions still NOT_TESTED.
required_ci: SOURCE process-docs and shared-tests JavaScript WebExtension, Kotlin/JVM Robolectric, AndroidTest Kotlin compile, Android lint regression baseline and checkout/evidence summary SUCCESS on exact final SHA. Separate PR integration JS/JVM/AndroidTest/lint/evidence SUCCESS; build/release SKIPPED acceptable only for tests-only.
device: NOT_TESTED
apk: NOT_BUILT; no signing or private APK requested for these three P2-G levels.
known_failures: Two P2-G3 code review defects recorded as ERR-20261105-P2G3-0001 and ERR-20261105-P2G3-0002 with regression cases. No P2-G3 CI failures observed before push. Historical Maven Central 429 P2-G2 was environmental and absent in final P2-G2 CI.
verification: Preflight at dfef9c383a5a347bbb226585605305fe6fa86c1c via direct GitHub connector: branch/PR #123 live SHA matched; AGENTS.md and docs/AI_HANDOFF.md plus STARTUP_PREFLIGHT.md, TEST_POLICY.md, TEST_PATTERNS.md, QUALITY_GATES.md, ERROR_LEARNING.md, DEVELOPMENT_WORKFLOW.md, CHECKPOINT_POLICY.md, TASK_TEMPLATE.md and EXCEPTIONS_AND_TOOLS.md all read from same explicit SHA. Repository tree contains only root AGENTS.md; P2-G1 and G2 SOURCE+PR CI on their own final SHAs green. P2-G3 JavaScript and added tests syntax checked in V8; full RED on old production source and local JDK/ADB NOT_TESTED, GitHub Actions must execute integration regressions. Code/diff self-reviewed before branch update.
changed_paths:
  - app/src/main/assets/extensions/zavro-video-search/content.js
  - app/src/test/js/video-search-p6-batches.test.cjs
  - app/src/test/js/video-search-p9-link-identity.test.cjs
  - app/src/test/java/com/prirai/android/nira/videosearch/VideoSearchPageBatchProtocolTest.kt
  - docs/AI_HANDOFF.md
  - docs/ai/ERROR_CASES.json
verified_before_push:
  - No Gecko engine bridges, Room schema, persisted JSON format, production Kotlin behavior, worker permissions, lifecycle/selection logic or signing workflow changed.
  - One existing JS protocol status 'stale' is reused; the Kotlin decoder and collector already reject stale windows.
  - A new crawl generation starts only at explicit index 0; trusted page action and genuine append remain possible.
  - Login/payment/download URLs are not film identities; genuine same-origin film links keep full query parameters and metadata.
  - No automatic user interaction, query logging, raw DOM persistence, profile/session changes, destructive operations or force push.
blockers:
  - Independent lifecycle/security reviewer unavailable; own review and CI do not prove complete real-device acceptance.
  - Device, real third-party sites, physical Gecko, private profile switching under actual load NOT_TESTED.
evidence_links:
  - https://github.com/laszko14/nira-browser/pull/123
  - https://github.com/laszko14/nira-browser/commit/ef711d573459f3a5eb689b1f0d1cf961ecca48b6
  - https://github.com/laszko14/nira-browser/commit/dde2b5f24fba336a6e8dc63b6df027c0050c4f35
  - https://github.com/laszko14/nira-browser/actions/runs/42092884956
  - https://github.com/laszko14/nira-browser/actions/runs/42092896183
security: No APK and no private data read/write; stale DOM never becomes a completed run, unsafe navigation never becomes a film URL. Original profile/Room CAS and native owner/epoch proof remain unchanged.
next_action: Check final pushed SHA, SOURCE process-docs/shared-tests and separate PR integration CI, record and fix any failures without weakening tests; after success stop. P2-G complete in code; real-device verification remains separate.
```
