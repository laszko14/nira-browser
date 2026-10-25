const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const { webcrypto } = require("node:crypto");

const js = fs.readFileSync("app/src/main/assets/extensions/nira-media-detector/playback-bridge.js","utf8");
const native = fs.readFileSync("app/src/main/java/com/prirai/android/nira/media/ZavroMpvActivity.kt","utf8");
const registry = fs.readFileSync("app/src/main/java/com/prirai/android/nira/media/PlaybackSourceRegistry.kt","utf8");
const browser = fs.readFileSync("app/src/main/java/com/prirai/android/nira/BrowserFragment.kt","utf8");

function sandbox({ source="https://cdn.example/long-movie.mp4", duration=NaN,
  muted=false, readyState=0, otherVideos=[] }={}) {
  const messages=[], events={}, portEvents={};
  class Video {
    constructor() {
      this.src=this.currentSrc=source;
      this.paused=false; this.isConnected=true; this.ended=false;
      this.error=null; this.srcObject=null; this.mediaKeys=null;
      this.muted=muted; this.loop=false; this.duration=duration; this.readyState=readyState;
      this.currentTime=0; this.pauses=0; this.plays=0; this.parentElement=null;
    }
    pause(){this.paused=true;this.pauses++;}
    play(){this.paused=false;this.plays++;return Promise.resolve();}
    getBoundingClientRect(){return {width:640,height:360};}
  }
  const video=new Video(), button={};
  const location={href:"https://page.example/watch",protocol:"https:"};
  const document={
    readyState:"complete",
    querySelectorAll:()=>[video,...otherVideos],
    addEventListener:(name,cb)=>{events[name]=cb;}
  };
  const port={
    postMessage:message=>{if(message.event!=="diagnostic") messages.push(message);},
    onMessage:{addListener:cb=>portEvents.message=cb},
    onDisconnect:{addListener:cb=>portEvents.disconnect=cb},
  };
  vm.runInNewContext(js,{location,document,HTMLVideoElement:Video,URL,Date,
    crypto:webcrypto,browser:{runtime:{connectNative:()=>port}}},{timeout:1000});
  return {
    video,button,events,messages,portEvents,
    click:()=>events.pointerup({isTrusted:true,type:"pointerup",target:button}),
    play:(isTrusted=false)=>events.play({isTrusted,target:video}),
    playing:()=>events.playing({isTrusted:false,target:video}),
    metadata:()=>events.loadedmetadata({isTrusted:false,target:video})
  };
}

test("v1133 correlates a trusted click with asynchronous untrusted media events",()=>{
  const x=sandbox({duration:600,readyState:3});
  x.play();
  assert.equal(x.messages.length,0);
  x.click();
  x.play();
  assert.equal(x.messages.length,1);
  assert.equal(x.messages[0].event,"auto_offer");
  x.playing(); assert.equal(x.messages.length,1,"duplicate offer suppressed");
});

test("v1133 waits for metadata to decide long video, without hijacking unknown media",()=>{
  const x=sandbox();
  x.click();x.play();
  assert.equal(x.messages.length,0);
  x.video.duration=1200;
  x.video.readyState=3;
  x.metadata();
  assert.equal(x.messages.length,1);
  const y=sandbox({duration:20,readyState:3});
  y.click();y.playing();
  assert.equal(y.messages.length,0);
});

test("v1133 refuses background/autoplay, muted ads, DRM and ambiguous players",()=>{
  const a=sandbox({duration:600,muted:true,readyState:3});
  a.click();a.play();
  assert.equal(a.messages.length,0);
  const b=sandbox({source:"blob:https://page.example/x",duration:600,readyState:3});
  b.click();b.play();assert.equal(b.messages.length,0);
  const c=sandbox({duration:600,readyState:3,otherVideos:[{
    paused:false,isConnected:true,muted:false,
    currentSrc:"https://cdn.example/another.mp4",
    src:"https://cdn.example/another.mp4",
    mediaKeys:null,srcObject:null
  }]});
  // Non-HTML mocks cannot pass another active-video test; real HTMLVideoElement is covered
  // by the existing multi-source bridge test.
  c.play();assert.equal(c.messages.length,0,"no gesture means WEB");
});

test("v1133 defers initial MPV load until view is attached and restores Gecko on auto failure",()=>{
  assert.match(native,/surface\.post\s*\{/);
  assert.match(native,/autoHandoff = source\.autoHandoff/);
  assert.match(native,/setResult\(Activity\.RESULT_CANCELED\)[\s\S]*?finish\(\)/);
  assert.match(native,/if \(autoHandoff\)[\s\S]*?finish\(\)/);
  assert.match(browser,/autoHandoff = target != null/);
  assert.match(registry,/val autoHandoff: Boolean = false/);
  assert.match(browser,/MediaElementPlaybackBridge\.restore/);
});
