# Zavro Development System — final acceptance and security audit (Stage 8)
**Date:** 2026-10-27  
**Repository:** `laszko14/nira-browser`  
**Infrastructure branch:** `tools/zavro-development-system-stage1-20261027`  
**Audit outcome:** **ACCEPTED for read-only / diagnostic functions. NOT ACCEPTED as autonomous production code editor or signed APK build and release pipeline.**

## 1. Verified results, direct evidence
- [GitHub Actions acceptance #40473570292](https://github.com/laszko14/nira-browser/actions/runs/40473570292): **SUCCESS**, 184/184 Node tests, 16/16 Python tests; Stage 1 baseline policy validation PASS. Authenticated GitHub GET-only checks independently re-read protected `main`, Clean and MPV refs, **7 historical workflows**, and their expected success/failure conclusions and attached checkpoint artifacts. Final sanitized report artifact: [12719677451](https://github.com/laszko14/nira-browser/actions/runs/40473570292/artifacts/12719677451) (857 bytes). This is an audit JSON, **not an APK**.
- [Stage 8 denied request #40473675057](https://github.com/laszko14/nira-browser/actions/runs/40473675057): **EXPECTED FAILURE** `STAGE8_INVALID_ACCEPTANCE_REQUEST`, because a synthetic acceptance request deliberately used `build_requested=true`. No follow-on tests/build were run.
- Prior Stage 3 unauthorized build request: [40466190188](https://github.com/laszko14/nira-browser/actions/runs/40466190188) preflight FAILURE and conditional `dispatch` SKIPPED (real GitHub job evidence).
- Prior Stage 5 Clean JS: [40469224355](https://github.com/laszko14/nira-browser/actions/runs/40469224355) SUCCESS, 131/131 protocol tests.
- Prior Stage 5 MPV JS: [40469257622](https://github.com/laszko14/nira-browser/actions/runs/40469257622) SUCCESS, 202/202 protocol tests.
- Prior Stage 6 Inspector: [40470818731](https://github.com/laszko14/nira-browser/actions/runs/40470818731) SUCCESS, including actual Codex PR overlap, real Kotlin import graph and synthetic TestLab TXT/JSON analysis.
- Prior Stage 7 Clean orchestration: [40471995681](https://github.com/laszko14/nira-browser/actions/runs/40471995681) SUCCESS; checkpoint artifact [12718951320](https://github.com/laszko14/nira-browser/actions/runs/40471995681/artifacts/12718951320).
- Prior Stage 7 MPV orchestration: [40472241780](https://github.com/laszko14/nira-browser/actions/runs/40472241780) SUCCESS, repeated local replay **and download/restore of the small checkpoint artifact in the same workflow run**; artifact [12719096410](https://github.com/laszko14/nira-browser/actions/runs/40472241780/artifacts/12719096410).
- Prior Stage 7 Kotlin proof insufficiency: [40472397516](https://github.com/laszko14/nira-browser/actions/runs/40472397516) **EXPECTED FAILURE** `REQUIRES_ADDITIONAL_TESTS`; incomplete checkpoint preserved in artifact [12719177242](https://github.com/laszko14/nira-browser/actions/runs/40472397516/artifacts/12719177242).

Above is **200 offline tests**, plus the GitHub Actions read-only provenance and ref checks. The 7 historical outcomes are independent run evidence, not 7 additional unit test cases.

## 2. Eight-stage acceptance matrix

| Stage | Actually works / confirmed | Not yet established |
|---|---|---|
| 1. Isolation/policy foundation | Isolated infrastructure branch, validated static guard configuration, tests and checkpoint | GitHub server-side branch protection rules |
| 2. Build Control | Exact variant, package/version/ABI/certificate, pinned SHA and preflight tests | Executing a successful new signed build; atomic dispatch idempotence |
| 3. Ordinary ChatGPT integration | Authenticated GitHub connector write triggers a dedicated Actions workflow; bad requests rejected | A successful approved `workflow_dispatch` from the Build Bridge and secure per-build owner authorization |
| 4. APK artifacts/reporting | Read-only real historical Actions metadata and signed CI log interpretation; synthetic ZIP/metadata verifier tests | Independent full-archive inspection of a real downloaded APK |
| 5. Smart CI | Real opt-in targeted JS runs: Clean 131/131 and MPV 202/202; readonly Kotlin/Gradle source audit | Reduced wall time for production full Android CI; actual production selective test routing |
| 6. Code Inspector | Real read-only #84/#85/#88/#89 PR overlap, exact import edges, safe synthetic TestLab TXT/JSON aggregation | Proof of textual merge conflicts, analysis of arbitrary PRs, a fresh real device TestLab export |
| 7. Orchestration | Strict diagnostics-only sequence, stage gates, checkpoint persistence/replay/restore in the same run and negative Kotlin test-sufficiency gate | Actual editing of browser sources, full-build/test/release orchestration, automatic recovery across separate Actions executions |
| 8. Final audit | 184 Node + 16 Python PASS; live seven-run provenance checks, positive and denial scenarios PASS | The limitations stated above remain open and must not be represented as passed |

## 3. Protected refs and scope verification
Immutable snapshots confirmed in final live audit:
- `main`: `0823f24eb48e2eefbe38c7616bc1d55e73917b37`
- Clean `feat/zavro-v1174-clean-video-metadata`: `3330a0b3a057008886fde392f788a1941e0dfb84`
- MPV `feat/zavro-x-mpv-v1167-reference-video-panel`: `d63f414acecae84c3507c3f4bb998e690281b91e`

All Stage 8 GitHub writes were limited to `devtools/`, `docs/dev-system/`, and dedicated `.github/workflows/zavro-*.yml` infrastructure paths on the isolated tooling branch. **No Android source or production Android CI workflow was modified**, no signed APK compiled, no PR merge or force-push, no signing secret exported.

## 4. Security finding and mitigation
**HIGH / BLOCKING for claiming production-build readiness:** Stage 3's existing Build Bridge has a conditional `dispatch` job with `contents: write`, `actions: write`, creates an immutable-intent technical tag and calls the GitHub Actions REST API. Although its invalid-request preflight was tested, there was **no successful end-to-end approved build**, no live POST dispatch confirmation and no proof of truly atomic cross-client idempotency. GitHub `actor` checks and a JSON `approval` field are **not strong per-build human authorization**.

**Hardening applied in Stage 8:** `.github/workflows/zavro-chat-build.yml` now adds a repository-owner-administered gating condition:
```yaml
if: needs.preflight.result == 'success' && needs.preflight.outputs.status == 'ready' && vars.ZAVRO_ALLOW_SIGNED_BUILD_DISPATCH == 'ENABLED_BY_REPOSITORY_OWNER'
```
Without that explicit repository Actions variable having this value, the powerful `dispatch` job cannot start. **The current value of repository variables could not be read via the available connector; therefore this audit does not assert that the variable is absent.** Crucially, Stage 8 did not create any valid build-request file at all.

**Additional production hardening needed:** a protected GitHub Environment with required human reviewers / GitHub App least-privilege permission, workflow action commit pinning, enforced immutable tag policy, atomic idempotency/lock against parallel dispatch, tested build and rollback after partial tag creation, CI release-condition verification on actual dispatched ref, and ideally tested secrets-scoping and signed provenance. Admin feature gating is a useful default but not a substitute for those controls.

## 5. Recovery and test accuracy limitations
- Checkpoint checksum is unkeyed SHA-256. It detects casual or accidental edits, **not a malicious actor capable of rewriting state and its checksum**.
- Checkpoint saved on a single Actions runner can resume locally; stage 7 also uploaded and downloaded that checkpoint to a fresh directory **within one Actions run**. There is **no verified automatic recovery between different GitHub Actions runs**, nor scheduled retry.
- Successful fast JS-only checks do not establish Kotlin, Gecko compositor, MPV video frame timing or signed APK behavior.
- `COMPLETED` TestLab report means a measurement ended; not app correctness. Inspector reports possible jank regressions and file overlap as candidates for review, not proven causes or merge conflicts.
- Historical Clean CI `40462413839` produced a signed ARM64 APK and original in-run verifier evidence; Stage 4 did **not independently re-download / cryptographically re-check its full 146 MB archive**.
- An active authenticated GitHub connector was available in this chat, but availability of the same connector and permissions in every future ordinary ChatGPT conversation is not guaranteed.
- Stage 8 acceptance workflow uses GET-only GitHub API for runtime verification, and its token grants read-only `contents/actions`; it never has permissions to dispatch or upload new APKs.
- GitHub artifact retention can expire; links must be revalidated when later used.

## 6. Safe operation guide
1. Diagnose: ordinary chat uses connected GitHub to inspect protected ref SHA, prior runs, TestLab exports and Code Inspector.
2. Fast JS: create one audited `devtools/smart-ci-probes/*.json` on tooling branch; only JS tests will run against pinned source SHA.
3. Full diagnostic pipeline: create one audited `devtools/orchestrator-probes/*.json`, only where Smart CI selects `js` exclusively. Successful output is a **diagnostic-only report and checkpoint**.
4. Kotlin / Android tasks: inspector stops when JS proof is insufficient. Explicit owner instruction and separately verified safe CI required before actual APK.
5. A signed APK request should *not* be assumed ready just because these eight project stages are complete. Use a separate approval and production readiness exercise first.

## 7. Final disposition
- **Accepted:** diagnostic control plane for ordinary ChatGPT, Safe Smart CI JS, Code Inspector limited to allowlisted PRs, historic Actions reporting, bounded local checkpoint recovery, no-build orchestration and comprehensive denial tests.
- **Conditional/unverified:** experimental gated signed build bridge, offline independent APK binary verifier against a real APK, unattended cross-run restore, general-purpose source implementation.
- **Not delivered:** an autonomous fully production-ready software engineer that safely edits/merges Clean or MPV and dispatches, signs, installs, device-tests and releases an APK end-to-end.

**Next action:** Treat further development as a separate project milestone. Do not run production Android CI or enable the repository owner dispatch variable without explicit, specific authorization.
