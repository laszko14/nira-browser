# Zavro Development System — further implementation: guarded Kotlin edits and cross-run recovery
Date: 2026-10-27
Status: **VERIFIED TEST MILESTONE**, not production release.

## A. Real Kotlin source edit, in isolated Clean sandbox only

- Protected Clean source remains `3330a0b3a057008886fde392f788a1941e0dfb84`.
- Sandbox branch `tools/zavro-edit-clean-20261027` now at `c8f2132a203f0ea9dc5315f7b63c3c3f3f833a0a`, 3 commits ahead of source.
- Edited **real production code** `app/src/main/java/com/prirai/android/nira/media/MediaRecentDeduplicator.kt` within the sandbox, without merging it into Clean. Old TTL loop did not expire an entry when the elapsed clock moved backwards. The sandbox change expires entries both when time goes backwards and when the TTL passes. Source patch commit `094106272b035e0a222238d8f0be28e55d8c1690`.
- Added `app/src/test/java/com/prirai/android/nira/media/MediaRecentDeduplicatorTest.kt` with 4 cases: TTL boundary, clock regression, separate native sessions and context change. Added test in commit `c8f2132a203f0ea9dc5315f7b63c3c3f3f833a0a`. Previous sandbox test file remains.
- `devtools/code-editor.mjs` extended to a tiny allowlist of existing Clean Kotlin production files, an exact expected GitHub blob SHA, expected workspace HEAD and **12 changed-line diff budget**. The allowed production paths are `MediaRecentDeduplicator.kt`, `VideoTilesPreferences.kt` and `MediaStreamIdentity.kt`. It forbids changing production files in MPV, creating new production files, altering import/package declarations in the changed region, modifying Gradle/workflows or targeting protected branches. A production edit is marked `production_affected=true` and requires Kotlin unit, Android lint **and Android build checks** before it could be promoted. The module is a validation and planning library, not yet a standalone trusted auto-commit GitHub workflow. Actual GitHub commit used create_tree/create_commit/update_ref with nonforced exact parent lease.
- [Sandbox CI run 40477361547](https://github.com/laszko14/nira-browser/actions/runs/40477361547): **SUCCESS**: **25/25 editor guard Node tests**, exact 3-file diff allowlist checked through GitHub compare, `:app:testDebugUnitTest` running the smoke test and four regression tests, and `:app:lintDebug`, **Gradle BUILD SUCCESSFUL in 3m 58s**. This task is NOT `:app:assembleDebug`; **no APK was built** and no certificate/install claim applies.
- Full Android assembly and device testing of this proposed behavioral fix remain pending. Do not merge to Clean without reviewing intended behavior and all required checks.

## B. Real cross-run checkpoint recovery

- New `devtools/resume-check.mjs` and `.github/workflows/zavro-cross-run-resume.yml`, read-only `contents:read/actions:read` trigger on one new `devtools/resume-probes/*.json`. Request strictly pins a historical completed MPV scenario and known artifact IDs; the workflow checks GitHub run SHA/conclusion/artifact expiry, downloads previous artifact and validates checkpoint/report with the exact original request and current config. It then invokes `progress` with all work callbacks set to throw, proving no step is repeated.
- [Recovery run 40478070913](https://github.com/laszko14/nira-browser/actions/runs/40478070913): **SUCCESS, 8/8 tests**, downloaded and restored the checkpoint from a **different earlier GitHub Actions run** [40472241780](https://github.com/laszko14/nira-browser/actions/runs/40472241780), artifact ID `12719096410`. Output: `CROSS_RUN_CHECKPOINT_RESTORED`, `verified_zero_repeat_operations=true`, no Android build or APK.
- This demonstrates **manual, explicitly pinned recovery of a completed checkpoint across runs**, not unattended restart of a partly failed job or ongoing orchestration. The checkpoint SHA-256 remains unkeyed and is NOT a cryptographic attestation.

## C. MPV build diagnostics reviewed

- MPV pinned v1167 historical Android CI [40444705833](https://github.com/laszko14/nira-browser/actions/runs/40444705833): build and lint jobs SUCCESS; test job FAILED because the protected Lua/DSP regression step timed out after 10 minutes while installing Ubuntu/FFmpeg libraries from apt mirrors. It is not proof of an application compile error.
- Separate emulator instrumentation run [40444705782](https://github.com/laszko14/nira-browser/actions/runs/40444705782): failure; two device test assertions regarding the last row of video tiles and accessibility controls after rotation, with additional Android emulator/ADB issues in logs. These are NOT fixed by the current Clean-only sandbox patch, and no Codex MPV source was modified.
- Stage 3 Build Bridge's signed workflow_dispatch remains **untested**. Stage 8 administrative dispatch gate remains in place. No new signed APK build request created, no release.

## D. Next steps
1. Review sandbox bugfix and decide whether to extend the allowlist and promote it through an isolated reviewed PR. Do not merge automatically.
2. Continue designing a safe GitHub Code Editor commit workflow with exact expected blob, branch update lease, explicit scope and production test sufficiency; current connected-GitHub manual CAS path has been proven on a small patch.
3. Add tested cross-run **partial** checkpoint recovery, with verification of all saved evidence; do not replay partial stages without validation.
4. Diagnose MPV emulator assertions separately; fix only on a separate MPV test branch after inspecting the Codex work.
5. When a new APK is necessary, require unique versionCode, stable signing certificate, approvals, full Android CI and independent APK inspection. User consent to a test compilation does not authorize release/merge/installation.

## Safety
- main, Clean and MPV heads re-verified unchanged.
- Only tooling and isolated Clean sandbox branches modified; no force push, production branch merges or APK.
