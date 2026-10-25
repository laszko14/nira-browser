# Zavro v1128 — embedded MPV update foundation (E1 / early E2)

This is an **incremental, experimental implementation** based on the v1127
standalone prototype. It is **not** the completed MPV Alpha port.

## What this branch changes

- Keeps the established release package `com.prirai.android.nira` **and**
  the existing signed testing track `com.prirai.android.nira.debug` from v1126.
  Removes the experimental `.mpv` suffix; effective ARM64 versionCode is
  **1128** (base 128 + 1000). CI produces the **.debug testing APK**.
- Reuses the tested-in-CI native libmpv Activity from PR #40 inside Zavro.
- Routes manual detected-media choices / manual HTTPS media link entry into
  the embedded player by default. The existing `ask_each_time` preference
  offers internal and external choices.
- Transfers media URL and optional HTTP context through a one-use, short-lived,
  **process-only ticket**; the Intent carries only an opaque identifier.
  Incognito player windows use `FLAG_SECURE`.
- Removes the prototype's unsafe switch from Gecko fullscreen to the first
  network media URL: no auto-handoff until per-element frame/ack is verified.
- Keeps GeckoView running as before and the existing external player path.

## Not yet implemented or verified

- No reliable pause/restore ACK for the exact HTMLVideoElement: simultaneous
  browser audio on some sites has not been excluded. Do not market this as
  automatic replacement of all web players.
- No fullscreen auto handoff, DRM/EME migration, cookies/Authorization sharing,
  arbitrary blob/MSE extraction, per-site policies or comprehensive fallback.
- No port of the full MPV Alpha controls (HUD, gestures, Lua 30+8, filters,
  subtitles, Auto Audio); v1127's basic UI remains.
- No instrumented tests or real phone playback, memory profiling, multi-profile
  authentication, PiP or controlled HLS/DASH fixture coverage.
- Existing v1127 use of `mpvRex-libmpv:0.0.9` requires a **separate native
  binary/license compliance audit** (FFmpeg GPL/LGPL and notices) before a
  production release.
- CI project signing certificate is pinned, but it must still be compared
  with the actual installed APK's **package (debug vs release)**, certificate
  and versionCode before any on-device update can be declared safe.
- Do not uninstall Zavro or clear browser storage if an update is rejected.

## Next gates

1. Run GitHub CI `build`, `test`, `lint`; record real statuses.
2. Inspect and document the actual AAR/ELF dependency licenses and obligations.
3. Smoke-test update / data preservation and playback on the user's device.
4. Add playback coordinator, per-video WebExtension bridge and pause/restore
   protocol before enabling automatic takeover.
5. Expand streaming robustness and port the rest of the MPV Alpha UI in stages.

Original architecture, staged plan, and 64-scenario test matrix:
`deep-research-report3.md` as supplied for implementation.
