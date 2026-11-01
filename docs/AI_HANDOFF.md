# AI_HANDOFF — Zavro Clean / T03 — podpisana aktualizacja v1199

> Bieżący checkpoint gałęzi procesu. Wcześniejszy stan T03: SHA 001648305bda927bceb10047aca6749934348184.

task_id: T03-SIGNED-APK-V1199
workflow_mode: staged
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: improve/zavro-clean-chatgpt-github-process-20261101
base_sha: 001648305bda927bceb10047aca6749934348184
status: CI_PENDING
acceptance_source: User command "Kompiluj" after T03 panel/overlay diagnostic changes; installed v1198 / 0.5.22-alpha103-clean

Version and package contract:
- Previous installed: 1198
- Expected APK code: 1199 (base Gradle 199 with arm64 output offset +1000)
- Expected APK name: 0.5.22-alpha104-clean
- Package: com.prirai.android.nira.debug
- ABI: arm64-v8a
- Certificate SHA256: df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00
- Artifacts: zavro-arm64-update-apk-v1199
- Changed: app/build.gradle, .github/workflows/apk-release.yml, docs/AI_HANDOFF.md

Required verification:
- At final SHA: shared JS, JVM/Robolectric, lint regression, AndroidTest compilation, quality evidence, process docs as required.
- Explicit workflow_dispatch for apk-release.yml, build_kind=debug-apk, approval_phrase=Kompiluj on exact branch.
- Runner verify-update-apk.sh must pass on the resulting signed APK; log SHA256.
- APK: NOT_YET_VERIFIED; phone: V1199_NOT_TESTED.

Phone tests after successful build:
- Right bookmarks panel background: code fix NOT_YET_DEVICE_VERIFIED
- Landscape grey rectangle: persistent in v1198, root cause UNKNOWN, collect "Zbadaj prostokąt • raport"
- First left drawer opening: slight lag in v1198, subsequent openings smooth

Next action: Dispatch signed v1199 workflow, verify all required jobs and APK, then hand over installation/test instructions. No merge, data removal, publication or additional CI-only commits.
