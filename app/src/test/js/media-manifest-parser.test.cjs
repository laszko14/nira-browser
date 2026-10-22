const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const source = fs.readFileSync(path.join(__dirname,
  "../../main/assets/extensions/nira-media-detector/manifest-parser.js"), "utf8");
const context = { URL };
vm.runInNewContext(source, context);
const parser = context.NiraManifestParser;

test("HLS master groups bounded variants and resolves relative URLs", () => {
  const text = `#EXTM3U
#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",LANGUAGE="pl",NAME="Polski",URI="audio/pl.m3u8"
#EXT-X-STREAM-INF:BANDWIDTH=2800000,RESOLUTION=1920x1080,CODECS="avc1.640028,mp4a.40.2",AUDIO="audio"
1080/index.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=900000,RESOLUTION=854x480
480/index.m3u8`;
  const result = parser.parseHls(text, "https://cdn.example/master/main.m3u8", 8);
  assert.equal(result.kind, "master");
  assert.equal(result.variants.length, 2);
  assert.equal(result.variants[0].url, "https://cdn.example/master/1080/index.m3u8");
  assert.equal(result.variants[0].height, 1080);
  assert.equal(result.variants[0].bitrate, 2800000);
  assert.equal(result.audioTracks[0].language, "pl");
  assert.equal(result.audioTracks[0].url, "https://cdn.example/master/audio/pl.m3u8");
});

test("HLS media playlist reports duration without treating segments as films", () => {
  const text = "#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:5.5,\na.ts\n#EXTINF:4.25,\nb.ts\n#EXT-X-ENDLIST";
  const result = parser.parseHls(text, "https://cdn.example/vod/list.m3u8", 8);
  assert.equal(result.kind, "media");
  assert.equal(result.variants.length, 0);
  assert.equal(result.durationSeconds, 10);
});

test("DASH parser extracts bounded video representations and separate audio", () => {
  const xml = `<?xml version="1.0"?><MPD type="static" mediaPresentationDuration="PT1H2M3S">
    <BaseURL>https://cdn.example/root/</BaseURL><Period>
      <AdaptationSet mimeType="video/mp4" codecs="avc1.640028"><Representation id="v1" bandwidth="2500000" width="1920" height="1080"><BaseURL>video/1080.mp4</BaseURL></Representation></AdaptationSet>
      <AdaptationSet mimeType="audio/mp4" lang="pl"><Representation id="a1" bandwidth="128000"><BaseURL>audio/pl.m4a</BaseURL></Representation></AdaptationSet>
    </Period></MPD>`;
  const result = parser.parseDash(xml, "https://cdn.example/manifest.mpd", 8);
  assert.equal(result.dynamic, false);
  assert.equal(result.durationSeconds, 3723);
  assert.equal(result.variants[0].url, "https://cdn.example/root/video/1080.mp4");
  assert.equal(result.variants[0].height, 1080);
  assert.equal(result.audioTracks[0].language, "pl");
});

test("DASH parser decodes XML entities in signed BaseURL values", () => {
  const xml = `<MPD><Period><AdaptationSet mimeType="video/mp4"><Representation height="720"><BaseURL>video.mp4?token=a&amp;signature=b</BaseURL></Representation></AdaptationSet></Period></MPD>`;
  const result = parser.parseDash(xml, "https://cdn.example/path/manifest.mpd", 8);
  assert.equal(result.variants[0].url,
    "https://cdn.example/path/video.mp4?token=a&signature=b");
});

test("manifest parsers reject oversized input and unsafe schemes", () => {
  assert.throws(() => parser.parseHls("x".repeat(524289), "https://cdn.example/a.m3u8", 8));
  const result = parser.parseHls("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nfile:///secret", "https://cdn.example/a.m3u8", 8);
  assert.equal(result.variants.length, 0);
});
