# Zavro x MPV v1142 — one-button Supertest (40 seconds)

## Purpose
The user no longer wants three separate phone tests and three reports. Replace the legacy 1/3 WEB, 2/3 HANDOFF, 3/3 MPV menu items with one **SUPERTEST • 40 s • jeden raport** entry. The same single short test can be started on the active native video screen using **SUPERTEST 40 s**. This is **one test accessible from two surfaces**, never three manual steps.

## One-tap phone instructions
- To diagnose AUTO/Gecko: open a video page, tap menu → SUPERTEST. Press Play on the page during the next **40 seconds**; if safe, the player may open automatically. After 40 seconds **one report is copied** automatically. Paste it in the next conversation.
- To diagnose HLS playback without AUTO: first manually open HLS in the embedded MPV, tap **SUPERTEST 40 s** in its controls; leave playback running. The report is copied automatically in 40 seconds.
- Do not wait three minutes, do not manually gather 3 reports, do not use browser URLs in report.

## Technical implementation
- RAM-only capture bound to active `EngineSession`, one active test at a time, 40-second monotonic Handler timeout, 30-second pre-roll for startup events that precede button tap.
- Report in Polish includes bridge ACK, playback gestures, MSE/DRM blocking, offer/prepare/ACK, MPV start/file-loaded/playback-start, OS CA status, bounded network error counts, buffer waits/resumes, retry, and numeric sampled playhead progress.
- No new network calls, pauses, seeks, URL access, cookie exports, or TLS verification bypass.
- Allowlisted event codes only; no hosts, headers, URL query strings, private page titles, tab IDs, tokens, or cookies.
- If MPV not started during test, report explicitly says untested; do not equate unsupported blob handoff with a failure.
- Existing signed app package and certificate unchanged; new arm64 versionCode 1142, versionName 0.5.22-alpha48-mpv16, manifest 1.5.8, dedicated new draft PR without merge.

## Caveat
GitHub Actions tests and signing verify the app build, not actual streaming on physical Android. After APK installation the user conducts exactly one short test as above.
