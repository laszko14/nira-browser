const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const read = p => fs.readFileSync(p, 'utf8');

test('v1145 uses one native surface parent with compact responsive controls', () => {
  const ui = read('app/src/main/java/com/prirai/android/nira/media/InlineMpvUi.kt');
  const frag = read('app/src/main/java/com/prirai/android/nira/BrowserFragment.kt');
  const trace = read('app/src/main/java/com/prirai/android/nira/media/MpvDebugTrace.kt');
  const lifecycle = ui.split('private fun toggleFullscreen()')[1].split('/** Android Back')[0];
  assert.match(ui, /windowHost.addView\(overlay/);
  assert.match(ui, /getGlobalVisibleRect\(engineVisible\)/);
  assert.match(ui, /rectangle.intersect\(hostVisible\)/);
  assert.match(ui, /main.postDelayed\(task,3_000L\)/);
  assert.match(ui, /dp\(48\),dp\(48\)/);
  assert.match(ui, /private fun updateControlsForWidth/);
  assert.match(ui, /PopupMenu\(context,anchor\)/);
  assert.match(lifecycle, /overlay.layoutParams=FrameLayout.LayoutParams\(-1,-1/);
  assert.doesNotMatch(lifecycle, /(?:windowHost|overlay|decor)\.(?:removeView|addView)\(|(?:player|video)\.playFile\(|MPVLib\.command\("loadfile"|\.destroy\(/);
  assert.match(frag, /ui.exitFullscreen\(\)/);
  assert.match(trace, /inline_position_preserved/);
  assert.match(trace, /inline_unexpected_reload/);
  assert.match(trace, /SUPERTEST 40 s/);
});

function runBridge(initialControls) {
  class Video {
    constructor() {
      this.controls = initialControls; this.currentSrc = 'https://media.example/movie.mp4';
      this.src = this.currentSrc; this.srcObject = null; this.mediaKeys = null;
      this.isConnected = true; this.ended = false; this.error = null;
      this.duration = 800; this.paused = false; this.muted = false;
      this.loop = false; this.readyState = 4; this.currentTime = 30;
    }
    getBoundingClientRect() {
      return {left:0,top:0,width:500,height:280};
    }
    pause() { this.paused = true; }
    play() { this.paused = false; return Promise.resolve(); }
  }
  const video = new Video(), handlers = {}, messages = [];
  let incoming = null, disconnected = null, uuid = 0;
  const port = {
    postMessage(v) { messages.push(v); },
    onMessage: { addListener(f) { incoming = f; } },
    onDisconnect: { addListener(f) { disconnected = f; } }
  };
  const location = {href:'https://example.org/watch',protocol:'https:'};
  const document = {
    readyState:'complete',
    addEventListener(name,f) { handlers[name] = f; },
    querySelectorAll(sel) { return sel === 'video' ? [video] : []; }
  };
  const window = {innerWidth:500,innerHeight:300,devicePixelRatio:1};
  window.top = window;
  const sandbox = {
    location, document, window, HTMLVideoElement:Video, URL,
    Date, Map, WeakMap, Number, Array, Promise,
    setTimeout(){}, crypto:{randomUUID() {
      return '10000000-0000-4000-8000-'+String(++uuid).padStart(12,'0');
    }},
    browser:{runtime:{
      connectNative(){return port;},
      sendNativeMessage(){return Promise.resolve({});}
    }}
  };
  vm.runInNewContext(read('app/src/main/assets/extensions/nira-media-detector/playback-bridge.js'),sandbox);
  assert.equal(typeof incoming,'function');
  incoming({version:1,event:'probe',nonce:'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'});
  handlers.pointerup({isTrusted:true,target:video,type:'pointerup'});
  handlers.play({target:video});
  const offer = messages.find(x=>x.event === 'auto_offer');
  assert.ok(offer,'A targeted direct media offer must exist');
  const nonce = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
  incoming({version:1,event:'prepare',nonce,pageUrl:location.href,
    mediaUrl:offer.mediaUrl,frameKey:offer.frameKey,elementKey:offer.elementKey});
  assert.equal(video.paused,true,'Gecko element paused on ACK');
  assert.equal(messages.filter(x=>x.event==='handoff_ack'&&x.ok).length,1);
  incoming({version:1,event:'inline_start',nonce});
  assert.equal(video.controls,false,'Only the verified video controls are disabled');
  return {video,incoming,disconnected,nonce};
}
test('v1145 restores original native HTMLVideo controls on rollback', () => {
  const a = runBridge(true);
  a.incoming({version:1,event:'restore',nonce:a.nonce});
  assert.equal(a.video.controls,true);
  assert.equal(a.video.paused,false);
});
test('v1145 preserves initially disabled controls on cancel', () => {
  const a = runBridge(false);
  a.incoming({version:1,event:'cancel',nonce:a.nonce});
  assert.equal(a.video.controls,false);
  assert.equal(a.video.paused,true);
});
test('v1145 restores controls when the Gecko port disconnects', () => {
  const a = runBridge(true);
  a.disconnected();
  assert.equal(a.video.controls,true);
});
