const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const path = require("node:path");
const context = { URL };
vm.runInNewContext(fs.readFileSync(path.join(__dirname,
  "../../main/assets/extensions/nira-media-detector/manifest-parser.js"), "utf8"), context);
const parser = context.NiraManifestParser;
const base = "https://cdn.example/master.m3u8";

test("VOD HLS sums EXTINF only after ENDLIST (no live duration guessing)", () => {
  const vod = parser.parseHls("#EXTM3U\n#EXTINF:4.50,\na.ts\n#EXTINF:5.75,\nb.ts\n#EXT-X-ENDLIST",base,8);
  assert.equal(vod.durationSeconds,11);
  assert.equal(vod.live,false);
  const live = parser.parseHls("#EXTM3U\n#EXTINF:4.50,\na.ts\n#EXTINF:5.75,\nb.ts",base,8);
  assert.equal(live.durationSeconds,null);
  assert.equal(live.live,true);
});
test("master keeps per-variant bandwidth separate from missing global duration", () => {
  const master=parser.parseHls('#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=2400000,AVERAGE-BANDWIDTH=1800000,RESOLUTION=1920x1080\n1080.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=900000,RESOLUTION=854x480\n480.m3u8',base,8);
  assert.equal(master.durationSeconds,null);
  assert.equal(master.variants.length,2);
  assert.equal(master.variants[0].bitrate,1800000);
});
test("DASH static period duration and single representation A/V bitrate", () => {
  const mpd='<MPD type="static"><Period duration="PT1M30S"><AdaptationSet contentType="video"><Representation bandwidth="1500000"><BaseURL>v.mp4</BaseURL></Representation></AdaptationSet><AdaptationSet contentType="audio"><Representation bandwidth="128000"><BaseURL>a.m4a</BaseURL></Representation></AdaptationSet></Period></MPD>';
  const result=parser.parseDash(mpd,"https://cdn.example/watch.mpd",8);
  assert.equal(result.durationSeconds,90);
  assert.equal(result.bitrate,1628000);
  assert.equal(result.live,false);
});
test("DASH dynamic never presents a fixed duration or total size estimate", () => {
  const mpd='<MPD type="dynamic" mediaPresentationDuration="PT1H"><Period><AdaptationSet contentType="video"><Representation bandwidth="700000"><BaseURL>v.mp4</BaseURL></Representation></AdaptationSet></Period></MPD>';
  const result=parser.parseDash(mpd,"https://cdn.example/watch.mpd",8);
  assert.equal(result.durationSeconds,null);
  assert.equal(result.live,true);
});
test("DASH multiple video quality variants yield no fabricated selected bitrate", () => {
  const mpd='<MPD type="static" mediaPresentationDuration="PT2M"><Period><AdaptationSet contentType="video"><Representation bandwidth="700000"><BaseURL>v1.mp4</BaseURL></Representation><Representation bandwidth="1700000"><BaseURL>v2.mp4</BaseURL></Representation></AdaptationSet></Period></MPD>';
  assert.equal(parser.parseDash(mpd,"https://cdn.example/watch.mpd",8).bitrate,null);
});

test("complete HLS byte ranges expose an exact size only when every segment is covered", () => {
  const playlist="#EXTM3U\n#EXTINF:5,\n#EXT-X-BYTERANGE:12345@0\na.ts\n#EXTINF:5,\n#EXT-X-BYTERANGE:23456@12345\na.ts\n#EXT-X-ENDLIST";
  const vod=parser.parseHls(playlist,base,8);
  assert.equal(vod.exactSizeBytes,35801);
  assert.equal(vod.durationSeconds,10);
  const withoutSecond=playlist.replace("#EXT-X-BYTERANGE:23456@12345\n","");
  assert.equal(parser.parseHls(withoutSecond,base,8).exactSizeBytes,null);
  const unfinalized=playlist.replace("#EXT-X-ENDLIST","");
  assert.equal(parser.parseHls(unfinalized,base,8).exactSizeBytes,null);
  const withInit=playlist.replace("#EXTINF:5,", '#EXT-X-MAP:URI="init.mp4"\n#EXTINF:5,');
  assert.equal(parser.parseHls(withInit,base,8).exactSizeBytes,null);
});
