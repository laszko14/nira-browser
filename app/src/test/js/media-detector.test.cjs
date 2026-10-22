const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const assets = path.join(__dirname, "../../main/assets/extensions/nira-media-detector");

function background(route = async () => false) {
  let receive, probe, filter, extras;
  const native = [], timers = [], routed = [];
  const browser = {
    runtime: {
      id: "detector",
      onMessage: { addListener: callback => { receive = callback; } },
      sendNativeMessage: async (...args) => { native.push(args); return true; },
    },
    tabs: { sendMessage: async (...args) => {
      routed.push(args); return route(...args);
    } },
    webRequest: { onHeadersReceived: { addListener: (callback, options, info) => {
      probe = callback; filter = options; extras = info;
    } } },
  };
  vm.runInNewContext(fs.readFileSync(path.join(assets, "background.js"), "utf8"), {
    browser, TextEncoder, URL, setTimeout: callback => timers.push(callback),
    Date,
  });
  const flushTimers = () => { while (timers.length) timers.shift()(); };
  return { receive, probe, native, filter, extras, routed, flushTimers };
}
function response(url, contentType = "video/mp4", extra = {}) {
  return { url, statusCode: 200, tabId: 7, incognito: false,
    responseHeaders: contentType ? [{ name: "Content-Type", value: contentType }] : [],
    ...extra };
}
const tick = () => new Promise(resolve => setImmediate(resolve));
const candidate = url => ({ schemaVersion: 1, event: "media_candidate", source: "dom", url });
const sender = { id: "detector", tab: { id: 7 }, frameId: 0 };

function page() {
  const { receive } = background();
  let networkReceiver;
  const handlers = new Map();
  const winHandlers = new Map();
  const native = [], videos = [], timers = [];
  let observerCallback;
  const location = { href: "https://site.example/watch/1" };
  class HTMLVideoElement {
    constructor(url, mime = null) {
      this.currentSrc = url; this.src = url;
      this.paused = false; this.ended = false; this.isConnected = true;
      this.sources = mime ? [{ src: url, type: mime }] : [];
    }
    querySelectorAll(type) { return type === "source" ? this.sources : []; }
  }
  const add = (map, name, callback) => map.set(name, [...(map.get(name) || []), callback]);
  const document = {
    querySelectorAll: type => type === "video" ? videos : [],
    addEventListener: (name, callback) => add(handlers, name, callback),
  };
  const window = { addEventListener: (name, callback) => add(winHandlers, name, callback) };
  const browser = { runtime: {
    id: "detector",
    onMessage: { addListener: fn => { networkReceiver = fn; } },
    sendMessage: message => receive(message, sender),
    sendNativeMessage: async (...args) => { native.push(args); return true; },
  } };
  class MutationObserver {
    constructor(callback) { observerCallback = callback; }
    observe() {}
  }
  vm.runInNewContext(fs.readFileSync(path.join(assets, "content.js"), "utf8"), {
    browser, HTMLVideoElement, document, window, location, MutationObserver,
    setTimeout: callback => timers.push(callback),
  });
  const flush = () => new Promise(resolve => setImmediate(resolve));
  const fire = async (type, video, trusted = true) => {
    for (const callback of handlers.get(type) || []) callback({ type, target: video, isTrusted: trusted });
    await flush();
  };
  const navigate = async (url) => {
    location.href = url;
    for (const callback of winHandlers.get("popstate") || []) callback({});
    while (timers.length) timers.shift()();
    await flush();
  };
  const mutate = async () => {
    observerCallback([]);
    while (timers.length) timers.shift()();
    await flush();
  };
  return { HTMLVideoElement, videos, native, fire, navigate, mutate, location,
    onNetwork: (msg, from = { id: "detector" }) => networkReceiver(msg, from) };
}

