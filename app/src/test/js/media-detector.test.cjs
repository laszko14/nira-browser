const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const assets = path.join(__dirname, "../../main/assets/extensions/nira-media-detector");

function background(route = async () => false, initialize = true, fetchImpl = undefined) {
  let receive, rawProbe, before, filter, extras;
  let requestId = 0;
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
    webRequest: {
      onBeforeRequest: { addListener: callback => { before = callback; } },
      onHeadersReceived: { addListener: (callback, options, info) => {
        rawProbe = callback; filter = options; extras = info;
      } },
    },
  };
  const context = { browser, TextEncoder, TextDecoder, URL, setTimeout: callback => timers.push(callback),
    clearTimeout: () => {}, Date, fetch: fetchImpl, AbortController };
  vm.runInNewContext(fs.readFileSync(path.join(assets, "manifest-parser.js"), "utf8"), context);
  vm.runInNewContext(fs.readFileSync(path.join(assets, "background.js"), "utf8"), context);
  if (initialize) receive({ schemaVersion: 1, event: "document_started",
    pageUrl: "https://site.example/watch/1" }, sender);
  const probe = details => {
    const request = { ...details, requestId: details.requestId ?? String(++requestId) };
    before(request);
    rawProbe(request);
  };
  const flushTimers = () => { while (timers.length) timers.shift()(); };
  return { receive, probe, before, rawProbe, native, filter, extras, routed, flushTimers };
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
  const { receive } = background(undefined, false);
  let networkReceiver;
  const handlers = new Map();
  const winHandlers = new Map();
  const native = [], videos = [], timers = [], extra = new Map();
  let observerCallback;
  const location = { href: "https://site.example/watch/1" };
  class HTMLVideoElement {
    constructor(url, mime = null, paused = false) {
      this.currentSrc = url; this.src = url;
      this.paused = paused; this.ended = false; this.isConnected = true;
      this.sources = mime ? [{ src: url, type: mime }] : [];
    }
    querySelectorAll(type) { return type === "source" ? this.sources : []; }
  }
  const add = (map, name, callback) => map.set(name, [...(map.get(name) || []), callback]);
  const document = {
    querySelectorAll: type => type === "video" ? videos : (extra.get(type) || []),
    addEventListener: (name, callback) => add(handlers, name, callback),
  };
  const window = { addEventListener: (name, callback) => add(winHandlers, name, callback) };
  const browser = { runtime: {
    id: "detector",
    onMessage: { addListener: fn => { networkReceiver = fn; } },
    sendMessage: message => receive(message, sender),
    sendNativeMessage: async (...args) => {
      if (args[1].event === "media_candidate") native.push(args);
      return true;
    },
  } };
  class MutationObserver {
    constructor(callback) { observerCallback = callback; }
    observe() {}
  }
  vm.runInNewContext(fs.readFileSync(path.join(assets, "content.js"), "utf8"), {
    browser, HTMLVideoElement, document, window, location, MutationObserver, URL, Date,
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
  return { HTMLVideoElement, videos, extra, native, fire, navigate, mutate, location, document,
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

test("v1111: HLS 403 is retained as an authorization failure, not confirmed", async () => {
  const b = background(async () => true);
  b.probe(response("https://cdn.example/index.m3u8?t=expired&s=1", "text/html",
    { statusCode: 403 }));
  await tick();
  assert.equal(b.routed.length, 1);
  assert.equal(b.routed[0][1].verification, "auth_error");
  assert.equal(b.routed[0][1].httpStatus, 403);
});

test("v1107: a bounded HLS manifest emits grouped playable variants without segment requests", async () => {
  let fetches = 0;
  const fetchImpl = async (url, options) => {
    fetches++;
    assert.equal(url, "https://site.example/master.m3u8?sig=a");
    assert.equal(options.credentials, "omit");
    const body = new TextEncoder().encode("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1920x1080\n1080/index.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=854x480\n480/index.m3u8");
    return {
      ok: true,
      url: "https://redirected.example/media/master.m3u8?sig=a",
      headers: { get: name => name.toLowerCase() === "content-length" ? "220" : null },
      body: { getReader: () => { let done = false; return { read: async () => done ?
        { done: true } : (done = true, { done: false, value: body }), cancel: async () => {} }; } },
    };
  };
  const b = background(async () => true, true, fetchImpl);
  b.probe(response("https://site.example/master.m3u8?sig=a", "application/vnd.apple.mpegurl"));
  await tick(); await tick(); await tick();
  assert.equal(fetches, 1);
  assert.equal(b.routed.length, 3);
  assert.equal(b.routed[0][1].url, "https://site.example/master.m3u8?sig=a");
  assert.deepEqual(b.routed.slice(1).map(x => x[1].quality), ["1080p", "480p"]);
  assert.equal(b.routed[1][1].url, "https://redirected.example/media/1080/index.m3u8");
  assert.equal(new Set(b.routed.map(x => x[1].groupId)).size, 1);
});

test("v1107: chunked manifest is aborted before exceeding the decoded byte cap", async () => {
  let cancelled = false;
  const chunk = new Uint8Array(300000);
  const fetchImpl = async () => ({ ok: true, url: "https://site.example/a.m3u8",
    headers: { get: () => null }, body: { getReader: () => { let reads = 0; return {
      read: async () => ++reads <= 2 ? { done: false, value: chunk } : { done: true },
      cancel: async () => { cancelled = true; }
    }; } } });
  const b = background(async () => true, true, fetchImpl);
  b.probe(response("https://site.example/a.m3u8", "application/vnd.apple.mpegurl"));
  await tick(); await tick(); await tick();
  assert.equal(cancelled, true);
  assert.equal(b.routed.length, 1);
});

test("v1107: redirect to a segment without MIME is rejected before reading its body", async () => {
  let reads = 0;
  const fetchImpl = async () => ({ ok: true, url: "https://cdn.example/segment-1.ts",
    headers: { get: () => null }, body: { getReader: () => ({
      read: async () => { reads++; return { done: true }; }, cancel: async () => {}
    }) } });
  const b = background(async () => true, true, fetchImpl);
  b.probe(response("https://site.example/master.m3u8", "application/vnd.apple.mpegurl"));
  await tick(); await tick();
  assert.equal(reads, 0);
  assert.equal(b.routed.length, 1);
});

test("v1107: repeated manifest responses retain a stable logical group", async () => {
  const b = background(async () => true);
  b.probe(response("https://site.example/master.m3u8", "application/vnd.apple.mpegurl"));
  const first = b.routed[0][1].groupId;
  const originalNow = Date.now;
  Date.now = () => originalNow() + 21000;
  try { b.probe(response("https://site.example/master.m3u8", "application/vnd.apple.mpegurl")); }
  finally { Date.now = originalNow; }
  assert.equal(b.routed[1][1].groupId, first);
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

test("stage 3.3: stale response after same-URL reload never reaches new document", async () => {
  const b = background(async () => true);
  const stale = response("https://cdn.example.org/old.mp4", "video/mp4", { requestId: "old" });
  b.before(stale);
  const newDoc = await b.receive({ schemaVersion: 1, event: "document_started",
    pageUrl: "https://site.example/watch/1" }, sender);
  assert.equal(newDoc.documentKey, 2);
  b.rawProbe(stale);
  await tick();
  assert.equal(b.routed.length, 0);
  assert.equal(b.native.some(x => x[1].event === "network_unassigned"), true);
  const fresh = response("https://cdn.example.org/new.mp4", "video/mp4", { requestId: "new" });
  b.probe(fresh);
  await tick();
  assert.equal(b.routed.length, 1);
  assert.equal(b.routed[0][1].documentKey, 2);
});

test("stage 3.3: missing request ID or uncertain document never guesses active tab", async () => {
  const b = background(async () => true);
  b.rawProbe(response("https://cdn.example.org/orphan.mp4", "video/mp4"));
  b.probe(response("https://cdn.example.org/old.mp4", "video/mp4",
    { documentUrl: "https://site.example/older" }));
  await tick();
  assert.equal(b.routed.length, 0);
  assert.equal(b.native.filter(x => x[1].event === "network_unassigned").length, 2);
});

test("stage 3.3: document key mismatches cannot enter another page", async () => {
  const p = page();
  await tick();
  const stale = { schemaVersion: 1, event: "media_candidate", source: "network",
    url: "https://cdn.example.org/old.mp4", isPrivate: false,
    documentKey: 99, pageUrl: p.location.href };
  assert.equal(await p.onNetwork(stale), false);
  assert.equal(p.native.length, 0);
  const valid = { ...stale, documentKey: 1 };
  assert.equal(await p.onNetwork(valid), true);
  assert.equal(p.native.length, 1);
  await p.navigate("https://site.example/watch/2");
  assert.equal(await p.onNetwork(valid), false);
  assert.equal(p.native.length, 1);
});

test("stage 3.3: SPA route changes register a new extension document", async () => {
  const p = page();
  const video = new p.HTMLVideoElement("https://cdn.example.org/one.mp4");
  p.videos.push(video);
  await p.fire("playing", video);
  const first = p.native[0][1].documentKey;
  await p.navigate("https://site.example/watch/2");
  const second = p.native[1][1].documentKey;
  assert.ok(second > first);
  assert.equal(p.native[1][1].pageUrl, "https://site.example/watch/2");
});


test("stage 4.0.1: video.src is discovered before Play and never causes playback", async () => {
  const p = page();
  const v = new p.HTMLVideoElement("https://cdn.example.org/preplay.mp4?signature=secret", null, true);
  p.videos.push(v);
  await p.mutate();
  assert.equal(p.native.length, 1);
  assert.equal(p.native[0][1].source, "page");
  assert.equal(v.paused, true);
  await p.mutate();
  assert.equal(p.native.length, 1);
});

test("stage 4.0.1: all source declarations and HLS/DASH manifest hints precede Play", async () => {
  const p = page();
  const v = new p.HTMLVideoElement("", null, true);
  v.sources = [
    { src: "https://cdn.example.org/one.mp4", type: "video/mp4" },
    { src: "https://cdn.example.org/two.webm", type: "video/webm" },
    { src: "https://cdn.example.org/master.m3u8", type: "application/vnd.apple.mpegurl" },
    { src: "https://cdn.example.org/master.mpd", type: "application/dash+xml" },
  ];
  p.videos.push(v);
  await p.mutate();
  assert.equal(p.native.length, 4);
  assert.equal(new Set(p.native.map(x => x[1].groupId)).size, 1);
  assert.equal(v.paused, true);
});

test("stage 4.0.1: JSON-LD VideoObject and Open Graph URLs are discoverable", async () => {
  const p = page();
  p.extra.set('script[type="application/ld+json"]', [{
    textContent: JSON.stringify({ "@context": "https://schema.org",
      "@type": "VideoObject", contentUrl: "https://cdn.example.org/schema.mp4" }),
  }]);
  p.extra.set('meta[property^="og:video"],meta[name="twitter:player:stream"]', [{
    getAttribute: name => ({ property: "og:video:secure_url",
      content: "https://cdn.example.org/og.webm" })[name],
  }]);
  await p.mutate();
  assert.deepEqual(p.native.map(x => x[1].source), ["page", "page"]);
});

test("stage 4.0.1: literal HLS.js player configuration is recognized without executing JS", async () => {
  const p = page();
  p.extra.set("script", [{
    textContent: "const hls = new Hls(); hls.loadSource('https://cdn.example.org/live.m3u8');",
    getAttribute: () => "",
  }]);
  await p.mutate();
  assert.equal(p.native.length, 1);
  assert.equal(p.native[0][1].url, "https://cdn.example.org/live.m3u8");
});

test("stage 4.0.1: iframe keys bind strictly to the parent document and expire on navigation", async () => {
  const b = background(async () => true);
  const iframe = { id: "detector", tab: { id: 7 }, frameId: 2,
    url: "https://embed.example.org/player" };
  const handshake = await b.receive({ schemaVersion: 1, event: "frame_started",
    frameUrl: iframe.url }, iframe);
  assert.ok(handshake.frameKey > 0);
  assert.equal(handshake.pageUrl, "https://site.example/watch/1");
  const accepted = await b.receive({
    schemaVersion: 1, event: "media_candidate", source: "page",
    url: "https://cdn.example.org/frame.mp4", groupId: "g1"
  }, iframe);
  assert.equal(accepted.frameKey, handshake.frameKey);
  assert.equal(accepted.pageUrl, handshake.pageUrl);
  assert.ok(accepted.groupId.startsWith("f"));
  assert.equal(await b.receive({ schemaVersion: 1, event: "frame_started",
    frameUrl: "https://fake.example.org/player" }, iframe), null);
  b.probe(response("https://cdn.example.org/in-frame.m3u8",
    "application/vnd.apple.mpegurl", { frameId: 2, documentUrl: iframe.url }));
  await tick();
  assert.equal(b.routed.length, 1);
  await b.receive({ schemaVersion: 1, event: "document_started",
    pageUrl: "https://site.example/watch/2" }, sender);
  assert.equal(await b.receive({ schemaVersion: 1, event: "media_candidate",
    source: "page", url: "https://cdn.example.org/stale.mp4" }, iframe), null);
});

test("v1111: iframe manifest verification uses embed referrer and confirms one segment", async () => {
  const calls = [];
  const manifest = new TextEncoder().encode("#EXTM3U\n#EXTINF:6,\nseg.ts?t=fresh&s=2");
  const fetchImpl = async (url, options) => {
    calls.push({ url, options });
    if (url.includes("index.m3u8")) return {
      ok: true, status: 200, url,
      headers: { get: name => name.toLowerCase() === "content-type" ?
        "application/vnd.apple.mpegurl" : null },
      body: { getReader: () => { let done = false; return {
        read: async () => done ? { done: true } :
          (done = true, { done: false, value: manifest }), cancel: async () => {}
      }; } },
    };
    return { ok: true, status: 206, url, headers: { get: () => null },
      body: { cancel: async () => {} } };
  };
  const b = background(async () => true, true, fetchImpl);
  const iframe = { id: "detector", tab: { id: 7 }, frameId: 2,
    url: "https://embed.example.org/player" };
  await b.receive({ schemaVersion: 1, event: "frame_started", frameUrl: iframe.url }, iframe);
  b.probe(response("https://cdn.example/index.m3u8?t=fresh&s=2",
    "application/vnd.apple.mpegurl", { frameId: 2, documentUrl: iframe.url }));
  await tick(); await tick(); await tick(); await tick();
  assert.equal(calls[0].options.referrer, iframe.url);
  assert.equal(calls[1].url, "https://cdn.example/seg.ts?t=fresh&s=2");
  assert.equal(calls[1].options.headers.Range, "bytes=0-0");
  assert.equal(b.routed.some(x => x[1].verification === "stream_verified"), true);
});

test("v1111: HTTP 200 HTML segment response is never confirmed", async () => {
  const manifest = new TextEncoder().encode("#EXTM3U\n#EXTINF:6,\nseg.ts");
  const fetchImpl = async (url) => url.endsWith(".m3u8") ? {
    ok: true, status: 200, url,
    headers: { get: name => name.toLowerCase() === "content-type" ?
      "application/vnd.apple.mpegurl" : null },
    body: { getReader: () => { let done = false; return {
      read: async () => done ? { done: true } :
        (done = true, { done: false, value: manifest }), cancel: async () => {}
    }; } },
  } : { ok: true, status: 200, url: "https://login.example/challenge",
    headers: { get: name => name.toLowerCase() === "content-type" ? "text/html" : null },
    body: { cancel: async () => {} } };
  const b = background(async () => true, true, fetchImpl);
  b.probe(response("https://cdn.example/index.m3u8", "application/vnd.apple.mpegurl"));
  await tick(); await tick(); await tick(); await tick();
  assert.equal(b.routed.some(x => x[1].verification === "stream_verified"), false);
});

test("stage 4.0.1: before-Play cannot discover a URL created only after Play", async () => {
  const p = page();
  const v = new p.HTMLVideoElement("", null, true);
  p.videos.push(v);
  await p.mutate();
  assert.equal(p.native.length, 0);
  v.currentSrc = "https://cdn.example.org/generated.mp4";
  v.src = v.currentSrc;
  v.paused = false;
  await p.fire("playing", v);
  assert.equal(p.native.length, 1);
  assert.equal(p.native[0][1].source, "dom");
});

test("stage 4.0.1: iframe embed, blob and ordinary links do not become videos", async () => {
  const p = page();
  p.extra.set("a[href],link[href]", [
    { href: "https://embed.example.org/player",
      getAttribute: () => "https://embed.example.org/player" },
    { href: "blob:https://site.example/synthetic.mp4",
      getAttribute: () => "blob:https://site.example/synthetic.mp4" },
    { href: "https://site.example/movie.jpg",
      getAttribute: () => "https://site.example/movie.jpg" },
  ]);
  await p.mutate();
  assert.equal(p.native.length, 0);
});


test("stage 4.0.2: concrete video title and duration take precedence", async () => {
  const p = page();
  p.document.title = "Unrelated site title";
  const v = new p.HTMLVideoElement("https://cdn.example.org/clip.mp4", null, true);
  v.duration = 624.2;
  v.getAttribute = name => name === "data-video-title" ? "Dokładny tytuł nagrania" : null;
  p.videos.push(v);
  await p.mutate();
  assert.equal(p.native.length, 1);
  assert.equal(p.native[0][1].title, "Dokładny tytuł nagrania");
  assert.equal(p.native[0][1].titleKind, "video");
  assert.equal(p.native[0][1].durationSeconds, 625);
});

test("stage 4.0.2: JSON-LD name stays attached to its contentUrl", async () => {
  const p = page();
  p.extra.set('script[type="application/ld+json"]', [{
    textContent: JSON.stringify({ "@type": "VideoObject",
      name: "Film z metadanych", contentUrl: "https://cdn.example.net/film.mp4" }),
  }]);
  await p.mutate();
  assert.equal(p.native[0][1].title, "Film z metadanych");
  assert.equal(p.native[0][1].titleKind, "video");
});

test("stage 4.0.2: full Content-Length and partial Content-Range give size without fetch", async () => {
  const p = page();
  p.document.title = "Odcinek pierwszy";
  const b = background((tab, message) => p.onNetwork(message, { id: "detector" }));
  b.probe(response("https://cdn.example.net/film.mp4", "video/mp4",
    { statusCode: 200, responseHeaders: [
      { name: "content-type", value: "video/mp4" },
      { name: "Content-Length", value: "252000000" }
    ] }));
  b.probe(response("https://cdn.example.net/other.webm", "video/webm",
    { statusCode: 206, responseHeaders: [
      { name: "content-type", value: "video/webm" },
      { name: "Content-Range", value: "bytes 0-2047/95100000" },
      { name: "Content-Length", value: "2048" }
    ] }));
  b.probe(response("https://cdn.example.net/master.m3u8", "application/vnd.apple.mpegurl",
    { statusCode: 200, responseHeaders: [
      { name: "Content-Type", value: "application/vnd.apple.mpegurl" },
      { name: "Content-Length", value: "3000" }
    ] }));
  await tick(); await tick();
  assert.equal(p.native.length, 3);
  assert.equal(p.native[0][1].sizeBytes, 252000000);
  assert.equal(p.native[0][1].title, "Odcinek pierwszy");
  assert.equal(p.native[0][1].titleKind, "page");
  assert.equal(p.native[1][1].sizeBytes, 95100000);
  assert.equal(p.native[2][1].sizeBytes, undefined);
});

test("stage 4.0.2: missing or ambiguous response size never becomes film size", async () => {
  const b = background(async () => true);
  b.probe(response("https://cdn.example.net/encoded.mp4", "video/mp4",
    { statusCode: 200, responseHeaders: [
      { name: "Content-Type", value: "video/mp4" },
      { name: "Content-Length", value: "123456" },
      { name: "Content-Encoding", value: "gzip" }
    ] }));
  b.probe(response("https://cdn.example.net/partial.mp4", "video/mp4",
    { statusCode: 206, responseHeaders: [
      { name: "Content-Type", value: "video/mp4" },
      { name: "Content-Length", value: "1024" }
    ] }));
  b.probe(response("https://cdn.example.net/big.mp4", "video/mp4",
    { statusCode: 200, responseHeaders: [
      { name: "Content-Type", value: "video/mp4" },
      { name: "Content-Length", value: "9000000000000" }
    ] }));
  await tick();
  assert.equal(b.routed.length, 3);
  assert.equal(b.routed.every(x => !("sizeBytes" in x[1])), true);
});

test("stage 4.0.2: title validation rejects injection and excess metadata", async () => {
  const { receive } = background();
  const original = candidate("https://cdn.example/a.mp4");
  assert.equal(await receive({ ...original, title: "A film" }, sender), null);
  assert.equal(await receive({ ...original, title: "X".repeat(301),
    titleKind: "video" }, sender), null);
  assert.equal(await receive({ ...original, title: "X\nY", titleKind: "video" }, sender), null);
  assert.equal(await receive({ ...original, sizeBytes: 9876 }, sender), null);
  const accepted = await receive({ ...original, title: "Zapis wideo",
    titleKind: "video", durationSeconds: 65 }, sender);
  assert.equal(accepted.title, "Zapis wideo");
});


test("stage 4.0.2: loadedmetadata enriches a previously detected paused movie", async () => {
  const p = page();
  const video = new p.HTMLVideoElement("https://cdn.example/m.mp4", null, true);
  p.videos.push(video);
  await p.mutate();
  assert.equal(p.native.length, 1);
  assert.equal(p.native[0][1].durationSeconds, undefined);
  video.duration = 3723;
  await p.fire("loadedmetadata", video);
  assert.equal(p.native.length, 2);
  assert.equal(p.native[1][1].durationSeconds, 3723);
  assert.equal(p.native[1][1].url, p.native[0][1].url);
});


test("v1106: deeper declarative video attributes are detected before playback", async () => {
  const p = page();
  const selector = "[data-video-url],[data-video-src],[data-hls],[data-mpd]," +
    "[data-src],[data-plyr-source],[data-file],[data-stream-url]," +
    "[data-playback-url],[data-manifest-url],[data-source-url]," +
    "[data-mp4],[data-m3u8],[data-stream]";
  p.extra.set(selector, [{
    getAttribute: name => ({
      "data-playback-url": "https://cdn.example.org/watch.mp4",
      "data-m3u8": "https://cdn.example.org/playlist.m3u8"
    })[name] || null
  }]);
  await p.mutate();
  assert.deepEqual(p.native.map(x => x[1].url), [
    "https://cdn.example.org/watch.mp4",
    "https://cdn.example.org/playlist.m3u8"
  ]);
});

test("v1106: preview assist plays a ready muted thumbnail without navigating", async () => {
  let click, plays = 0, prevented = false, stopped = false;
  const video = {
    paused: true, isConnected: true, readyState: 3, muted: true,
    src: "https://cdn.example.org/preview.mp4",
    querySelectorAll: () => [], querySelector: () => null,
    hasAttribute: () => false,
    play: () => { plays++; return Promise.resolve(); }
  };
  const card = {
    closest: () => null,
    querySelector: selector => selector === "video" ? video : {}
  };
  const control = {
    className: "preview-play", textContent: "Play",
    getAttribute: () => null,
    closest: selector => selector === "nav,header,footer" ? null :
      selector === "a[href]" ? {} : card
  };
  vm.runInNewContext(
    fs.readFileSync(path.join(assets, "preview-assist.js"), "utf8"),
    { document: { addEventListener: (type, fn) => { if (type === "click") click = fn; } } }
  );
  click({
    isTrusted: true, cancelable: true, defaultPrevented: false, button: 0,
    target: { closest: () => control },
    preventDefault: () => { prevented = true; },
    stopImmediatePropagation: () => { stopped = true; }
  });
  assert.equal(plays, 1);
  assert.equal(prevented, true);
  assert.equal(stopped, true);
});


test("v1108: thumbnail click without actual preview preserves navigation", () => {
  let click, prevented = false;
  const card = { closest: () => null, querySelector: () => null };
  const link = {};
  const thumb = {
    closest: selector => selector === "a[href]" ? link : card
  };
  vm.runInNewContext(
    fs.readFileSync(path.join(assets, "preview-assist.js"), "utf8"),
    { document: { addEventListener: (type, fn) => { if (type === "click") click = fn; } } }
  );
  click({
    isTrusted: true, cancelable: true, defaultPrevented: false, button: 0,
    target: { closest: selector => selector.startsWith("button,") ? null : thumb },
    preventDefault: () => { prevented = true; },
    stopImmediatePropagation: () => { throw Error("must not intercept link"); }
  });
  assert.equal(prevented, false);
});
