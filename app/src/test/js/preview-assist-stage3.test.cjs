"use strict";
// Stage 3/10: real shipped WebExtension script in deterministic synthetic DOM.
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const script = fs.readFileSync(path.join(__dirname,
  "../../main/assets/extensions/nira-media-detector/preview-assist.js"), "utf8");

// The failing old behavior is a page-owned button with an image child:
// the old handler interpreted img as a preview request and suppressed click.
function fixture({ controlLabel = null, autoplay = false, muted = false,
                   declared = true, explicitControlTarget = false } = {}) {
  const calls = { plays: 0, prevented: 0, stopped: 0 };
  const source = "https://cdn.example.test/sample-preview.mp4";
  const video = {
    tagName: "VIDEO", currentSrc: source, src: source,
    paused: true, isConnected: true, autoplay, muted, playsInline: true,
    dataset: {}, querySelector: () => null,
    play() { calls.plays++; return Promise.resolve(); }
  };
  const link = { href: "https://site.example.test/watch/123" };
  const card = {
    tagName: "ARTICLE", closest: () => null,
    getAttribute: key => declared && key === "data-preview-src" ? source : null,
    querySelectorAll: () => [],
    querySelector: key => key === "video" ? video :
      key === "a[href]" ? link : null
  };
  const thumb = { closest: key => key === "a[href]" ? link : card };
  const control = controlLabel === null ? null : {
    tagName: "BUTTON", textContent: controlLabel,
    className: "site-player-button",
    getAttribute: () => null,
    closest: key => key === "nav,header,footer" ? null :
      key === "a[href]" ? link : card
  };
  const target = {
    tagName: explicitControlTarget ? "BUTTON" : "IMG",
    closest(selector) {
      if (selector.startsWith("button,")) return control;
      if (selector === "video") return null;
      if (selector.startsWith("img,video,")) {
        return explicitControlTarget ? null : thumb;
      }
      return null;
    }
  };
  const handlers = [];
  vm.runInNewContext(script, {
    document: {
      baseURI: "https://site.example.test/",
      addEventListener(name, listener, options) {
        if (name === "click") handlers.push({ listener, options });
      }
    },
    location: { href: "https://site.example.test/" }, URL
  });
  assert.equal(handlers.length, 1, "only one click listener");
  assert.equal(handlers[0].options.capture, true);
  function tap(overrides = {}) {
    const before = { ...calls };
    handlers[0].listener({
      isTrusted: true, cancelable: true, defaultPrevented: false,
      button: 0, target, ...overrides,
      preventDefault() { calls.prevented++; },
      stopImmediatePropagation() { calls.stopped++; }
    });
    return {
      plays: calls.plays - before.plays,
      prevented: calls.prevented - before.prevented,
      stopped: calls.stopped - before.stopped
    };
  }
  return { tap, calls, video };
}

test("stage3: site-owned ordinary Play button with nested image is untouched", () => {
  const f = fixture({ controlLabel: "Play" });
  assert.deepEqual(f.tap(), { plays: 0, prevented: 0, stopped: 0 });
  assert.equal(f.video.muted, false, "page player volume not changed");
});
test("stage3: non-preview button inside linked thumbnail stays site-owned", () => {
  const f = fixture({ controlLabel: "Show details" });
  assert.deepEqual(f.tap(), { plays: 0, prevented: 0, stopped: 0 });
});
test("stage3: temporarily paused muted autoplay video stays site-owned", () => {
  const f = fixture({ autoplay: true, muted: true });
  assert.deepEqual(f.tap(), { plays: 0, prevented: 0, stopped: 0 });
  assert.equal(f.video.muted, true);
});
test("stage3: muted autoplay does not block an explicit Preview control", () => {
  const f = fixture({
    controlLabel: "Preview", autoplay: true,
    muted: true, explicitControlTarget: true
  });
  assert.deepEqual(f.tap(), { plays: 1, prevented: 1, stopped: 1 });
});
test("stage3: explicit thumbnail preview still works without page-owned controls", () => {
  const f = fixture();
  assert.deepEqual(f.tap(), { plays: 1, prevented: 1, stopped: 1 });
  assert.equal(f.video.muted, true);
});
test("stage3: a video not already muted remains eligible for explicit thumb assist", () => {
  const f = fixture({ autoplay: true, muted: false });
  assert.deepEqual(f.tap(), { plays: 1, prevented: 1, stopped: 1 });
  assert.equal(f.video.muted, true);
});
test("stage3: rejected, modified or synthetic actions stay with the webpage", () => {
  for (const props of [{ ctrlKey: true }, { isTrusted: false },
                       { defaultPrevented: true }, { button: 1 }]) {
    const f = fixture();
    assert.deepEqual(f.tap(props), { plays: 0, prevented: 0, stopped: 0 });
  }
});
