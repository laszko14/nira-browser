# Zavro Development System — Stage 4, APK artifacts and reporting

Date: 2026-10-27. **No new Android build executed.**

## Implementation
- `devtools/report-build.mjs`: read-only, scoped GitHub Actions report (`--run ID --variant clean|mpv`), checks immutable commit SHA, expected branch or `workflow_dispatch` technical tag, correct workflow, build/test/lint status and skipped release.
- Artifacts are tied to run ID, repository ID, SHA, branch/tag, required artifact name, nonzero ZIP size, expiry and uniqueness.
- Reports expose `artifact_observed` separately from `artifact` (only usable as **CI-verified** when complete workflow succeeded AND exact stable-signing metadata line was observed in the build-job log).
- Reports also expose `independently_inspected_apk_bytes=false` until an independent full archive check actually occurs. No auto-download of large archives.
- Failure classification uses fixed category codes (SIGNING, APK_METADATA, DEPENDENCY, COMPILER, ANDROID_LINT, JAVASCRIPT_TEST, KOTLIN_TEST, INFRASTRUCTURE, UNKNOWN). Logs are not copied into reports. Unrecognized job step text is redacted to avoid leaking credentials, media URLs or cookies.
- `devtools/verify-apk-artifact.py`: **optional** independent ZIP SHA-256, CRC, traversal, ZIP contents and one-APK check; calls a **trusted** pinned `scripts/verify-update-apk.sh` with Android SDK apksigner/aapt to check package, version, ARM64 ABI and signing certificate. Fails closed when tools/digest/verifier are unavailable.
- These tools make GET requests only (for metadata), and never invoke Android build, merge, release or dispatch.

## Usage — no build
From a trusted environment with Node 22 and read-only GitHub API access:
```bash
node devtools/report-build.mjs --run 40462413839 --variant clean
node --test devtools/tests/build-control.test.mjs devtools/tests/build-request-validator.test.mjs devtools/tests/report-build.test.mjs
python3 -m unittest discover -s devtools/tests -p test_verify_apk_artifact.py -v
```
The first command returns `VERIFICATION_REQUIRED` and includes `artifact_observed` until the build log is independently read. `--with-ci-logs` attempts `gh api` with `GH_TOKEN`, but failed to fetch an older job log using the Stage-4 GitHub Actions token; **do not claim this is a confirmed working download method**. The already-connected GitHub ChatGPT tool `fetch_workflow_job_logs` can read the log: inspect for exact line `Verified package=... versionCode=... versionName=... ABI=arm64-v8a certificateSHA256=...`. Do not copy raw log into public reports.

Optional offline ZIP check (after artifact is downloaded legitimately, with authoritative `sha256:...` ZIP digest from the artifact API):
```bash
python3 devtools/verify-apk-artifact.py --archive /trusted/path/artifact.zip --zip-sha256 sha256:<trusted-64-hex> --verifier /trusted/pinned-repo/scripts/verify-update-apk.sh --package com.prirai.android.nira.debug --version-code 1174 --version-name 0.5.22-alpha79-clean --certificate-sha256 df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00
```
Never invent a digest; if API digest absent, independently obtain archive SHA-256 from a trusted source and record its provenance. Never use the ZIP itself as a source of expected certificate/version. The tool has NOT inspected the full existing 146 MB APK archive in this stage.

## Actual GitHub evidence
- Clean v1174 run **40462413839**: `success`, SHA `3330a0b3a057008886fde392f788a1941e0dfb84`, jobs `build/test/lint=success`, `release=skipped`. Signed ARM64 build job log records exact expected package, code 1174, name `0.5.22-alpha79-clean`, certificate `df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00`.
- Observed, not freshly built: `zavro-arm64-update-apk-v1174`, artifact ID **12714733371**, ZIP size **146105805 bytes**, expiry 2026-11-26. [Existing artifact](https://github.com/laszko14/nira-browser/actions/runs/40462413839/artifacts/12714733371). GitHub artifact ID alone does not prove file bytes were reverified in this stage.
- Earlier CI run **40462194006**: `failure` even though `build=success` and artifact ID **12714263779** exists; `lint/test=failure`. Lint log includes dependency resolution error (HTTP 503 from Mozilla Maven); JS protocol test has an assertion failure. This is an example of why artifact presence is NOT success. No failures were repaired in production branches.
- Probe run **40467652995**: **46/46 JS + 9/9 Python PASS**; historical log download by `gh api` failed, so live log verification was not claimed.
- Probe run **40467847250**: **46/46 JS + 9/9 Python PASS**, and live run metadata read PASS, no build.
- Final probe **40467965528**: **48/48 JS + 9/9 Python PASS**, and live read-only metadata report PASS, no build.
- Stage 3 Build Bridge still has no successful signed `workflow_dispatch` test; its own dispatch must be tested only if user explicitly orders a new build. This stage does not change that gate.

## Security and known limitations
- The CI verified log is evidence of original on-runner APK check, not a fresh cryptographic reinspection of the downloaded archive. Distinguish `CI_VERIFIED_APK_ARTIFACT` from binary independently reverified.
- The first 100 jobs/artifacts only; if API says more, stop and demand a pagination implementation, do not silently omit evidence.
- Reporter's pinned variant SHAs must be refreshed only after comparing current GitHub refs and version configuration; do not merge Clean and MPV.
- Artifacts expire. Link availability must be reconfirmed before recommending an APK.
- Existing CI success is not equivalent to a successful physical device install or functional end-to-end playback.
- Preserve `main`, current Clean, MPV and Codex branches untouched. No PR was created for this work.
