"use strict";
// Stage 5/5 final regression: the actual shipped WebExtension in synthetic,
// deterministic DOM/Gecko transport. NOT a real GeckoView/site/device test.
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const root = path.resolve(__dirname, "../../../..");
const read = p => fs.readFileSync(path.join(root,p),"utf8");
const script = read("app/src/main/assets/extensions/zavro-preview-diagnostics/content.js");
const manifest = JSON.parse(read("app/src/main/assets/extensions/zavro-preview-diagnostics/manifest.json"));
const ONE = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
const TWO = "11111111-2222-3333-4444-555555555555";

function fixture({ subframe = false, failedPost = false, connectFailures = 0 } = {}) {
  let clock = 1000, inbound, onMutation, disconnected, observed = 0, disposed = 0;
  const packet = [], listeners = new Map(), scheduled = [];
  let connectCalls = 0;
  const changes = { play:0, pause:0, prevent:0, stop:0 };
  const doc = {
    querySelectorAll: () => [video, button, frame],
    createTreeWalker: () => ({ nextNode: () => null }),
    addEventListener(type, fn) { listeners.set("doc:"+type, fn); },
    removeEventListener(type, fn) {
      if (listeners.get("doc:"+type) === fn) listeners.delete("doc:"+type);
    }
  };
  function el(tag) {
    return {
      tagName:tag, id:"private-username", className:"site-css-secret",
      dataset:{}, parentElement:null, isConnected:true,
      getRootNode:()=>doc, hasAttribute:()=>true,
      getAttribute:()=>tag==="BUTTON" ? "Play" : "secret-session-token",
      getBoundingClientRect:()=>({x:9,y:8,width:250,height:140}),
      closest() { return this; }
    };
  }
  const video = Object.assign(el("VIDEO"),{
    paused:true,muted:false,autoplay:false,loop:false,controls:true,
    playsInline:true,poster:"https://site.test/private/media.mp4?secret=123",
    readyState:3,networkState:2,preload:"metadata",error:null,
    duration:32.5,currentTime:1.3,
    play() { changes.play++; return Promise.resolve(); },
    pause() { changes.pause++; }
  });
  const button = el("BUTTON"), frame = el("IFRAME");
  const win = {
    addEventListener(type, fn) { listeners.set("win:"+type,fn); },
    removeEventListener(type, fn) {
      if (listeners.get("win:"+type) === fn) listeners.delete("win:"+type);
    }
  };
  win.top = subframe ? {} : win;
  const port = {
    postMessage(m) {
      if(failedPost) throw Error("transport failed");
      packet.push(JSON.parse(JSON.stringify(m)));
    },
    onMessage:{addListener(cb){inbound=cb}},
    onDisconnect:{addListener(cb){disconnected=cb}}
  };
  vm.runInNewContext(script,{
    browser:{runtime:{connectNative(name) {
      assert.equal(name,"zavro.preview.forensics");
      connectCalls++;
      return connectCalls <= connectFailures ? null : port;
    }}}, document:doc, window:win,
    crypto:{getRandomValues(bytes) { bytes.fill(subframe ? 17 : 12); return bytes; }},
    NodeFilter:{SHOW_ELEMENT:1}, setTimeout:fn=>scheduled.push(fn),
    Date:class extends Date { static now(){return clock;} },
    getComputedStyle:()=>({display:"block",visibility:"visible",
      pointerEvents:"auto",zIndex:"7"}),
    MutationObserver:class {
      constructor(cb){onMutation=cb}
      observe(){observed++}
      disconnect(){disposed++}
    }
  }, { timeout:2000 });
  return {
    packets:packet, listeners, video, button, frame, changes, port,
    command(action, sessionId=ONE){inbound({schemaVersion:2,action,sessionId})},
    badCommand(value){inbound(value)},
    fire(type,target=video,pointerType="mouse") {
      const key=(type==="error"||type==="unhandledrejection")&&target===win
        ? "win:"+type : "doc:"+type;
      const fn=listeners.get(key);
      assert.ok(fn,"missing listener "+key);
      fn({type,target,pointerType,
        preventDefault(){changes.prevent++},
        stopImmediatePropagation(){changes.stop++}});
    },
    fireWindow(type){this.fire(type,win)},
    mutation(batch){onMutation(batch)},
    disconnect(){disconnected()},
    retry(){const fn=scheduled.shift();assert.ok(fn);fn()},
    connectCalls(){return connectCalls},
    pendingRetries(){return scheduled.length},
    clockTo(ms){clock=ms},
    stats(){return {observed,disposed,activeListeners:listeners.size}}
  };
}


