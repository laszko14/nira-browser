const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const path = require("node:path");
const context = { URL };
vm.runInNewContext(fs.readFileSync(path.join(__dirname,
 "../../main/assets/extensions/nira-media-detector/manifest-parser.js"), "utf8"), context);
const p = context.NiraManifestParser;
const base = "https://cdn.example/base/watch.mpd";
test("DASH hierarchical BaseURL, inherited dimensions and fractional FPS", () => {
 const xml = '<MPD type="static" mediaPresentationDuration="PT40S"><BaseURL>root/</BaseURL><Period><BaseURL>p/</BaseURL>'+
 '<AdaptationSet contentType="video" width="1920" height="1080" frameRate="30000/1001" codecs="avc1.640028"><BaseURL>v/</BaseURL>'+
 '<Representation id="1" bandwidth="2000000"><BaseURL>movie.mp4</BaseURL></Representation></AdaptationSet>'+
 '<AdaptationSet contentType="video" width="640" height="360"><Representation id="2" bandwidth="400000"><BaseURL>low.mp4</BaseURL></Representation></AdaptationSet></Period></MPD>';
 const r=p.parseDash(xml,base,8);
 assert.equal(r.variants[0].width,1920);
 assert.equal(r.variants[0].height,1080);
 assert.equal(r.variants[0].frameRateRaw,"30000/1001");
 assert.equal(r.variants[0].codecs,"avc1.640028");
 assert.equal(r.variants[0].url,"https://cdn.example/base/root/p/v/movie.mp4");
 assert.equal(r.variants[1].width,640);
 assert.equal(r.variants[1].url,"https://cdn.example/base/root/p/low.mp4");
 assert.equal(r.bitrate,null);
});
test("DASH multiple periods are distinct; SegmentTemplate is not MP4", () => {
 const xml='<MPD type="static"><Period duration="PT10S"><AdaptationSet contentType="video" height="720">'+
 '<SegmentTemplate media="$Number$.m4s"/><Representation bandwidth="800000"><BaseURL>segments/</BaseURL></Representation>'+
 '</AdaptationSet></Period><Period duration="PT12S"><AdaptationSet contentType="video" height="1080">'+
 '<Representation bandwidth="1800000"><BaseURL>second.mp4</BaseURL></Representation></AdaptationSet></Period></MPD>';
 const r=p.parseDash(xml,base,8);
 assert.equal(r.periodCount,2);assert.equal(r.durationSeconds,22);
 assert.equal(r.variants[0].url,null);
 assert.equal(r.variants[0].periodIndex,0);
 assert.equal(r.variants[1].periodIndex,1);
 assert.equal(r.bitrate,null);
});
test("Reject DTD and malformed DASH", () => {
 for(const xml of ['<!DOCTYPE MPD [<!ENTITY x SYSTEM "file:///secret">]><MPD/>',
 '<MPD><Period></MPD>', '<MPD><Other/></MPD>']) assert.throws(()=>p.parseDash(xml,base,8));
});
test("HLS records bitrate kind, peak, average, FPS and rendition group", () => {
 const r=p.parseHls('#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=3000000,AVERAGE-BANDWIDTH=1900000,FRAME-RATE=59.94,'+
 'RESOLUTION=1920x1080,AUDIO="en",SUBTITLES="pl"\nvid.m3u8',
 'https://cdn.example/master.m3u8',8);
 assert.equal(r.variants[0].bitrateKind,"average");
 assert.equal(r.variants[0].peakBitrate,3000000);
 assert.equal(r.variants[0].averageBitrate,1900000);
 assert.equal(r.variants[0].frameRate,59.94);
 assert.equal(r.variants[0].subtitleGroup,"pl");
});

function audit60DashPeriods(durations, mpdAttrs = 'type="static"') {
  return '<MPD ' + mpdAttrs + '>' + durations.map(d => d === null ?
    '<Period/>' : '<Period duration="' + d + '"/>').join('') + '</MPD>';
}

test("audit60 17F: fractional DASH periods round only the combined total", () => {
  const three = p.parseDash(audit60DashPeriods(["PT0.4S", "PT0.4S", "PT0.4S"]), base, 8);
  const one = p.parseDash(audit60DashPeriods(["PT1.2S"]), base, 8);
  assert.equal(three.durationSeconds, 2, "three 0.4s periods total 1.2s, not 3s");
  assert.equal(three.durationSeconds, one.durationSeconds);
  assert.equal(p.parseDash(audit60DashPeriods(["PT0.25S", "PT0.25S",
    "PT0.25S", "PT0.25S"]), base, 8).durationSeconds, 1);
  assert.equal(p.parseDash(audit60DashPeriods(["PT10S", "PT12S"]), base, 8).durationSeconds, 22);
  assert.equal(p.parseDash(audit60DashPeriods(["PT0.001S", "PT0.001S",
    "PT0.001S"]), base, 8).durationSeconds, 1);
});

test("audit60 17F: accumulating many fractional DASH periods stays on an exact-second boundary", () => {
  const list = Array(1000).fill("PT0.001S");
  assert.equal(p.parseDash(audit60DashPeriods(list), base, 8).durationSeconds, 1);
});

test("audit60 17F: global mediaPresentationDuration keeps precedence", () => {
  const global = p.parseDash(audit60DashPeriods(["PT0.4S", "PT0.4S", "PT0.4S"],
    'type="static" mediaPresentationDuration="PT8.1S"'), base, 8);
  assert.equal(global.durationSeconds, 9);
  const missingPeriod = p.parseDash(audit60DashPeriods([null, "PT1S"],
    'type="static" mediaPresentationDuration="PT3.1S"'), base, 8);
  assert.equal(missingPeriod.durationSeconds, 4);
  const invalidGlobal = p.parseDash(audit60DashPeriods(["PT0.4S", "PT0.4S"],
    'type="static" mediaPresentationDuration="not-a-duration"'), base, 8);
  assert.equal(invalidGlobal.durationSeconds, 1, "invalid global duration falls back to complete periods");
});

test("audit60 17F: dynamic DASH never fabricates duration", () => {
  for (const attrs of [
    'type="dynamic"',
    'type="dynamic" mediaPresentationDuration="PT7S"'
  ]) assert.equal(p.parseDash(audit60DashPeriods(["PT0.4S", "PT0.4S"], attrs),
    base, 8).durationSeconds, null);
});

test("audit60 17F: missing, invalid and unsafe Period duration never yield partial time", () => {
  for (const list of [
    [null, "PT1S"],
    ["bogus", "PT1S"],
    ["PT0S", "PT1S"],
    ["PT-1S", "PT1S"],
    ["PT9007199254740990S", "PT9007199254740990S"]
  ]) assert.equal(p.parseDash(audit60DashPeriods(list), base, 8).durationSeconds,
    null, JSON.stringify(list));
  assert.equal(p.parseDash(audit60DashPeriods(["PT1S"],
    'type="static" mediaPresentationDuration="PT999999999999999999999999S"'),
    base, 8).durationSeconds, 1, "unsafe global duration falls back to safe Period");
});
