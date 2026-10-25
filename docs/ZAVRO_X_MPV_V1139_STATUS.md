# Zavro x MPV v1139 — version invariants

Follow-up to v1138: earlier legacy regression tests still pinned Gradle base versionCode 135 and alpha41-mpv9. Replaced those historical literals by version-independent patterns, and added a dedicated strict metadata consistency test that derives ARM64 versionCode from Gradle and checks both GitHub Actions job expectations and artifact identity, while pinning the package ID and signing certificate.

ARM64 versionCode 1139, versionName 0.5.22-alpha45-mpv13, extension 1.5.5.

Functional fixes from v1136 are preserved: Gecko one-shot challenge protocol bound to actual EngineSession/Port, correct MPV loadfile queuing before surface creation, and bounded HLS startup. No change to regular Zavro, no merge.

Device instructions and scope limitations: docs/ZAVRO_X_MPV_V1136_STATUS.md.
