# Zavro x MPV v1144 — bounded blob/MSE matching

## Report from v1143
Gecko probe ACK, gesture and video_play succeed; source_blob emits and AUTO/HANDOFF is empty. No libmpv activity. libmpv already proved HLS playback in v1142; block is element-to-network matching.

## Implementation
- Gecko sends an AUTO blob *identity offer* after a real gesture on one visible, unmuted, non-DRM, long-running HTML5 element with sane geometry; no blob URL is used by MPV.
- Native requires a unique non-expired network-observed HLS playlist from the same frame URL and currently selected, real Gecko EngineSession. No brute forcing segment URLs, no DRM bypass, no downloading blobs/cookies.
- Native pause request is bound to the exact frame + element and confirmed via nonce ACK. Fail closed for ambiguity and stale frames.
- A child iframe that fills its iframe viewport may request top-document iframe geometry, but only from an authenticated top-level content port and exact unique iframe.src match; scroll/resize updates bound to the lease. Otherwise remain Gecko.
- Single 40-second Supertest includes blob candidate lookup/ambiguity/frame resolution allowlisted markers; no URLs in copied report.
- v1144 same package/signature, ARM64 versionCode 1144, Draft PR only, no merge. Physical CDA validation required.