test("stage1: shipped manifest, native expectation and running content handshake agree", () => {
  const native = read("app/src/main/java/com/prirai/android/nira/browser/MpvPlayPageBridgeController.kt");
  const scriptVersion = script.match(/const SCRIPT_VERSION = "([^"]+)"/)?.[1];
  const expectedNative = native.match(/EXPECTED_SCRIPT_VERSION = "([^"]+)"/)?.[1];
  assert.equal(scriptVersion, manifest.version);
  assert.equal(expectedNative, manifest.version);
  const f=fixture();
  assert.equal(f.packets[0].event,"ready");
  assert.equal(f.packets[0].scriptVersion, manifest.version);
  assert.equal(f.stats().activeListeners,0);
  assert.equal(f.stats().observed,0);
});

test("final5: probe manifest is HTTP(S) frame-scoped, passive until opt-in", () => {
  assert.deepEqual(manifest.content_scripts[0].matches, ["http://*/*","https://*/*"]);
  assert.equal(manifest.content_scripts[0].all_frames,true);
  assert.equal(manifest.content_scripts[0].match_about_blank,false);
  assert.deepEqual(manifest.permissions,
    ["nativeMessaging","nativeMessagingFromContent","geckoViewAddons"]);
  const f=fixture();
  f.badCommand({schemaVersion:2,action:"start",sessionId:"not-a-uuid"});
  assert.equal(f.packets.length,1);
  assert.equal(f.packets[0].event,"ready");
  assert.equal(f.packets[0].scriptVersion, manifest.version);
  assert.deepEqual(Object.keys(f.packets[0]).sort(),
    ["schemaVersion","event","documentId","frame","scriptVersion"].sort());
  assert.deepEqual(f.stats(),{observed:0,disposed:0,activeListeners:0});
  assert.equal(f.changes.play,0);
});

test("final5: normal HTML5 preview, site Play button, subframe and private data", () => {
  const top=fixture(), iframe=fixture({subframe:true});
  top.video.dataset.niraPreviewCreated="1";
  top.command("start");iframe.command("start");
  assert.ok(top.packets.length>=4);
  assert.equal(top.packets[0].event,"ready");
  assert.equal(top.packets.find(x=>x.event==="start_ack").sessionId,ONE);
  assert.ok(top.packets.some(x=>x.detail==="SNAPSHOT_FINISHED"));
  const media=top.packets.find(x=>x.kind==="MEDIA");
  assert.equal(media.metrics.frame,"TOP");
  assert.equal(media.metrics.controls,true);
  assert.equal(media.metrics.poster,true);
  assert.equal(media.metrics.helperMarker,true);
  assert.equal(iframe.packets.find(x=>x.kind==="MEDIA").metrics.frame,"SUBFRAME");
  assert.notEqual(media.documentId,iframe.packets[0].documentId);
  assert.equal(top.changes.play,0);
  assert.equal(top.changes.pause,0);
  const full=JSON.stringify(top.packets.concat(iframe.packets));
  assert.doesNotMatch(full,/secret-session-token|private-username|site-css-secret|private\/media|secret=123|https:/);
  for(const x of top.packets.concat(iframe.packets)){
    if(x.event==="ready"){
      assert.deepEqual(Object.keys(x).sort(),
        ["schemaVersion","event","documentId","frame","scriptVersion"].sort());
      continue;
    }
    if(x.event==="start_ack"){
      assert.deepEqual(Object.keys(x).sort(),
        ["schemaVersion","event","sessionId","documentId","frame"].sort());
      continue;
    }
    assert.deepEqual(Object.keys(x).sort(),
      ["schemaVersion","event","sessionId","documentId","sequence","kind","target","detail","metrics"].sort());
    assert.ok(Object.keys(x.metrics).length<=16);
  }
});

test("final5: repeat START after lost ACK re-sends baseline without duplicate listeners", () => {
  const f=fixture();
  f.command("start");
  const before=f.packets.filter(x=>x.detail==="SNAPSHOT_FINISHED").length;
  assert.equal(f.stats().observed,1);
  f.command("start");
  assert.equal(f.packets.filter(x=>x.event==="start_ack").length,2);
  assert.equal(f.packets.filter(x=>x.detail==="SNAPSHOT_FINISHED").length,before+1);
  assert.equal(f.stats().observed,1);
  assert.equal(f.stats().disposed,0);
});

test("final5: ordinary site click and hover never consume input or start playback", () => {
  const f=fixture();f.command("start");
  const before=f.packets.length;
  f.fire("pointerover",f.button);
  f.fire("click",f.button);
  f.fire("pointerdown",f.video);
  assert.equal(f.changes.prevent,0);
  assert.equal(f.changes.stop,0);
  assert.equal(f.changes.play,0);
  assert.equal(f.changes.pause,0);
  assert.ok(f.packets.length>before);
  assert.ok(f.packets.some(x=>x.detail==="ON_CLICK"&&x.target==="BUTTON"));
});

