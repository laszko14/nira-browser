# Zavro x MPV v1140 — trusted Android system CAs for libmpv / better TLS diagnostics

## On-device v1139 observations
- Gecko WebExtension probe: `probe_ack_oneshot`, success; Handoff remains empty because the tested sources were `blob:` (MSE); intentional fail-closed behavior.
- Manual HLS: `mpv_start_file` now fires (previous v1135 bug fixed), but `mpv_tls_error`, `mpv_end_before_load`, `mpv_open_failed` appear immediately, without `mpv_file_loaded`. Failure is in HTTPS/TLS rather than SurfaceView queueing.
- A generic TLS code cannot prove whether the failure is a missing CA, TLS protocol-version mismatch, or remote server error.

## v1140 change
- Generate private, locally stored PEM trust bundle from AndroidCAStore **system** roots, not user-installed roots.
- Set `tls-ca-file` via `ZavroMpvView.initOptions()`, before player initialization/stream load.
- Keep `tls-verify=yes` always. Do not retry with disabled verification, do not copy browser cookies/tokens or emit URLs in logs.
- Add allowlisted `mpv_tls_ca_prepared` or `mpv_tls_ca_unavailable`; classify `mpv_tls_certificate_error` vs `mpv_tls_handshake_error` vs generic `mpv_tls_error`. Do not save raw log text.
- Add JVM/JS tests; preserve AUTO safety gates, per-session isolation and Gecko fallback for blob/MSE/DRM.

## Remaining device validation
1. Update v1139 -> v1140 (same experimental package/signature), ensure data persists.
2. Use manual HLS selection; check MPV report starts with `mpv_tls_ca_prepared`; look for `mpv_file_loaded` + video/audio.
3. If `mpv_tls_handshake_error` persists, inspect bundled FFmpeg's TLS backend/version in a separate patch. Some backends may not negotiate TLS 1.3-only servers; a CA bundle alone cannot repair protocol support.
4. Verify safe Gecko playback for CDA/blob and no duplicate audio. For AUTO test a permitted direct MP4/HLS stream with a user-initiated gesture.
5. CI signing, lint and unit tests do not replace physical-device playback testing.
