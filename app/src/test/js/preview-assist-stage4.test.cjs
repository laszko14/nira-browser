"use strict";
// Stage 4/10: gestures against the actual shipped preview-assist WebExtension.
// Synthetic DOM only. No real GeckoView, site network or physical device.
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const code = fs.readFileSync(path.join(__dirname,
  "../../main/assets/extensions/nira-media-detector/preview-assist.js"), "utf8");

function deferred() {
  let resolve, reject;
  const promise = new Promise((ok, fail) => { resolve = ok; reject = fail; });
  return { promise, resolve, reject };
}

function fixture({ declared = true, existing = false, nativeAutoplay = false,
                   nativeSrc = null, behavior = null } = {}) {
  const calls = { plays: 0, pauses: 0, removed: 0, prevented: 0, stopped: 0 };
  const src = "https://cdn.example.test/clip.mp4";
  const handlers = new Map();
  let currentVideo = null;
  const link = { href: "https://site.example/watch/1" };
  const card = {
    getAttribute: name => declared && name === "data-preview-src" ? src : null,
    querySelectorAll: () => [],
    querySelector(selector) {
      if (selector === "video") return currentVideo;
      if (selector === "img") return image;
      if (selector === "a[href]") return link;
      return null;
    },
    closest: () => null,
    contains: node => node === card || node === image || node === currentVideo
  };
  const layer = {
    style: { position: "relative" },
    appendChild(video) { currentVideo = video; video.isConnected = true; }
  };
  const image = {
    tagName: "IMG", parentElement: layer,
    closest(selector) {
      if (selector === "video" || selector.startsWith("button,")) return null;
      if (selector === "a[href]") return link;
      if (selector.startsWith("img,video,") ||
          selector === "img,[class*='thumb'],[class*='Thumb']") return image;
      return card;
    }
  };
  link.closest = selector => selector === "a[href]" ? link : card;
  function newVideo() {
    const video = {
      tagName: "VIDEO", src: nativeSrc || src, currentSrc: nativeSrc || src,
      paused: true, isConnected: false,
      muted: nativeAutoplay, defaultMuted: nativeAutoplay,
      autoplay: nativeAutoplay, playsInline: nativeAutoplay, loop: false,
      dataset: {}, style: {}, setAttribute() {},
      querySelector: () => null,
      play() {
        calls.plays++;
        if (behavior) return behavior(video);
        video.paused = false;
        return Promise.resolve();
      },
      pause() { calls.pauses++; video.paused = true; },
      remove() {
        calls.removed++;
        video.isConnected = false;
        if (currentVideo === video) currentVideo = null;
      }
    };
    return video;
  }
  if (existing) { currentVideo = newVideo(); currentVideo.isConnected = true; }
  vm.runInNewContext(code, {
    document: {
      baseURI: "https://site.example/",
      addEventListener(name, handler, opts) {
        handlers.set(name, { handler, opts });
      },
      createElement(type) { assert.equal(type, "video"); return newVideo(); }
    },
    getComputedStyle: () => ({ position: "relative" }),
    location: { href: "https://site.example/" }, URL
  });
  const emit = (name, props = {}) => {
    const entry = handlers.get(name);
    assert.ok(entry, "listener " + name + " registered");
    const before = { ...calls };
    const event = {
      isTrusted: true, cancelable: true, defaultPrevented: false,
      button: 0, buttons: 0, pointerType: "mouse", target: image,
      timeStamp: 100, touches: [], changedTouches: [],
      preventDefault() { calls.prevented++; },
      stopImmediatePropagation() { calls.stopped++; },
      ...props
    };
    entry.handler(event);
    return Object.fromEntries(Object.keys(before).map(
      key => [key, calls[key] - before[key]]));
  };
  return {
    emit, card, image, handlers, calls,
    video: () => currentVideo,
    src
  };
}
const unchanged = { plays: 0, pauses: 0, removed: 0, prevented: 0, stopped: 0 };