test("final5: only user-authorized picker consumes one tap and records synthetic identity", () => {
  const f=fixture();f.command("start");
  f.command("pick");
  f.fire("pointerdown",f.button);
  assert.equal(f.changes.prevent,0);
  f.fire("pointerdown",f.button,"touch");
  assert.equal(f.changes.prevent,1);
  assert.equal(f.changes.stop,1);
  const pick=f.packets.at(-1);
  assert.equal(pick.kind,"PICK");
  assert.ok(pick.metrics.node>0);
  assert.equal(pick.metrics.node,f.packets.at(-2).metrics.node);
  f.fire("pointerdown",f.button,"touch");
  assert.equal(f.changes.prevent,1);
  f.command("pick");f.command("cancel_pick");
  f.fire("pointerdown",f.button,"touch");
  assert.equal(f.changes.stop,1);
});

test("final5: media errors and unhandled play promises carry only category codes", () => {
  const f=fixture();f.command("start");
  f.fire("play");f.fire("pause");f.fire("loadedmetadata");f.fire("error");
  f.fireWindow("error");f.fireWindow("unhandledrejection");
  assert.ok(f.packets.some(x=>x.kind==="MEDIA"&&x.detail==="ON_PLAY"));
  assert.ok(f.packets.some(x=>x.kind==="MEDIA"&&x.detail==="ON_PAUSE"));
  assert.ok(f.packets.some(x=>x.kind==="ERROR"&&x.detail==="JS_ERROR"));
  assert.ok(f.packets.some(x=>x.kind==="ERROR"&&x.detail==="PROMISE_REJECTION"));
  assert.equal(f.changes.play,0);assert.equal(f.changes.pause,0);
  assert.doesNotMatch(JSON.stringify(f.packets),/private\/media|secret=123/);
});

test("final5: dynamic DOM mutations keep attribute categories without raw values", () => {
  const f=fixture();f.command("start");
  f.mutation([
    {type:"attributes",target:f.video,attributeName:"poster"},
    {type:"attributes",target:f.video,attributeName:"src"},
    {type:"attributes",target:f.button,attributeName:"class"},
    {type:"attributes",target:f.video,attributeName:"style"},
    {type:"childList",addedNodes:[f.button,f.video]},
  ]);
  assert.equal(f.packets.filter(x=>x.detail==="ATTRIBUTE_POSTER").length,1);
  assert.equal(f.packets.filter(x=>x.detail==="ATTRIBUTE_SRC").length,1);
  assert.ok(f.packets.some(x=>x.detail==="ATTRIBUTE_BATCH"));
  assert.ok(f.packets.some(x=>x.detail==="NODES_ADDED"&&x.metrics.count===2));
  assert.doesNotMatch(JSON.stringify(f.packets),/secret=123|private-username|site-css-secret/);
});

test("final5: event budget caps at 160 and explicitly indicates truncation", () => {
  const f=fixture();f.command("start");
  for(let i=0;i<250;i++)f.fire("click",f.button);
  assert.equal(f.packets.filter(x=>x.event==="observation").length,160);
  assert.equal(f.packets.at(-1).kind,"GAP");
  assert.equal(f.packets.at(-1).detail,"EVENT_LIMIT");
  assert.equal(f.packets.at(-1).sequence,160);
  assert.equal(f.changes.play,0);
});

test("final5: a restarted session restores bounded reconnect after an earlier STOP", () => {
  const stopped = fixture();
  stopped.command("start", ONE);
  stopped.command("stop", ONE);
  stopped.disconnect();
  assert.equal(stopped.pendingRetries(), 1,
    "an idle port lost after STOP must reconnect for the next diagnostic");
  stopped.retry();
  assert.equal(stopped.stats().activeListeners, 0,
    "idle reconnection must not reactivate page listeners");
  stopped.command("start", TWO);
  assert.equal(stopped.packets.filter(x=>x.event==="start_ack").at(-1).sessionId, TWO);

  const restarted = fixture();
  restarted.command("start", ONE);
  restarted.command("stop", ONE);
  restarted.command("start", TWO);
  restarted.disconnect();
  assert.equal(restarted.pendingRetries(), 1,
    "new opted-in session must reconnect after a transient disconnect");
  restarted.retry();
  assert.equal(restarted.connectCalls(), 2);
  restarted.command("start", TWO);
  assert.equal(restarted.packets.at(-1).sessionId, TWO);
});