test("background validates without changing signed URLs or trusting supplied IDs", async () => {
  const { receive } = background();
  const message = candidate("https://example.org/flower.mp4?signature=secret");
  assert.equal(JSON.stringify(await receive(message, sender)), JSON.stringify(message));
  assert.equal(await receive({ ...message, tabId: "spoofed" }, sender), null);
  assert.equal(await receive(message, { ...sender, id: "other-extension" }), null);
  assert.equal(await receive(message, { ...sender, frameId: 1 }), null);
  assert.equal(await receive(message, { id: "detector" }), null);
});

test("background accepts a bounded MIME hint but rejects forged or unsafe fields", async () => {
  const { receive } = background();
  const base = candidate("https://example.org/stream?token=secret");
  const message = { ...base, contentType: "video/webm; codecs=vp9" };
  assert.equal(JSON.stringify(await receive(message, sender)), JSON.stringify(message));
  for (const invalid of [
    { ...base, contentType: 123 }, { ...base, contentType: "video/mp4\nX: a" },
    { ...base, contentType: "a".repeat(129) }, { ...base, contextId: "forged" },
  ]) assert.equal(await receive(invalid, sender), null);
});

test("background rejects malformed messages and oversized or unsafe URLs", async () => {
  const { receive } = background();
  for (const message of [null, [], {}, { ...candidate("https://example.org/a"), schemaVersion: "1" },
    candidate(false), candidate("blob:https://example.org/a"), candidate("file:///a.mp4"),
    candidate("https://user:pass@example.org/a.mp4"), candidate("https://example.org/a b"),
    candidate("https://example.org/" + "a".repeat(8192)),
    candidate("https://example.org/" + "ą".repeat(4096))]) {
    assert.equal(await receive(message, sender), null);
  }
});

test("webRequest uses wildcard passive response headers and keeps public fixture probe", async () => {
  const { probe, native, filter, extras } = background();
  assert.deepEqual(Array.from(filter.urls), ["http://*/*", "https://*/*"]);
  assert.deepEqual(Array.from(extras), ["responseHeaders"]);
  probe(response("https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4"));
  probe(response("https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4"));
  await tick();
  assert.equal(native.filter(x => x[1].event === "network_probe").length, 1);
  assert.equal(native.every(x => !JSON.stringify(x).includes("flower.mp4")), true);
});

test("playing validates through background before session-bound native messaging; repeats suppressed", async () => {
  const p = page(), video = new p.HTMLVideoElement("https://example.org/flower.mp4?token=secret");
  p.videos.push(video);
  await p.fire("playing", video, false);
  assert.equal(p.native.length, 0);
  await p.fire("playing", video);
  assert.equal(p.native.length, 1);
  assert.equal(p.native[0][1].url, video.currentSrc);
  await p.fire("playing", video);
  assert.equal(p.native.length, 1);
});

test("two playing videos are not dropped by a global one-second debounce", async () => {
  const p = page();
  const first = new p.HTMLVideoElement("https://example.org/one.mp4");
  const second = new p.HTMLVideoElement("https://example.org/two.webm");
  p.videos.push(first, second);
  await p.fire("playing", first);
  await p.fire("playing", second);
  assert.equal(p.native.length, 2);
});

test("SPA navigation and source replacement emit updated sources without stale duplicates", async () => {
  const p = page(), video = new p.HTMLVideoElement("https://cdn.example.org/a.mp4");
  p.videos.push(video);
  await p.fire("playing", video);
  await p.navigate("https://site.example/watch/2");
  assert.equal(p.native.length, 2);
  video.currentSrc = "https://cdn.example.org/b.webm";
  video.src = video.currentSrc;
  await p.mutate();
  assert.equal(p.native.length, 3);
  assert.equal(p.native[2][1].url, "https://cdn.example.org/b.webm");
  await p.mutate();
  assert.equal(p.native.length, 3);
});

test("selected source MIME hint supports extensionless URLs and rejects blob", async () => {
  const p = page(), video = new p.HTMLVideoElement("https://cdn.example.org/play?sig=secret", "video/mp4");
  p.videos.push(video);
  await p.fire("playing", video);
  assert.equal(p.native[0][1].contentType, "video/mp4");
  video.currentSrc = "blob:https://cdn.example.org/opaque";
  video.src = video.currentSrc;
  video.sources = [];
  await p.mutate();
  assert.equal(p.native.length, 1);
});

