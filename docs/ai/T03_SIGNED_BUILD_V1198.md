# T03 — signed update APK v1198 (landscape)

Explicit user authorization: **Kompiluj**, 2026-11-01.

Source SHA: `678c429e80d651d2ac38ed05f94a7b6f29f57749`; shared CI: https://github.com/laszko14/nira-browser/actions/runs/41268887414, completed SUCCESS. Branch: `build/zavro-clean-t03-landscape-v1198-20261101`.

- Version: `0.5.22-alpha103-clean` / `versionCode = 1198` (arm64 ABI output from Gradle base 198).
- ApplicationId: `com.prirai.android.nira.debug`; ABI `arm64-v8a`; no universal APK.
- Signer SHA-256: `df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00` (must be verified in CI).
- Includes previous T03 panel mount reuse and recent transient `MediaFloatingUi` window cleanup on orientation change.
- Single trusted workflow: `.github/workflows/zavro-t03-landscape-update-v1198.yml`, with strict source, shared regressions, verified APK, SHA-256 provenance.
- Do not claim device-tested or issue-resolved until user tests the signed version on the phone. No merge, install, publication, cache/profile/session cleanup.
