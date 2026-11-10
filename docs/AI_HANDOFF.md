# AI_HANDOFF — Zavro Clean MPV/Play, APK update versionCode 1226

Archive: docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md.

Etap 5/5 potwierdzony przez SOURCE 42856474338 i PR 42857649495,
oba completed/success na SHA `8453a70e150cd8fbca01e79a16be5980d214aa03`. Podpisana kompilacja
v1225 (run 42860175611, artifact 13783719511) otrzymała APK_VERIFIED
dla pakietu, certyfikatu i ABI, ale użytkownik potwierdził, że ma
już zainstalowany versionCode 1225. Ten APK NIE jest aktualizacją.
Następna podpisana kompilacja musi mieć versionCode 1226.

Poniższy checkpoint zapisano PRZED końcowym CI zmiany v1226.
Wynik bieżący sprawdzaj na żywo na nowym pełnym HEAD SHA,
nie wyciągaj PASS z poniższego historycznego CI_PENDING.

```yaml
task_id: ZC-MPV-PLAY-APK-1226
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-mpv-play-diagnostics-stage1-20261110
base_sha: 8453a70e150cd8fbca01e79a16be5980d214aa03
observed_head_sha: 8453a70e150cd8fbca01e79a16be5980d214aa03
status: CI_PENDING
acceptance_source: User 2026-11-10 reports Clean v1225 already installed and requests corrected v1226 APK as continuation of the approved private signed update. No merge, release, force-push, uninstall or signing-key changes.
completed: Stage5 SOURCE 42856474338 and PR 42857649495 verified SUCCESS at baseline; prior owner-reviewed v1225 APK run 42860175611 successful but same versionCode as installed. Prepared minimal v1226 correction using app/build.gradle base versionCode=226 for arm64 output 1226 and versionName=0.5.22-alpha122-clean; matching explicit APK workflow verifier, artifact name, and strict JS update regression with actual user-installed baseline 1225. Recorded same-version install regression in canonical ERROR_CASES.json. Code-only fix commit 481505cf805840cafdfda4f88c3881678535bb11 prepared; checkpoint and error registry follow in same push.
remaining: Push complete two-commit change to current branch, confirm final SHA and SOURCE process-docs/shared-tests and PR independent full required checks all SUCCESS on final SHA. Then submit exactly one owner COMMENT review request bound to final PR HEAD, verify private signed arm64 APK package versionCode 1226 versionName alpha122, certificate and artifact. Physical installation and actual Play on site NOT_TESTED.
required_ci: SOURCE process-docs and shared-tests JavaScript, Kotlin/Robolectric, AndroidTest compile, Android lint regression, quality evidence all SUCCESS; PR shared-tests JavaScript, Kotlin/Robolectric, AndroidTest compile, lint regression, error-learning evidence all SUCCESS at final SHA.
apk: V1225 APK_VERIFIED but NOT_UPDATEABLE over installed V1225. New v1226 NOT_BUILT; use only authorized exact-SHA PR review trigger and stable GitHub Secrets.
device: NOT_TESTED; installed versionCode 1225 is user-confirmed; user's installed signature is not independently read.
privacy: No data migration, profile, session, playback, URL, cert, package ID or new APK feature changes; signed update is intended to preserve installation.
known_failures: ERR-20261110-MPV-APK-VERSIONCODE-1225 recorded under ZC-MPV-PLAY-APK-1226; issue is same update code as installed. Earlier open preview/Play site cases remain OPEN pending real on-device evidence; do not claim those bugs fixed by version bump.
verification: Base stage5 SOURCE 42856474338 and PR 42857649495 success (659/659 JS); v1225 signed APK 42860175611 success but unusable as version increment over installed v1225. New v1226 full Android CI and APK verification PENDING. Regression oracle now requires install versionCode greater than user-confirmed 1225.
verified_before_push: Checked live branch full SHA and draft PR #140 head at 8453a70e150cd8fbca01e79a16be5980d214aa03; read AGENTS.md, docs/AI_HANDOFF.md, active project instructions, APK_CHAT_GITHUB_REVIEW_TRIGGER.md, APK_RELEASE.md, signing policy, STARTUP_PREFLIGHT.md, TEST_POLICY.md, QUALITY_GATES.md, ERROR_LEARNING.md, CHECKPOINT_POLICY.md and exact Gradle/workflow/test/verifier scripts at base. No local device checkout; test results only after GitHub CI.
blockers: None known for SOURCE/PR CI or approved review mechanism. No assurance of installation without comparing installed cert and APK on actual phone; refuse uninstall/data loss.
evidence_links:
  - https://github.com/laszko14/nira-browser/actions/runs/42856474338
  - https://github.com/laszko14/nira-browser/actions/runs/42857649495
  - https://github.com/laszko14/nira-browser/actions/runs/42860175611
  - https://github.com/laszko14/nira-browser/commit/481505cf805840cafdfda4f88c3881678535bb11
  - https://github.com/laszko14/nira-browser/pull/140
changed_paths:
  - app/build.gradle
  - .github/workflows/apk-release.yml
  - app/src/test/js/release-contract-stage19a.test.cjs
  - docs/ai/ERROR_CASES.json
  - docs/AI_HANDOFF.md
next_action: Verify pushed final SHA, required SOURCE and PR CI; repair FAIL on new SHA; then owner-review private signed APK v1226, verifier and artifact. After APK_VERIFIED STOP and report; no uninstall, merge or public release.
```
