# Zavro Clean — stage 4/10: hover, touch and gesture compatibility

## Source-level diagnosis

Baseline SOURCE and PR CI for stage 3 (source HEAD `59793c070db0ac31daa24e49e897caa1d3c58cc0`) were SUCCESS: SOURCE run 42770471322, PR run 42770527941. The shipped `preview-assist.js` previously registered **only** a trusted `click` handler. It therefore did not itself start previews on hover. Its click handler also had no previous-touch-movement guard. These code facts do **not** establish which actual website or GeckoView issue caused the user's report.

## Implemented scope

1. **Desktop mouse hover**: trusted `pointerover` starts an explicitly declared `.mp4/.webm` preview muted, only after proving the actual video source matches the declaration. `pointerout` pauses and removes only a helper-created video, or restores the original `muted`/`playsInline` state for an existing video. The session is tied to its DOM card; a stale rejected play promise cannot stop a new session.
2. **Site ownership**: native `autoplay muted playsInline` remains under website control; never start preview from an ordinary button, video surface, untrusted pointer event, touch-generated `pointerover`, or undelared media. No use of `preventDefault()` or `stopImmediatePropagation()` on pointer or touch events.
3. **Mobile touch**: `touchstart/move/end/cancel` listeners are passive. They track a single gesture and reject preview interception from a swipe (motion over 12 CSS pixels), a long hold (over 650 ms), or a cancelled gesture. A *short tap* still reaches the existing trusted `click` path for its explicitly declared preview, without independently playing at `touchstart` (which could steal scrolling). Any synthetic click from a cancelled gesture is allowed to navigate using the native site behavior; the guard expires at 1000 ms and is card-specific.

Docs: https://developer.mozilla.org/en-US/docs/Web/API/Pointer_events and https://developer.mozilla.org/en-US/docs/Web/API/Touch_events/Using_Touch_Events — touch pointerover can occur without actual hover; cancelling touchstart/touchmove can suppress native mouse/click and scrolling.

## Deterministic verification

- New `app/src/test/js/preview-assist-stage4.test.cjs`: 14 cases executing the **production script** in a controlled DOM, covering hover enter/exit, native media restoration, explicit-source guard, rejection and sync throw, mouse vs touch pointer identity, passive touch, tap, swipe, hold, cancel and multi-touch.
- Precommit semantic V8 harness: 14/14 GREEN on proposed code; 14/14 RED against stage3 source without pointer/touch listeners. This is a deterministic isolated JS smoke check, **not** Node/Gecko on device. Final Node and Android checks belong to SOURCE and PR CI.
- Existing `preview-assist-audit60.test.cjs` (18A), `preview-assist-stage18b.test.cjs` (18B), `preview-compat-stage1.test.cjs` and `preview-assist-stage3.test.cjs` must remain green.
- Mandatory SOURCE: process-docs, shared-tests JS/JVM/Robolectric/AndroidTest compile/lint with lint baseline and quality-evidence. Mandatory PR: Android CI JS/JVM/AndroidTest compile/lint/error-learning, no signed APK.

## Known limitations / manual matrix

Actual website hover/dwell behavior, synthetic click ordering in GeckoView, phone scrolling, first tap and next-tap navigation still require a **device test**. Compare on Nothing Phone (1) Android 15 vs Firefox Android, normal/private modes: mouse hover or pointer device, short tap, swipe, long press, touchcancel, ordinary HTML5 video/player buttons, play() NotAllowedError, native muted autoplay. Capture only event types, muted/play status and error names — no cookies, authentication or signed media URLs.

The original user issue `ERR-20261110-PREVIEW-01` and stage3 owner-protection risk remain OPEN until real-site validation. No APK, merge, release, MPV Alpha, privacy, profiles, signing or package changes. Further optimization only after new DALEJ.
