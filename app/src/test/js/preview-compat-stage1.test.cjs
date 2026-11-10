"use strict";
// Stage 1/10: real shipped WebExtension source, synthetic DOM only (not Gecko).
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const root = path.resolve(__dirname, "../..");
const read = name => fs.readFileSync(path.join(root, name), "utf8");
const source = read("main/assets/extensions/nira-media-detector/preview-assist.js");

function fixture({ declared = false, failure = false, videoSurface = false } = {}) {
  const handlers = new Map();
  const url = "https://cdn.example.test/clip.mp4";
  const count = { plays: 0, prevented: 0, stopped: 0 };
  const video = {
    tagName: "VIDEO", src: url, currentSrc: url, paused: true,
    isConnected: true, muted: false, playsInline: false, dataset: {},
    querySelector: () => null,
    play() {
      count.plays++;
      if (failure) return Promise.reject(Object.assign(new Error("blocked"), { name: "NotAllowedError" }));
      return Promise.resolve();
    }
  };
  const link = { href: "https://site.example/movie" };
  const card = {
    getAttribute: key => declared && key === "data-preview-src" ? url : null,
    querySelectorAll: () => [],
    querySelector: key => key === "video" ? video : key === "a[href]" ? link : null,
    closest: () => null
  };
  const thumb = { closest: key => key === "a[href]" ? link : card };
  const target = {
    tagName: videoSurface ? "VIDEO" : "IMG",
    closest(key) {
      if (key.startsWith("button,")) return null;
      if (key === "video") return videoSurface ? video : null;
      if (key.startsWith("img,video,")) return videoSurface ? video : thumb;
      return null;
    }
  };
  const document = {
    baseURI: "https://site.example/",
    addEventListener(name, listener) {
      if (!handlers.has(name)) handlers.set(name, []);
      handlers.get(name).push(listener);
    }
  };
  vm.runInNewContext(source, { document, URL, location: { href: "https://site.example/" } });
  function emit(type, options = {}) {
    const before = { ...count };
    const event = {
      isTrusted: true, cancelable: true, defaultPrevented: false, button: 0,
      target, ...options,
      preventDefault() { count.prevented++; },
      stopImmediatePropagation() { count.stopped++; }
    };
    for (const handler of handlers.get(type) || []) handler(event);
    return {
      plays: count.plays - before.plays,
      prevented: count.prevented - before.prevented,
      stopped: count.stopped - before.stopped
    };
  }
  return { emit, video, count, handlers };
}

test("preview stage1: manifest loads assist across HTTP(S) frames", () => {
  const manifest = JSON.parse(read("main/assets/extensions/nira-media-detector/manifest.json"));
  const item = manifest.content_scripts.find(x => x.js.includes("preview-assist.js"));
  assert.ok(item, "preview script is shipped");
  assert.equal(item.all_frames, true);
  assert.deepEqual(Array.from(item.matches), ["http://*/*", "https://*/*"]);
});

test("preview stage1: Gecko autoplay preference evidence comes from real source", t => {
  const prefs = read("main/java/com/prirai/android/nira/components/UserJsPreferences.kt");
  const runtime = read("main/java/com/prirai/android/nira/components/Components.kt");
  const get = key => {
    const escaped = key.replace(/\./g, "\\.");
    const match = prefs.match(new RegExp('"' + escaped + '"\\s+to\\s+(\\d+)'));
    assert.ok(match, "missing autoplay setting " + key);
    return Number(match[1]);
  };
  const mode = get("media.autoplay.default");
  const policy = get("media.autoplay.blocking_policy");
  assert.match(prefs, /securityPrefs \+ mediaPrefs \+ performancePrefs/);
  assert.match(prefs, /builder\.configFilePath\(configFile\.absolutePath\)/);
  assert.match(runtime, /UserJsPreferences\.applyTo\(applicationContext, builder\)/);
  t.diagnostic("source autoplay.default=" + mode + ", blocking_policy=" + policy +
    "; runtime/device behavior NOT_TESTED");
});

test("preview stage1: hover and first touch do not get intercepted", () => {
  const f = fixture({ declared: true });
  assert.ok(f.handlers.has("click"));
  for (const kind of ["pointerenter", "mouseenter", "mouseover", "touchstart"])
    assert.deepEqual(f.emit(kind), { plays: 0, prevented: 0, stopped: 0 });
  assert.equal(f.video.muted, false);
});

test("preview stage1: ordinary thumbnail click stays with site", () => {
  const f = fixture();
  assert.deepEqual(f.emit("click"), { plays: 0, prevented: 0, stopped: 0 });
  assert.equal(f.video.muted, false);
});

test("preview stage1: click on native video surface remains site-owned", () => {
  const f = fixture({ declared: true, videoSurface: true });
  assert.deepEqual(f.emit("click"), { plays: 0, prevented: 0, stopped: 0 });
  assert.equal(f.video.muted, false);
});

test("preview stage1: declared thumbnail click takes explicit preview path", async () => {
  const f = fixture({ declared: true });
  assert.deepEqual(f.emit("click"), { plays: 1, prevented: 1, stopped: 1 });
  await Promise.resolve();
  assert.equal(f.video.muted, true);
});

test("preview stage1: synthetic NotAllowedError restores next-tap navigation", async () => {
  const f = fixture({ declared: true, failure: true });
  assert.deepEqual(f.emit("click"), { plays: 1, prevented: 1, stopped: 1 });
  await Promise.resolve();
  assert.equal(f.video.muted, false);
  assert.deepEqual(f.emit("click"), { plays: 0, prevented: 0, stopped: 0 });
  assert.equal(f.count.plays, 1);
});

test("preview stage1: modifier clicks are never captured", () => {
  for (const key of ["ctrlKey", "shiftKey", "altKey", "metaKey"]) {
    const f = fixture({ declared: true });
    assert.deepEqual(f.emit("click", { [key]: true }),
      { plays: 0, prevented: 0, stopped: 0 });
  }
});
