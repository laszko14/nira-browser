# Zavro Clean — Video Search Diagnostics — Stage 2/7

Task ZC-VIDEO-DIAG-2; product Zavro Clean, separate from x MPV.
Base branch feat/zavro-clean-video-diagnostics-20261108, HEAD at start 3467b4967f857c43a4158daf5c52168630310a14.
Authorized: stage 2 only. No APK, merge, release, force push or signing.

## Contract

Add a passive, opt-in WebExtension diagnostics module in the top-level HTTP(S)
content script. It runs only after an explicit verified native-bound diag_start;
otherwise it does no observation or storage. It never changes search decisions,
submits forms, clicks, intercepts requests, or changes extension permissions.
The module is loaded after recognition.js and before content.js.
Existing video bridge protocol schema 1 remains unchanged for normal search;
the diagnostic event schema v1 is separate.

### Events and evidence
- FORM inspection/analyze/search response: selector, submission, reason,
  confidence, query (redacted), no input values, passwords or POST capture.
- RESULTS_REGION: CSS/structural selector matches; zero cards is UNCERTAIN,
  visible explicit site empty marker is a separate observed reason;
  dynamic loading is WAITING; password/login is HUMAN_GATE not zero results.
- CARDS: recognition reason and matched/scanned count; extraction counters
  for visible, candidate, actually accepted and unique films; at most 2 rejection examples for
  excluded ads/recommendations/sidebar and invalid safe film anchors.
- METADATA: missing title, duration or publication date signals and bounded
  accepted sample fields URL/title/duration/date, not full HTML.
- SPA: host-observable route change with old document ID and next revision;
  throttled DOM_MUTATION_OBSERVED is never proof of AJAX completion.
- PAGINATION: existing mode and exact status (pending, dispatched, end, unsafe);
  a dispatch is not a completed pagination. Stale/cancel/error statuses are not
  silently interpreted as no results.
- BRIDGE: extraction exception reason, not falsely successful results.

Observation and inference remain separate. An absent observation is unknown.

## Command shape for Stage 3 native correlation

All commands must be delivered by an existing bound verified native port, with
schemaVersion 1, exact approved keys, documentId and native requestId (32 hex).
There is no Kotlin sender or handler yet; those belong to Stage 3.

diag_start: documentId, requestId, sessionId (UUID). ACK diag_ack active/rejected.
diag_read: documentId, requestId, cursor (last ACKed JS sequence). Response
  diag_events containing batch {schemaVersion, sessionId, attempted, captured,
  dropped, limited, oldestSequence, latestSequence, stopped, stopReason, events}.
  <=4 events/read and <=7000 event bytes. The host must validate live
  run/site/profile/worker/document ownership, deduplicate by
  (sessionId, documentId, sequence), and preserve dropped counts.
diag_snapshot: documentId,requestId, emits an explicit limited, structural-only
  DOM sample for form/results/pager into the RAM event buffer, then ACK.
  Read the events through diag_read, not raw HTML.
diag_stop: documentId,requestId,sessionId, stops capture; read may drain until
  disconnect. Disconnect destroys all JS RAM evidence.

The same-origin SPA route has a fresh documentId/revision. Buffered events
contain their original document identity; JS is never authoritative for
profile/run/site owner. No automatic upload or localStorage/IndexedDB.

## Privacy and limits

JS limit: 256 events, 192 KiB, 45 minutes; bounded native replies; capped
field count, URL length 1200, ordinary field 300, DOM sample 768 characters.
A skipped event increments dropped and attempt serial; full buffers cannot
silently pretend completeness. Sample DOM includes only allowlisted
structural tags, class/id/role/data-testid and sanitized HTTP(S) href;
no input/textarea/script/style content, field values, innerHTML, body text,
hidden/private elements, header data, cookies, Authorization, secrets,
or complete network responses. URL fragments and user-info are omitted,
sensitive query params replaced with redactions. Stage 7 must independently
verify unknown secret patterns on real-site samples.

## Checks and boundaries

New JS test: app/src/test/js/video-search-diagnostics-stage2.test.cjs:
opt-in gating, native correlation, real empty-vs-loading, SPA revision,
pagination outcomes, bounded read/overflow, sanitized DOM, no secrets,
disconnect RAM deletion, no new permissions and no altered primary responses.
Full JS suite plus JVM/Robolectric, AndroidTest Kotlin compile, lint,
process-docs, evidence validation in SOURCE and PR CI on exact final SHA.

Stage 2 deliberately does not connect events to Kotlin recorder or UI.
These are separate Stage 3/5 responsibilities. NOT_TESTED: actual GeckoView,
real sites, physical Nothing Phone (1), profile lifetime integration,
device FPS/RAM measurements, final exported report. No APK.
