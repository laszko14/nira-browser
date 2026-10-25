# Zavro x MPV v1145 — mini UI and fullscreen continuity

Base: v1144 (`a92fe083a7b53feb19f00cbe5a1a4b5662238174`).
This change does **not** alter blob/MSE/HLS selection, Gecko's verified
frame/element ACK, transport, TLS or the process-global MPV gate.

## Root causes in v1144

1. Seven separate dark 44dp text buttons were rendered across a small video.
   They occupied most of the playback image and had no inactivity timer.
2. The original HTML5 controls could remain visible after the verified
   handoff; the UI should only touch the paused, verified `video` element.
3. Inline player overlay was attached inside the browser's coordinator, but
   fullscreen removed it and attached it to the window decor. Moving the
   `SurfaceView` hierarchy can recreate its surface.
4. The overlay position was bounded to its host but not explicitly to the
   actually visible Gecko engine viewport.

## Changes

- Use 32dp visual icons with 48dp touch targets in one translucent bar at
  the bottom of the video. At small widths show essential actions plus
  an overflow menu. Auto-hide after 3 seconds; touch to show.
- Keep the native `ZavroMpvView` mounted in **one** window-decor overlay for
  its entire lifecycle. Inline position/size change with layout parameters.
  Fullscreen expands **the same** overlay, and Back restores the inline
  bounds. No additional `loadfile`, `initialize`, `destroy` or new Activity.
- Intersect video geometry with both the visible Gecko view and the fragment
  host before drawing; hide when it is offscreen or too small to use.
- After confirmed `inline_start`, set `controls=false` **only on the exact
  ACK-verified HTMLVideoElement**. Restore its previous boolean on
  `restore`, `cancel` and port disconnect. No generic player-wrapper CSS
  and no unrelated DOM mutations. Proprietary site controls drawn outside
  the element's rectangle cannot universally be hidden safely.
- Report allowlisted events for native overlay geometry, fullscreen entry,
  return, unexpected media restart and observed playback continuity.
  Screen geometry != proof of non-black video or successful output audio.

## APK and CI invariants

ARM64 `versionCode=1145`, `versionName=0.5.22-alpha51-mpv19`.
Application ID remains `com.prirai.android.nira.mpv`; signed with existing
CI keystore and checked against cert
`df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00`.
Stable Zavro is not changed. No merge.

## Single device test

1. Install the ARM64 artifact as an update to existing Zavro x MPV.
2. Select AUTO, open the same formerly working blob/MSE/HLS video.
3. Start **SUPERTEST 40 s** just before Play, then Play.
4. Check that the mini video plays, controls at the bottom disappear after
   about 3 seconds and reappear on touch. Scroll the page slightly.
5. Press fullscreen and then Android Back. Verify the *same* moving picture,
   audio and playback position without restarting, doubled audio or black
   frames. Within the 40-second capture, perform both transitions.
6. Paste the automatically copied single report and, if UI geometry is wrong,
   one screenshot.

Only the phone test can confirm real video/audio, visual placement and
SurfaceView behavior across OEM Android devices; CI tests are not a substitute.
