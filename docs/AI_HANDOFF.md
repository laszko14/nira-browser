# AI_HANDOFF — Zavro Clean / T03 — podpisana aktualizacja v1199

> Bieżący checkpoint gałęzi procesu; historia: [archiwum](ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md).

```yaml
task_id: T03-SIGNED-APK-V1199
workflow_mode: staged
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: build/zavro-clean-t03-v1199-20261101
base_sha: e496e141f97045f1a9d315e349a1666277547569
observed_head_sha: e496e141f97045f1a9d315e349a1666277547569
status: CI_PENDING
acceptance_source: ETAP 9/9 — user command Kompiluj; produce signed v1199 update from T03 right panel fix and landscape rectangle diagnostics, installed version v1198
required_ci: zavro-t03-v1199-signed-update.yml authorized-source shared-js shared-jvm shared-lint shared-androidtest-compile signed-update verify-all
device: USER_REPORT_V1198 (left first-open slight jank; transparent right bookmarks fixed in code but not device verified; grey rectangle still present in landscape); V1199_NOT_TESTED
apk: V1199_NOT_BUILT; require signed arm64-v8a APK; package com.prirai.android.nira.debug; expected versionCode 1199; versionName 0.5.22-alpha104-clean
changed_paths:
  - app/build.gradle
  - .github/workflows/apk-release.yml
  - docs/AI_HANDOFF.md
  - app/src/test/js/release-contract-stage19a.test.cjs
  - app/src/test/js/update-apk-workflow-scope.test.cjs
  - .github/workflows/zavro-t03-v1199-signed-update.yml
verified_before_push:
  - Active branch HEAD checked before updating
  - app/build.gradle base versionCode 199 plus arm64 offset 1000 => expected output versionCode 1199
  - Android CI expected code 1199, name 0.5.22-alpha104-clean and artifact zavro-arm64-update-apk-v1199
  - Unchanged signing certificate SHA256 df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00
  - Previous run 41271943477 failed 5 JS version-fixture assertions after intentional version bump; corrected affected tests, preserving invalid version/cert negative controls
  - Only this dedicated branch/commit marker [authorized-v1199-build] allows the one-off signed build on push after user Kompiluj
blockers:
  - Confirm completed success on final SHA for all required process and shared tests
  - The one-off authorized v1199 build workflow must pass signed-update and verify-all
  - Confirm successful verify-update-apk.sh plus artifact SHA256 in build output before APK_VERIFIED
  - On phone retest right bookmarks background, first left panel open and investigate grey landscape rectangle
next_action: Check the one-off authorized v1199 build run, verify all required jobs and real arm64 APK/signature/SHA256 before delivering; then request phone report. Do not infer grey rectangle fixed.
evidence_links:
  - https://github.com/laszko14/nira-browser/actions/runs/41271158549
  - https://github.com/laszko14/nira-browser/actions/runs/41271943477
```

## Wznowienie

Przeczytaj AGENTS.md i checkpoint z jawnego SHA, porównaj żywy HEAD, nie używaj main jako źródła stanu. Kontrole CI i budowa APK są osobne; nie dopisuj po zielonym CI kolejnego commitu tylko dla run ID. Nie wykonuj merge, publikacji, kasowania danych ani instalacji. Zachowaj certyfikat, profile i sesje. Prostokąt ma nieustaloną przyczynę; przycisk Zbadaj prostokąt • raport nie widzi systemowych nakładek i obrazu Gecko.
