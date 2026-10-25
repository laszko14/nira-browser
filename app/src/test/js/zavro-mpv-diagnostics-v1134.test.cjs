const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const {webcrypto} = require("node:crypto");

const script = fs.readFileSync("app/src/main/assets/extensions/nira-media-detector/playback-bridge.js","utf8");
const trace = fs.readFileSync("app/src/main/java/com/prirai/android/nira/media/MpvDebugTrace.kt","utf8");
const bridge = fs.readFileSync("app/src/main/java/com/prirai/android/nira/media/MediaElementPlaybackBridge.kt","utf8");
const browser = fs.readFileSync("app/src/main/java/com/prirai/android/nira/BrowserFragment.kt","utf8");
const player = fs.readFileSync("app/src/main/java/com/prirai/android/nira/media/ZavroMpvActivity.kt","utf8");
const detector = fs.readFileSync("app/src/main/java/com/prirai/android/nira/media/MediaDetectorController.kt","utf8");

function harness({blob=false,duration=1200,muted=false}={}){
  const messages=[],listeners={};
  class Video {
    constructor(){
      this.currentSrc=this.src=blob?"blob:https://site.example/id":"https://cdn.example/movie.mp4?sig=SECRET";
      this.paused=false;this.ended=false;this.isConnected=true;this.error=null;
      this.srcObject=null;this.mediaKeys=null;this.muted=muted;this.loop=false;
      this.duration=duration;this.readyState=3;this.currentTime=3;this.parentElement=null;
    }
    getBoundingClientRect(){return {width:640,height:360};}
    pause(){this.paused=true;}
  }
  const video=new Video(),location={protocol:"https:",href:"https://site.example/watch?token=SECRET"};
  const port={postMessage:m=>messages.push(m),onMessage:{addListener:()=>{}},onDisconnect:{addListener:()=>{}}};
  vm.runInNewContext(script,{
    location,document:{readyState:"complete",querySelectorAll:()=>[video],addEventListener:(name,fn)=>listeners[name]=fn},
    HTMLVideoElement:Video,browser:{runtime:{connectNative:()=>port}},
    URL,crypto:webcrypto,Date
  },{timeout:1000});
  return {messages,video,listeners,
    gesture:()=>listeners.pointerup({isTrusted:true,target:video,type:"pointerup"}),
    play:()=>listeners.play({target:video,isTrusted:false})
  };
}

test("v1134 HTML5 script emits diagnostic codes only, not signed URLs",()=>{
  const h=harness({blob:true});
  h.gesture();h.play();
  const diagnostic=h.messages.filter(x=>x.event==="diagnostic");
  assert.ok(diagnostic.some(x=>x.code==="bridge_ready"));
  assert.ok(diagnostic.some(x=>x.code==="gesture"));
  assert.ok(diagnostic.some(x=>x.code==="source_blob"));
  assert.ok(!h.messages.some(x=>x.event==="auto_offer"));
  for(const d of diagnostic){
    assert.deepEqual(Object.keys(d).sort(),["code","event","version"]);
    assert.doesNotMatch(JSON.stringify(d),/SECRET|site\.example|cdn\.example|token/);
  }
});

test("v1142 one Supertest replaces three diagnostic buttons, without changing player mode controls",()=>{
  assert.match(browser,/id = "mpv_supertest"/);
  assert.match(browser,/onClick = \{ startMpvSupertest\(\) \}/);
  assert.doesNotMatch(browser,/id = "mpv_diag_(?:web|handoff|stream)"/);
  assert.doesNotMatch(browser,/id = "toggle_video_player_choice"/);
  assert.doesNotMatch(browser,/id = "open_video_link"/);
  assert.match(browser,/private fun startMpvSupertest\(\)/);
  assert.match(browser,/VideoPlayerPolicy.Mode.AUTO/);
  assert.match(browser,/onClick = \{ showVideoPlayerModeDialog\(\) \}/);
});

test("v1134 native trace is bounded, whitelisted, RAM only and keeps Gecko session identity",()=>{
  assert.match(trace,/private const val MAX_EVENTS = 240/);
  assert.match(trace,/if \(code !in allowed\) return/);
  assert.match(trace,/it.session === session/);
  assert.match(trace,/section == section/);
  assert.doesNotMatch(trace,/SharedPreferences|Log\.|writeText|HttpURLConnection/);
  assert.match(bridge,/data.optString\("event"\) == "diagnostic"/);
  assert.match(detector,/session_handler_attached/);
  assert.match(player,/mpv_end_before_load/);
  assert.match(player,/mpv_file_loaded/);
  assert.match(player,/mpv_auto_rollback/);
  assert.match(player,/classifySource\(url\)/);
});

test("v1134 diagnostics do not change secure Gecko and native handoff",()=>{
  assert.match(bridge,/it === target.port/);
  assert.match(bridge,/data.optString\("event"\) == "auto_offer"/);
  assert.match(browser,/target != null && leaseId == null/);
  assert.match(browser,/putExtra\(ZavroMpvActivity.EXTRA_TICKET_ID, ticket\)/);
});
