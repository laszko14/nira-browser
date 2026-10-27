# Zavro Development System — Stage 7 orchestration

**Date:** 2026-10-27  
**Outcome:** working **read-only diagnostic** orchestration across existing Zavro Build Control configuration, Smart CI, Code Inspector and authenticated GitHub Actions evidence. No Android compilation or APK, no merges, no production code changes. Full implementation/build orchestration remains intentionally gated.

## Components

- `devtools/orchestrate.mjs` imports `chooseTests` and `liveAudit` from Smart CI and `inspectLivePRs` from Code Inspector; relies on strict `devtools/build-control.json` target pins and safety flags.
- `.github/workflows/zavro-stage7-orchestrator.yml`: authenticated push-to-Actions bridge for **new** `devtools/orchestrator-probes/*.json` files on the isolated tools branch. Its token has read-only `contents`, `actions`, `pull-requests`. No keystore, Gradle, mutation API, Android build or workflow_dispatch is used.
- The evidence checker uses GET-only GitHub API calls to verify one real completed fast JavaScript Actions run and its immutable request file at the source run's SHA. The target branch and Android version/signing metadata are reverified through Smart CI audit.
- A GitHub artifact preserves the small state checkpoint and a sanitized summary report. Each file excludes raw logs, code, user media URLs, secrets and APK bytes.

## State machine

```text
CREATED
  │ ANALYZE: changed-file task selection
  ▼
ANALYZED
  │ INSPECT: read-only real Codex PR evidence (#84/#85/#88/#89)
  ▼
INSPECTED
  │ PREPARE: isolated branch proposal ONLY, no code changes
  ▼
ISOLATION_PLANNED
  │ VERIFY_TEST: authenticated fast-JS run and pinned target HEAD
  ▼
TEST_VERIFIED
  │ SKIP_BUILD: user has NOT requested an Android build
  ▼
BUILD_SKIPPED
  │ REPORT: scope-limited, true no-build diagnostic result
  ▼
REPORTED
```

**Important:** This is a dry-run/diagnostic coordinator. `ISOLATION_PLANNED` does not mean implementation was written. `BUILD_SKIPPED` does not mean an APK passed CI, and `REPORTED` does not mean the Android app was compiled or device-tested. A signed APK build still requires a separately authorized Stage-3 Build Bridge invocation and independent Stage-4 artifact verification; **this orchestrator cannot invoke that build**. It is not yet a general code-writing agent.

The request format is restricted to `kind="orchestrator-diagnostic"`, `mode="DIAGNOSTIC_ONLY"`, `build_requested=false`, one of two strictly pinned source branches/commits, and a bounded changed-file task list. A changed-file list is a **scenario input / proposed scope**, not independently certified as a Git commit diff. The inspector's Codex overlap is an overlap signal, not proof of Git textual conflict or lost changes.

A fast JavaScript Actions proof can satisfy ONLY a plan whose entire selected task family is `["js"]`. Kotlin, Lua, Android lint, APK packaging and instrumentation tasks are not silently waived. The system refuses completion with `REQUIRES_ADDITIONAL_TESTS` when JS evidence is insufficient.

## Resume and replay model

`plan_id = SHA-256(JSON.stringify(request))` binds the state to a particular scenario. Each accepted state records revision 0–6, enumerated events and checksum, and gets written using exclusive local lock + temporary file + atomic rename. Replaying a completed step returns the unchanged state; state downgrade, hash mismatch, stale request and out-of-order transitions are rejected.

**A checksum detects accidental changes, not deliberate forgery**: it is not an HMAC, signed attestation, or identity verification. For trusted origin, correlate the checkpoint with the immutable GitHub Actions run/artifact and source commit SHA. GitHub artifacts have limited retention and might be manually deleted.

The live workflow demonstrated three recovery behaviors:
1. A successful fresh Clean diagnostic run stored a checkpoint artifact.
2. A successful MPV diagnostic repeated the command against the already-completed checkpoint with byte-identical state and report, then **downloaded the uploaded artifact into a different directory and replayed from the restored checkpoint** with no duplicated work.
3. An invalid Kotlin scenario halted at the test sufficiency gate, with a partial state artifact retained even when its workflow concluded with expected failure.

