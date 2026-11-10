# AI_HANDOFF — Zavro Clean stage 10/10 (update version correction)

Archive: `docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md`.

```yaml
task_id: ZC-PREVIEW-COMPAT-10
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-preview-final-stage10-20261110
base_sha: a0afd6140cbb3847265eb12091e9c07e793d8e31
observed_head_sha: f1304b4510f28bce7b6e0d06b92e7eb1278e0634
status: CI_PENDING
acceptance_source: User authorized stages 7–10 and private signed update after green CI; supplied Nothing Phone (1) About screenshot showing installed versionCode 1224.
completed: Stage7–9 SOURCE/PR CI SUCCESS. Stage10 prior SHA 9617574be8d2b74e9d5ef82b2fbe31b3387d0497 SOURCE 42806429603 and PR 42806437189 SUCCESS; signed build 42807937319 passed but also had code 1224, so cannot update installed code 1224. Version bumped to arm64 install code 1225 and name 0.5.22-alpha121-clean; local JS 632/632 and process tests 62/62 PASS.
remaining: Verify final SOURCE and PR CI on new SHA; re-read APK trigger, submit exact-SHA owner PR review, build and inspect new signed artifact. Actual installed package and certificate remain unverified.
required_ci: SOURCE process-docs, shared-tests JS, JVM/Robolectric, AndroidTest compile, lint/baseline, quality-evidence; PR Android CI integration JS, JVM, AndroidTest compile, lint, error-learning evidence.
device: NOT_TESTED — About screenshot confirms installed version only; no physical execution test of new APK, real site or MPV Alpha.
apk: OLD_BUILD_INVALID_FOR_UPDATE — artifact 13761880931 has code 1224, equal to installed code. New signed APK pending final CI.
known_failures: 46 open ledger cases. Stage10 GitHub metadata anomaly ACCEPTED_RISK; installed-version parity registered as FIXED in code, CI pending.
verification: User screenshot shows installed 0.5.22-alpha120-clean (1224). Stage10 RED version monotonicity test failed on old code; GREEN targeted and full JS 632/632 after bump. Actual installed applicationId and certificate unknown.
changed_paths:
  - app/build.gradle
  - .github/workflows/apk-release.yml
  - app/src/test/js/release-contract-stage19a.test.cjs
  - app/src/test/js/video-search-neon-redesign.test.cjs
  - docs/ai/PREVIEW_COMPAT_STAGE10.md
  - docs/ai/ERROR_CASES.json
  - docs/AI_HANDOFF.md
verified_before_push:
  - New arm64 versionCode 1225 is strictly greater than installed 1224 in screenshot.
  - Debug package and signing certificate contract are unchanged; workflow expected version and artifact name match Gradle.
  - Full local JavaScript 632/632, process tests 62/62 and error-learning validation PASS.
blockers:
  - Final SOURCE/PR CI and new signed build pending. Installed package and certificate not shown by screenshot.
evidence_links:
  - https://github.com/laszko14/nira-browser/pull/139
  - https://github.com/laszko14/nira-browser/actions/runs/42806429603
  - https://github.com/laszko14/nira-browser/actions/runs/42806437189
  - https://github.com/laszko14/nira-browser/actions/runs/42807937319
next_action: Verify final SOURCE/PR CI, build corrected signed APK via exact-SHA owner review, then compare to installed package/certificate if evidence becomes available.
```