test("final5: STOP, restart and disconnect remove observer/listeners", () => {
  const f=fixture();f.command("start");
  assert.equal(f.stats().observed,1);
  f.command("stop");
  assert.deepEqual(f.stats(),{observed:1,disposed:1,activeListeners:0});
  const old=f.packets.length;
  f.badCommand({schemaVersion:2,action:"snapshot",sessionId:ONE});
  assert.equal(f.packets.length,old);
  f.command("start",TWO);
  assert.equal(f.packets.find(x=>x.event==="start_ack").sessionId,ONE);
  assert.equal(f.packets.at(-1).sessionId,TWO);
  assert.equal(f.packets.at(-1).sequence>0,true);
  f.disconnect();
  assert.deepEqual(f.stats(),{observed:2,disposed:2,activeListeners:0});
});

test("late native connection and lost port retry are bounded and remain dormant before START", () => {
  const early=fixture();
  early.disconnect();
  assert.equal(early.stats().activeListeners,0);
  assert.equal(early.pendingRetries(),1);
  early.retry();
  assert.equal(early.connectCalls(),2);
  assert.equal(early.packets.filter(x=>x.event==="observation").length,0);
  const f=fixture({connectFailures:2});
  assert.equal(f.packets.length,0);
  assert.equal(f.stats().activeListeners,0);
  f.retry();f.retry();
  assert.equal(f.connectCalls(),3);
  assert.equal(f.pendingRetries(),0);
  f.command("start");
  assert.equal(f.packets[0].event,"ready");
  assert.equal(f.packets[1].event,"start_ack");
  const oldSequence=f.packets.filter(x=>x.event==="observation").at(-1).sequence;
  f.disconnect();
  assert.equal(f.stats().activeListeners,0);
  assert.equal(f.pendingRetries(),1);
  f.retry();
  assert.equal(f.connectCalls(),4);
  f.command("start");
  assert.equal(f.packets.filter(x=>x.event==="start_ack").length,2);
  assert.ok(f.packets.filter(x=>x.event==="observation").at(-1).sequence>oldSequence);
  f.command("stop");
  f.disconnect();
  assert.equal(f.pendingRetries(),0);
  const unavailable=fixture({connectFailures:5});
  unavailable.retry();unavailable.retry();unavailable.retry();
  assert.equal(unavailable.connectCalls(),4);
  assert.equal(unavailable.pendingRetries(),0);
  assert.equal(unavailable.stats().activeListeners,0);
});

test("final5: time expiry and failed native port fail closed", () => {
  const f=fixture();f.command("start");
  const before=f.packets.length;
  f.clockTo(1000+15*60*1000+1);
  f.fire("click",f.button);
  assert.equal(f.packets.length,before);
  assert.equal(f.stats().activeListeners,0);
  assert.equal(f.stats().disposed,1);
  const bad=fixture({failedPost:true});
  bad.command("start");
  assert.equal(bad.stats().activeListeners,0);
  assert.equal(bad.stats().observed,0);
  assert.equal(bad.stats().disposed,0);
});

test("final5: integration boundaries keep existing preview, autoplay, media detection and external MPV", () => {
  const readMain=p=>read("app/src/main/"+p);
  const helper=readMain("assets/extensions/nira-media-detector/preview-assist.js");
  const bgplay=readMain("assets/extensions/nira-bg-play/content.js");
  const media=JSON.parse(readMain("assets/extensions/nira-media-detector/manifest.json"));
  const fragment=readMain("java/com/prirai/android/nira/BrowserFragment.kt");
  const headers=readMain("java/com/prirai/android/nira/media/MpvPlaybackHeaders.kt");
  assert.ok(media.content_scripts.some(s=>s.js.includes("preview-assist.js")));
  assert.match(helper,/event\.preventDefault\(\)/);
  assert.match(helper,/niraPreviewCreated/);
  assert.match(bgplay,/if \(!resp \|\| !resp\.allowed\) return;/);
  assert.match(bgplay,/isTransientPreview/);
  assert.match(fragment,/Intent\.ACTION_VIEW/);
  assert.match(fragment,/launchVideoInMpv/);
  assert.match(fragment,/MpvPlaybackHeaders/);
  assert.match(headers,/User-Agent/);
  const probe=readMain("assets/extensions/zavro-preview-diagnostics/content.js");
  assert.doesNotMatch(probe,/\.play\(|\.pause\(|XMLHttpRequest|localStorage|indexedDB/);
  // HLS/MSE transport and media detectors are covered by the full repository
  // suites; this fixture cannot make claims about playback in real GeckoView.
  for(const name of ["media-detector.test.cjs","media-manifest-parser.test.cjs",
    "preview-compat-stage1.test.cjs","preview-bgplay-stage5.test.cjs",
    "preview-autoplay-stage2.test.cjs"])
    assert.ok(fs.existsSync(path.join(root,"app/src/test/js",name)));
});
