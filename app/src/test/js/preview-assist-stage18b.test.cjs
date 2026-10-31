"use strict";

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

function harness() {
  let click;
  const created = [];
  function newVideo() {
    const video = {
      tagName: "VIDEO", src: "", currentSrc: "", paused: true,
      muted: false, defaultMuted: false, loop: false, playsInline: false,
      isConnected: false, dataset: {}, style: {},
      setAttribute() {},
      querySelector: () => null,
      play() {
        video.owner.calls++;
        return video.owner.behavior(video);
      },
      remove() {
        if (video.owner && video.owner.video === video) video.owner.video = null;
        video.isConnected = false;
      }
    };
    created.push(video);
    return video;
  }
  function card(name, source = "https://cdn.example.test/clip.mp4") {
    const item = {
      source, video: null, calls: 0,
      behavior: () => Promise.resolve(),
      closest: () => null,
      querySelectorAll: () => [],
      getAttribute: key => key === "data-preview-src" ? item.source : null
    };
    const link = { href: "https://example.test/watch/" + name };
    const layer = {
      style: { position: "relative" },
      appendChild(v) { v.owner = item; v.isConnected = true; item.video = v; }
    };
    const image = { parentElement: layer };
    item.querySelector = selector => selector === "video" ? item.video :
      selector === "img" ? image : selector === "a[href]" ? link : null;
    item.link = link;
    item.attachExisting = () => {
      const v = newVideo();
      v.src = item.source;
      v.currentSrc = item.source;
      layer.appendChild(v);
      return v;
    };
    const thumb = {
      tagName: "IMG",
      closest: selector => selector === "a[href]" ? link : item
    };
    const target = {
      tagName: "IMG",
      closest: selector => selector.startsWith("button,") ? null :
        selector === "video" ? null : selector.startsWith("img,video,") ?
          thumb : null
    };
    item.tap = (overrides = {}) => {
      const flags = { prevented: 0, stopped: 0 };
      click({
        isTrusted: true, cancelable: true, defaultPrevented: false, button: 0,
        target, ...overrides,
        preventDefault() { flags.prevented++; },
        stopImmediatePropagation() { flags.stopped++; }
      });
      return flags;
    };
    return item;
  }
  vm.runInNewContext(code, {
    document: {
      baseURI: "https://example.test/",
      addEventListener: (event, fn) => { if (event === "click") click = fn; },
      createElement: type => {
        assert.equal(type, "video");
        return newVideo();
      }
    },
    getComputedStyle: () => ({ position: "relative" }),
    location: { href: "https://example.test/" },
    URL
  });
  assert.equal(typeof click, "function");
  return { card, created };
}

test("audit60 18B: generated preview rejection restores next-tap navigation", async () => {
  const h = harness(), item = h.card("a"), play = deferred();
  item.behavior = () => play.promise;
  assert.deepEqual(item.tap(), { prevented: 1, stopped: 1 });
  assert.equal(item.calls, 1);
  assert.equal(h.created.length, 1);
  play.reject(new Error("unsupported video"));
  await Promise.resolve();
  assert.equal(item.video, null, "failed generated node was removed");
  assert.deepEqual(item.tap(), { prevented: 0, stopped: 0 });
  assert.equal(item.calls, 1, "no second preview or accidental download");
  assert.equal(h.created.length, 1, "no newly generated video after rejection");
});

test("audit60 18B: existing video rejection returns mute and navigation to page", async () => {
  const h = harness(), item = h.card("existing"), v = item.attachExisting();
  const play = deferred();
  item.behavior = () => play.promise;
  assert.deepEqual(item.tap(), { prevented: 1, stopped: 1 });
  assert.equal(v.muted, true);
  play.reject(new Error("bad format"));
  await Promise.resolve();
  assert.equal(v.muted, false);
  assert.equal(v.playsInline, false);
  assert.deepEqual(item.tap(), { prevented: 0, stopped: 0 });
  assert.equal(item.calls, 1);
});

test("audit60 18B: synchronous play throw does not steal first or next click", () => {
  const h = harness(), item = h.card("throw");
  item.behavior = () => { throw Error("play unavailable"); };
  assert.deepEqual(item.tap(), { prevented: 0, stopped: 0 });
  assert.equal(item.video, null);
  assert.deepEqual(item.tap(), { prevented: 0, stopped: 0 });
  assert.equal(h.created.length, 1);
  assert.equal(item.calls, 1);
});

test("audit60 18B: changing preview URL permits a new deliberate attempt", async () => {
  const h = harness(), item = h.card("change");
  const first = deferred();
  item.behavior = () => first.promise;
  assert.equal(item.tap().prevented, 1);
  first.reject(new Error("rejected A"));
  await Promise.resolve();
  assert.equal(item.tap().prevented, 0);
  item.source = "https://cdn.example.test/second.mp4";
  item.behavior = () => Promise.resolve();
  assert.deepEqual(item.tap(), { prevented: 1, stopped: 1 });
  assert.equal(item.calls, 2);
  assert.equal(h.created.length, 2);
});

test("audit60 18B: changing link target permits retry on recycled same card", async () => {
  const h = harness(), item = h.card("recycle"), bad = deferred();
  item.behavior = () => bad.promise;
  assert.equal(item.tap().prevented, 1);
  bad.reject(Error("rejected"));
  await Promise.resolve();
  assert.equal(item.tap().prevented, 0);
  item.link.href = "https://example.test/watch/new-movie";
  item.behavior = () => Promise.resolve();
  assert.equal(item.tap().prevented, 1);
  assert.equal(item.calls, 2);
});

test("audit60 18B: independent card and fresh DOM owner are not poisoned", async () => {
  const h = harness(), a = h.card("a"), b = h.card("b"), bad = deferred();
  a.behavior = () => bad.promise;
  assert.equal(a.tap().prevented, 1);
  bad.reject(Error("rejected"));
  await Promise.resolve();
  assert.equal(a.tap().prevented, 0);
  assert.equal(b.tap().prevented, 1);
  assert.equal(h.card("a").tap().prevented, 1);
});

test("audit60 18B: successful preview remains captured and playable", async () => {
  const h = harness(), item = h.card("success");
  item.behavior = () => Promise.resolve();
  assert.deepEqual(item.tap(), { prevented: 1, stopped: 1 });
  await Promise.resolve();
  assert.equal(item.calls, 1);
  assert.equal(item.video.muted, true);
});

test("audit60 18B: stale source rejection cannot poison a newer source", async () => {
  const h = harness(), item = h.card("stale"), old = deferred();
  item.behavior = () => old.promise;
  assert.equal(item.tap().prevented, 1);
  const oldVideo = item.video;
  oldVideo.remove();
  item.source = "https://cdn.example.test/new.mp4";
  item.behavior = () => Promise.resolve();
  assert.equal(item.tap().prevented, 1);
  const newVideo = item.video;
  old.reject(Error("late rejection from A"));
  await Promise.resolve();
  assert.equal(item.video, newVideo, "old rejection cannot remove the new video");
  assert.equal(item.tap().prevented, 1, "the new source is not marked failed");
});

test("audit60 18B: after rejection an ordinary modifier click remains untouched", async () => {
  const h = harness(), item = h.card("mod"), bad = deferred();
  item.behavior = () => bad.promise;
  assert.equal(item.tap().prevented, 1);
  bad.reject(Error("rejected"));
  await Promise.resolve();
  assert.deepEqual(item.tap({ ctrlKey: true }), { prevented: 0, stopped: 0 });
  assert.deepEqual(item.tap(), { prevented: 0, stopped: 0 });
});