test("network routes only to known tab and content handler passes to native session", async () => {
  const p = page();
  const b = background((tab, message) => p.onNetwork(message, { id: "detector" }));
  b.probe(response("https://cdn.example.net/watch?id=55&sig=secret", "video/mp4"));
  await tick(); await tick();
  assert.equal(b.routed.length, 1);
  assert.equal(b.routed[0][0], 7);
  assert.equal(b.routed[0][2].frameId, 0);
  assert.equal(p.native.length, 1);
  assert.equal(p.native[0][1].source, "network");
  assert.equal(p.native[0][1].url, "https://cdn.example.net/watch?id=55&sig=secret");
  assert.equal(b.native.length, 0);
});

test("network detects WebM HLS/DASH and skips segment floods", async () => {
  const b = background(async () => true);
  b.probe(response("https://site.example/movie.webm?x=1", "video/webm"));
  b.probe(response("https://site.example/master.m3u8?sig=a", "application/vnd.apple.mpegurl"));
  b.probe(response("https://site.example/manifest.mpd", "application/dash+xml"));
  for (let i = 0; i < 150; i++) {
    b.probe(response("https://site.example/segment-" + i + ".m4s", "video/mp4"));
    b.probe(response("https://site.example/frag_" + i + ".ts", "video/mp2t"));
  }
  b.flushTimers();
  await tick(); await tick();
  assert.equal(b.routed.length, 3);
  assert.equal(b.native.some(x => x[1].event === "network_segments" && x[1].count === 300), true);
  assert.equal(b.native.every(x => !JSON.stringify(x).includes("site.example")), true);
});

test("extensionless, query, MIME precedence and invalid MIME/HTML protection", async () => {
  const b = background(async () => true);
  b.probe(response("https://v.example/stream?token=private", "video/mp4"));
  b.probe(response("https://v.example/other.mp4", "video/webm"));
  b.probe(response("https://v.example/error.mp4", "text/html"));
  b.probe(response("https://v.example/bad.mp4", "video/mp4\\nAuthorization: X"));
  b.probe(response("https://v.example/not-video", "text/html"));
  b.probe(response("https://v.example/master.m3u8", null));
  await tick();
  assert.equal(b.routed.length, 3);
  assert.equal(b.routed[1][1].contentType, "video/webm");
  assert.equal(b.routed[2][1].url.endsWith("master.m3u8"), true);
});

test("uncertain tab, private mode and rejected routing never bind to active tab", async () => {
  const b = background(async () => false);
  b.probe(response("https://v.example/private.mpd", "application/dash+xml",
    { tabId: -1, incognito: true }));
  b.probe(response("https://v.example/public.mp4", "video/mp4"));
  b.probe(response("https://v.example/unknown.webm", "video/webm",
    { tabId: -1, incognito: undefined }));
  await tick(); await tick();
  const anonymous = b.native.filter(x => x[1].event === "network_unassigned");
  assert.equal(anonymous.length, 2);
  assert.equal(anonymous[0][1].isPrivate, true);
  assert.equal(anonymous[1][1].isPrivate, false);
  assert.equal(b.routed.length, 1);
  assert.equal(b.native.every(x => !JSON.stringify(x).includes("v.example")), true);
});

test("repeat responses are bounded; forged extension network messages are ignored", async () => {
  const p = page(), b = background(async () => true);
  for (let i = 0; i < 20; i++) b.probe(response("https://v.example/video.mp4?token=1"));
  await tick();
  assert.equal(b.routed.length, 1);
  assert.equal(await p.onNetwork({ schemaVersion: 1, event: "media_candidate",
    source: "network", url: "https://v.example/one.mp4" }, { id: "different" }), false);
  assert.equal(p.native.length, 0);
});
