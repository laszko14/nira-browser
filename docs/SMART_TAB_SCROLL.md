# Smart active tab scrolling — v1188

Base: Clean v1187 `dea9f3bdaad785a4cb312d42d0096cc23cd11022`, PR #109.
Scope: native portrait and Compose landscape strips only; preserve order, groups,
private profiles, favicon settings, no X badges and 120 Hz policy.

Plan (executed autonomously per user request):
- [x] Test shared measured-bounds policy: fitting content, complete groups, comfortable
  icons, clipped/offscreen icons, collapsed headers and variable icon dimensions.
- [x] Integrate native layout-time check with latest selection, cancelable animation
  and manual-touch cancellation; reset single tab to zero.
- [x] Replace competing Compose effects/requesters with one cancelable measured check;
  materialize absent LazyRow item, then measure the actual nested icon. No scroll keys
  based on favicon/title or manual scroll; skip during drag.
- [x] Run regression suite, build/lint, review diff, bump effective ARM64 code to 1188,
  commit/push Draft PR against v1187; inspect CI and signed artifact.

Device behavior (both orientations, gestures, 120 Hz and installation) requires a
phone and remains NOT_TESTED until performed. No merge to main.

## Acceptance evidence

`PASS` below means automated logic/integration evidence, not an on-phone visual claim.
Every physical-device case in both orientations remains `NOT_TESTED` (no adb device).

| Criterion | Automated/review status |
|---|---|
| One tab left; close down to one | PASS — policy + native reset test |
| All content fits | PASS — policy + native test |
| Visible six-tab group, every selected position | PASS — native + policy |
| Visible group with other groups offscreen | PASS — native + policy |
| Comfortable tab; minor edge differences | PASS — policy |
| Offscreen tab | PASS — policy + native |
| Partially clipped tab | PASS — policy |
| Specific icon inside oversized group | PASS — measured policy; adapters reviewed |
| Collapsed group header without expansion | PASS — native + policy |
| Rapid switching, last selection wins | PASS — native race test; Compose cancellation reviewed |
| Manual scroll, no metadata-triggered return | PASS — native metadata/touch tests; Compose fling guard reviewed |
| Orientation/viewport change | PASS — native resize test; Compose width key reviewed |
| Icon sizes/gaps | PASS — variable measured width policy + existing preference regressions |
| Drag-and-drop/order | PASS — existing order/drop tests; new logic does not mutate order |
| Restore/profile/private filtering | PASS — existing profile/order suite; attach/structural hooks reviewed |
| No X badges; toolbar customization | PASS — existing regression suite |
| 120 Hz policy/per-frame cost | PASS — policy code unchanged; event-only calculations reviewed; device performance NOT_TESTED |

Two review findings addressed: observe native touch before child dispatch, and protect
Compose fling after finger release while joining a cancelled preceding auto-check.
Offscreen lazy items are first materialized using LazyListState, then actual nested
coordinates decide whether a further centering motion is needed. Native and Compose
containers clamp to real content bounds; no added end padding.

## Final verification — 2026-10-28

Source/test commit: `35d5ab3aab5f965398793987fc3a0314929ba118`.
CI: https://github.com/laszko14/nira-browser/actions/runs/40752492399 — SUCCESS
(build, test, lint; release skipped). 371 Kotlin/Robolectric, 0 failures/skips;
212 JS, 0 failures. Lint retains pre-existing `abortOnError=false`.
Artifact: https://github.com/laszko14/nira-browser/actions/runs/40752492399/artifacts/12845111607
— single arm64-v8a APK, independently checked after download using the existing
verification script, aapt and apksigner. Package com.prirai.android.nira.debug;
versionCode 1188, versionName 0.5.22-alpha93-clean; expected certificate SHA256
`df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00` verified.
APK SHA256: `17636c4d36e88cbd21ab8f72cd16df7e1483f3b99af8e44f40af5360b0b95231`.
Git/package/version/ABI/signing/no-main-merge: PASS. Full device acceptance remains
open: NOT_TESTED; no phone connected. Final checkpoint changes documentation only.
