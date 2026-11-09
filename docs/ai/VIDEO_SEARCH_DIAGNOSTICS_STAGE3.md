# Zavro Clean — Video Search Diagnostics — Stage 3/7

Task: ZC-VIDEO-DIAG-3. Scope: Kotlin GeckoView bridge, session/owner correlation,
bounded native drain, normal-profile no-backup JSONL and private-lifetime RAM.
Starting branch feat/zavro-clean-video-diagnostics-20261108 at 1966b95120b4620c2aafa812988a057b24add281.
One explicit "dalej" authorized stage 3. No APK, merge, signing or release.

## Native architecture and trust boundaries

VideoSearchDiagnosticWire.kt implements a separate, strict parser for only
diag_ack and diag_events messages on the existing native Gecko port.
It refuses unknown schema keys, spoofed profile/site/run/worker identifiers,
unknown count/field vocabulary, malformed document/request/session IDs, ill-typed
or negative counters, overly long strings, inferences by JavaScript, more than
four events in a packet, a non-9000-byte wire budget, duplicate or out-of-order
event sequence, invalid loss accounting and malformed stop reasons.

The native controller verifies session-to-tab BrowserStore ownership, actual
profile, exact Gecko native port sender, bound document, native request nonce
and site/worker run lease before recording. JavaScript can report what it saw
on a page, but cannot choose the profile, the site, the run, the worker or a
Room destination. Native request IDs are associated with a real site/worker
only when created by the existing request and privateRequest dispatch paths.
Uncorrelated page events are retained without invented site/run attribution.
Per-worker run IDs are tied to exact native-created worker-tab lifetime; they
are not inferred from a previously completed group or arbitrary JS payload.

VideoSearchDiagnosticNativeSession records observations with typed schema v1,
native request and queue status, observed errors, explicit gaps, drops and port
interruptions. A verified SPA hello can retain at most 32 old document
identities within the same bound Gecko port, allowing old buffered page events
to keep their original document IDs. Requests and traces are bounded (512).
Untrusted delayed replies and packets for other tabs, owners or documents are
never accepted or declared successful.

## Recording lifecycle

Controller.beginDiagnostics(owner) is an explicit opt-in API and is NOT
called by any existing UI or background service at stage 3.
A verified owner may start a single native recorder session. The controller
sends diag_start to at most 12 eligible bound ports, then pulls a capped
4-event cursor batch approximately every 1.3 s per port when no ordinary
search request is in flight. Only one diagnostic request may be outstanding
per port; timeouts, transport loss and invalid context are recorded as
interruptions, not as successful searches. The existing 12-replies/5-second
rate limit remains in place, and diagnostic responses never complete ordinary
search jobs. On an explicit stop, the controller signals diag_stop and marks
any undrained JS evidence as incomplete (USER_STOP_UNDRAINED); Stage 5 must
provide final graceful-drain/export user interaction.

Normal recording uses the already approved stage-1 writer in
Context.noBackupFilesDir/zavro-video-diagnostics/<hashed-profile>/...jsonl.
It does not store diagnostic page URLs in Room, browser history, or cloud.
Room remains the authoritative existing normal-search journal, untouched.
Private recording is RAM-only under the exact
VideoSearchScope.Private(profileId, privateLifetimeId). Profile/mode switches
stop active capture without incorrectly transferring data to a different
profile; private data is retired only when that private lifetime actually ends
(last matching private tab closes or registry retires it). A repeated or late
Gecko response cannot resurrect a retired private recorder.

Normal search outcomes and queue state are observed at the native bridge
without changing worker limits, queue decisions, Room transactions, user
tabs, Gecko navigation, or search result acceptance. A QUEUE_DONE observation
describes the executor's reported status; it is not independent proof of
device rendering, final persisted count or website correctness. Those
funnel checkpoints are stage 4.

## Tests and open boundaries

Synthetic Robolectric tests exercise strict decoder, foreign owner fields,
malicious counts, old documents and duplicate sequences, safe source attribution,
private RAM and exact-epoch retirement, explicit dropped counters and
read-only normal session. Stage 2 JS tests still cover the original page
extractor; full SOURCE and PR CI must run JS, Kotlin/Robolectric, AndroidTest
compilation, lint regression, process docs and error-learning evidence.

NOT_TESTED: actual GeckoView device/physical Nothing Phone (1), live sites,
WebExtension native drain under actual network and CPU load, complete UI
start/stop/export, Android FPS/RAM overhead. The new recording APIs are
dormant until Stage 5; do not claim end-to-end recording on the user's phone.
Stage 4 will add the result-view funnel and per-site report judgments.
