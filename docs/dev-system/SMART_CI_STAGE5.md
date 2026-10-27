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
