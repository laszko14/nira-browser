# Stage 1 audit — evidence snapshot (2026-10-27 UTC, non-atomic)

## Sources read in authenticated GitHub connector
- Repository: `laszko14/nira-browser`, public, user `laszko14`, permissions: admin/push/pull=true. Default `main` at `0823f24eb48e2eefbe38c7616bc1d55e73917b37`; GitHub branch list reports `main protected=false` (NOT safe to modify).
- Retrieved repo root, `.github/workflows/`, `app/build.gradle`, `settings.gradle`, `gradle.properties`, `AGENTS.md`, PR and Actions metadata; comparison is based on these paths, not a full line-by-line repository audit.
- Main has `.github/workflows/apk-release.yml` containing `workflow_dispatch`, push only on main and tags, and pull_request targeting main. Main workflow build/lint/unit tests + optional release on explicit tag.
- Newer Clean v1174 branch has Android CI with separate build/test/lint/release, ARM64-only signed debug APK, verifier `scripts/verify-update-apk.sh`, Kotlin unit tests, JS WebExtension protocol tests, Android lint. Separate `.github/workflows/testlab-device.yml` supports emulator instrumentation. PR CI trigger targets arbitrary PR base; avoid opening infrastructure PR.
- Clean snapshot: `feat/zavro-v1174-clean-video-metadata` at `3e194d2a20b6f649e55600580f1e62ba7d78439b` when sampled; PR #89. Branch is changing during audit (observed earlier SHAs differed). Do NOT use this as an automatically current HEAD.
- MPV snapshot: `feat/zavro-x-mpv-v1167-reference-video-panel` at `d63f414acecae84c3507c3f4bb998e690281b91e`; PR #84. It uses independent `applicationId=com.prirai.android.nira.mpv`, its own libmpv dependency and distinct version family.
- Clean app debug: `applicationId=com.prirai.android.nira.debug`; Gradle base versionCode 174 -> ARM64 final 1174; versionName `0.5.22-alpha79-clean`. MPV ref base versionCode 167 -> ARM64 final 1167, versionName `0.5.22-alpha73-mpv40`; debug package `com.prirai.android.nira.mpv.debug`.
- Stable public certificate fingerprint expectation: `df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00`. CI references keystore from GitHub Secrets. No secret values read, availability/access of secrets NOT asserted.
- Current Clean Gradle settings include caching, parallel, configuration cache, JDK 17; workflow uses `gradle/actions/setup-gradle@v5`. Actual speed/parallel correctness not benchmarked.
- At audit, v1174 had several overlapping GitHub Actions runs, some failed and some running. No claims about final result of latest SHA.
- Available GitHub tools include creating branch/commit/tree/files, reading PRs, ref comparison, fetching run jobs, job steps, job logs and downloading artifacts; rerun failed jobs is present. NO tool for initiating a fresh workflow_dispatch is exposed.
- Repository structure: `app/`, `mozilla/`, `gradle/`, `.github/`, `docs/`, `settings.gradle` includes `:app`. New tools intentionally reside outside these modules.

## Risks + gates
1. `main` differs significantly from evolving Clean/MPV; base Dev System on immutable main commit for infrastructure only. For any application change, use variant-specific pinned checkout in a separate future task.
2. Dynamic PR changes during audit: compare with live HEAD before applying any code changes; don't overwrite others' branches.
3. Existing main manual-dispatch workflow is legacy and not an approved update-safe signed arm64 build: NEVER invoke it as a convenient shortcut.
4. No user action or Android build requested in this stage. Keep all execution flags false.
5. Connected GitHub write permission does not prove workflow_dispatch or permission to read/alter Secrets.
6. No protected branch enforced by GitHub observed for main; enforcement must be in the independent controller.

## Stage-1 outputs / check later
- One independent branch and one documentation-only/tooling-only commit.
- `devtools/policy.json` pins two variant snapshots and denies execution.
- `devtools/check_policy.py` and offline unit tests verify reject-by-default invariants.
- Confirm branch head and file paths after commit; store SHA in report.
- No PR, no Actions dispatch, no APK and no changes to Android source.

## Verified execution / acceptance
- 2026-10-27: Created isolated branch from pinned main. Published eight standalone docs/tooling files using commit e65e50431e83ffdb9f373283144f6baeab500bb9; corrected strict regex anchors in cb35e708623a5f6900aa7b0c788c84f358ec959a.
- Download was unavailable from the local sandbox network; locally reconstructed files were verified byte-for-byte against their GitHub blob SHA (checker cf77389e90fddb34c63a3e2bcad3ed048d2372b8, tests 90af2b3393c7aa550c78db5e50a6b963b3d6b473, policy 9cbc252c09dfd5ccabf37aeedcaeaabd537786f1).
- Ran actual Python 3 checker successfully; seven offline unittest cases **7/7 PASS** and `py_compile` PASS. No Android code or build executed. GitHub compare vs pinned main shows exactly eight new files, all under `docs/dev-system/` or `devtools/`; branch-specific Actions count **0**.
- This is an implemented, tested *read-only policy baseline*, not a production-enabled controller or native security boundary. Runtime permission enforcement and dispatch remain stages 2–3.
