const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const assets = path.join(__dirname, "../../main/assets/extensions/nira-media-detector");

function background() {
  let receive, probe, filter;
  const native = [];
  const browser = {
    runtime: {
      id: "detector",
      onMessage: { addListener: callback => { receive = callback; } },
      sendNativeMessage: async (...args) => { native.push(args); },
    },
    webRequest: { onHeadersReceived: { addListener: (callback, options) => {
      probe = callback; filter = options;
    } } },
  };
  vm.runInNewContext(fs.readFileSync(path.join(assets, "background.js"), "utf8"), {
    browser, TextEncoder, URL,
  });
  return { receive, probe, native, filter };
}
const candidate = url => ({ schemaVersion: 1, event: "media_candidate", source: "dom", url });
const sender = { id: "detector", tab: { id: 7 }, frameId: 0 };

function page() {
  const { receive } = background();
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
    sendMessage: message => receive(message, sender),
    sendNativeMessage: async (...args) => { native.push(args); },
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
  return { HTMLVideoElement, videos, native, fire, navigate, mutate, location };
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

test("webRequest probe remains restricted and URL-free", () => {
  const { probe, native, filter } = background();
  assert.equal(filter.urls[0], "https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4*");
  probe(); probe();
  assert.equal(native.length, 1);
  assert.equal(JSON.stringify(native[0]), JSON.stringify([
    "nira.media.detector", { schemaVersion: 1, event: "network_probe" },
  ]));
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
