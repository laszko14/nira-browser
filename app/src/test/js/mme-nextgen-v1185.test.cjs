const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

function parser() {
  const src = fs.readFileSync(path.join(__dirname,
    "../../main/assets/extensions/nira-media-detector/manifest-parser.js"), "utf8");
  const scope = { URL };
  vm.runInNewContext(src, scope, { timeout: 1000 });
  return scope.NiraManifestParser;
}

test("v1185 HLS trick-play is never presented as a normal movie rendition", () => {
  const body = [
    "#EXTM3U",
    "#EXT-X-STREAM-INF:BANDWIDTH=2000000,RESOLUTION=1280x720,CODECS=\"avc1.640028\"",
    "720.m3u8",
    "#EXT-X-I-FRAME-STREAM-INF:BANDWIDTH=300000,RESOLUTION=320x180,URI=\"iframes.m3u8\"",
    "#EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID=\"subs\",NAME=\"Polski\",LANGUAGE=\"pl\",URI=\"pl.m3u8\""
  ].join("\n");
  const p = parser().parseHls(body, "https://cdn.example/master.m3u8?token=secret", 8);
  assert.equal(p.kind, "master");
  assert.equal(p.variants.length, 1);
  assert.equal(p.variants[0].height, 720);
  assert.equal(p.trickplayTracks.length, 1);
  assert.equal(p.trickplayTracks[0].height, 180);
  assert.equal(p.trickplayTracks[0].role, "trickplay");
  assert.equal(p.subtitleTracks.length, 1);
  assert.equal(p.durationSeconds, null);
});

test("v1185 HLS exact size requires complete byterange VOD", () => {
  const body = [
    "#EXTM3U", "#EXTINF:5.0,", "#EXT-X-BYTERANGE:100@0", "single.ts",
    "#EXTINF:4.0,", "#EXT-X-BYTERANGE:200@100", "single.ts",
    "#EXT-X-ENDLIST"
  ].join("\n");
  const p = parser().parseHls(body, "https://cdn.example/vod.m3u8", 8);
  assert.equal(p.durationSeconds, 9);
  assert.equal(p.exactSizeBytes, 300);
  assert.equal(p.live, false);
  const live = parser().parseHls(body.replace("#EXT-X-ENDLIST", ""),
    "https://cdn.example/live.m3u8", 8);
  assert.equal(live.live, true);
  assert.equal(live.exactSizeBytes, null);
  assert.equal(live.durationSeconds, null);
});

test("v1185 DASH dynamic keeps video, audio and subtitles separate", () => {
  const xml = [
    '<MPD type="dynamic"><Period duration="PT9S">',
    '<AdaptationSet contentType="video" width="1920" height="1080">',
    '<Representation id="v1" bandwidth="4000000"></Representation>',
    '</AdaptationSet>',
    '<AdaptationSet contentType="audio" lang="pl">',
    '<Representation id="a1" bandwidth="128000"></Representation>',
    '</AdaptationSet>',
    '</Period></MPD>'
  ].join("");
  const p = parser().parseDash(xml, "https://cdn.example/movie.mpd", 8);
  assert.equal(p.live, true);
  assert.equal(p.durationSeconds, null);
  assert.equal(p.variants.length, 1);
  assert.equal(p.variants[0].role, "video");
  assert.equal(p.variants[0].height, 1080);
  assert.equal(p.audioTracks.length, 1);
  assert.equal(p.audioTracks[0].role, "audio");
  assert.equal(p.trickplayTracks.length, 0);
});
