const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const { webcrypto } = require("node:crypto");

const js = fs.readFileSync("app/src/main/assets/extensions/nira-media-detector/playback-bridge.js", "utf8");
const native = fs.readFileSync("app/src/main/java/com/prirai/android/nira/media/MediaElementPlaybackBridge.kt", "utf8");
const browser = fs.readFileSync("app/src/main/java/com/prirai/android/nira/BrowserFragment.kt", "utf8");
const policy = fs.readFileSync("app/src/main/java/com/prirai/android/nira/media/VideoPlayerPolicy.kt", "utf8");

function harness(url = "https://cdn.example/film.mp4", options = {}) {
  const messages = [], listeners = {}, callbacks = {};
  const port = {
    postMessage: m => messages.push(m),
    onMessage: { addListener: cb => callbacks.message = cb },
    onDisconnect: { addListener: cb => callbacks.disconnect = cb },
  };
  class Video {
    constructor() {
      this.currentSrc = this.src = url;
      this.srcObject = null; this.mediaKeys = null; this.isConnected = true;
      this.ended = false; this.error = null; this.muted = false; this.loop = false;
      this.paused = false; this.duration = 1200;
      this.parentElement = { contains: target => target === this };
      this.pauses = this.plays = 0;
    }
    getBoundingClientRect() { return { width: 640, height: 360 }; }
    pause() { this.pauses++; this.paused = true; }
    play() { this.plays++; this.paused = false; return Promise.resolve(); }
  }
  const video = new Video();
  Object.assign(video, options);
  const document = {
    readyState: "complete",
    addEventListener: (type, fn) => { listeners[type] = fn; },
    querySelectorAll: () => [video],
  };
  const location = { href: "https://site.example/watch", protocol: "https:" };
  vm.runInNewContext(js, {
    browser: { runtime: { connectNative: name =>
      name === "nira.media.playback" ? port : null } },
    crypto: webcrypto, HTMLVideoElement: Video, document, location, URL, Date,
  }, { timeout: 1000 });
  const playEvent = () => listeners.play({ target: video, isTrusted: true });
  const gesture = (trusted = true) =>
    listeners.pointerup({ target: video, type: "pointerup", isTrusted: trusted });
  return { video, messages, callbacks, playEvent, gesture, location };
}

test("v1132 offers only after trusted user gesture on visible long-form direct video", () => {
  const h = harness();
  h.playEvent(); assert.equal(h.messages.length, 0, "autoplay stays in Gecko");
  h.gesture(false); h.playEvent(); assert.equal(h.messages.length, 0);
  h.gesture(); h.playEvent();
  assert.equal(h.messages.length, 1);
  const offer = h.messages[0];
  assert.equal(offer.event, "auto_offer");
  assert.match(offer.frameKey, /^[0-9a-f-]{36}$/);
  assert.match(offer.elementKey, /^[0-9a-f-]{36}$/);
  assert.equal(offer.mediaUrl, h.video.src);
  assert.equal(offer.pageUrl, h.location.href);
  h.callbacks.message({version:1,event:"prepare",nonce:"8ccf6254-a401-4c14-8948-90dd782c2763",
    pageUrl:offer.pageUrl,mediaUrl:offer.mediaUrl,
    frameKey:offer.frameKey,elementKey:offer.elementKey});
  assert.equal(h.video.pauses, 1);
  assert.equal(h.messages[1].ok, true);
  h.video.paused = false;
  h.playEvent();
  assert.equal(h.video.paused, true, "site cannot replay leased video beside MPV");
  h.callbacks.message({version:1,event:"cancel",nonce:"8ccf6254-a401-4c14-8948-90dd782c2763"});
  assert.equal(h.video.plays, 0, "cancel does not autoplay");
});

test("v1132 rejects mismatched frame/element and retains Gecko for ads, MSE and short video", () => {
  const h = harness();
  h.gesture(); h.playEvent();
  const offer = h.messages[0];
  h.callbacks.message({version:1,event:"prepare",nonce:"8ccf6254-a401-4c14-8948-90dd782c2763",
    pageUrl:offer.pageUrl,mediaUrl:offer.mediaUrl,
    frameKey:offer.frameKey,elementKey:"f00deece-3c66-47a6-b52d-2eab95487384"});
  assert.equal(h.messages.at(-1).ok, false);
  assert.equal(h.video.pauses, 0);
  for (const options of [
    {duration:25}, {muted:true}, {currentSrc:"blob:https://site.example/x",src:"blob:https://site.example/x"},
    {mediaKeys:{}}, {srcObject:{}}, {currentSrc:"https://ads.example/preroll.mp4",src:"https://ads.example/preroll.mp4"}
  ]) {
    const x = harness("https://cdn.example/film.mp4", options);
    x.gesture(); x.playEvent();
    assert.equal(x.messages.length, 0, "rejected player remains under Gecko control");
  }
});

test("v1132 native, UI and package policy remain fail-closed", () => {
  assert.match(native, /val elementKey: String/);
  assert.match(native, /it === target\.port/);
  assert.match(native, /data\.optString\("event"\) == "auto_offer"/);
  assert.match(native, /fun discard\(token: String\)/);
  assert.match(browser, /MediaElementPlaybackBridge\.onAutoOffer = auto@/);
  assert.match(browser, /target != null && leaseId == null/);
  assert.match(browser, /VideoPlayerPolicy\.Mode\.AUTO/);
  assert.match(policy, /enum class Mode \{ AUTO, MPV, WEB, EXTERNAL \}/);
  assert.match(policy, /if \(isPrivate\) return \/\/ Never persist/);
  assert.match(policy, /Mode\.MPV/);
});
