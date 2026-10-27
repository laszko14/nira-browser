# Zavro Clean — MME ETAP 2: Media Identity Graph (shadow mode)

**Base:** MME ETAP 1, `d5f0f485d8eb9414225cef6b2db2250d55b1f291`, PR #98, derived from v1182.  
**Purpose:** compare provisional media-owner/film/source/variant relationships without changing the application behavior.

## Actual integration points

- `MediaRepository.add()` continues to own accepting, rejecting, normalizing and deduplicating media records. No new URL normalization.
- `TestLabMediaObserver.entry()` mirrors accepted `MediaEntry` snapshots to `MediaIdentityShadowGraph` only when video TestLab is active.
- `TestLabMediaObserver.navigation()` invalidates correlation epoch for that browser EngineSession; `scope()` also detects profile/private/tab partition changes.
- `MediaFloatingUi.showSheet()` submits a **diagnostic-only** panel comparison of the current unmodified `MediaEntry` list. Actual tile grouping and playback are left untouched.
- `TestLabReportGenerator` exports **aggregates only**, with a labelled SHADOW MODE section. JSON field `mediaIdentityShadow` has a default null for older reports.

## Correlation graph and invariants

- Owner key: native EngineSession object identity (not page-supplied tab ID), a local scope serial and document/navigation epoch.
- Film candidate: (owner serial, epoch, frameKey, **opaque parser group ID**), or per-stream record ID when group ID is missing.
- Stream/source node: native `MediaEntry.id` scoped to owner + epoch. Distinct source records do **not** become one film merely because a title, page URL, hostname or stripped query parameter matches.
- Provisional rendition/variant edges: count of additional sources in each group; these are *not proof* that all renditions play the same movie.
- Cross-frame reused group IDs are detected but are **not combined**.
- Conflicting specific titles at the same highest rank are flagged; page-wide titles never count as individual titles. A title conflict does not trigger a forced merge or automatic rename.
- A source that changes its group or frame is flagged; a stronger later mapping is **not silently forced into another film in shadow mode**. The existing production MediaRepository may update its own group independently, unmodified.
- No confirmed per-film audio/subtitle tracks are available in `MediaEntry`; linked track counts are marked unavailable (zero is not evidence that the film has no audio/subtitles).
- When no frame key is available, a multi-source group is flagged as tentative.

## Comparison with current UI

`panelSnapshotsCompared` counts panel openings with available entries. `panelGroupCountDisagreements` counts snapshots where film-node counts differ from existing `VideoTilesPanel` grouping key. `panelShadowUnsafeGroups` counts panels' provisional multi-source groups with ambiguous frame owner or mismatched individual titles. It does not validate pixels or final player playback.

Current UI groups by `(frameKey, groupId ?: exactUrl)`; shadow uses `(session,epoch,frameKey,groupId ?: sourceRecordId)`. Comparing different keys is intentional: possible false merges due to identical anonymous URLs should become measurable. **Do not treat count agreement as proof of correctness.**

## Privacy and runtime cost

- Scope, frame and group identity and raw title hints live **only in bounded, private process memory** while the test is active. No raw titles/URLs/hosts/cookies/tokens/session IDs are exported.
- Bounded up to 256 unique sources and 256 EngineSession scopes; excess sources increment `droppedSources`, rather than being recorded.
- TestLab inactive: a volatile check returns without allocation or traversal.
- No new network probes, cookies, auth, manifest requests, WebExtensions changes, video autoplay or other page read.
- No shared disk cache; `clear()` releases all session references at end of test.
- Runtime snapshot aggregates are computed on finalization, not at each frame. Comparison is limited to panel opening; avoid new expensive scans inside the frame path.
- 120 Hz policy, application ID and current update signing are unmodified.

## Acceptance tests

JUnit must pass for:
- group variants in same frame,
- same group ID across different iframe frames (no merge),
- conflicting individual titles,
- stronger individual titles versus weaker page-only titles,
- 30/50 independent videos all having identical PAGE_ONLY title,
- navigation and private/profile partition switch,
- two separate EngineSessions with same stream IDs,
- anonymous duplicate URLs (compare shadow vs legacy),
- tentative null frame owner and manifest variant count hints,
- source reassignment,
- bounded storage and private information not present in JSON,
- full TestLab report JSON+TXT with shadow semantics.

## Open risks / stage 3 prerequisites

This graph mirrors already accepted records. It does **not** see rejected page candidates, read new titles or solve the 0/57 individual-title problem. In Stage 3, add evidence at extraction and transport boundaries and improve per-card title correlation using the graph's provisional identity rules. Confirm any title relation against DOM/player-specific evidence; never inherit `document.title` on many films.

Any future promotion of the shadow graph to production must have explicit approval, dedicated variant/source rules, site fixtures, device comparison and rollback. No silent switch from old UI grouping.

## Build policy

NO signed APK build without separate user approval. For this stage's draft PR, CI's `build` job is skipped by an exact head-branch guard; run JUnit/Node/lint only. `versionCode`, `versionName` and signing certificate remain at the v1182 baseline until an approved update build.
