# AI_HANDOFF — Zavro Clean V4 / P3-B safe semantic recognition
> History: docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md. P3-A analysis completed in ChatGPT. Base P2 SOURCE 42095971084 and PR 42095981503 were SUCCESS on bbb57d58456b1afa458b13811b018859400824f3. Those are historical P2 receipts, not CI for P3-B.

```yaml
task_id: ZC-V4-P3-B-SEMANTIC-RECOGNITION
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-v4-p3b-recognition-20261106
base_sha: bbb57d58456b1afa458b13811b018859400824f3
observed_head_sha: bbb57d58456b1afa458b13811b018859400824f3
status: CI_PENDING
acceptance_source: User said "Dalej" after full P3-A analysis; IMPLEMENT next agreed P3-B stage on a new stacked branch. No merge, release, forced update, certificate change, destructive operations or explicit APK request.
completed: P3-B JS recognition adds explicit positive result-region evidence, rejection of related/recommended/sidebar card containers even on /search pages, shared 1600-query budget across structure discovery, host locator checks, card-local metadata and group scoring. Query exhaustion reports SCAN_LIMIT rather than fabricating recognition. Legacy content.js fallback rejects region-excluded groups instead of bypassing structural negative evidence. Existing zcs1 locators, old CSS, Kotlin protocol and stored Room JSON untouched. Added regression tests for recommendation false positives, positive region evidence, query-cost cap, legacy fallback compatibility.
remaining: Run all required SOURCE and stacked PR integration CI on exact final commit SHA; diagnose regressions from actual CI. Performance and GeckoView behavior on real sites remain NOT_TESTED; P3-C / P3-D / P3-E are separate future stages.
required_ci: SOURCE process-docs + shared-tests (JS WebExtension, Kotlin/JVM Robolectric, AndroidTest compilation, Android lint-regression), checkout/evidence summary all SUCCESS on exact HEAD. Stacked PR integration JS/JVM/AndroidTest/lint/evidence SUCCESS. Missing/pending/skipped required job is NOT PASS.
device: NOT_TESTED; user-confirmed v1224 startup is historical P2 smoke only
apk: NOT_BUILT; no separate explicit request for private signed APK in this P3-B step
known_failures: Initial PR 42257993545 JS (494/495 PASS) and SOURCE 42257964558 JS both failed the existing single-homepage-recommendation test because positive region bonus promoted homepage cards. SOURCE process-docs failed check-ai-handoff because required_ci omitted shared-tests and archive path. SOURCE summary failed because required jobs failed. All four occurrences must be registered and repaired in this task; no baseline tests weakened.
verification: Direct GitHub preflight read AGENTS.md, docs/AI_HANDOFF.md, docs/ai/STARTUP_PREFLIGHT.md, docs/AGENT_REFERENCE.md, docs/VIDEO_SEARCH_AUDIT.md, TASK_TEMPLATE, DEVELOPMENT_WORKFLOW, TEST_POLICY, TEST_PATTERNS, QUALITY_GATES, ERROR_LEARNING, CHECKPOINT_POLICY and APK trigger at exact P2 base SHA. Existing PR 123 open/unmerged, matching full head. A new branch was created from exact P2 SHA. Local JavaScript/Gradle/ADB execution unavailable via this connector; required CI pending.
changed_paths:
  - app/src/main/assets/extensions/zavro-video-search/recognition.js
  - app/src/main/assets/extensions/zavro-video-search/content.js
  - app/src/test/js/video-search-p1-recognition.test.cjs
  - app/src/test/js/video-search-structural-v4.test.cjs
  - docs/AI_HANDOFF.md
  - docs/ai/ERROR_CASES.json
verified_before_push:
  - No new page clicks, navigation, POST, external requests, JavaScript permissions, profile/session changes or schema migration in P3-B.
  - Automatic negative evidence cannot be replaced by legacy fallback; manual selectors remain unchanged.
  - CSS lookup cap fails closed without promoting partial inspection to successful evidence.
  - Original manual picker and owner-bound Gecko message protocol stay unchanged.
  - P2 branch and stacked open PR 123 remain untouched.
blockers:
  - No physical Android/GeckoView execution in this task, hence device NOT_TESTED.
  - Independent reviewer unavailable; self review and CI do not prove live site acceptance.
evidence_links:
  - https://github.com/laszko14/nira-browser/pull/123
  - https://github.com/laszko14/nira-browser/actions/runs/42095971084
  - https://github.com/laszko14/nira-browser/actions/runs/42095981503
next_action: Complete SOURCE and stacked PR integration CI on P3-B final SHA, fix any real regression without weakening tests; then STOP pending separate P3-C implementation instruction.
```
