# Zavro Development System — Stage 6, Code Inspector
Date: 2026-10-27. Status: read-only implementation tested on real GitHub; no Android build, no changes to Android code.

## Real source and branch audit
- Checkpoint at stage 5 before stage 6: branch `tools/zavro-development-system-stage1-20261027` SHA `75aa4c87aaafdeccf036e3cc5e94cb4f55a1e1ca`. Baseline main `0823f24eb48e2eefbe38c7616bc1d55e73917b37`.
- Clean pinned `feat/zavro-v1174-clean-video-metadata` at `3330a0b3a057008886fde392f788a1941e0dfb84` (PR #89).
- MPV pinned `feat/zavro-x-mpv-v1167-reference-video-panel` at `d63f414acecae84c3507c3f4bb998e690281b91e` (PR #84).
- Current open draft PRs include #84 (MPV), #85 (Clean), #88 (Clean), #89 (Clean). No merging, rebasing or branch writes performed.

## Implemented Inspector
`devtools/code-inspector.mjs` is bounded read-only tooling:
1. **Codex PR overlap**: authenticated GET-only reads of 4 real PR metadata and complete file lists. Validates repository, full SHA, expected open/draft state, and <=99 files per PR to fail on likely pagination. Compares changed paths, including both sides of renames. Overlap means *candidate collision when porting changes*, **not proven textual Git merge conflict or lost changes**. Sequential PR branch lineage is flagged for interpretation.
2. **Protected branch mutation gate**: its mutation helper always refuses writes; `main` and every `feat/*`/`fix/*` branch is explicitly denied, as are stale SHAs and production files on a tooling branch. These are code-level guards, not a claim that GitHub itself has configured branch protection/rulesets.
3. **Explicit import dependency graph**: for caller-provided Kotlin/Java source maps, resolves only explicit `import com.prirai.android.nira...` edges between included modules, then traces reverse impacted imports. No inferred runtime call graph, reflection, same-package auto dependencies or ABI proofs. At most 45 files per graph. Externally unresolved imports are explicitly surfaced.
4. **TestLab TXT/JSON**: parses supported TestLab status, version, duration, observed anomalies, Android Window frame/jank, VIDEO_DISCOVERY anonymous title/group conflict counters, VIDEO_METADATA anonymous duration/size completeness counters, and compares *like-for-like* test modes with conservative frame thresholds. Never claims jank = Gecko FPS/MPV FPS, correlation = causation, or PARTIAL = full coverage.
5. **Privacy**: strips event timeline and media examples from JSON output; rejects textual reports with URL/auth/token patterns; strips raw report contents from output and does not log original TestLab data. No copying cookies, page URLs, authentication headers, media names or profile IDs into GitHub Actions.

## Measured real repo overlap
- **PR #84 vs PR #85**: **12 shared changed paths** among 39/21 changed paths. These include `.github/workflows/apk-release.yml`, `app/build.gradle`, `MediaFloatingUi.kt`, `VideoTilesEditor.kt`, `VideoTilesPanel.kt`, `VideoTilesPreferences.kt`, `VideoTilesSettings.kt` and related test files. This signals review needed if code crosses MPV vs Clean.
- **PR #88 vs PR #89**: **12 shared changed paths** among 13/28 changed paths; #89 targets #88's head branch, so much overlap reflects **sequential PR ancestry** rather than an independent merge collision. Examples: `content.js`, `TestLabRuntime.kt`, `app/build.gradle` and unit tests.
- Do not claim there are 12 actual merge conflicts. The tool has not performed a merge/rebase simulation.

## Real source dependency verification
Source `BrowserFragment.kt` explicitly imports `com.prirai.android.nira.media.MediaFloatingUi`. Stage-6 GitHub Actions fetched both files at the immutable Clean v1174 SHA. Inspector resolved the import and marked `BrowserFragment.kt` as potentially affected by a change to `MediaFloatingUi.kt`, **without recompilation**.

## TestLab compatibility and regression caution
Grounded in the real `TestLabContract.kt` and `TestLabReportGenerator.kt`: `COMPLETED` means an ended measurement only, not a stable app; `PARTIAL` is incomplete, `SUSPECTED_CORRELATION` is not a demonstrated cause, and JankStats covers Android Window frames, not Gecko compositor/video FPS.
- JSON `videoMetadata` exports are projected down to six anonymous counters: number of sources, known/unknown duration, exact/estimated/unknown size. Film examples/titles/URLs are not exposed.
- JSON `videoDiscovery` exports are projected down to anonymous source/group/uncertainty/conflict counts.
- TXT `VIDEO_METADATA` and `VIDEO_DISCOVERY` anonymous aggregate counters are also parsed.
- Compare reports only for same test type, completed/truncated-state constraints and >=250 Window samples on both sides. A >=3 percentage-point jank difference is a **candidate for review** only; matching device, pages, workload and replay is needed to assert a regression.
- Real, user-exported TestLab reports were NOT supplied in stage 6; end-to-end format handling was tested with synthetic sanitized fixtures. No device diagnosis or regression determination is claimed.

## Actual workflow verification
- Initial Stage 6 probe `40470256239` failed 4 synthetic-fixture newline tests, 27/31 Inspector tests passed; fixed only the fixture.
- `40470365864` passed all 31/31 Inspector tests and authenticated live PR overlap.
- `40470574701` passed, including two real Kotlin imports and standalone synthetic TXT CLI.
- Final `40470818731`: 33/33 Inspector Node PASS, 43/43 prior Smart CI Node PASS, 48/48 previous Node PASS, 9/9 Python PASS; live PR audit, real Kotlin graph, TestLab TXT CLI PASS. All steps and job `success`, no new APK or Android CI run.

## Usage (normal chat; no Codex required)
From trusted network-enabled Node 22 environment on the isolated tooling branch:
```bash
node devtools/code-inspector.mjs audit-prs
node devtools/code-inspector.mjs testlab /trusted/local/testlab-complex.txt
node devtools/code-inspector.mjs testlab /trusted/local/testlab-video_metadata.json
node devtools/code-inspector.mjs compare-testlab /trusted/local/old.txt /trusted/local/new.txt
node --test devtools/tests/code-inspector.test.mjs
```
CLI reads local files but emits **only sanitized derived metadata**. For account-connected GitHub reads, optional `GH_TOKEN` via environment is supported (never commit a token). The live PR mode supports only allowlisted PR IDs #84, #85, #88, #89, deliberately not an arbitrary unaudited target.

## Rollback / limitations
- Independent inspector never writes to production files or production branches; disabling its probe job or ceasing use has no product impact. This does not replace GitHub administrator rulesets.
- Only 4 allowlisted PRs audited; other Codex branches/PRs require a later explicit extension.
- File overlap != line conflict; import graph != full call graph; TestLab measurements != proof of root cause.
- No real TestLab device export analyzed in this stage, no APK compiled, no PR merged, no active Codex branch touched.
