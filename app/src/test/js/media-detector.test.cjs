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

test("background validates without changing signed URLs or trusting supplied IDs", async () => {
  const { receive } = background();
  const message = candidate("https://example.org/flower.mp4?signature=secret");
  assert.equal(JSON.stringify(await receive(message, sender)), JSON.stringify(message));
  assert.equal(await receive({ ...message, tabId: "spoofed" }, sender), null);
  assert.equal(await receive(message, { ...sender, id: "other-extension" }), null);
  assert.equal(await receive(message, { ...sender, frameId: 1 }), null);
  assert.equal(await receive(message, { id: "detector" }), null);
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

test("webRequest probe is restricted to the fixture and sends one URL-free signal", () => {
  const { probe, native, filter } = background();
  assert.equal(filter.urls[0], "https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4*");
  probe(); probe();
  assert.equal(native.length, 1);
  assert.equal(JSON.stringify(native[0]), JSON.stringify([
    "nira.media.detector", { schemaVersion: 1, event: "network_probe" },
  ]));
});

test("playing goes through background validation before session-bound native messaging", async () => {
  const { receive } = background();
  let playing, now = 0;
  const native = [];
  class HTMLVideoElement {}
  const video = Object.assign(new HTMLVideoElement(), {
    currentSrc: "https://example.org/flower.mp4?token=secret", paused: false, isConnected: true,
  });
  const browser = { runtime: {
    sendMessage: message => receive(message, sender),
    sendNativeMessage: async (...args) => { native.push(args); },
  } };
  vm.runInNewContext(fs.readFileSync(path.join(assets, "content.js"), "utf8"), {
    browser, HTMLVideoElement, performance: { now: () => now },
    document: { defaultView: {}, addEventListener: (name, callback, options) => {
      assert.equal(name, "playing"); assert.equal(options.capture, true); playing = callback;
    } },
  });
  await playing({ isTrusted: false, target: video });
  assert.equal(native.length, 0);
  await playing({ isTrusted: true, target: video });
  assert.equal(native.length, 1);
  assert.equal(native[0][1].url, video.currentSrc);
  await playing({ isTrusted: true, target: video });
  assert.equal(native.length, 1);
  now = 1001;
  video.currentSrc = "blob:https://example.org/a";
  await playing({ isTrusted: true, target: video });
  assert.equal(native.length, 1);
});
