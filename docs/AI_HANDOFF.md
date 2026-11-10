# AI_HANDOFF — Zavro Clean, stage 5/10: nira-bg-play

Archiwum dawnego handoffu: `docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md`.
Checkpoint przed końcowym commit/CI; live HEAD i GitHub Actions mają pierwszeństwo.

```yaml
task_id: ZC-PREVIEW-COMPAT-5
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-preview-bgplay-stage5-20261110
base_sha: fb41a867319adc21c6a81ba7d3425bacd55cc1ec
observed_head_sha: fb41a867319adc21c6a81ba7d3425bacd55cc1ec
status: CI_PENDING
acceptance_source: User DALEJ — stage 5/10 nira-bg-play audit and justified fixes, preserve background playback and previews; no APK or merge.
completed: Verified stage4 source HEAD/PR #133 and exact SOURCE #42773664164 + PR #42773669505 SUCCESS. Read AGENTS and relevant V3 instructions plus content/background JS and Kotlin source at same SHA. Found page-world pause auto-resume affecting preview/late intent, and native-host-vs-cache allowlist race. Added targeted fixes, 10 actual-JS synthetic tests, report, two error records.
remaining: Execute SOURCE process-docs/shared-tests JS/JVM/AndroidTest compile/lint/quality-evidence and PR Android CI on exact final SHA. Diagnose/fix any FAIL. Gecko/device, live affected site and profile behavior NOT_TESTED.
required_ci: SOURCE process-docs plus shared-tests JS, Kotlin/Robolectric, AndroidTest compile, lint/baseline and quality-evidence SUCCESS; PR Android CI shared-tests and error-learning-evidence SUCCESS on PR merge ref for final source SHA.
device: NOT_TESTED — no real site or physical Nothing Phone (1) traces.
apk: NOT_BUILT — no signed private APK in stages 1-10.
known_failures: ERR-20261110-PREVIEW-01/-03/-04 remain OPEN for site/device; ERR-20261110-PREVIEW-05 and ERR-20261110-BGPLAY-ALLOWLIST-RACE in new stage. Stage2 CI-42767349592-1-129666996717 FIXED.
verification: Direct GitHub connector confirmed live stage4 SHA, PR and both CI; actual WebExtension scripts inspected. Local checkout unavailable; mandatory tests run in GitHub CI. High-risk independent reviewer unavailable (self-review required).
changed_paths:
  - app/src/main/assets/extensions/nira-bg-play/content.js
  - app/src/main/assets/extensions/nira-bg-play/background.js
  - app/src/test/js/preview-bgplay-stage5.test.cjs
  - docs/ai/PREVIEW_COMPAT_STAGE5.md
  - docs/ai/ERROR_CASES.json
  - docs/AI_HANDOFF.md
verified_before_push:
  - Only allowlisted origins activate page-world patch; background playback retained.
  - No changes to Kotlin, stored user data, profiles, package, ABI, cert, MPV Alpha, or APK.
blockers:
  - Final SOURCE/PR CI pending; real Gecko/device/site NOT_TESTED.
evidence_links:
  - https://github.com/laszko14/nira-browser/pull/133
  - https://github.com/laszko14/nira-browser/actions/runs/42773664164
  - https://github.com/laszko14/nira-browser/actions/runs/42773669505
next_action: Verify final stage5 SHA SOURCE + PR CI, repair FAIL inside stage5, report and STOP until next DALEJ for stage6.
```
