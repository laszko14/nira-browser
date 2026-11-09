# Zavro Clean — Video Search Diagnostics — Stage 5/7

Task ID: ZC-VIDEO-DIAG-5. Product: Zavro Clean (not x MPV).
Branch: feat/zavro-clean-video-diagnostics-20261108
Preflight live HEAD: 22c6fef04140fe6650172065514aefd64eb5857b. User authorized only stage 5 by "Dalej".
No APK, merge, public release, destructive operations, signing change,
automatic network sending, hidden recording or private disk persistence.

## UI in "Moje witryny"

Both normal and private panels contain a compact diagnostics strip:
**▶ Logi** (start), **■ Stop** (stop and finalize), **↥ Raport** (share).
Each action checks the active BrowserStore/ProfileManager owner and, in
private mode, its exact VideoSearchPrivateTab / registry lifetime. Each panel
only sees its own scope; switching profiles/modes cannot adopt another
profile's report. Controls are disabled when there is no authorized scope.

Start requires an explicit confirmation dialog warning that page URLs, film
titles, search queries and small DOM evidence may appear. The existing source
sanitizers remove common passwords/cookies/tokens but cannot guarantee
catching unrecognized secrets. No event capture until explicit confirmation.
The session runs up to 45 min; normal stored under noBackup, private in RAM.
The screen samples bounded recorder status while visible; it does not persist
diagnostic UI preferences or add background polls to the browser. Rotation
recreates the screen but does not create another recorder.

Stop uses a **process-owned coroutine** so activity rotation/navigation cannot
cancel the final drain. For every currently verified document, the native
host sends diag_stop, verifies its ACK and uses cursor-based diag_read until
the post-stop buffer returns an empty batch. Final drain is bounded to
24 seconds; missing/invalid/stale/closed/timeout ports emit explicit
INTERRUPTED events. The diagnostic rate window (32 replies/5 seconds per
port) is separate from ordinary search commands (12/5s) and cannot consume
their rate budget. A stop ACK, closed file or queue DONE is never proof that
a website returned all results. On timeout, local recording still stops with
a partial reason; loss/interruption counters are recorded in the final footer.
The main thread does not perform JSONL serialization or file reading.

## Single user-triggered export

The **↥ Raport** button is enabled only after the exact completed recorder's
writer has finished. Another confirmation warns about potentially sensitive
URLs, titles, page metadata and recipient access. Only on user approval
does Android ACTION_SEND chooser receive a short-lived content URI with
FLAG_GRANT_READ_URI_PERMISSION.

`VideoSearchDiagnosticReportProvider` is NOT exported, has grantUriPermissions
true and rejects non-read operations. It is a custom, read-only stream provider
using ParcelFileDescriptor.createPipe; it writes NO cache/export file.
Normal reports are streamed from the existing noBackup JSONL only after
canonical-path/size checks. **Private reports are streamed directly from the
recorder's RAM buffer**, never via FileProvider, clipboard, plain Binder
string or temporary filesystem. For private streams, ownership is verified
against the active ProfileManager mode, exact private registry lifetime AND
a currently present tab on every read segment. Once private lifetime expires,
the RAM buffer is retired and further URI reads fail. A new export token
invalidates the previous one; access expires in 120s even if URI grant remains.
The receiving application may keep a copy once the user explicitly chooses
it; Zavro cannot revoke that external copy.

The export consists of newline-delimited JSON: first an export_manifest
(schemaVersion=1, sessionId, private flag, fileState, attempted, recorded,
dropped, interruptions, incomplete, failure, local-only disclaimer),
then existing schema-v1 recorder lines. There are no device-PASS or site-PASS
claims derived from a complete file alone. A missing/corrupt session_end or
any interruption/loss makes manifest incomplete=true. The existing recorder
limits are max 20,000 events, 8 MiB, 45 min; per-profile normal retention
remains four files/seven days. Sharing creates no new persistent file.

## Tests, safety and limits

- Robolectric/JVM: explicit share grants only for finished recorders;
  expiry, wrong token, revoke, private retirement/clearing, privacy in
  export_manifest, interruptions and clean-local manifest contract.
- Pure JVM: native stop/drain state machine with STOP ACK before drain,
  nonempty/empty final batch, rejected/stale ACK and timeout fail-closed.
- JavaScript structural regression: both My Sites panels have controls,
  Android provider is non-exported/read-only, no network upload, chooser
  requires affirmative click and URI read grant.
- Existing full JS, Kotlin/Robolectric, AndroidTest Kotlin compile,
  Android lint+baseline and process-docs/error-learning CI must remain
  SUCCESS on the same final exact source SHA. PR build/release SKIPPED.

NOT_TESTED: real Nothing Phone (1), Android content chooser permissions,
external app recipient compatibility, live GeckoView and sites, exact CPU/RAM
overhead or private background/rotation behavior on a physical device.
The private RESULTS VIEW per-site saved→displayed funnel from stage 4 is
still not present; this is a known coverage gap for stages 6/7. A synthetic
green CI is not device verification. No APK was built or authorized.
