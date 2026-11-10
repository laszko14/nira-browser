# AI_HANDOFF — Zavro Clean stage 6/10 (GeckoView and WebExtension lifecycle)

Archiwum dawnego handoffu: `docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md`.
Checkpoint przed końcowym CI. Live HEAD i workflow na końcowym SHA są autorytatywne.

```yaml
task_id: ZC-PREVIEW-COMPAT-6
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-preview-mediasession-stage6-20261110
base_sha: 7bee6c61b8c1a6235a3c6e00f4db189393bb4799
observed_head_sha: 82604e300de5162984f7280b6f9599f7734c1e04
status: CI_PENDING
acceptance_source: User DALEJ — stage 6/10 audit and repair confirmed GeckoView MediaSession/WebExtension lifecycle risks; preserve profiles and media; no APK or merge.
completed: Stage5 verified. Stage6 fixed native bridge reuse and stale port epoch in 41664b9f972bfd8ae7573b5af183cf833a978350. Fixed Kotlin test fixture errors from prior CI: non-generic Robolectric getApplication, Mockito non-null recording and WebExtension ID, decoded JSONArray comparisons. Full SOURCE/PR Kotlin/Robolectric, JavaScript and AndroidTest compile PASS on code SHA 82604e300de5162984f7280b6f9599f7734c1e04. Canonical error ledger updated with 4 stage6 events and recurrence of historic getApplication error.
remaining: Verify final SOURCE process-docs, JS, Kotlin/Robolectric, AndroidTest compile, lint/baseline, quality-evidence and separate PR CI on last documentation/error-learning SHA. Repair FAIL in stage6. Gecko/physical device/site NOT_TESTED; stage7 only after DALEJ.
required_ci: SOURCE process-docs plus shared-tests JavaScript, Kotlin/Robolectric, AndroidTest compile, lint/baseline, quality-evidence SUCCESS; PR Android CI JS/Kotlin/AndroidTest/lint/error-learning SUCCESS on merge checkout referencing final source SHA.
device: NOT_TESTED — no Nothing Phone (1), affected website, live profile switching or audiofocus runtime.
apk: NOT_BUILT — no private signed APK until separate approval after stage 10.
known_failures: Stage5 and original preview site-level cases still OPEN until real device verification. Stage6 ERR-20261110-BGPLAY-REUSE and ERR-20261110-BGPLAY-PORT-EPOCH FIXED in code and unit tests. Stage6 CI fixtures registered as CI-42783004919-1-129717396079 and CI-42785624952-1-129726068606; previously known static getApplication error recurrence reconciled with historical registry.
verification: Direct GitHub validated code SHA 82604e300de5162984f7280b6f9599f7734c1e04, SOURCE run 42790511898 and PR run 42790519789: Kotlin JVM/Robolectric, JS, AndroidTest compile succeeded. Final lint/quality and new documentation SHA CI pending checkpoint. No local checkout or physical runtime; independent high-risk reviewer not available.
changed_paths:
  - app/src/main/java/com/prirai/android/nira/permissions/NiraBgPlayController.kt
  - app/src/main/java/com/prirai/android/nira/permissions/NiraBgPlayAllowlistBridge.kt
  - app/src/test/java/com/prirai/android/nira/permissions/NiraBgPlayBridgeLifecycleTest.kt
  - docs/ai/PREVIEW_COMPAT_STAGE6.md
  - docs/ai/ERROR_CASES.json
  - docs/AI_HANDOFF.md
verified_before_push:
  - Existing extension reused without reinstall, native handler reattached.
  - Stale port callbacks ignored by generation; installed extension and current host permissions preserved.
  - No changes to per-origin stored data, profiles, Android signing, package, MPV Alpha or APK.
blockers:
  - Final SOURCE and PR CI on this new documentation/error-ledger SHA pending. Real Gecko/device/site NOT_TESTED.
evidence_links:
  - https://github.com/laszko14/nira-browser/pull/134
  - https://github.com/laszko14/nira-browser/actions/runs/42778905706
  - https://github.com/laszko14/nira-browser/actions/runs/42778931733
  - https://github.com/laszko14/nira-browser/pull/135
  - https://github.com/laszko14/nira-browser/actions/runs/42790511898
  - https://github.com/laszko14/nira-browser/actions/runs/42790519789
next_action: Verify stage6 final SOURCE and PR CI on final SHA, repair any FAIL, document exact success in PR #135 then STOP; stage7 after next DALEJ.
```
