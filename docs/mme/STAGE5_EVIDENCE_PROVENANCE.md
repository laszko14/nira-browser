# Zavro Clean — MME ETAP 5: poster evidence, provenance and conflicts

**Baseline:** v1182 → stages 1–4, PR #101, base commit `d0ddc0a7094c2593b4c6973104a5c4a3073ed735`. Stage 5 is additive; no merge and no approved APK build.

## Root cause

Native `MediaRepository.Record.metadata()` used `candidate.titleKind` to rank `candidate.posterUrl`. This conflated **the confidence that a title belongs to a video** with **the provenance of the thumbnail image**. Even when a video already exposed its own poster, an unrelated higher-ranked title could indirectly lock out the correct poster.

## Implementation

### Provenance transport

- An OPTIONAL `posterKind` field is added alongside `posterUrl` to the existing version-1 `media_candidate` IPC protocol. Accepted only for DOM/PAGE, only with `posterUrl` and an allowlisted value:
  - `element`: explicit HTML video poster attribute;
  - `card`: local verified media-card poster/thumbnail;
  - `player`: static player configuration of a specific media item;
  - `structured`: JSON-LD VideoObject linked to a direct video URL.
- Message origin is **a validated hint, not independent proof**; no website text, raw selectors or arbitrary JSON are accepted as provenance.
- The existing `background.js` extension-only sender and frame/document checks stay in force, with strict key equality and parser validation in native Kotlin.
- Older valid version-1 messages lacking `posterKind` remain supported: `LEGACY` class.

### Native conflict model

- `MediaPosterResolver.State` ranks `VIDEO_ELEMENT > LOCAL_CARD > PLAYER_CONFIG > STRUCTURED_VIDEO > LEGACY`, independent from title.
- Same-rank distinct URLs give `posterUrl=null`, `posterAmbiguous=true`, and preserve origin as diagnostics only.
- Repeated same-rank URL is a no-op; weaker evidence never overrides a stronger poster; stronger evidence may resolve a conflict.
- URL validation continues in `MediaMessageProtocol`, owner checks in `MediaRepository`; the exact stream and image URLs are never normalized or written to TestLab.
- The resolver is **per native media record**, scoped to existing EngineSession, frame and navigation controls; no changes to tile grouping or player handoff.
- App-level `MediaEntry` has backward-compatible defaults for `posterOrigin` and `posterAmbiguous`. No persistence/database changes.

### TestLab

- `VideoMetadataLedger` counts selected poster origins and unresolved same-rank conflicts; only closed enum-like origin strings and integer counts appear in TXT/JSON.
- Existing duration/size provenance (`HTML5`, `HLS_EXTINF`, `DASH_MPD`, `HTTP_LENGTH`, `HLS_BYTERANGE`, `MANIFEST_BITRATE`) is kept unchanged. `EXACT`, `ESTIMATED`, `UNKNOWN` and `LIVE` remain distinguished. This stage **does not** add new probes or claim extra precision.
- Existing schema uses defaulted optional summary fields to keep older report deserialization compatible.

## Safety and limits

- Avoid claiming verified poster ownership from the ranking alone; the source is only scoped evidence.
- No image fetching, screenshot scanning, user history retrieval, DRM bypass, background web requests, network probing or player changes.
- No propagation of a poster from an ancestor gallery or parent cross-origin iframe to an unrelated stream.
- Existing WebExtension metadata sources and titles remain untouched outside adding explicit poster kind.
- Cap and performance of existing DOM scans, native queues and TestLab unchanged; preserve 120 Hz.
- No signed APK, no versionCode or certificate mutation, no main merge. Build job is explicitly skipped on Stage-5 PR.

## Verification matrix

1. direct HTML-video poster (untitled video) → `element`;
2. 50 local cards → `card` scoped by owner;
3. VideoObject thumbnail/identical ImageObject choices → `structured`;
4. static player playlist item image → `player`;
5. malformed, spoofed, numeric or network-origin posterKind → reject;
6. missing `posterKind` in legacy message → accept as lowest rank;
7. conflicting same-strength images → UNKNOWN instead of arbitrary first;
8. stronger element evidence resolves an earlier card conflict;
9. stronger evidence cannot be replaced by later weak metadata;
10. accepted native title rank does not influence poster rank;
11. no raw URLs, tokens, image paths, hosts, title or profile IDs in anonymous TestLab export;
12. native document/frame/private profile isolation and existing signed-URL invariants unchanged;
13. existing Android Kotlin tests and Node WebExtensions regressions pass, lint completes.

## Unfinished work

The next controlled stage should improve correlation of passive network media records to already verified DOM player evidence and collect more explicit missing-metadata reasons without associating based on a shared hostname, page title or guessed URL. Report real-site improvement only after the separately approved signed APK and FULL_AUDIT on device.

**STOP after ETAP 5 CI. Proceed only on user `Dalej`.**
