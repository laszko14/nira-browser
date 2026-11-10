"use strict";
// Stage 5/10: real nira-bg-play scripts under isolated synthetic VM worlds.
// No GeckoView, real website, device or private-profile runtime test.
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const dir = path.resolve(__dirname, "../../main/assets/extensions/nira-bg-play");
const contentJS = fs.readFileSync(path.join(dir, "content.js"), "utf8");
const backgroundJS = fs.readFileSync(path.join(dir, "background.js"), "utf8");

async function drain() { for (let i = 0; i < 10; i++) await Promise.resolve(); }
function page({ allowed = true, timeout = false, background = false, options = {} } = {}) {
  const calls = { injected: 0, plays: 0, pauses: 0, blocked: 0 };
  const videoEvents = new Map(), pageEvents = new Map();
  const video = {
    paused: true, ended: false, isConnected: true,
    src: "https://media.test/full.mp4", currentSrc: "https://media.test/full.mp4",
    muted: false, loop: false, autoplay: false, dataset: {},
    addEventListener(name, handler) {
      if (!videoEvents.has(name)) videoEvents.set(name, []);
      videoEvents.get(name).push(handler);
    },
    play() {
      calls.plays++;
      this.paused = false;
      for (const handler of videoEvents.get("play") || []) handler();
      return Promise.resolve();
    },
    pause() { calls.pauses++; this.paused = true; },
    ...options
  };
  let nativeHidden = background;
  const document = {
    readyState: "complete",
    querySelectorAll: selector => selector === "video" ? [video] : [],
    addEventListener(name, handler) {
      if (!pageEvents.has(name)) pageEvents.set(name, []);
      pageEvents.get(name).push(handler);
    },
    createElement(name) {
      assert.equal(name, "script");
      return { textContent: "", parentNode: null };
    }
  };
  Object.defineProperty(document, "hidden", {
    configurable: true, get() { return nativeHidden; }
  });
  const root = {
    appendChild(script) {
      script.parentNode = root;
      calls.injected++;
      vm.runInContext(script.textContent, pageWorld);
    },
    removeChild(script) { script.parentNode = null; }
  };
  document.documentElement = root;
  const window = {
    location: { origin: "https://site.test" },
    addEventListener: (name, handler) => document.addEventListener(name, handler)
  };
  const pageWorld = vm.createContext({
    document, window, navigator: {}, Promise, Date, Object,
    MutationObserver: class { observe() {} }
  });
  const browser = {
    runtime: { sendMessage: () =>
      timeout ? new Promise(() => {}) : Promise.resolve({ allowed }) }
  };
  const timers = [];
  vm.runInNewContext(contentJS, {
    browser, document, window, Promise,
    setTimeout(fn) { timers.push(fn); return timers.length; },
    clearTimeout() {}
  });
  return {
    video, calls, timers,
    setNativeHidden(value) { nativeHidden = value; },
    nativePause() {
      video.paused = true;
      for (const cb of videoEvents.get("pause") || []) cb();
    },
    pageEvent(name) {
      const event = {
        stopImmediatePropagation() { calls.blocked++; },
        stopPropagation() {}, preventDefault() {}
      };
      for (const cb of pageEvents.get(name) || []) cb(event);
    }
  };
}
test("stage5: non-allowed origin cannot patch or intercept the page", async () => {
  const f = page({ allowed: false });
  await drain();
  assert.equal(f.calls.injected, 0);
  f.pageEvent("visibilitychange");
  assert.equal(f.calls.blocked, 0);
  f.nativePause();
  await drain();
  assert.equal(f.calls.plays, 0);
});
test("stage5: missing host response times out fail-closed", async () => {
  const f = page({ timeout: true });
  for (const fire of f.timers) fire();
  await drain();
  assert.equal(f.calls.injected, 0);
});
test("stage5: allowed origin installs the page lifecycle guard once", async () => {
  const f = page();
  await drain();
  assert.equal(f.calls.injected, 1);
  f.pageEvent("visibilitychange");
  assert.ok(f.calls.blocked > 0);
});
test("stage5: native background pause resumes previously playing full media", async () => {
  const f = page({ background: true });
  await drain();
  await f.video.play();
  f.nativePause();
  await drain();
  assert.equal(f.calls.plays, 2);
  assert.equal(f.video.paused, false);
});
test("stage8: a foreground pause event stays paused even when the site bypasses video.pause", async () => {
  const f = page();
  await drain();
  await f.video.play();
  f.nativePause();
  await drain();
  assert.equal(f.calls.plays, 1);
  assert.equal(f.video.paused, true);
  f.setNativeHidden(true);
  f.nativePause();
  await drain();
  assert.equal(f.calls.plays, 1, "later background event must not revive foreground stop");
});
test("stage8: delayed recovery does not resume after returning to foreground", async () => {
  const f = page({ background: true });
  await drain();
  await f.video.play();
  f.nativePause();
  f.setNativeHidden(false);
  await drain();
  assert.equal(f.calls.plays, 1);
  assert.equal(f.video.paused, true);
});
test("stage5: never-played or deliberately paused media cannot be resurrected", async () => {
  const initial = page();
  await drain();
  initial.nativePause();
  await drain();
  assert.equal(initial.calls.plays, 0);
  const f = page();
  await drain();
  await f.video.play();
  f.video.pause();
  f.nativePause(); // A delayed pause event from a site-owned pause().
  await drain();
  assert.equal(f.calls.plays, 1);
  assert.equal(f.video.paused, true);
});
test("stage5: helper-created and muted site previews remain stopped", async () => {
  for (const options of [
    { dataset: { niraPreviewCreated: "1" } },
    { muted: true, loop: true },
    { muted: true, autoplay: true }
  ]) {
    const f = page({ options });
    await drain();
    await f.video.play();
    f.nativePause();
    await drain();
    assert.equal(f.calls.plays, 1, "preview was unexpectedly restarted");
  }
});
test("stage5: late recovery cannot revive detached or changed-source media", async () => {
  for (const invalidate of [
    f => { f.video.isConnected = false; },
    f => { f.video.currentSrc = "https://media.test/new.mp4"; }
  ]) {
    const f = page();
    await drain();
    await f.video.play();
    f.nativePause();
    invalidate(f); // happens before recovery microtask
    await drain();
    assert.equal(f.calls.plays, 1);
  }
});
function background() {
  let push, query, hydrate;
  const stored = [];
  const cache = new Promise(resolve => { hydrate = resolve; });
  const browser = {
    storage: { local: {
      get() { return cache; },
      set(v) { stored.push(v); return Promise.resolve(); }
    } },
    runtime: {
      connectNative() {
        return { onMessage: { addListener(fn) { push = fn; } } };
      },
      onMessage: { addListener(fn) { query = fn; } }
    }
  };
  vm.runInNewContext(backgroundJS, { browser, Set, Promise, URL });
  return {
    host: origins => push({ type: "set", origins }),
    cached: origins => hydrate({ allowlist: origins }),
    isAllowed: origin => query({ type: "nira:bgplay:isAllowed", origin }),
    stored
  };
}
test("stage5: a late stale cache cannot override live allowlist", async () => {
  const f = background();
  f.host(["https://fresh.test:443"]);
  f.cached(["https://stale.test:443"]);
  await drain();
  assert.equal((await f.isAllowed("https://fresh.test")).allowed, true);
  assert.equal((await f.isAllowed("https://stale.test")).allowed, false);
  assert.deepEqual(Array.from(f.stored.at(-1).allowlist), ["https://fresh.test"]);
});
test("stage5: revocation remains in force despite late stale cache", async () => {
  const f = background();
  f.host([]);
  f.cached(["https://revoked.test"]);
  await drain();
  assert.equal((await f.isAllowed("https://revoked.test")).allowed, false);
});
test("stage5: cached state is only a fallback before authoritative host push", async () => {
  const f = background();
  f.cached(["https://cached.test:443"]);
  await drain();
  assert.equal((await f.isAllowed("https://cached.test")).allowed, true);
  f.host(["https://new.test:443"]);
  assert.equal((await f.isAllowed("https://cached.test")).allowed, false);
  assert.equal((await f.isAllowed("https://new.test")).allowed, true);
});