The workflow does **not** currently self-reschedule a failed run on a future date. Restoring the checkpoint across separate Actions executions requires an authorized invocation that supplies the original request and artifact; only in-run artifact restore and deterministic local resume were verified in stage 7. Native scheduled/automated background work is out of scope.

## Measured GitHub executions

| Test | GitHub Actions run | Outcome |
|---|---|---|
| Clean v1174 diagnostic, earlier fast JS run 40469224355 | [40471995681](https://github.com/laszko14/nira-browser/actions/runs/40471995681) | **SUCCESS**, 27/27 orchestrator tests; no Android build; checkpoint artifact [12718951320](https://github.com/laszko14/nira-browser/actions/runs/40471995681/artifacts/12718951320) |
| MPV v1167 diagnostic, earlier fast JS run 40469257622, byte-identical replay, download & restore | [40472241780](https://github.com/laszko14/nira-browser/actions/runs/40472241780) | **SUCCESS**, 27/27 tests, in-run replay + independently restored artifact checkpoint passed; artifact [12719096410](https://github.com/laszko14/nira-browser/actions/runs/40472241780/artifacts/12719096410) |
| A proposed Kotlin source modification with only JS test evidence | [40472397516](https://github.com/laszko14/nira-browser/actions/runs/40472397516) | **EXPECTED FAILURE**: `REQUIRES_ADDITIONAL_TESTS`, 27/27 tests passed before gate; partial checkpoint preserved as artifact [12719177242](https://github.com/laszko14/nira-browser/actions/runs/40472397516/artifacts/12719177242); no build |

All three use a separate, explicitly read-only workflow. The actual successful fast-JS proof was **131/131 tests on Clean** and **202/202 tests on MPV** from Stage 5; this is neither a Kotlin/testlab/Android proof nor a new production CI run.

## Operator workflow for ordinary ChatGPT

1. Read `docs/dev-system/CHECKPOINT.json` at live tools HEAD, validate protected Clean/MPV ref SHA.
2. Pick variant and desired diagnostic scope. No side-effects without an explicit change command.
3. For a **diagnostic only** request, create a new unique `devtools/orchestrator-probes/<slug>.json` file via connected GitHub; never edit the same request to replay.
4. Inspect triggered `Zavro Development System Orchestrator Diagnostics` workflow for final status, 27 test results, artifact SHA/size/expiration and logical step evidence.
5. Open/download the saved small state as needed, compare plan id, scope, revision and original source run. Avoid claiming Android build.
6. If changes require Kotlin/Android test families, report the test gap and ask for separate permission before executing costly work.
7. An actual signed build, if explicitly authorized later, must go through the independent gated Build Bridge; only the Stage 4 reporter can verify corresponding actual APK provenance.

## Security, rollback and open risks

- No ref writes or production source patching exist in the Stage 7 orchestration code. It cannot invoke GitHub POST. Required GitHub token scopes are read-only. Only probe files/workflow/tests/docs/checkpoint are modified on the tooling branch through explicit connected GitHub actions.
- Test evidence is pinned to an existing recorded Actions run, its job result, source request and full target SHA; no invented test result. A validated historical JS run does not prove new production code runs correctly.
- File overlap != merge conflict; import-graph != runtime dependency graph; TestLab did not ingest a fresh phone export in Stage 7.
- The stage7 scenario is only fast JS: requested Android build path remains unexercised; Stage 3 dispatch remains unconfirmed and must not be auto-triggered.
- Rate limits, artifact retention, available GH_TOKEN permissions and no cross-run automatic resume remain operational limitations.
- Rollback: stop creating `devtools/orchestrator-probes` requests. The feature is isolated, production workflows/branches remain untouched.

**Next:** Stage 8 final acceptance/security/recovery audit. Only begin after user says **Dalej**.
