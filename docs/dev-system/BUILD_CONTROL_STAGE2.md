# Etap 2 — Zavro Build Control (2026-10-27)

## Status
Implemented: read-only CLI planner in `devtools/build-control.mjs`; strict JSON configuration, variant isolation, full SHA pin, live ref check, workflow text guard, SHA+branch+workflow duplicate detection, unit tests. **Dispatch is not implemented**: the GitHub connector has no workflow_dispatch action and the source contains no POST method. The REST API dispatcher belongs to Stage 3 after a verified authenticated bridge and immutable run refs.

## Usage in an environment with GitHub API network access
```bash
node --test devtools/tests/build-control.test.mjs
node devtools/build-control.mjs plan --variant clean --branch feat/zavro-v1174-clean-video-metadata --sha 3330a0b3a057008886fde392f788a1941e0dfb84
node devtools/build-control.mjs plan --variant mpv --branch feat/zavro-x-mpv-v1167-reference-video-panel --sha d63f414acecae84c3507c3f4bb998e690281b91e
```
When the target branch head changes, `HEAD_MOVED` is correct; update the pinned SHA and metadata after review. The CLI uses public GitHub GET APIs; optionally provide an access token in environment `GH_TOKEN` for higher API limits (never put a token on command line or in repo).

## Controls
- Only `clean` or `mpv`; exact branch name and full SHA; expected debug applicationId, versionCode/name, ARM64 ABI, expected signing certificate SHA256.
- Validate actual workflow file for debug build, verifier, expected values and release guard. Fail closed.
- Query workflow runs for SHA; `REUSE_RUNNING`, `REUSE_SUCCESS`, `REVIEW_PREVIOUS_FAILURE`, `READY_FOR_REVIEW`. This is a read-only preliminary duplicate check, **not an atomic cross-client mutex**.
- Zero GitHub POSTs, zero Android compilations. No changes to production workflow.
- The existing default-branch workflow is a legacy version; dispatching unpinned `ref` on a moving branch risks compiling unintended commits. Stage 3 must verify an immutable tag or other safe ref, authorization, exclusive idempotency, and safe workflow dispatch semantics.
- A passing mock test does not prove the CLI can reach GitHub from the current ChatGPT container.

## Verification
Tests run against the same implementation in the available JavaScript execution environment with mocked GitHub API. The live read-only checks of reference SHA/workflow evidence are separately reported. Source files and checkpoint must be verified after commit. No PR or Actions run to avoid accidental CI.
