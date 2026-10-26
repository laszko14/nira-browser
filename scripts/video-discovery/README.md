# Controlled video discovery fixtures

`npm ci --ignore-scripts --prefix scripts/video-discovery`

`scripts/video-discovery/node_modules/.bin/playwright install --with-deps chromium`

`DISCOVERY_REPORT=app/build/reports/video-discovery.json node scripts/video-discovery/fixtures.cjs`

An existing system Chromium can be selected with `CHROMIUM_PATH=/usr/bin/chromium`.
The test executes the shipped content/background/parser in real Chromium DOM with
controlled runtime/native/webRequest adapters, not a real Gecko WebExtension.
All browser requests are fulfilled locally with synthetic `.test` fixtures.
No real page, credentials, Android player, DRM system or download manager is used.
No media is played. The post-interaction fixture injects a previously absent URL.

For the same baseline run, extract content.js/background.js/manifest-parser.js
from b6908c9238d43fbfa69d3cc77445d650b0e9bb35 into a temporary directory using
`git show`, then set `DISCOVERY_ASSETS=/absolute/directory DISCOVERY_BASELINE=1`.
This changes neither the checkout nor the working files. Do not use reset/checkout.
`DISCOVERY_BASELINE=1` records misses rather than asserting the current target.

Metrics separate raw candidate messages, all native messages (including document
registration and diagnostics), exact-URL upserts, grouped material alternatives,
and false/missing candidates. Group calculations are fixture-level proxies; the
real Kotlin repository/classifier/protocol/session/BlobHlsSelector tests are required.
Old SPA document results are excluded from duplicate-material counting. Failed or
DRM sources remain diagnostic entries, excluded from viable-source recall.
The three old false positives are upstream init.mp4 (already filtered by Kotlin),
invalid header-only HLS, and DRM MPD lacking an unavailable classification.

Elapsed values are host JavaScript timings, including script startup, selector
work and synchronous timer callbacks. They are not Android FPS/CPU/RAM estimates,
network timing, end-to-end Kotlin latency, or a statistically stable speedup.
The per-callback budget is checked between elements, not during one JSON parse.
Current repository capacity remains 64 entries per tab /384 global; the tail case
has150 DOM video elements,130 placeholders and20 real films. The40-film case fits
within the existing capacity. A source absent before interaction is ineligible
for pre-play recall and is never recovered by automatically starting playback.
