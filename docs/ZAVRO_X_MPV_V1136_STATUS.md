# Zavro x MPV v1136 — Gecko handshake and queued MPV file

Based on `e0bf4b231b3af3ae0dd372b7e15990311436a6f3` (v1135), new branch, draft PR, no merge. Only the separate `com.prirai.android.nira.mpv` package is changed.

## Evidence from v1135 device reports
- `script_started`, `bridge_ready`, `oneshot_received`, `gesture` and `video_play` prove that content scripts execute and one-shot session messages arrive.
- `probe_received` proves native-to-script Port delivery; `probe_no_ack` shows Port replies were not confirmed. It does not prove whether the reply was sent or delivered.
- `source_blob` on the tested page means AUTO must stay in Gecko (unresolved MSE/blob, no DRM interception).
- Manual HLS: `mpv_video_ready` before `mpv_loading`; no START_FILE/FILE_LOADED/END_FILE, then 20-second timeout. This is consistent with lost initial loadfile.

## v1136 changes
1. `BaseMPVView.playFile` is a queue drained only from `surfaceCreated`. Previous post-attachment call could miss it. Queue the URL immediately after libmpv initialization, before attaching the view, and mark actual `surfaceCreated`; longer bounded startup windows for HLS/DASH.
2. A fresh native random UUID challenge per port is also acknowledged via Gecko's working `sendNativeMessage`. One-shot AUTO offers and prepare ACKs are accepted only if the nonce resolves uniquely to a connected port in the exact real EngineSession; the port has to be verified before any handoff. Existing Port callbacks stay supported. Fail closed for unsupported or ambiguous content.
3. Stop surface resize from seeking to position zero when paused.
4. Regression tests; extension 1.5.2; ARM64 1136 / 0.5.22-alpha42-mpv10.

## Test checklist on real phone
1. Install v1136 *over* Zavro x MPV v1135; ordinary Zavro must remain untouched.
2. Open a public direct .mp4 (no auth, no cookies). In AUTO tap Play. Look for `probe_ack_oneshot` or `probe_ack`, `auto_offered`, `auto_offer_received`, `prepare_sent`, `ack_ok`; MPV must emit START_FILE and FILE_LOADED.
3. On CDA and another blob/MSE player, tap Play. Expect `source_blob`; Gecko must continue playback without duplicate audio or an MPV takeover.
4. Select a known HLS link manually in media detector. Look for `mpv_surface_attached`, `mpv_start_file`, `mpv_file_loaded` or a redacted error class. Verify video/audio/seek and retry.
5. Test two tabs, normal/private profile, back/restore, orientation change and PiP.
6. Copy all three diagnostic reports (WEB, HANDOFF, MPV), including cases where no events appear.

No cookies or auth headers are exported; we have not established that signed/CDN streams are authorized for embedded mpv. No DRM/MSE extraction, forced page interception or user-profile cookie copying.

## Pending
- Device verification of one-shot handshake and native-to-script restore; if still missing ACK, instrument isolated Gecko message routing.
- Resolve HLS-specific server authorization/range/manifest faults from redacted libmpv events.
- Profile-bound auth propagation only after explicit security review; blob/MSE Gecko fallback.
- MPV Alpha full UI, gestures, Audio Premium and Auto Audio are separate follow-ups.
