# Zavro Clean - Video Search Diagnostics - Stage 1/7

Task ID: ZC-VIDEO-DIAG-1. Product: Zavro Clean, separate from x MPV.
Base branch: feat/zavro-clean-v4-p4-20261107.
Base source SHA: 469d93b236b0a14790b82919dbc89c8c5abacdb4.
Mode: IMPLEMENT only stage 1/7. No APK, no merge, no release and no signing.

## Verified observation points from base SHA

- WebExtension content.js and recognition.js: top-level document ID, route revision, search form, result region, cards, dynamic pages, finite extraction batches, pagination; no new JS hook in stage 1.
- VideoSearchBridgeController and VideoSearchBridgeProtocol: Kotlin-owned Gecko tab/profile context, document ID, request ID, native port and worker. JS must not claim a profile owner. Bridge protocol schema 1 is independent of diagnostic schema 1.
- VideoSearchDispatchQueue, VideoSearchMultiSiteExecutor and VideoSearchMultiRunJournal: worker leases, statuses, cancellation, counters and atomic Room checkpoints. No changes to worker logic in this stage.
- VideoSearchRepository, VideoSearchPrivateStore, VideoSearchPrivateSessionRegistry: Room only for normal profiles; distinct private lifetime IDs and RAM-only data for private tabs. Same run ID is not sufficient to identify an owner.
- VideoSearchResultsPresenter and results view: storage-to-display counts, sort, filter, grouping, tab identity; actual observers will be wired in stage 4.
- TestLab, TestLabExports, TestLabReportGenerator: usable design precedent for explicit session/stop and bounded manual export. Its non-URL reporting structure is not reused for the site diagnostics.

## Schema v1

One session UUID per recording. UTF-8 newline-delimited JSON, one record per line.
A session_start record starts the file. Individual event records contain:
schemaVersion=1, kind=event, sessionId, ownerId (SHA-256-derived, not raw profile ID),
private flag, monotonically increasing producer-attempt sequence and timeMs;
stage, claim (OBSERVATION vs INFERENCE), decision and reason; trace with nullable
runId, siteId, documentId, requestId, workerId and batchId;
nonnegative counts with explicit VISIBLE_CARDS, CANDIDATES, ACCEPTED_FILMS,
UNIQUE_FILMS, SAVED_FILMS, DISPLAYED_FILMS, PAGES, REQUESTS vocabulary;
a finite set of detailed fields (page URL, film URL, search phrase, title,
duration, date, selector, DOM fragment, detail).

A session_end record has reason, attempted, recorded, dropped and limited.
Missing event/field means UNKNOWN, never successful processing. The end marker
means the recorder was closed; it does not mean a page is complete or that
search succeeded. Gaps in attempt sequence and dropped counters are explicit
partial evidence. A missing/corrupt session_end on disk is INTERRUPTED.

The complete downstream per-site classification (OK / PROBLEM / UNCERTAIN /
INTERRUPTED) is not implemented in stage 1. It belongs to stage 4, after
observation and Kotlin correlation. Explicit failure reasons like true empty
results vs unmatched cards vs AJAX vs stale document vs CAPTCHA cannot yet be
observed by this isolated module.

## Storage, limits, lifecycle, safety

The normal factory stores exclusively under Context.noBackupFilesDir,
zavro-video-diagnostics/<hashed-profile>/session-UUID.jsonl, never in
Room/history/session state. Directory setup and writing run in a dedicated
background worker. Only this module's old diagnostic files may be pruned:
at most four retained per normal profile, with seven-day retention on next
session creation. This is explicitly diagnostic-data retention and does not
delete any browser data or existing user profile files.

Private scope requires null filesystem destination. All private diagnostic
lines remain RAM-only. The exact host-owned private lifetime is required to
read the buffer; retirePrivateLifetime(exactScope) empties it, stops writers
and invalidates late events. The application host must connect retirement to
the existing private tab registry in later stages. Do not export private data
after the lifetime ends. Process death also loses the private RAM.

Limits: 20,000 accepted producer events per session, 8 MiB per session,
256 pending entries, 45 minutes; 1,024 bytes are reserved for a session_end
marker. When capped, drop counts are recorded rather than blocking the Gecko
or Android UI thread. Producers only perform O(1) enqueue under a short lock;
JSON encoding, redaction and file IO run on a background daemon. IO failure
becomes an explicit failure signal; incomplete disk files are not considered
complete. No network calls, automatic sharing, manifest permissions or UI.

Secrets: event payloads have a finite field vocabulary, not arbitrary
headers/cookies/form maps. Generic token/password/cookie/Bearer patterns and
sensitive URL parameters are masked; URL userinfo/fragments are removed; DOM
input/textarea/script/style/meta nodes and value/handler attributes are
discarded; snippet length <= 2,048 characters and ordinary field lengths <=
1,024 (URLs <= 2,048). Non-sensitive film query parameters are retained for
diagnostics. This is defense in depth, not proof against every possible
user-entered secret. Stage 2 must sanitize DOM before extraction and
instrumentation, independently of this recorder.

Important: this stage deliberately adds no instrumentation, no UI and no
new automatic WebExtension behavior. The recorder remains dormant in normal
app flows until stage 5's opt-in session control.

## Stage 1 tests

VideoSearchDiagnosticRecorderTest covers: versioned record/correlation;
six concurrent sites and producer ordering; explicit capacity drops and
terminal counters; byte budget with reserved footer; idle timeout; crash
with missing/corrupt footer; masking passwords, cookies and tokens while
retaining benign film URL parameters; private lifetime retirement, foreign
scope refusal and invalid filesystem ownership.

Required SOURCE CI: process-docs + shared-tests JS, Kotlin/Robolectric,
AndroidTest Kotlin compilation, lint with regression baseline, required
evidence summary all SUCCESS at exact final source SHA. Required stacked
PR CI: shared-tests JS, Kotlin/Robolectric, AndroidTest compile, lint,
error-learning evidence all SUCCESS on same PR head SHA. APK build and
release workflows must remain SKIPPED.

NOT_TESTED: real GeckoView, real sites, Nothing Phone (1), UI, profile switch
integration, real Android FPS/RAM and end-to-end exporter. No claim that the
recorder already covers the entire search process.

Next: stage 2/7 - WebExtension page observations and bounded safe DOM evidence,
only after the separate user message "dalej".
