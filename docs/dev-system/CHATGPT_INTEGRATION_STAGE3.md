# Stage 3 — Ordinary ChatGPT ↔ GitHub Actions integration (2026-10-27)

## Architecture and real evidence

No plugin, PAT, external broker or Codex required for the now-tested **trigger route**.

1. ChatGPT uses the already-connected GitHub connector's authenticated Contents API to CREATE a new file, on the isolated `tools/zavro-development-system-stage1-20261027` branch only.
2. `.github/workflows/zavro-chat-probe.yml` listens for `devtools/control-probes/*.json` pushes. It verifies exactly one added file, correct request and branch, runs lightweight tests; it does NOT compile Android or access keystores.
3. `.github/workflows/zavro-chat-build.yml` listens *only* for `devtools/build-requests/*.json` pushes; an allowlisted, explicit build request is validated in `devtools/build-request-validator.mjs`. It has two jobs: read-only preflight and conditional dispatch with `contents:write`, `actions:write` GitHub-issued GITHUB_TOKEN. No new long-lived credentials.
4. For a valid, explicitly approved request, the conditional job is designed to check live target branch SHA, create a non-release `zavro-build-<variant>-<40-char-sha>` tag (no force or overwrite) and dispatch the existing `apk-release.yml` workflow on this immutable ref, leaving release input empty. The target workflow already handles stable signing and ARM64 verification.
5. ChatGPT checks Actions runs, jobs, steps and artifacts through the connected GitHub tools. Never report build completion until observing the run and exact artifact metadata.

**The push-to-Actions integration IS confirmed in ordinary ChatGPT. The signed build-dispatch stage is IMPLEMENTED but NOT YET EXECUTED or CONFIRMED against a real APK.**

### Evidence (actual GitHub Actions)
- Probe 1: [40465189634](https://github.com/laszko14/nira-browser/actions/runs/40465189634), `success`; GitHub actor `laszko14`; no build.
- Probe 2: [40466030198](https://github.com/laszko14/nira-browser/actions/runs/40466030198), `failure`; exposed six pre-existing incorrect asynchronous test assertions (20/26 passed), *not* a rejected connector trigger.
- Fixed tests in `devtools/tests/build-control.test.mjs`, without changing production code.
- Probe 3: [40466137395](https://github.com/laszko14/nira-browser/actions/runs/40466137395), `success`; **26/26 Node tests PASS** on GitHub runner, including 11 new build-request security tests.
- Negative build bridge test: [40466190188](https://github.com/laszko14/nira-browser/actions/runs/40466190188), intentionally invalid request; `preflight` failure and `dispatch` skipped; **zero build and zero tags created by this test**.
- Original `main` and both pinned Clean/MPV refs remained unchanged after these requests.
- No APK compile, no artifact, no new Pull Request, no release, no modifications to Codex branches.

## Valid build request contract (DO NOT SUBMIT without explicit human request)
Path must be NEW and deterministic:
`devtools/build-requests/<variant>-<full-sha>.json`

JSON must contain **exactly** these fields:
```json
{
  "schema_version": 1,
  "kind": "build",
  "repo": "laszko14/nira-browser",
  "approval": "APPROVE_ONE_SIGNED_ARM64_BUILD",
  "variant": "clean",
  "branch": "feat/zavro-v1174-clean-video-metadata",
  "sha": "3330a0b3a057008886fde392f788a1941e0dfb84",
  "version_code": 1174,
  "version_name": "0.5.22-alpha79-clean"
}
```
The shown SHA/version is a snapshot, NOT a recommendation to build it later: check branch HEAD and config anew. MPV uses `com.prirai.android.nira.mpv` without `.debug` and separate metadata.

**Never create the build request file merely to test connectivity.** Creating it WILL trigger a job that may initiate a paid Android build and create a technical tag after validation.

## Fail-closed gates
- Repository, `github.actor=laszko14`, exact isolated branch, single newly-added request file in triggering commit; edits/replays rejected.
- Exact variant, SHA, branch, 1174/1167 pinned versions and application ID, explicit approval field; extra JSON fields rejected.
- Live GitHub HEAD must still equal immutable SHA; target's `apk-release.yml` must contain signed arm64 debug build and verifier and guarded release condition.
- Pre-existing manual workflow_dispatch run on that target SHA blocks new dispatch pending review.
- Unique path permits only one create through Contents API; unique Git tag refuses an existing tag. No force push, no tag update, no implicit release.
- Job-level permissions: read-only validation; isolated `contents:write/actions:write` on actual dispatch job only.
- Tokens remain provided ephemerally by GitHub Actions, never committed/logged. No signing secret in bridge; it exists in the original signed build workflow.

## Limits and safety follow-ups
- The explicit approval field and `github.actor` are policy checks, not cryptographic proof of the human approving each run. A compromised GitHub identity/write access remains a risk. The tooling branch is not branch-protected.
- No successful signed Android `workflow_dispatch` has been executed via this bridge; `GITHUB_TOKEN` permissions, creation of an immutable tag and actual tag-targeted dispatch must be validated on first **user-authorized** build.
- If the job creates a tag but the dispatch fails, **do not rerun blindly**: recover by inspecting tag, run and SHA; a reused tag is deliberately blocked.
- GitHub concurrency can replace pending runs if many requests are queued. Inventory request files vs runs after bursts.
- Static pins must be reviewed and updated as Clean advances; `HEAD_MOVED` prevents a stale request.
- No secrets or cookie/URL media data are stored in requests and summaries.
- Actions/pinned action dependency security and strict contents API permissions may be hardened in later CI stages.
- No need to create a private Plugin Creator plugin for this integration: the already-installed GitHub connector was sufficient. External plugin/broker remains an optional future extension.

## Ordinary ChatGPT commands
- `Sprawdź aktualny stan Zavro Build Control i ostatnie przebiegi. Niczego nie kompiluj.`
- `Sprawdź Clean i MPV, przygotuj bezpieczny plan builda bez uruchamiania.`
- `Uruchom jeden podpisany build ARM64 Clean z potwierdzonego SHA ...; nie ruszaj innych gałęzi.` — only with explicit intent, current verified pin/config, and fully validated request.
- `Pokaż status i błędy builda z workflow ...`
- `Dalej` — begin Stage 4 after re-reading this checkpoint.

## Rollback
Stop issuing requests and disable/remove the two dedicated `zavro-chat-*.yml` workflows in the isolated tooling branch only (with ordinary audited commits; no force-push). Existing Android workflows remain untouched. Existing completed runs and tagged provenance remain audit evidence.
