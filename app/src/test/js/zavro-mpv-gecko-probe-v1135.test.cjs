const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const { webcrypto } = require('node:crypto');
const read = name => fs.readFileSync(name,'utf8');
const bridge = read('app/src/main/assets/extensions/nira-media-detector/playback-bridge.js');
const native = read('app/src/main/java/com/prirai/android/nira/media/MediaElementPlaybackBridge.kt');
const install = read('app/src/main/java/com/prirai/android/nira/media/MediaDetectorController.kt');
const trace = read('app/src/main/java/com/prirai/android/nira/media/MpvDebugTrace.kt');
const player = read('app/src/main/java/com/prirai/android/nira/media/ZavroMpvActivity.kt');
const manifest = JSON.parse(read('app/src/main/assets/extensions/nira-media-detector/manifest.json'));

function createHarness() {
  const ports=[], single=[], callbacks={}, events={};
  const port = {
    postMessage: m=>ports.push(m),
    onMessage:{addListener: f => callbacks.message=f},
    onDisconnect:{addListener:f=>callbacks.disconnect=f},
  };
  class Video {}
  const sandbox = {
    browser:{runtime:{
      connectNative: () => port,
      sendNativeMessage:(_name, msg)=>{single.push(msg);return Promise.resolve(true);}
    }},
    location:{href:'https://example.org/watch',protocol:'https:'},
    document:{readyState:'complete',addEventListener:(type,f)=>events[type]=f},
    crypto:webcrypto,HTMLVideoElement:Video,URL,Date,
  };
  vm.runInNewContext(bridge,sandbox,{timeout:1000});
  return {single,ports,callbacks,events};
}

test('v1135 WebExtension sends Gecko one-shot heartbeat even if port payloads are lost',()=>{
 const h=createHarness();
 assert.ok(h.single.some(x=>x.event==='diagnostic'&&x.code==='script_started'));
 assert.ok(h.single.some(x=>x.event==='diagnostic'&&x.code==='bridge_ready'));
 assert.ok(h.ports.some(x=>x.event==='diagnostic'&&x.code==='bridge_ready'));
 assert.ok(h.single.every(x=>Object.keys(x).sort().join(',')==='code,event,version'));
 const nonce='ee1b2f56-8205-4c7b-84c1-884455667788';
 h.callbacks.message({version:1,event:'probe',nonce});
 assert.ok(h.ports.some(x=>x.event==='probe_ack'&&x.nonce===nonce));
 assert.ok(h.single.some(x=>x.code==='probe_received'));
});

test('v1135 detector registers a separate session-bound one-shot callback, no URLs in trace',()=>{
 assert.match(install,/override fun onMessage\(message: Any, source: EngineSession\?\): Any/);
 assert.match(install,/if \(source !== session\) return false/);
 assert.match(install,/oneshot_received/);
 assert.match(native,/probe_no_ack/);
 assert.match(native,/probe_ack/);
 assert.match(native,/message_not_json/);
 assert.match(trace,/code !in allowed/);
 assert.doesNotMatch(trace,/SharedPreferences|writeText\(|Log\.d\(/);
});

test('v1135 reports only bounded MPV network classes and event lifecycle',()=>{
 for(const code of ['mpv_http_401','mpv_http_403','mpv_http_404','mpv_network_timeout',
   'mpv_tls_error','mpv_dns_failure','mpv_start_file','mpv_cache_wait']){
   assert.ok(trace.includes('"'+code+'"'));
 }
 assert.match(player,/MPVLib\.addLogObserver\(mpvLogs\)/);
 assert.match(player,/MPVLib\.removeLogObserver\(mpvLogs\)/);
 assert.match(player,/MPV_EVENT_START_FILE/);
 assert.match(player,/paused-for-cache/);
 assert.match(trace,/classifyMpvLog\(prefix: String, level: Int, message: String\)/);
 assert.match(manifest.version,/^1\.5\.[1-9]\d*$/);
 assert.equal(manifest.content_scripts[0].all_frames,true);
 assert.equal(manifest.content_scripts[0].match_about_blank,true);
});

test('v1135 keeps trusted frame ACK before AUTO mpv, not start based on diagnostics',()=>{
 const b=read('app/src/main/java/com/prirai/android/nira/BrowserFragment.kt');
 assert.match(b,/target != null && leaseId == null/);
 assert.match(native,/it === target\.port/);
 assert.match(native,/handoff_ack/);
 assert.match(b,/com\.prirai\.android\.nira\.media\.PlaybackSourceRegistry\.issue/);
});
