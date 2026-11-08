# AI_HANDOFF — Zavro Clean V4 / P4-B2 film-card recognition and extraction relevance

> Legacy archive: docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md. This checkpoint is prepared before the P4-B2 commit, so observed_head_sha is a historical pre-write snapshot. Live GitHub ref is authoritative. P4-B1 CODE_VERIFIED on `fe0607080a631960ab9b08b34c3e42ba512351c6`: SOURCE #42464810955 and PR #42464816562 completed SUCCESS, attempt 1.

```yaml
task_id: ZC-V4-P4-B2-FILM-CARD-RELEVANCE
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-v4-p4-20261107
base_sha: fe0607080a631960ab9b08b34c3e42ba512351c6
observed_head_sha: fe0607080a631960ab9b08b34c3e42ba512351c6
status: CI_PENDING
acceptance_source: User wrote "Dalej" after P4-B1 CODE_VERIFIED. The agreed next stage P4-B2 improves structural recognition of actual film cards and consistency of extraction, especially mixed result grids, recommendations and unrelated links. No APK, merge, release, cert change, profile modification or unapproved operations.
completed: Enhanced production recognition.js to reject profile/author/person/cast/comment/review/forum routes and same-page fragment anchors, exclude recommendation/related/trending/featured card hints, require card-local anchor title/semantic or film-route evidence rather than inheriting an unrelated poster. Enhanced production content.js extraction with bounded per-card exclusion of sponsored and recommendation/sidebar/featured contexts even when configured CSS selects all cards, aligned catalogLink navigation-route exclusions and same-page fragments. Added source-backed P4-B2 recognition tests for blocked routes, hash links, weak utility anchors, recommendations, valid unconventional title links, mixed user/movie links and ambiguous dual-film links. Added extraction integration tests for mixed poisoned grids and all-invalid lists using the real production recognition + bridge VM. Preserved source and protocol behavior for legitimate cards, CSS configuration and prior tests.
remaining: Execute SOURCE process-docs + JS + Kotlin/Robolectric + AndroidTest Kotlin compile + lint regression + evidence summary and stacked Draft PR shared gates + error-learning evidence at exact final P4-B2 SHA. Triage all CI failures and reconcile ERROR_CASES. Next step P4-C1 focuses pagination controls, only on separate user "Dalej".
required_ci: SOURCE process-docs and shared-tests JS WebExtension, Kotlin/Robolectric, AndroidTest Kotlin compile, Android lint baseline, final evidence summary all SUCCESS on exact commit SHA; stacked PR shared-tests JS/Kotlin/AndroidTest Kotlin compile/lint plus error-learning evidence SUCCESS on identical PR head SHA.
device: NOT_TESTED — source/VM/Gradle only, no real Nothing Phone (1) or live external websites.
apk: NOT_BUILT_FOR_P4 — prior signed P3-E v1224 belongs to another SHA; user did not authorize any new APK.
known_failures: Before initial P4-B2 CI zero confirmed P4-B2 failures. Reconcile observed jobs and record every distinct CI/review problem in ERROR_CASES before CODE_VERIFIED. Historical P4-A1 registered 8 FIXED, P4-A2/B1 zero observed CI failures.
verification: Verified live branch and PR #128 head fe0607080a631960ab9b08b34c3e42ba512351c6, Draft open unmerged with P3-E as base. Read AGENTS.md, docs/AI_HANDOFF.md and STARTUP_PREFLIGHT.md and task, workflow, test, pattern, review, error-learning, checkpoint, exception instructions at exact SHA. Reviewed current recognition.js, content.js extraction routes and score rules, P1/P4-A1/P3-E tests, and verified previous SOURCE #42464810955 and PR #42464816562 successes. No local checkout; CI required.
changed_paths:
  - app/src/main/assets/extensions/zavro-video-search/recognition.js
  - app/src/main/assets/extensions/zavro-video-search/content.js
  - app/src/test/js/video-search-p4b2-card-relevance.test.cjs
  - app/src/test/js/video-search-p3e-integration.test.cjs
  - docs/ai/ERROR_CASES.json
  - docs/AI_HANDOFF.md
verified_before_push:
  - Scan is read-only and bounded; no navigation, click, tracking, data transfer or added permissions.
  - Film URL must have film anchor-local proof; strong title-labelled unusual paths are allowed, ambiguous two-film links remain unaccepted.
  - CSS group can be broader than eligible cards, so per-card validation repeats during extraction. Profile links, hash anchors, sponsored/recommendations not persisted.
  - No schema/profile/session/key/ABI/version/build workflow edits. Tests run genuine production WebExtension scripts with synthetic site-owned DOM.
blockers:
  - Real-site / device acceptance E01-E18 remains NOT_TESTED.
  - Independent high-risk review unavailable; self-review and green CI are not independent on-device acceptance.
evidence_links:
  - https://github.com/laszko14/nira-browser/pull/128
  - https://github.com/laszko14/nira-browser/actions/runs/42464810955
  - https://github.com/laszko14/nira-browser/actions/runs/42464816562
next_action: Fix any P4-B2 CI failure and record it, get both SOURCE and PR required gates green at exact final SHA, confirm Draft PR head and STOP. On separate "Dalej" implement P4-C1 pager controls, not APK.
```
