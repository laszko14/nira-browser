# Zavro Clean — stage 7: title correlation acceptance and measurement

This document is a reproducible test plan, not proof of on-device success.
Scope: Clean browser only. No MPV, UI redesign, APK build, or merge.

## Baseline and scope

Reference: v1175 VIDEO_DISCOVERY, 60 s; 8 navigations; 74 sources,
68 groups, 67 groups without a title; 52 sources observed by the panel,
1 with an individual title. These counts come from an earlier capture
and do not prove the names were correct or that tiles were drawn.

Stages 1–6 are carried forward. Stage 7 adds **frame-aware badge grouping**
and **anonymous group-level VIDEO_DISCOVERY coverage counters**. The new
counters are absent from v1175 and therefore have no baseline.

## Execution (after separate permission to compile/install an APK)

1. Build the Clean branch under its next versionCode with the existing
   signing identity. Do not replace either stable Zavro or Zavro x MPV.
2. Open the same test site and same set of cards as in the original v1175
   run. Prefer at least 12 independently titled videos and multiple
   qualities for selected videos. Record site/video particulars privately:
   they must not be included in any shared TestLab report.
3. Start **TestLab → VIDEO_DISCOVERY**, run the 60-second window, scroll
   across all cards, reveal the detected-film panel, and revisit one card
   after its title has appeared dynamically. Include an iframe and an
   SPA transition if the site offers them.
4. Export the TestLab text report. Retain only aggregate counters and
   synthetic local "Film #n" ordinals; no page URL, title, origin hostname,
   token, cookie, or authorization header.
5. Repeat a 60-second VIDEO_DISCOVERY run on the same site and compare
   independent-film titles, variant count, missing titles, contradictions,
   dropped work, frame mismatch, and CPU/jank indicators. Also perform
   COMPLEX 60 s only when evaluating performance regressions.

## Compare these measurements

- "Zaobserwowane źródła", "grupy", "Brak tytułu" and "konflikty grup".
- Historical per-source values: "Przekazane panelowi źródła" and
  "z indywidualnym tytułem" (not a count of named tiles).
- **New**: "Grupy przekazane panelowi", "z jednoznacznym tytułem",
  "bez jednoznacznego tytułu", "konfliktowe". These are observed
  group snapshots across the measurement, not proof of rendered pixels,
  not proof of textual correctness, and not directly comparable to the
  historical per-source numerator.
- Frame/local group isolation and exact HLS/DASH signatures:
  check that different media do not get the same title or tile.
- URL/manifest observations must preserve exact playback addresses.
  Two opaque token variants may remain separate without verified lineage.

## Acceptance and limits

- Every visibly different film on the reference page keeps a unique and
  correct title when the site exposes unambiguous local metadata.
- All documented qualities of the same movie may belong to one tile;
  different iframe/player/playlist items must not mix.
- Late title upgrades preserve the source/tile identity.
- Generic "Play", "720p", and page-wide headers are not individual titles.
- No leaked URL/title/token/hostname in exported diagnostics.
- Stable browsing and no obvious new 120 Hz scroll regression.
- "COMPLETED" means the measurement ended, not that these conditions
  were satisfied. Website accuracy and real frame pacing still need
  device validation.

## Automated coverage included in stage 7

- 7 native synthetic integration fixtures covering 12 movies/24 variants,
  iframe ownership, player-priority upgrades, same-rank title conflict,
  page-only titles, late attribution, and opaque signed tokens.
- 2 additional native ledger regression fixtures.
- Existing JS title/playlist fixtures: 26/26 passed under an isolated
  V8 test runner. This is not a standard Node suite or Android/Gradle
  test execution; no APK was built.
