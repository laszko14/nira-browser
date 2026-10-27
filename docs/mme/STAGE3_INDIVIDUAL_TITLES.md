# Zavro Clean MME — ETAP 3: per-film DOM caption correlation

Baseline: Stage 2 PR #99 (`1bd22b8c3b42524d29bc86885129ca72dd58fcf0`), based on v1182. This stage does **not** turn on shadow graph decisions in production.

## Confirmed gap

`content.js:scopedMediaCard()` only accepted one title element matching `figcaption,[itemprop=name],h1..h4` inside a narrowly recognized card. Many actual media grids use `span.video-title`, `.card-title`, `.video-caption` or `data-video-title` adjacent to a `video` element or a direct media link, and no h-tags. Those produced anonymous detected URLs despite available per-film captions.

## Implementation

- Add bounded `cardTitle(card)` to content script (no network access). For a verified local owner accept `data-video-title` or `data-title` on its card, or a **single consistent** nearby local caption among CSS classes, `figcaption`, `itemprop=name`, headings. Search is capped at 16 nodes.
- Add explicit locally verifiable card selectors for `.video-tile`, `.video-entry`, `.playlist-item`, `[data-media-id]`, with the previous card selectors preserved.
- Continue refusing to associate when the enclosing card contains multiple players, or more than one distinct candidate caption. Direct media links require the pre-existing explicit-media-URL guard and ownership checks.
- Extend mutation rescan target to the same caption nodes, so late SPA/DOM text changes update the proper candidate. All existing rate limits, queue bounds and dedup continue to apply.
- The complete candidate is transmitted only via the existing validated extension→Gecko→native protocol. Title resolution still uses `MediaTitleResolver` with per-source rank and conflict logic.
- Do **not** assign `document.title` to unrelated media, infer a stream URL from thumbnail navigation, or associate parent-page iframe heading with opaque child streams without independent cross-frame proof.
- Leave `full-titles.js` presentational; the new per-film source attribution is within `content.js`.
- Keep original media URL, query, cookies, stream and playback unchanged.

## Test criteria

- 30 and 50 independent media cards and 50 independent direct media links with CSS captions remain separately titled, with separate group keys.
- A global site heading does not become each film's title.
- Ambiguous conflicting local captions yield UNKNOWN rather than assigning wrong text.
- Two players in one article do not inherit one caption.
- Late caption mutations queue the matching card only.
- Retain old `individual-titles-v1175.test.cjs` coverage for scoped HTML heading, JSON-LD `VideoObject`, manifest links, iframe-owned local captions, prioritization, and signed stream URL invariants.

## Limitations

Title extraction is improved only on sites with accessible local declarative DOM evidence. This is **not** proof that v1182's real-site 0/57 issue is resolved; repeat physical-device FULL_AUDIT after a separately approved APK build. Parent document card titles cannot safely identify cross-origin iframe streams without a stronger verified association protocol (future stage). Network-only sources can still have no title; unknown is correct when no evidence is available.

## Delivery

PR is draft, no main merge and no APK build. On this PR, CI `build` job is explicitly skipped; Kotlin unit tests, JS regression tests and lint are permitted. Next stages (post approval) cover thumbnail correlation and stronger iframe/player identity, without broad site-specific guesses.
