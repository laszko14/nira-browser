# Zavro x MPV v1141 — bounded reconnect and correct player-retry lifecycle

## Evidence from v1140 device diagnostics
- Verified `probe_ack_oneshot` → Gecko bridge connected. `source_blob` and empty HANDOFF represent intentional fail-closed MSE/blob; do not change it.
- Native manual HLS *works*: `mpv_tls_ca_prepared`, `mpv_start_file`, `mpv_file_loaded`, `mpv_playback_started`. Android system trust store solved initial TLS failure in this test.
- After about 30 seconds `mpv_cache_wait`, then `mpv_tls_error` and `mpv_cache_resume` occurred. The evidence shows a transient download failure, **not** a fatal certificate error or proof of a TLS regression.
- Explicit `Ponów` generated `mpv_end_before_load` + `mpv_manual_error`, then `mpv_file_loaded`: the old code issued `stop` and `loadfile`, and misinterpreted the first END_FILE.

## v1141 changes
- On HLS and DASH configure two **bounded** FFmpeg connection-retry option sets (`stream-lavf-o`, `demuxer-lavf-o`): `reconnect=1,reconnect_on_network_error=1,reconnect_streamed=1,reconnect_delay_max=2`. No retry-at-EOF, no 401/403 retry, no cert bypass or cookie escalation. Other sources are unaffected.
- Replace the old `stop`+ `loadfile` sequence with a single `loadfile url replace`; consume exactly the expected old-file END_FILE, not new-file errors.
- For finite VOD only, preserve and attempt to resume at previous position after `FILE_LOADED`; skip seeking for live/unknown-duration or near-end streams.
- Record separate `mpv_tls_after_load`, `mpv_retry_replacement_end`, `mpv_retry_resume_attempt` (all no-URL allowlisted diagnostic codes).
- Added retry state unit tests and JS source-invariant tests. Rebase from successful v1140, new signed ARM64 versionCode **1141**, versionName **0.5.22-alpha47-mpv15**.
- Do not merge any previous Draft PR. Normal Zavro is untouched.

## Device checklist
1. Update v1140 → v1141 without deleting data.
2. On HLS start confirm `mpv_file_loaded` and `mpv_playback_started`; play for at least 1–2 minutes to see whether HLS buffers smoothly with reconnect.
3. During playback use `Ponów`; confirm no fake `mpv_manual_error`, and whether the video returns near its previous position.
4. CDA blob/MSE must remain Gecko without automatic unsafe handoff. Use direct eligible MP4/HLS for testing AUTO.
5. Send new MPV 3/3 report; network retries may not fix server-specific TLS protocol failures and are not a guarantee.
