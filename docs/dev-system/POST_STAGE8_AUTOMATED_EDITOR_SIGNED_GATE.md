# Zavro Development System — automated editing and signed sandbox APK gate
Date: 2026-10-27
Status: **Automated sandbox editing/test and partial cross-run recovery VERIFIED. Stable-signed APK compilation BLOCKED by repository-owner administrative gate.**

## Verified protected baseline
- main: `0823f24eb48e2eefbe38c7616bc1d55e73917b37`
- Clean v1174: `3330a0b3a057008886fde392f788a1941e0dfb84`
- MPV v1167: `d63f414acecae84c3507c3f4bb998e690281b91e`
- Isolated Clean sandbox: `tools/zavro-edit-clean-20261027` at `d9c233bbf886d3fb2d320fe118f1d2768ca46092`. Sandbox `app/build.gradle` only has a bumped `versionCode = 175` yielding arm64 `1175`, `versionName = 0.5.22-alpha80-clean-sandbox`; original Clean 1174 unchanged.
- Isolated MPV sandbox: `tools/zavro-edit-mpv-20261027` at `e6e8f71eb67ee61f8bfe67b45a25ba9f2b4149a0` with exactly one automatically added `app/src/test/java/com/prirai/android/nira/devtools/ZavroAutomatedEditorTest.kt`; MPV source unchanged.

## 1. Owner-gated test APK, actual GitHub result
- `.github/workflows/zavro-sandbox-signed-1175.yml` is a dedicated one-file request handler. It requires an exact approved request, commit, source diff, package, version, ABI, stable certificate and an independent owner-administered repository Actions variable before its signed assembly job can start. A tag, Release or production ref are never created.
- Its artifact upload is conditional on successful `scripts/verify-update-apk.sh`, including a single signer with the known SHA-256, expected package `com.prirai.android.nira.debug`, versionCode 1175, versionName and ARM64-only native libraries.
- [Run 40479200630](https://github.com/laszko14/nira-browser/actions/runs/40479200630): `preflight` SUCCESS, `build` SKIPPED. Workflow conclusion overall SUCCESS **does not mean an APK was created**. In this run there was no compiled or signed APK and no release.
- Before signed compilation, an owner-controlled admin setting must satisfy `vars.ZAVRO_ALLOW_SIGNED_BUILD_DISPATCH == 'ENABLED_BY_REPOSITORY_OWNER'`. The value is not readable by the connected GitHub app and this chat cannot change repository administrative variables. The user authorized test compilation, but this **does not override the independent security gate**.
- Duplicate-artifact preflight was added after the original preflight run; it has not been exercised with an actual signed APK. Atomic idempotency and binary re-verification remain unproven.

## 2. Actual autonomous Code Editor
- `devtools/apply-sandbox-edit.mjs` is a new authenticated GitHub Actions code editor. The tool checks actor, repo, isolated tooling event, precisely one newly added JSON request, variant-specific source pin, existing sandbox HEAD and blob SHA, bounded file allowlist/content, and no tokens/keystores. It constructs one GitHub tree, commit and non-force branch update, then verifies the new sandbox HEAD.
- `.github/workflows/zavro-controlled-code-editor.yml` executes its apply job with `contents:write` limited to the guarded writer code. The subsequent JUnit compilation runs in a **separate** job with `contents:read`; no APK or signing secrets.
- [Run 40479677437](https://github.com/laszko14/nira-browser/actions/runs/40479677437): both `apply` and `verify` SUCCESS. The apply job passed **37/37 Node tests**, created one Kotlin JUnit test on MPV sandbox. The verify job compiled and executed it successfully: Gradle **BUILD SUCCESSFUL in 2m 17s**. MPV production source was not edited.
- Earlier Clean sandbox code change `MediaRecentDeduplicator.kt` was applied using chat-orchestrated direct GitHub CAS and passed four Kotlin regressions + lint in [40477361547](https://github.com/laszko14/nira-browser/actions/runs/40477361547); only the narrow manual edit path was tested on production Kotlin. Automated production Kotlin editing remains restrictively allowlisted and not proven end-to-end on a real production patch.

## 3. Resume after interrupted work
- `devtools/orchestrate.mjs` now refuses `REQUIRES_ADDITIONAL_TESTS` before any remote audit/verify callback for non-JS-only tasks.
- `devtools/partial-resume-check.mjs` and `.github/workflows/zavro-partial-cross-run.yml` download a historical failed Stage 7 Kotlin checkpoint from **another workflow execution** and establish that an incomplete phase does not replay previous operations or skip missing tests.
- [Run 40480182477](https://github.com/laszko14/nira-browser/actions/runs/40480182477): **7/7 Node PASS**; original checkpoint from [40472397516](https://github.com/laszko14/nira-browser/actions/runs/40472397516), artifact 12719177242 was downloaded and validated; stage remained `ISOLATION_PLANNED`, zero operations replayed and zero builds started.
- Both complete and partial cross-run checkpoint reads now have real verified examples. Automatic unattended scheduling/retrying with new evidence and signed attestation remain outstanding.

## 4. Whole-system acceptance
- [Final acceptance 40480738745](https://github.com/laszko14/nira-browser/actions/runs/40480738745): **247/247 Node PASS**, **16/16 Python PASS**, historical Stage 8 provenance checks, read-only protected/sandbox branch checks, successful automatic GitHub edit/JUnit, cross-run partial recovery and owner-gated build SKIPPED all verified. Report artifact [12721949409](https://github.com/laszko14/nira-browser/actions/runs/40480738745/artifacts/12721949409).
- The previous attempt [40480594324](https://github.com/laszko14/nira-browser/actions/runs/40480594324) failed one stale Stage 8 test expectation (checkpoint now at completed stage 8); fixed and rerun above.
- No Android APK was built during this continuation. Earlier `testDebugUnitTest` and `lintDebug` tasks succeeded on sandbox; those do not substitute for APK assembly/signature verification.

## 5. Remaining work / required owner action
**Hard blocker for stable-signed test APK:** GitHub repository-owner Actions variable `ZAVRO_ALLOW_SIGNED_BUILD_DISPATCH` must be explicitly enabled and scoped to the intended build. It gates the powerful Stage 3 Build Bridge too, so the owner should carefully review which jobs it allows and disable it again after intended use. This chat cannot change/read it via connected Github App.

Still to finish: one actual signed ARM64 v1175 sandbox build with original certificate and APK download/verification, device TestLab playback and rendering, broader controlled production Code Editor/CI integration with review approvals, actual full CI performance benchmark, atomic build-dispatch idempotency/recovery, and unattended asynchronous cross-run partial retry.

## Safe resume
1. Read the current `docs/dev-system/CHECKPOINT.json`, this report and live refs. Do **not** repeat code editor run 40479677437, full acceptance 40480738745 or source edit.
2. Do not claim APK success from run 40479200630: its build job was SKIPPED.
3. When the repository owner explicitly enables the gate, attempt only the reviewed isolated v1175 sandbox build, using the fixed SHA; verify certificate and version from actual binary; no Release or merge.
4. Protect Clean, MPV, main and Codex. All current new changes are on tools or sandbox refs.
