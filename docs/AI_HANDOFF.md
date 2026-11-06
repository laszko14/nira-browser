# AI_HANDOFF — Zavro Clean V4 / P3-D inteligentne metadane i scalanie
> Archiwum: docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md. P3-C VERIFIED na 86cf7ebdf6b3f84b890d1a46fecf0802108b1db5: SOURCE 42269940678 i PR 42269950801 SUCCESS, attempt 1. To historyczne dowody P3-C, a nie wynik P3-D.

```yaml
task_id: ZC-V4-P3-D-SMART-METADATA
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-v4-p3d-smart-metadata-20261106
base_sha: 86cf7ebdf6b3f84b890d1a46fecf0802108b1db5
observed_head_sha: 86cf7ebdf6b3f84b890d1a46fecf0802108b1db5
status: CI_PENDING
acceptance_source: User said "Dalej" after P3-C CODE_VERIFIED, authorizing the next stage P3-D smart metadata and grouping. Scope: preserve existing two grouping toggles, no merge/release/force-push/destructive action/certificate change; no explicit private signed APK request.
completed: Add separate optional presentation-only "Scalaj inteligentnie" setting exclusive with legacy "Scalaj po nazwie" and "Scalaj po długości" choices, retaining run/tab/profile-local state and pagination anchors. New pure conservative bounded grouping requires exact or strong fuzzy title plus compatible duration if known, episode-marker equality, bounded title variants without transitive chains, exact original URLs and first-seen ordering. Legacy two grouping algorithms untouched. Card-local JSON-LD now understands safe image arrays/ImageObject and TVEpisode (only exact film URL); local schema duration meta content is parsed. No remote fetch, native protocol/schema/Room migration or identity rewrite. Added Kotlin smart-mode, UI-memory and JS metadata negative/positive regressions.
remaining: Verify SOURCE and stacked PR required CI on exact P3-D final SHA, triage and register CI/review errors without weakening assertions. P3-E is later integration and device acceptance.
required_ci: SOURCE process-docs + shared-tests JS WebExtension, Kotlin/JVM Robolectric, AndroidTest compile, Android lint baseline, exact-head checkout/evidence summary all SUCCESS. Stacked PR shared-tests and error-learning evidence SUCCESS. Any skipped/pending mandatory job is not PASS.
device: NOT_TESTED; cannot demonstrate on real GeckoView or real film sites until device tests.
apk: NOT_BUILT; only explicit authorized signed APK instruction may invoke owner PR review trigger, not ordinary push/PR.
known_failures: Six CI job failures registered in ERROR_CASES.json across two P3-D attempts. First SOURCE 42272583480 / PR 42272620819 Kotlin test compile error was Int rather than Long?; fixed at 56934d0c48fd9eb14edae7ef5e91e585ef42bc28. Second SOURCE 42273735594 / PR 42273745396 Kotlin suite had 1469/1470 PASS and one incorrect legacy duration-OR test expectation; test-only fix at e7d8ba7d93848d7d4593fafde420ee162928e891 now asserts actual unchanged two-group membership. Both SOURCE required summary jobs correctly failed. Six logged FIXED; new full CI still required on final SHA.
verification: Direct GitHub G1 preflight on 86cf7ebdf6b3f84b890d1a46fecf0802108b1db5: branch/ref and PR #125 open Draft, exact HEAD SHA, AGENTS.md and docs/AI_HANDOFF.md, docs/ai/STARTUP_PREFLIGHT.md, VIDEO_SEARCH docs, TASK_TEMPLATE, DEVELOPMENT_WORKFLOW, TEST_POLICY, TEST_PATTERNS, QUALITY_GATES, ERROR_LEARNING, CHECKPOINT_POLICY, EXCEPTIONS, and APK trigger all read at same ref. No repository file mutations on P3-C branch. Local Gradle/ADB unavailable; CI is required source test evidence.
changed_paths:
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchResultsMerge.kt
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchResultsActivity.kt
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchResultsTabUiMemory.kt
  - app/src/main/assets/extensions/zavro-video-search/content.js
  - app/src/test/java/com/prirai/android/nira/videosearch/VideoSearchResultsMergeTest.kt
  - app/src/test/java/com/prirai/android/nira/videosearch/VideoSearchResultsTabUiMemoryTest.kt
  - app/src/test/js/video-search-p9-link-identity.test.cjs
  - docs/AI_HANDOFF.md
  - docs/ai/ERROR_CASES.json
verified_before_push:
  - Smart merge is optional, legacy two filters and OR semantics untouched.
  - Grouping is UI-only, never rewrites original URLs, data, or tab/profile ownership.
  - Structured metadata remains card-local and linked to exact same-origin film URL; bounded parser.
  - No network requests, media capture, executable selectors, new permissions or persistent Room schema.
  - No PR merge, public release, force push or unauthorized APK trigger.
blockers:
  - Physical GeckoView/site tests NOT_TESTED; CI is not real device acceptance.
  - No independent external code reviewer; self-review plus required CI only.
evidence_links:
  - https://github.com/laszko14/nira-browser/pull/125
  - https://github.com/laszko14/nira-browser/actions/runs/42269940678
  - https://github.com/laszko14/nira-browser/actions/runs/42269950801
next_action: Run third SOURCE and stacked PR CI on final P3-D SHA after fixing strict legacy OR expectation; confirm Kotlin full 1470+ tests, JS, AndroidTest, lint, docs, evidence all SUCCESS then STOP before P3-E.
```
