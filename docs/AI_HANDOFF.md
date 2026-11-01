# AI_HANDOFF — Zavro Clean / nowy interfejs wyszukiwarki filmów

```yaml
task_id: VIDEO-SEARCH-UI-DATE-TITLE-V1204
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-video-search-ui-date-v1203-20261101
base_sha: 6ab034baeb1b973452bb0bb551ffbbf4556a2dd9
status: CI_PENDING
source_version: 0.5.22-alpha108-clean (ARM64 versionCode 1203)
target_version: 0.5.22-alpha109-clean (ARM64 versionCode 1204)
scope:
  - Refresh video result surfaces and group per-site actions into collapsible sections.
  - Collapse result controls while the results list/grid scrolls forward; reveal on reverse scroll.
  - Extract safe publication dates, persist through Room v2->v3, and sort newest/oldest.
  - Reject titles that consist only of a runtime timestamp.
  - Preserve profile/session isolation, history, result positions, package, ABI, and signing certificate.
required_ci: PR regression workflow on final SHA; then authorized workflow_dispatch debug-apk on same final SHA, verify-update-apk.sh, certificate and SHA-256 evidence.
device: NOT_TESTED
local_android_tests: BLOCKED (Gradle wrapper download unavailable: network unreachable); use GitHub Actions.
local_js_tests: 405/405 PASS, including publication-date/runtime-title regressions.
safety: No merge to main, public release, force-push, or destructive migration.
checkpoint: Implementation and final source review complete. 405/405 local JavaScript tests PASS; focused JS title/date regression PASS. Android/Room/Kotlin verification is pending because local Gradle distribution download failed with network unreachable. No APK/device test yet.
next_action: Push commit, open draft PR against the v1203 branch and wait for required CI. Then run authorized private debug-apk workflow on the same final SHA and verify package, version, ARM64 ABI, fixed signing certificate and APK SHA-256.
```

## Wznowienie

Sprawdź żywy HEAD gałęzi oraz ten handoff z jawnego SHA przed dalszymi zmianami. Zachowaj starszy checkpoint scalania wyników w `docs/ai/archive/AI_HANDOFF_VIDEO_RESULTS_MERGE_V1203_20261101.md`. `CI_PENDING` nie oznacza PASS. Raportuj CODE_VERIFIED, APK_VERIFIED i DEVICE_VERIFIED osobno.
