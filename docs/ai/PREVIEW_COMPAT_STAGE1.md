# Zavro Clean — Stage 1/10: preview hover/touch diagnostics

## Grounded findings

- Source: `feat/zavro-clean-preview-diagnostics-stage1-20261110`, base `beebd957ff979ab3644c248e1622b8a98692e6bc` (Zavro Clean, not x MPV). No app production behavior changed.
- `UserJsPreferences.kt`: `media.autoplay.blocking_policy=2` and `media.autoplay.default=5` are passed to Gecko config via `Components.kt`. Both settings existed already in pre-MPV v1126; a causal connection to embedded MPV is NOT proven.
- `preview-assist.js`: injected across HTTP(S) frames, listens for click, not for hover or initial touch; may call `video.play()`, mute, and consume explicit preview clicks.
- In-app libmpv and `ZavroMpvActivity` are absent from current Clean, but the explicit external MPV Alpha ACTION_VIEW path remains.

## Executable synthetic tests

`node --test app/src/test/js/preview-compat-stage1.test.cjs app/src/test/js/preview-assist-audit60.test.cjs app/src/test/js/preview-assist-stage18b.test.cjs`

| Case | Test boundary |
|---|---|
| Source config | Autoplay values are extracted from shipping Kotlin sources, not pinned permanently as correct |
| Extension injection | Preview helper is installed for HTTP(S) and child frames |
| Hover and first touch | Synthetic `pointerenter`, `mouseenter`, `mouseover`, `touchstart` do not trigger helper play/capture |
| Ordinary click or video surface | No mutation of native player or navigation |
| Declared preview click | Existing helper calls play, consumes click, mutes player |
| Rejected play | A synthetic `NotAllowedError` restores state, later tap stays navigable |
| Modifier click | Existing helper leaves navigation to website |

## Actual Gecko matrix still required

Run a real website where previews fail, compare with current Firefox Android on the same connection, check normal/private mode and background-playback allowlist. For each trigger (mouse hover, tap, long press, swipe), record only whether the site called `play()`, the promise error name (if any), muted/playsInline state, whether the click was intercepted, and whether playback began. Do not retain session tokens, cookies, account details or complete signed URLs. A synthetic Node DOM is not a substitute for GeckoView or a Nothing Phone (1).

## Delivery and next stage

Stage 1 changes only JS tests, this document, error learning and the handoff checkpoint. Full mandatory SOURCE and PR CI on exact final SHA. No APK, merge, release or data changes. Stage 2/10 should confirm the effect of autoplay settings and then narrowly permit muted inline previews without allowing audible autoplay globally.
