# AI_HANDOFF — Zavro Clean V4 / P4-D1 card-local film metadata

> Historical archive: docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md. P4-D1 pre-commit checkpoint. The live branch HEAD always overrides historical observed_head_sha. P4-C2 CODE_VERIFIED at `76a99619094d980b47c54f694e2affc6b8c94321`; SOURCE #42476720975 and PR #42476724535 completed SUCCESS.

```yaml
task_id: ZC-V4-P4-D1-CARD-METADATA
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-v4-p4-20261107
base_sha: 76a99619094d980b47c54f694e2affc6b8c94321
observed_head_sha: 76a99619094d980b47c54f694e2affc6b8c94321
status: CI_PENDING
acceptance_source: User requested to locate stalled "Analiza architektury wyszukiwarki" and continue the existing one-stage-at-a-time P4 sequence. After independently verifying P4-C2 final SOURCE/PR CI, proceed only with P4-D1. No APK or merge.
completed: Improved exact-film-URL, card-local JSON-LD handling for schema.org absolute URL types and TVEpisode season/episode labels, preserving identity in the existing title field. Accept bounded ISO 8601 duration minutes/hours up to one day; reject invalid calendar dates that Date.parse silently normalizes. Added six source-backed integration tests for exact URL ownership, two distinct episodes, URLs from neighboring films, invalid dates and duplicate episode markers. Kept prior strict no-fetch, no-navigation, native protocol and profile isolation.
remaining: Verify required final-SHA SOURCE and PR CI, fix any observed failures and register each real failure in ERROR_CASES.json. Next P4-D2 only after a new user "Dalej".
required_ci: SOURCE process-docs and shared-tests JS WebExtension, Kotlin/Robolectric JVM, AndroidTest Kotlin compile, Android lint regression baseline and final evidence summary all SUCCESS on exact source SHA; stacked PR JS/Kotlin/AndroidTest/lint and error-learning evidence all SUCCESS.
device: NOT_TESTED — no physical Nothing Phone (1), Android GeckoView nor real site tests.
apk: NOT_BUILT_FOR_P4 — no P4 signing or APK authorized, prior signed P3-E v1224 belongs to an earlier SHA.
known_failures: One observed P4-D1 SOURCE process-docs job failure #128730766555 in run #42480367904 at SHA 5d69a26e164e5f86d3a2bacaf3ad37c01e32abd1: missing exact legacy archive reference in rewritten handoff. Fixed in this correction; failure event and any dependent summary job must be recorded at next registry checkpoint before final CI.
verification: Confirmed live P4 branch HEAD 76a99619094d980b47c54f694e2affc6b8c94321, open/unmerged Draft PR #128, AGENTS.md, docs/AI_HANDOFF.md and selected startup/test/checkpoint/error sources at explicit SHA; independently verified final SOURCE and PR P4-C2 jobs and exact checkout. No local Android build or physical device tests.
changed_paths:
  - app/src/main/assets/extensions/zavro-video-search/content.js
  - app/src/test/js/video-search-p3e-integration.test.cjs
  - docs/AI_HANDOFF.md
  - docs/ai/ERROR_CASES.json
verified_before_push:
  - Only same exact film page URL permits structured metadata correlation; advertisements and neighbors cannot supply fields.
  - No network fetch, clicks, new permissions, data persistence, Room schema, native protocol, profile or certificate changes.
  - Episode marker only from exact-matched TVEpisode structured entity and valid bounded season/episode fields.
blockers:
  - Real-site and device acceptance NOT_TESTED.
  - Independent lifecycle review not available; this is self-review plus source/integration CI.
evidence_links:
  - https://github.com/laszko14/nira-browser/pull/128
  - https://github.com/laszko14/nira-browser/actions/runs/42476720975
  - https://github.com/laszko14/nira-browser/actions/runs/42476724535
next_action: Wait for final exact-SHA SOURCE and PR CI; repair only confirmed regressions; reconcile any CI failures, then STOP at P4-D1. P4-D2 requires a separate user continuation.
```
