const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const { webcrypto } = require('node:crypto');
const read = p => fs.readFileSync(p, 'utf8');
const js = read('app/src/main/assets/extensions/nira-media-detector/playback-bridge.js');
const native = read('app/src/main/java/com/prirai/android/nira/media/MediaElementPlaybackBridge.kt');
const controller = read('app/src/main/java/com/prirai/android/nira/media/MediaDetectorController.kt');
const player = read('app/src/main/java/com/prirai/android/nira/media/ZavroMpvActivity.kt');

test('v1136 one-shot probe is tied to native challenge and still sends Port ACK', () => {
  const oneshots = [], outgoing = [], callbacks = {};
  const port = {
    postMessage: m => outgoing.push(m),
    onMessage: { addListener: f => callbacks.incoming = f },
    onDisconnect: { addListener: f => callbacks.disconnected = f }
  };
  const sandbox = {
    browser: {runtime: {
      connectNative: () => port,
      sendNativeMessage: (_app, msg) => { oneshots.push(msg); return Promise.resolve(true); }
    }},
    location: {href:'https://example.org/watch',protocol:'https:'},
    document: {readyState:'complete', addEventListener() {}},
    crypto:webcrypto, URL, Date, HTMLVideoElement:class {}, setTimeout:()=>0,
  };
  vm.runInNewContext(js,sandbox,{timeout:1000});
  const challenge='11111111-2222-4333-8444-555555555555';
  assert.ok(!oneshots.some(x => x.event==='probe_ack'));
  callbacks.incoming({version:1,event:'probe',nonce:challenge});
  assert.ok(outgoing.some(x => x.event==='probe_ack' && x.nonce===challenge));
  assert.ok(oneshots.some(x => x.event==='probe_ack' && x.probeNonce===challenge));
  callbacks.disconnected();
});

test('v1136 fail-closed one-shot offer and ACK are session+port challenge bound', () => {
  assert.match(native,/it\.engineSession === source && probes\[it\] == challenge/);
  assert.match(native,/val port = matching\.singleOrNull\(\)/);
  assert.match(native,/verifiedPorts\.contains\(port\)/);
  assert.match(native,/verifiedPorts\.remove\(port\)/);
  assert.match(native,/received\(port, message\)/);
  assert.match(controller,/source !== session/);
  assert.match(controller,/MediaElementPlaybackBridge\.receivedOneShot\(session, payload\)/);
  assert.match(js,/protocolOneShot\("auto_offer",offer\)/);
  assert.match(js,/protocolOneShot\("handoff_ack", \{nonce, ok\}\)/);
  assert.match(js,/if \(!probeNonce/);
});

test('v1136 sets initial loadfile before attaching SurfaceView', () => {
  const initialize = player.indexOf('surface.initialize(');
  const queued = player.indexOf('surface.playFile(url)');
  const attached = player.indexOf('root.addView(surface,');
  const setContent = player.indexOf('setContentView(root)');
  assert.ok(initialize >= 0 && initialize < queued && queued < attached && attached < setContent);
  assert.match(player,/override fun surfaceCreated\(holder: SurfaceHolder\)/);
  assert.match(player,/super\.surfaceCreated\(holder\)/);
  assert.match(player,/onSurfaceReady\?\.invoke\(\)/);
  assert.doesNotMatch(player,/surface\.post\s*\{[\s\S]*?surface\.playFile/);
  assert.doesNotMatch(player,/MPVLib\.command\("seek", "0", "relative\+exact"\)/);
});
