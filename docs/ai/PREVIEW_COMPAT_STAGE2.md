# Zavro Clean — stage 2/10: GeckoView autoplay and muted previews

## Verified configuration and scope

- Baseline: `b80cba336f1a34f0eeadfd4ae1dd59e5e474b7da` on stage 1 branch. Product: Zavro Clean, not x MPV.
- Repo `UserJsPreferences.kt` serializes `mediaPrefs` into Gecko's config file; `Components.kt` registers the path before `GeckoRuntimeSettings.Builder.build()` and creates the runtime with those settings.
- Mozilla defines `media.autoplay.default=5` as block **audible and inaudible** autoplay, `1` as block **audible** autoplay, and `0` as allow both. Hence the shipped value `5` is a concrete configuration barrier for otherwise valid muted previews.
- Mozilla's `media.autoplay.blocking_policy=2` is strict click-to-play: async user activation can fail even after a gesture. It remains unchanged in this small stage to avoid widening audible autoplay after incidental taps.
- Mozilla's `media.autoplay.allow-muted` default is true; do not force it to false.

References:
- https://wiki.mozilla.org/Media/block-autoplay
- https://developer.mozilla.org/en-US/docs/Web/Media/Guides/Autoplay
- https://support.mozilla.org/en-US/kb/block-autoplay

## Code change

Change exactly `media.autoplay.default: 5 -> 1`. Do not change media WebExtension handlers, MPV integration, profiles, site permissions, media session, package, ABI or signing. Existing user/site overrides may still govern behavior.

## Tests and interpretation

- `node --test app/src/test/js/preview-autoplay-stage2.test.cjs` checks the **shipping Kotlin preferences** and runtime wiring. In-memory negative variants with block-all, allow-all and relaxed gesture mode must fail the oracle.
- Stage 1 and legacy `preview-assist` JS suites remain in full JS regression jobs.
- Required SOURCE and PR CI: documentation/registry checks, full JS, JVM/Robolectric, AndroidTest compile, lint + baseline and evidence jobs.
- These source-level tests **do not execute Gecko's media policy or a website**, and cannot establish that the original user's preview issue is fixed.

## Manual/device acceptance still needed

On Nothing Phone (1), Android 15, compare Zavro and Firefox Android on one affected site: (1) muted inline hover/touch preview starts, (2) unmuted video with no gesture does not start with sound, (3) deliberate playback controls still function, (4) test normal/private and site permission differences, (5) check any NotAllowedError and whether a script consumed the event. Record only event types, muted state, error names, and outcomes; no private URLs, tokens or cookies.

Main preview incident `ERR-20261110-PREVIEW-01` remains OPEN until behavior on a real site/device is established. No APK, merge or release in stage 2.
