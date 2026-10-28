# Zavro Clean v1186 — staged MME safety remediation (2026-10-28)

Base: v1185 PR #107 @ 6e6f08d37bc94228acdfda78a93863b089840764. Feature branch is v1186 Draft PR #108; no merge to main.

## Code changes (not physical-device claims)
- HTTP 206 Content-Range total is accepted as EXACT only with 0 <= start <= end < total; compressed payloads remain UNKNOWN.
- HLS byte range EXACT requires every counted segment's valid resolved URI, one valid range per segment, complete ENDLIST, no unknown init, malformed range or encryption. This is stream scope, not automatic A/V presentation total.
- HLS AUDIO/SUBTITLES track roles are surfaced separately from primary video variants.
- Conflicting independent local card titles or conflicting aria-labelledby labels are UNKNOWN. Existing unambiguous per-card titles still work.
- Tiles with no verified individual title display neutral 'Film #n', not an unverified filename masquerading as title.
- No changes to external MPV Alpha Play, GeckoView engine, 120 Hz policy, or layout preferences.

## Verification gates
- JS fixtures and Kotlin mapping tests must pass in real GitHub CI; lint report must be checked separately.
- Signed APK is publishable only when exact versionCode=1186, versionName=0.5.22-alpha91-clean, package com.prirai.android.nira.debug, arm64-v8a ABI and pinned cert SHA256 df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00 are validated by scripts/verify-update-apk.sh.
- Physical phone Play and 120 FPS remain NOT_TESTED until measured on actual device. Owner-generation identity and complete cross-site metadata attribution are not asserted by this stage.