test("stage4: pointerover mouse on declared thumbnail starts muted helper-owned preview", () => {
  const f = fixture();
  const result = f.emit("pointerover");
  assert.equal(result.plays, 1);
  assert.equal(result.prevented, 0);
  assert.equal(result.stopped, 0);
  assert.equal(f.video().muted, true);
  assert.equal(f.video().dataset.niraPreviewCreated, "1");
});
test("stage4: pointerout restores ownership and removes helper-created video", () => {
  const f = fixture();
  f.emit("pointerover");
  const result = f.emit("pointerout", { relatedTarget: null });
  assert.equal(result.pauses, 1);
  assert.equal(result.removed, 1);
  assert.equal(f.video(), null);
  assert.equal(result.prevented, 0);
});
test("stage4: pointerout between children of same card never stops preview", () => {
  const f = fixture();
  f.emit("pointerover");
  assert.deepEqual(f.emit("pointerout", { relatedTarget: f.image }), unchanged);
  assert.equal(f.calls.plays, 1);
  assert.ok(f.video());
  assert.equal(f.emit("pointerover").plays, 0, "no duplicate play during hover");
});
test("stage4: native HTML5 preview with matching source restores original media state", () => {
  const f = fixture({ existing: true });
  const before = f.video();
  assert.equal(before.muted, false);
  f.emit("pointerover");
  assert.equal(before.muted, true);
  f.emit("pointerout");
  assert.equal(before.muted, false);
  assert.equal(before.playsInline, false);
  assert.equal(f.video(), before);
  assert.equal(f.calls.pauses, 1);
  assert.equal(f.calls.removed, 0);
});
test("stage4: page-owned muted autoplay never gets restarted or paused", () => {
  const f = fixture({ existing: true, nativeAutoplay: true });
  assert.deepEqual(f.emit("pointerover"), unchanged);
  assert.deepEqual(f.emit("pointerout"), unchanged);
  assert.equal(f.video().muted, true);
});
test("stage4: mismatched native source and missing preview declaration do nothing", () => {
  for (const opts of [{ existing: true, nativeSrc: "https://site.example/movie.mp4" },
                     { declared: false }]) {
    const f = fixture(opts);
    assert.deepEqual(f.emit("pointerover"), unchanged);
    assert.deepEqual(f.emit("pointerout"), unchanged);
  }
});
test("stage4: touch pointerover is not mouse hover", () => {
  const f = fixture();
  assert.deepEqual(f.emit("pointerover", { pointerType: "touch" }), unchanged);
  assert.deepEqual(f.emit("pointerover", { isTrusted: false }), unchanged);
  assert.deepEqual(f.emit("pointerover", { buttons: 1 }), unchanged);
  assert.equal(f.video(), null);
});
test("stage4: rejected hover restores state; deliberate click may retry under gesture", async () => {
  const f = fixture({ behavior: () => Promise.reject(new Error("NotAllowedError")) });
  assert.equal(f.emit("pointerover").plays, 1);
  await Promise.resolve();
  assert.equal(f.video(), null);
  // User click has activation that may succeed where muted hover was rejected.
  const click = f.emit("click", { timeStamp: 200 });
  assert.equal(click.plays, 1);
  assert.equal(click.prevented, 1);
  assert.equal(click.stopped, 1);
  await Promise.resolve();
  // A second rejection restores the site's original native navigation.
  assert.deepEqual(f.emit("click", { timeStamp: 250 }), unchanged);
});
test("stage4: synchronous throw on hover restores existing player", () => {
  const f = fixture({ existing: true, behavior: () => { throw new Error("blocked"); } });
  assert.equal(f.emit("pointerover").plays, 1);
  assert.equal(f.video().muted, false);
  assert.equal(f.video().playsInline, false);
  assert.equal(f.calls.removed, 0);
});
test("stage4: touch listeners are passive; touchstart does not start or capture media", () => {
  const f = fixture();
  for (const name of ["touchstart","touchmove","touchend","touchcancel"]) {
    const listener = f.handlers.get(name);
    assert.ok(listener && listener.opts.passive, name + " must be passive");
  }
  const start = { identifier: 1, clientX: 10, clientY: 10 };
  assert.deepEqual(f.emit("touchstart", {
    timeStamp: 100, changedTouches: [start], touches: [start]
  }), unchanged);
  assert.equal(f.video(), null);
});
test("stage4: short touch tap delegates preview to normal click", () => {
  const f = fixture();
  const start = { identifier: 2, clientX: 10, clientY: 10 };
  f.emit("touchstart", { timeStamp: 100, touches: [start], changedTouches: [start] });
  f.emit("touchend", { timeStamp: 170, changedTouches: [start] });
  const click = f.emit("click", { timeStamp: 185 });
  assert.equal(click.plays, 1);
  assert.equal(click.prevented, 1);
  assert.equal(click.stopped, 1);
});
test("stage4: swipe movement cannot be mistaken for a preview click", () => {
  const f = fixture();
  const start = { identifier: 3, clientX: 10, clientY: 10 };
  const end = { identifier: 3, clientX: 65, clientY: 50 };
  assert.deepEqual(f.emit("touchstart", {
    timeStamp: 100, touches: [start], changedTouches: [start]
  }), unchanged);
  assert.deepEqual(f.emit("touchmove", {
    timeStamp: 140, touches: [end], changedTouches: [end]
  }), unchanged);
  assert.deepEqual(f.emit("touchend", {
    timeStamp: 170, changedTouches: [end]
  }), unchanged);
  assert.deepEqual(f.emit("click", { timeStamp: 185 }), unchanged);
  assert.equal(f.video(), null);
  // Guard belongs to the gesture, not the card forever.
  assert.equal(f.emit("click", { timeStamp: 1500 }).plays, 1);
});
test("stage4: long hold and cancelled touch don't hijack native click", () => {
  for (const kind of ["long", "cancel"]) {
    const f = fixture();
    const point = { identifier: 4, clientX: 10, clientY: 10 };
    f.emit("touchstart", { timeStamp: 100, touches: [point], changedTouches: [point] });
    f.emit(kind === "cancel" ? "touchcancel" : "touchend", {
      timeStamp: 900, changedTouches: [point]
    });
    assert.deepEqual(f.emit("click", { timeStamp: 930 }), unchanged);
  }
});
test("stage4: two-finger gesture and touch without declared media remain unmanaged", () => {
  const f = fixture({ declared: false });
  const point = { identifier: 1, clientX: 0, clientY: 0 };
  const other = { identifier: 2, clientX: 15, clientY: 0 };
  assert.deepEqual(f.emit("touchstart", {
    touches: [point, other], changedTouches: [point]
  }), unchanged);
  assert.deepEqual(f.emit("touchmove", {
    touches: [point, other], changedTouches: [point, other]
  }), unchanged);
  assert.deepEqual(f.emit("click", { timeStamp: 190 }), unchanged);
});
