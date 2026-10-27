const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

function detector(fetchManifest) {
  let before, received;
  const routed = [];
  const browser = {
    runtime: {
      id: "test-extension",
      onMessage: { addListener(fn) { received = fn; } },
      sendNativeMessage: async () => true,
    },
    tabs: { sendMessage: async (id, msg) => { routed.push(msg); return true; } },
    webRequest: {
      onBeforeRequest: { addListener(fn) { before = fn; } },
      onHeadersReceived: { addListener(fn) { detector.onHeaders = fn; } },
    },
  };
  const ctx = { browser, URL, TextEncoder, TextDecoder, AbortController, Date,
    setTimeout: () => 1, clearTimeout: () => {}, fetch: fetchManifest };
  const base = path.join(__dirname, "../../main/assets/extensions/nira-media-detector");
  vm.runInNewContext(fs.readFileSync(path.join(base, "manifest-parser.js"), "utf8"), ctx);
  vm.runInNewContext(fs.readFileSync(path.join(base, "background.js"), "utf8"), ctx);
  const sender = { id: "test-extension", frameId: 0, tab: { id: 5 },
    url: "https://page.test/watch" };
  return {
    routed,
    start: () => received({ schemaVersion: 1, event: "document_started",
      pageUrl: "https://page.test/watch" }, sender),
    response(url, requestId, contentType = "application/vnd.apple.mpegurl") {
      const details = { tabId: 5, requestId, frameId: 0, url, incognito: false,
        statusCode: 200, responseHeaders: [{ name: "Content-Type", value: contentType }] };
      before(details);
      detector.onHeaders(details);
    }
  };
}
const tick = () => new Promise(resolve => setImmediate(resolve));

test("arbitrary s and t manifest query values must not share one film group", async () => {
  const d = detector();
  await d.start();
  const a = "https://cdn.test/hls/index.m3u8?s=100&t=A";
  const b = "https://cdn.test/hls/index.m3u8?s=243&t=B";
  d.response(a, "a");
  d.response(b, "b");
  await tick();
  assert.equal(d.routed.length, 2);
  assert.equal(d.routed[0].url, a);
  assert.equal(d.routed[1].url, b);
  assert.notEqual(d.routed[0].groupId, d.routed[1].groupId);
  assert.equal(d.routed[1].provenance, "observed");
});

test("fully formed AWS SigV4 can keep one group with exact renewed URLs", async () => {
  const d = detector();
  await d.start();
  const root = "https://cdn.test/hls/index.m3u8?video=1&" +
    "X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=account%2Fregion&" +
    "X-Amz-SignedHeaders=host&";
  const a = root + "X-Amz-Date=20261027T100000Z&X-Amz-Expires=300&X-Amz-Signature=AAA";
  const b = root + "X-Amz-Date=20261027T110000Z&X-Amz-Expires=900&X-Amz-Signature=BBB";
  d.response(a, "a");
  d.response(b, "b");
  await tick();
  assert.equal(d.routed.length, 2);
  assert.equal(d.routed[0].url, a);
  assert.equal(d.routed[1].url, b);
  assert.equal(d.routed[0].groupId, d.routed[1].groupId);
  assert.ok(d.routed[1].requestSequence > d.routed[0].requestSequence);
});

test("token, signed credential, media identity and duplicate fields remain significant", async () => {
  const d = detector();
  await d.start();
  const root = "https://cdn.test/hls/index.m3u8?X-Amz-Algorithm=AWS4-HMAC-SHA256&" +
    "X-Amz-Credential=account%2Fregion&X-Amz-SignedHeaders=host&X-Amz-Signature=AAA";
  const urls = [
    "https://cdn.test/hls/index.m3u8?token=first",
    "https://cdn.test/hls/index.m3u8?token=second",
    root + "&video=A",
    root + "&video=B",
    root.replace("account%2Fregion", "different%2Fregion") + "&video=A",
    root + "&video=A&x-amz-signature=duplicate"
  ];
  urls.forEach((url,i) => d.response(url, "req" + i));
  await tick();
  assert.equal(d.routed.length, urls.length);
  assert.equal(new Set(d.routed.map(x=>x.groupId)).size, urls.length);
  assert.deepEqual(d.routed.map(x=>x.url), urls);
});

test("inspected HLS children are marked derived and never inherit master query", async () => {
  const fetchManifest = async () => {
    const bytes = new TextEncoder().encode("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000\nindex.m3u8");
    return { ok: true, url: "https://cdn.test/final/master.m3u8?token=master",
      headers: { get: () => null }, body: { getReader: () => {
        let done = false;
        return { read: async () => done ? { done: true } :
          (done = true, { done: false, value: bytes }), cancel: async () => {} };
      } } };
  };
  const d = detector(fetchManifest);
  await d.start();
  d.response("https://cdn.test/master.m3u8?token=old", "old");
  await tick(); await tick();
  assert.equal(d.routed.length, 2);
  assert.equal(d.routed[0].provenance, "observed");
  assert.equal(d.routed[1].provenance, "derived");
  assert.equal(d.routed[1].url, "https://cdn.test/final/index.m3u8");
});

test("DASH MPD does not emit BaseURL child as a fake video", async () => {
  const fetchManifest = async () => {
    const bytes = new TextEncoder().encode(
      '<MPD><Period><AdaptationSet mimeType="video/mp4"><Representation height="720">' +
      '<BaseURL>chunk.mp4</BaseURL></Representation></AdaptationSet></Period></MPD>');
    return { ok: true, url: "https://cdn.test/manifest.mpd",
      headers: { get: () => null }, body: { getReader: () => {
        let done = false; return { read: async () => done ? { done: true } :
          (done = true, { done: false, value: bytes }), cancel: async () => {} };
      } } };
  };
  const d = detector(fetchManifest);
  await d.start();
  d.response("https://cdn.test/manifest.mpd", "mpd", "application/dash+xml");
  await tick(); await tick();
  assert.equal(d.routed.length, 2);
  assert.equal(d.routed[0].url, "https://cdn.test/manifest.mpd");
  assert.equal(d.routed[1].url, d.routed[0].url);
  assert.equal(d.routed[1].verification, "manifest_verified");
});
