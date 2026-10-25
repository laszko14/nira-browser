const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");

const bridge = fs.readFileSync("app/src/main/assets/extensions/nira-media-detector/playback-bridge.js", "utf8");
const native = fs.readFileSync("app/src/main/java/com/prirai/android/nira/media/MediaElementPlaybackBridge.kt", "utf8");
const browser = fs.readFileSync("app/src/main/java/com/prirai/android/nira/BrowserFragment.kt", "utf8");
const detector = fs.readFileSync("app/src/main/java/com/prirai/android/nira/media/MediaDetectorController.kt", "utf8");
const manifest = JSON.parse(fs.readFileSync("app/src/main/assets/extensions/nira-media-detector/manifest.json", "utf8"));
const build = fs.readFileSync("app/build.gradle", "utf8");
const ci = fs.readFileSync(".github/workflows/apk-release.yml", "utf8");

function harness(videos) {
  const messages = [];
  const callbacks = {};
  const port = {
    postMessage: message => { if(message.event !== "diagnostic") messages.push(message); },
    onMessage: { addListener: fn => { callbacks.message = fn; } },
    onDisconnect: { addListener: fn => { callbacks.disconnect = fn; } },
  };
  class HTMLVideoElement {
    constructor(url, paused = false) {
      this.currentSrc = url;
      this.src = url;
      this.paused = paused;
      this.isConnected = true;
      this.ended = false;
      this.error = null;
      this.mediaKeys = null;
      this.pauses = 0;
      this.plays = 0;
    }
    pause() { this.pauses++; this.paused = true; }
    play() { this.plays++; this.paused = false; return Promise.resolve(); }
  }
  const location = { href: "https://site.example/watch", protocol: "https:" };
  const document = {
    readyState: "complete",
    querySelectorAll: () => videos,
    addEventListener: () => {},
  };
  vm.runInNewContext(bridge, {
    browser: { runtime: { connectNative: name => name === "nira.media.playback" ? port : null } },
    location, document, HTMLVideoElement, URL,
  }, { timeout: 1000 });
  return { callbacks, messages, HTMLVideoElement, location };
}
const nonce = "4eaf885e-1bdf-41f9-a66a-33d95ad1635c";

test("v1131 bridge pauses exactly one matching connected HTMLVideoElement and restores it", () => {
  const video = { currentSrc:"https://cdn.example/movie.mp4", src:"https://cdn.example/movie.mp4",
    paused:false, isConnected:true, ended:false, error:null, mediaKeys:null, pauses:0, plays:0,
    pause(){this.pauses++;this.paused=true;},
    play(){this.plays++;this.paused=false;return Promise.resolve();} };
  // The injected script requires a genuine HTMLVideoElement. Construct one
  // in the same VM realm by exposing a class and using its prototype below.
  const items=[video];
  const h=harness(items);
  Object.setPrototypeOf(video,h.HTMLVideoElement.prototype);
  h.callbacks.message({version:1,event:"prepare",nonce,
    pageUrl:h.location.href, mediaUrl:video.src});
  assert.equal(video.pauses,1);
  assert.equal(h.messages.length,1);
  assert.equal(h.messages[0].ok,true);
  h.callbacks.message({version:1,event:"restore",nonce});
  assert.equal(video.plays,1);
  h.callbacks.message({version:1,event:"restore",nonce});
  assert.equal(video.plays,1,"restore is one-shot");
});

test("v1131 refuses ambiguous same-source players, MSE blobs and stale navigation", () => {
  const make = () => ({ currentSrc:"https://cdn.example/x.m3u8",src:"https://cdn.example/x.m3u8",
    paused:false,isConnected:true,ended:false,error:null,mediaKeys:null,pauses:0,plays:0,
    pause(){this.pauses++;this.paused=true;},
    play(){this.plays++;return Promise.resolve();} });
  const one=make(),two=make();
  const ambiguous=harness([one,two]);
  for(const v of [one,two]) Object.setPrototypeOf(v,ambiguous.HTMLVideoElement.prototype);
  ambiguous.callbacks.message({version:1,event:"prepare",nonce,pageUrl:ambiguous.location.href,mediaUrl:one.src});
  assert.equal(ambiguous.messages[0].ok,false);
  assert.equal(one.pauses+two.pauses,0);

  const video=make(),fresh=harness([video]);
  Object.setPrototypeOf(video,fresh.HTMLVideoElement.prototype);
  fresh.callbacks.message({version:1,event:"prepare",nonce,pageUrl:fresh.location.href,mediaUrl:video.src});
  assert.equal(fresh.messages[0].ok,true);
  fresh.location.href="https://site.example/next";
  fresh.callbacks.message({version:1,event:"restore",nonce});
  assert.equal(video.plays,0);

  const blob = make(),other=harness([blob]);
  Object.setPrototypeOf(blob,other.HTMLVideoElement.prototype);
  blob.currentSrc="blob:https://site.example/123";
  blob.src="blob:https://site.example/123";
  other.callbacks.message({version:1,event:"prepare",nonce,pageUrl:other.location.href,mediaUrl:"https://cdn.example/x.m3u8"});
  assert.equal(other.messages[0].ok,false);
});

test("v1131 native bridge fail-closed on session, frame, document and token", () => {
  assert.match(native, /it\.engineSession === tab\.binding\.session/);
  assert.match(native, /runCatching \{ it\.senderUrl\(\) \}\.getOrNull\(\) == frame/);
  assert.match(native, /candidates\.singleOrNull/);
  assert.match(native, /ACK_TIMEOUT_MS/);
  assert.match(native, /p\.port !== port/);
  assert.match(native, /matches\(l\.binding, actual\.binding\)/);
  assert.match(browser, /MediaElementPlaybackBridge\.prepare/);
  assert.match(browser, /MediaElementPlaybackBridge\.restore/);
  assert.match(detector, /registerContentMessageHandler\([\s\S]*?MediaElementPlaybackBridge\.NATIVE_APP/);
  assert.ok(manifest.content_scripts[0].js.includes("playback-bridge.js"));
});

test("v1131 keeps original Zavro untouched; signed MPV app version increments", () => {
  assert.match(build, /applicationId = "com\.prirai\.android\.nira\.mpv"/);
  assert.match(build, /versionCode = 1\d{2}/);
  assert.match(ci, /EXPECTED_VERSION_CODE: "11\d+"/);
  assert.match(ci, /zavro-x-mpv-arm64-v11\d+/);
  assert.doesNotMatch(build,/applicationIdSuffix\s*=/);
});
