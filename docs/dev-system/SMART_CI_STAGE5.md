# Zavro Smart CI — stage 5 implementation and safety boundaries

## Source audit (2026-10-27, verified before editing)
- Existing Clean v1174 and experimental MPV v1167 workflows already run **build**, **lint**, and **test** as independent jobs; Gradle setup-gradle@v5 already configured.
- Gradle properties already enable build caching, parallel and configuration cache; blindly introducing another cache or merging build jobs may make performance worse.
- Successful Clean CI historical runs: **40462211883**, **40462391836**, **40462413839**, durations 332s, 316s, 312s. Exact job times from GitHub REST read:
  - run 40462211883 build 161s, test 181s, lint 329s
  - run 40462391836 build 179s, test 211s, lint 311s
  - run 40462413839 build 202s, test 185s, lint 307s
  - median total 316s, median build 179s, median test 185s, median lint 311s.
- Lint is the longest job in all three runs. This does NOT establish that a new change has improved performance.
- Earlier run 40462194006 failed: dependency resolution encountered Mozilla Maven HTTP 503 and JS assertion failed. No changes made to those production branches.

## Implemented (non-production tooling)
- `devtools/smart-ci.mjs`: classify changed paths and choose task families JavaScript, Lua, Kotlin unit, Android lint, Android assemble, Android instrumentation, APK metadata.
- Unknown paths, Gradle settings, Android manifests, workflow changes, or renamed files affecting critical code escalate to full matrix rather than suppress checks.
- Android test/build/instrumentation tasks are **plans only**. No automatic Gradle runs, no signing keys read, no workflow_dispatch or merges.
- Independent real-ref/version/ABI/signing metadata audit checks target pinned GitHub SHA, `app/build.gradle`, `gradle.properties` and `.github/workflows/apk-release.yml`.
- Historical timing helper calculates median and identifies longest existing job, not an invented speedup.
- Any future production Android CI optimization must be introduced and benchmarked only after explicit user approval on isolated variant-specific branch; do not modify Codex branches.

## Run safely
```bash
node --test devtools/tests/*.test.mjs
node devtools/smart-ci.mjs audit clean
node devtools/smart-ci.mjs audit mpv
```
Audit commands send read-only GETs, use optional `GH_TOKEN`, check moving branch HEAD and refuse mismatches.

## Selector defaults
- Documentation only: no Android build.
- WebExtension JS unit test: JS only.
- Production JS in WebExtension: JS, Kotlin integration and Android packaging plan.
- Production Kotlin: Kotlin tests + lint + packaging plan.
- MPV Lua: Lua lint + Android packaging plan.
- Build system, unrecognized or risky changes: full matrix.
- Real Android jobs are deferred until separately authorized; selective planning is **not** yet wired into the existing Clean/MPV GitHub workflow.

## Rollback
Do not send Smart CI probe requests, and stop using the isolated tooling branch. No Android production workflow is modified.

## Stage 5 implemented and verified fast executor (addendum 2026-10-27)

A separate, opt-in, read-only workflow `.github/workflows/zavro-smart-ci-js.yml` now listens only to new `devtools/smart-ci-probes/*.json` diagnostic files, validates a single newly-added request, variant and **full pinned SHA** against `devtools/build-control.json`, reconfirms target branch HEAD from GitHub and checks out exactly that commit. It executes only `node --test app/src/test/js/*.test.cjs` in the pinned source checkout, **without Gradle or any APK creation**. Its GitHub Actions permission is `contents:read`; no signing secrets or reusable PAT were introduced. Path `devtools/build-requests/` that can invoke Stage 3's build bridge remains separate.

### Verified GitHub executions
- [Stage 5 controller/safe-plan probe 40468846773](https://github.com/laszko14/nira-browser/actions/runs/40468846773): **31/31 Smart CI Node PASS**, 48/48 existing Node PASS, 9/9 Python PASS; both targets metadata audit PASS; real historical diff classified as Kotlin unit test only.
- [Updated control probe 40469212651](https://github.com/laszko14/nira-browser/actions/runs/40469212651): **43/43 Smart CI Node PASS**, 48/48 existing Node PASS, 9/9 Python PASS; real diff + both target metadata audits PASS. End-to-end 16s from run start to updated time, job duration 8s.
- [Clean fast JavaScript 40469224355](https://github.com/laszko14/nira-browser/actions/runs/40469224355): **131/131 JS PASS**, end-to-end 12s, job duration 9s.
- [Zavro x MPV fast JavaScript 40469257622](https://github.com/laszko14/nira-browser/actions/runs/40469257622): **202/202 JS PASS**, end-to-end 13s, job duration 9s.

The historic full Android CI median was 316s across three runs. These new targeted JavaScript checks are not equivalent to a full Android build, and therefore **do not establish an overall 316→12 second improvement**. They establish the cost of a narrowly scoped diagnostic that can avoid an *unnecessary* Android compile when only JS verification is required.

### Exact scope of optimization
- Implemented and tested: selective risk-aware planning, fast isolated JS check with pinned source, cache/version/workflow auditing, historical CI timing reports, unnecessary Gradle avoidance for diagnostic-only requests.
- Audited and deliberately unchanged: existing Gradle cache, parallelism, configuration cache, Android CI jobs. No claims of faster Gradle or Lint.
- Planned for later separately authorized variant-specific work: instrumentation scheduling, measured Gradle Lint/dependency optimization, safe selective test routing into actual Clean/MPV workflows, APK version/signing checks in production. A potential network HTTP 503 from Mozilla Maven is an infrastructure failure and not proof of an application defect.

### Safe request example — diagnostic JS ONLY
Create a **new** file `devtools/smart-ci-probes/<unique-safe-id>.json` on the isolated tooling branch, with the following fields, after verifying live SHA and variant:
```json
{
 "schema_version":1, "kind":"smart-ci-js", "intent":"diagnostic-only",
 "repo":"laszko14/nira-browser", "build_requested":false,
 "variant":"clean", "branch":"feat/zavro-v1174-clean-video-metadata",
 "sha":"3330a0b3a057008886fde392f788a1941e0dfb84"
}
```
This request is NOT an Android build request. It will start a short GitHub Actions JS job; use only when requested or justified. SHA snapshots may become stale.
