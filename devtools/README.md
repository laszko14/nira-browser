# Zavro Development System (infrastructure only)

Stage 1 is an **audit and read-only policy gate**, not a build controller. This folder never stores credentials and does not modify Android app source.

Validate offline with Python 3:

```sh
python3 devtools/check_policy.py
python3 -m unittest discover -s devtools/tests -v
```

The validator refuses enabling dispatch, merge, release, force push or mutations while stage_state is `audit_only`. This is **not** an active GitHub policy enforcement bot yet; it validates configuration and provides a tested baseline. Never use the older `main` APK workflow to install over Clean or MPV.

Next stage begins ONLY after the human message `Dalej`. Check `docs/dev-system/CHECKPOINT.json`, refresh GitHub branch refs and confirm every test and commit. Do not silently reuse the stale target SHAs.

Rollback: stop using the isolated `tools/zavro-development-system-stage1-20261027` branch. Do not delete or modify other branches.


## Stage 2 Build Control
See `docs/dev-system/BUILD_CONTROL_STAGE2.md`. The new controller is read-only, validates both variants and blocks dispatch unconditionally. Execute Node 22 tests with `node --test devtools/tests/build-control.test.mjs`.

## Stage 3 — Ordinary ChatGPT integration
Connector-triggered GitHub Actions without a new plugin is implemented. See `docs/dev-system/CHATGPT_INTEGRATION_STAGE3.md`. Probe GitHub Actions **40466137395** passed 26/26 Node tests; intentionally invalid build request **40466190188** failed preflight and skipped dispatch. Creating a `devtools/build-requests/<variant>-<sha>.json` file can start a signed ARM64 build: do this ONLY when the human explicitly orders compilation. The build dispatch itself has not yet been exercised.

## Stage 4 — APK artifacts and reporting
Read `docs/dev-system/ARTIFACT_REPORTING_STAGE4.md`. A read-only `devtools/report-build.mjs` reports Actions jobs and observed APKs; only an exact signed CI log upgrades confidence. `devtools/verify-apk-artifact.py` optionally checks downloaded ZIP and APK with trusted Android SDK tools. Final stage 4 GitHub tests: 48/48 Node plus 9/9 Python, live metadata PASS, no new build. The full 146 MB artifact was not downloaded/reverified in this stage.

## Stage 5 — Zavro Smart CI
See `docs/dev-system/SMART_CI_STAGE5.md`. The isolated fast JS workflow now ran on actual pinned Clean and MPV commits with 131/131 and 202/202 PASS. Full Android CI workflows, cache and production branches were not modified. The source classifier and version/signing cache audit are read-only; Android jobs remain opt-in requiring separate explicit approval.

## Stage 6 — Code Inspector
See `docs/dev-system/CODE_INSPECTOR_STAGE6.md`. Read-only `devtools/code-inspector.mjs` detects potential Codex PR overlap, protects refs, maps explicit Kotlin/Java imports and extracts anonymous TestLab TXT/JSON diagnostic counters. Real GitHub Actions #40470818731: 33/33 Inspector tests plus 43/43 Smart CI, 48/48 prior Node and 9/9 Python PASS. No merge, no Kotlin compilation, no APK. A true TestLab user export was not supplied or processed.

## Stage 7 — Orchestration and checkpoints
See `docs/dev-system/ORCHESTRATOR_STAGE7.md`. Connected GitHub read-only coordinator uses Smart CI task selection, real Codex PR inspection, pinned target and historical fast-JS Actions proof, an isolated implementation *plan* (not a code modification), strict no-build gate, and sanitized report. Runs: Clean [40471995681] success 27/27; MPV [40472241780] success 27/27 incl. restored artifact replay; Kotlin denial [40472397516] expected failure with partial checkpoint. No Android build and no production changes. Stage 8 requires explicit **Dalej**.

## Stage 8 — Final acceptance status
See `docs/dev-system/FINAL_ACCEPTANCE_STAGE8.md`. Live read-only acceptance run #40473570292: 184/184 Node and 16/16 Python PASS, all 7 historical runs checked against GitHub, protected refs unchanged. Explicit synthetic build request denied in #40473675057. Stage 3 Build Bridge powerful dispatch job now additionally requires a specific repository-owner Actions variable; current variable value could not be verified. **Diagnostic toolchain accepted; autonomous production APK build/edit/release system NOT yet accepted.**

## Post-Stage 8 continuation — Build Control + Code Editor
See `docs/dev-system/POST_STAGE8_BUILD_EDITOR.md`. Authenticated build-readiness reviews: Clean SUCCESS run 40475008770, MPV SUCCESS run 40475634929 (prior failure requires review). A separate Clean sandbox gained one Kotlin JUnit test commit c1021a6; runner 40475558294 passed 18/18 editor guards and Gradle testDebugUnitTest. No signed APK was assembled. General production-code editing and signed build dispatch are still unverified.

## Continued implementation — guarded production Kotlin sandbox & cross-run recovery
See `docs/dev-system/POST_STAGE8_EDITOR_RECOVERY_V2.md`. An isolated Clean sandbox production Kotlin TTL fix + 4 tests passed JUnit and Android Lint in run 40477361547 (25/25 guard tests). A different Actions run restored and verified MPV diagnostic checkpoint without replay in run 40478070913 (8/8). Both production Clean/MPV branches remain unchanged; no APK built.
