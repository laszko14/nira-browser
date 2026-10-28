# Smart active tab scrolling — v1188

Base: Clean v1187 `dea9f3bdaad785a4cb312d42d0096cc23cd11022`, PR #109.
Scope: native portrait and Compose landscape strips only; preserve order, groups,
private profiles, favicon settings, no X badges and 120 Hz policy.

Plan (executed autonomously per user request):
- [ ] Test shared measured-bounds policy: fitting content, complete groups, comfortable
  icons, clipped/offscreen icons, collapsed headers and variable icon dimensions.
- [ ] Integrate native layout-time check with latest selection, cancelable animation
  and manual-touch cancellation; reset single tab to zero.
- [ ] Replace competing Compose effects/requesters with one cancelable measured check;
  materialize absent LazyRow item, then measure the actual nested icon. No scroll keys
  based on favicon/title or manual scroll; skip during drag.
- [ ] Run regression suite, build/lint, review diff, bump effective ARM64 code to 1188,
  commit/push Draft PR against v1187; inspect CI and signed artifact.

Device behavior (both orientations, gestures, 120 Hz and installation) requires a
phone and remains NOT_TESTED until performed. No merge to main.
