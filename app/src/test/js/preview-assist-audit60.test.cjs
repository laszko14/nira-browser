"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

// Execute the actual shipped WebExtension script in a controlled DOM contract.
const previewScript = fs.readFileSync(path.join(__dirname,
  "../../main/assets/extensions/nira-media-detector/preview-assist.js"), "utf8");

function exercise({
  declared = false, previewControl = false, ordinaryPlayControl = false,
  targetKind = "image", sameVideo = true, muted = false, loop = false,
  paused = true, modifiers = {}, linkFromThumb = true
} = {}) {
  const declaredSource = "https://cdn.example.test/thumbnail-preview.mp4";
  const videoSource = sameVideo ? declaredSource : "https://cdn.example.test/movie.mp4";
  const calls = { plays: 0, prevented: 0, stopped: 0 };
  let click;
  let card;
  const link = { tagName: "A" };
  const video = {
    tagName: "VIDEO", paused, isConnected: true,
    muted, defaultMuted: muted, loop, playsInline: true,
    currentSrc: videoSource, src: videoSource, dataset: {},
    hasAttribute: name => name === "playsinline",
    querySelector: () => null,
    play: () => { calls.plays++; return Promise.resolve(); }
  };
  card = {
    tagName: "ARTICLE",
    closest: () => null,
    getAttribute: name => declared && name === "data-preview-src" ? declaredSource : null,
    querySelectorAll: () => [],
    querySelector: selector => selector === "video" ? video :
      selector === "a[href]" ? link : null
  };
  link.closest = selector => selector === "a[href]" ? link : card;
  const thumb = {
    tagName: "IMG",
    closest: selector => selector === "a[href]" ?
      (linkFromThumb ? link : null) : card
  };
  const control = {
    tagName: "BUTTON",
    className: previewControl ? "preview-play" : "play",
    textContent: previewControl ? "Preview" : "Play",
    getAttribute: () => null,
    closest: selector => selector === "a[href]" ? link :
      selector === "nav,header,footer" ? null : card
  };
  const target = {
    tagName: targetKind === "video" ? "VIDEO" :
      targetKind === "control" ? "BUTTON" : "IMG",
    closest: selector => {
      if (selector.startsWith("button,")) {
        return targetKind === "control" ? control : null;
      }
      if (selector === "video") return targetKind === "video" ? video : null;
      if (selector.startsWith("img,video,")) {
        return targetKind === "control" ? null :
          targetKind === "video" ? video : thumb;
      }
      return null;
    }
  };
  vm.runInNewContext(previewScript, {
    document: {
      baseURI: "https://example.test/",
      addEventListener: (name, fn) => { if (name === "click") click = fn; }
    },
    location: { href: "https://example.test/" },
    URL
  });
  assert.equal(typeof click, "function");
  click({
    isTrusted: true, cancelable: true, defaultPrevented: false, button: 0,
    target, ...modifiers,
    preventDefault: () => { calls.prevented++; },
    stopImmediatePropagation: () => { calls.stopped++; }
  });
  return { calls, video };
}

test("audit60 18A: ordinary playsinline video in linked card is untouched", () => {
  const { calls, video } = exercise({ sameVideo: false });
  assert.deepEqual(calls, { plays: 0, prevented: 0, stopped: 0 });
  assert.equal(video.muted, false);
  assert.equal(video.playsInline, true);
});

test("audit60 18A: muted looping movie without declared preview still navigates", () => {
  const { calls, video } = exercise({ muted: true, loop: true, sameVideo: false });
  assert.deepEqual(calls, { plays: 0, prevented: 0, stopped: 0 });
  assert.equal(video.muted, true);
});

test("audit60 18A: ordinary Play control is not an explicit preview control", () => {
  const { calls, video } = exercise({
    targetKind: "control", ordinaryPlayControl: true, sameVideo: false
  });
  assert.equal(calls.plays, 0);
  assert.equal(calls.prevented, 0);
  assert.equal(video.muted, false);
});

test("audit60 18A: native video surface is never captured even with preview URL", () => {
  const { calls, video } = exercise({ declared: true, targetKind: "video" });
  assert.equal(calls.plays, 0);
  assert.equal(calls.prevented, 0);
  assert.equal(video.muted, false);
});

test("audit60 18A: declared preview of the same video still starts", () => {
  const { calls, video } = exercise({ declared: true });
  assert.deepEqual(calls, { plays: 1, prevented: 1, stopped: 1 });
  assert.equal(video.muted, true);
});

test("audit60 18A: declared preview cannot mute another video in the card", () => {
  const { calls, video } = exercise({ declared: true, sameVideo: false });
  assert.deepEqual(calls, { plays: 0, prevented: 0, stopped: 0 });
  assert.equal(video.muted, false);
});

test("audit60 18A: preview button is a deliberate independent user gesture", () => {
  const { calls, video } = exercise({
    targetKind: "control", previewControl: true
  });
  assert.deepEqual(calls, { plays: 1, prevented: 1, stopped: 1 });
  assert.equal(video.muted, true);
});

test("audit60 18A: non-linked thumbnail cannot capture an unrelated card link", () => {
  const { calls, video } = exercise({ declared: true, linkFromThumb: false });
  assert.deepEqual(calls, { plays: 0, prevented: 0, stopped: 0 });
  assert.equal(video.muted, false);
});

test("audit60 18A: Ctrl/Shift click keeps normal navigation", () => {
  for (const modifiers of [{ ctrlKey: true }, { shiftKey: true }, { metaKey: true }]) {
    const { calls, video } = exercise({ declared: true, modifiers });
    assert.deepEqual(calls, { plays: 0, prevented: 0, stopped: 0 });
    assert.equal(video.muted, false);
  }
});

test("audit60 18A: already playing previews are not restarted or intercepted", () => {
  const { calls } = exercise({ declared: true, paused: false });
  assert.deepEqual(calls, { plays: 0, prevented: 0, stopped: 0 });
});
