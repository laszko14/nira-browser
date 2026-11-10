# AI_HANDOFF — Zavro Clean, Gecko muted-autoplay stage 2/10

Archiwum dawnego handoffu: `docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md`.

Checkpoint przed commitem/CI; live HEAD gałęzi jest źródłem prawdy. Bez merge.

```yaml
task_id: ZC-PREVIEW-COMPAT-2
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-preview-autoplay-stage2-20261110
base_sha: b80cba336f1a34f0eeadfd4ae1dd59e5e474b7da
observed_head_sha: 64451584416d3097e169df0245d491b5efb11f59
status: CI_PENDING
acceptance_source: User DALEJ — stage 2/10; permit muted HTML5 autoplay, retain protection from audible autoplay, no APK.
completed: Verified source prefs (default 5 blocks all, blocking_policy 2 strict click-to-play), Gecko runtime config path and Mozilla semantics; changed default to 1 with the strict gesture policy unchanged; added stage 2 source-contract and negative tests, documented evidence and limits.
remaining: Confirm mandatory SOURCE and PR CI on exact final SHA, then STOP. Actual problem-site, Gecko runtime behavior and device playback NOT_TESTED; later stage 3 covers preview-assist.
required_ci: SOURCE process-docs plus shared-tests JS, JVM/Robolectric, AndroidTest compile, lint/baseline and quality-evidence SUCCESS; PR Android CI shared regression and error-learning evidence on merge checkout tied to final source SHA.
device: NOT_TESTED — no actual affected site, physical device or Gecko media playback probed.
apk: NOT_BUILT — explicitly excluded for all 10 stages until later authorization.
known_failures: Preview incident ERR-20261110-PREVIEW-01 remains OPEN; no actual site test. Historical stage 1 enum failure fixed by SHA b80cba336f1a34f0eeadfd4ae1dd59e5e474b7da; SOURCE run 42767349592 / attempt 1 / process-docs job 129666996717 failed handoff restart contract because required_ci omitted literal shared-tests; corrected content without weakening tests. Also stage2 registry references were corrected before the error-learning gate.
verification: Read AGENTS.md, AI_HANDOFF.md, STARTUP_PREFLIGHT.md, CHATGPT_PROJECT_INSTRUCTIONS.md, quality gates, test policy, error-learning and Kotlin sources at explicit SHA b80cba336f1a34f0eeadfd4ae1dd59e5e474b7da; Mozilla media policy verified separately.
changed_paths:
  - app/src/main/java/com/prirai/android/nira/components/UserJsPreferences.kt
  - app/src/test/js/preview-autoplay-stage2.test.cjs
  - docs/ai/PREVIEW_COMPAT_STAGE2.md
  - docs/ai/ERROR_CASES.json
  - docs/AI_HANDOFF.md
verified_before_push:
  - Default autoplay only changes 5 to 1. Click-to-play blocking_policy 2 unchanged; no unmuted autoplay globally allowed.
  - No media WebExtension, MPV Alpha, user settings, profiles, ABI, package, signing or persistent user data modified.
  - No runtime or device execution claimed from static source-contract tests.
blockers:
  - Mandatory SOURCE/PR CI must be rerun on the corrected final SHA; first SOURCE had a confirmed handoff-content FAIL.
  - No reproducible real website or device available; behavior NOT_TESTED.
evidence_links:
  - https://github.com/laszko14/nira-browser/pull/130
  - https://github.com/laszko14/nira-browser/actions/runs/42764145426
  - https://github.com/laszko14/nira-browser/actions/runs/42764151788
  - https://wiki.mozilla.org/Media/block-autoplay
next_action: Verify required SOURCE and PR CI on final stage2 SHA, resolve FAIL within stage 2, report, then STOP. Next DALEJ starts stage 3/10 only after CI verified.
```
