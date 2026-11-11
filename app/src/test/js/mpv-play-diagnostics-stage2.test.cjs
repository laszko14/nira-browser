const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const { webcrypto } = require('node:crypto');
const root = path.resolve(__dirname, '../../../..');
const script = fs.readFileSync(path.join(root,
  'app/src/main/assets/extensions/zavro-preview-diagnostics/content.js'), 'utf8');
const manifest = JSON.parse(fs.readFileSync(path.join(root,
  'app/src/main/assets/extensions/zavro-preview-diagnostics/manifest.json'), 'utf8'));
const ID = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee';

function harness(frame = false) {
  const packets = [];
  const listeners = new Map();
  let receive, disconnected;
  const node = {
    tagName: 'VIDEO', paused: true, muted: false, autoplay: false,
    controls: false, loop: false, poster: 'https://example.org/SUPER_SECRET',
    playsInline: true, preload: 'metadata', duration: 27.2,
    currentTime: 0, readyState: 2, networkState: 1, error: null,
    getBoundingClientRect: () => ({ x: 20, y: 30, width: 220, height: 120 }),
    getRootNode: () => document,
    getAttribute: () => 'SUPER_SECRET',
    closest() { return this; }
  };
  const document = {
    addEventListener: (name, fn) => listeners.set(name, fn),
    removeEventListener: (name, fn) => {
      if (listeners.get(name) === fn) listeners.delete(name);
    },
    querySelectorAll: () => [node],
    createTreeWalker: () => ({nextNode: () => null})
  };
  const window = {
    addEventListener: (name, fn) => listeners.set('window:' + name, fn),
    removeEventListener: (name, fn) => {
      if (listeners.get('window:' + name) === fn) listeners.delete('window:' + name);
    }
  };
  window.top = frame ? {} : window;
  let observeCount = 0, disconnectCount = 0, onMutation;
  class MutationObserver {
    constructor(fn) { onMutation = fn; }
    observe() { observeCount++; }
    disconnect() { disconnectCount++; }
  }
  const port = {
    postMessage(message) { packets.push(JSON.parse(JSON.stringify(message))); },
    onMessage: { addListener(fn) { receive = fn; } },
    onDisconnect: { addListener(fn) { disconnected = fn; } }
  };
  vm.runInNewContext(script, { window, document, NodeFilter:{SHOW_ELEMENT:1},
    crypto:webcrypto, MutationObserver, setTimeout: () => 0, browser: {
      runtime: {connectNative(name) {
        assert.equal(name, 'zavro.preview.forensics'); return port;
      }}
    }, getComputedStyle: () => ({
      display:'block', visibility:'visible', pointerEvents:'auto', zIndex:'2'
    }) }, {timeout: 1000});
  return {packets, listeners, node, command: command => receive(command),
    disconnect: () => disconnected(), observerState: () => [observeCount,disconnectCount],
    mutate: batch => onMutation(batch)};
}

test('manifest runs frame-aware content probe, but no page observers before user START', () => {
  assert.equal(manifest.version, '1.0.2');
  assert.equal(manifest.content_scripts[0].all_frames, true);
  assert.equal(manifest.content_scripts[0].run_at, 'document_start');
  const x = harness();
  assert.equal(x.packets.length, 1);
  assert.equal(x.packets[0].event, 'ready');
  assert.equal(x.listeners.size, 0);
  assert.deepEqual(x.observerState(), [0,0]);
  x.command({schemaVersion:2,action:'start',sessionId:'bad'});
  assert.equal(x.packets.length, 1);
});

test('START captures bounded metadata not page text, passwords, raw source URL or token', () => {
  const x = harness();
  x.command({schemaVersion:2,action:'start',sessionId:ID});
  assert.equal(x.packets[0].event, 'ready');
  assert.equal(x.packets[1].event, 'start_ack');
  assert.equal(x.packets[1].sessionId, ID);
  assert.match(x.packets[1].documentId, /^[0-9a-f]{32}$/);
  assert.ok(x.packets.some(p => p.detail === 'SNAPSHOT_FINISHED'));
  assert.ok(x.packets.length >= 2);
  const media = x.packets.find(p => p.kind === 'MEDIA');
  assert.equal(media.schemaVersion, 2);
  assert.equal(media.metrics.poster, true);
  assert.equal(media.metrics.readyState, 2);
  assert.equal(media.metrics.paused, true);
  assert.equal(media.metrics.controls, false);
  assert.equal(Object.keys(media.metrics).length <= 16, true);
  assert.equal(media.metrics.frame, 'TOP');
  assert.doesNotMatch(JSON.stringify(x.packets), /SUPER_SECRET|https:\/\/example\.org/);
  assert.deepEqual(x.observerState(), [1,0]);
  x.command({schemaVersion:2,action:'snapshot',sessionId:'ffffffff-ffff-ffff-ffff-ffffffffffff'});
  assert.equal(x.observerState()[0], 1);
  const old = x.packets.length;
  x.command({schemaVersion:2,action:'snapshot',sessionId:ID});
  assert.ok(x.packets.length > old);
  x.command({schemaVersion:2,action:'stop',sessionId:ID});
  assert.equal(x.listeners.size, 0);
  assert.deepEqual(x.observerState(), [1,1]);
  const done = x.packets.length;
  x.command({schemaVersion:2,action:'snapshot',sessionId:ID});
  assert.equal(x.packets.length, done);
});

test('picker consumes exactly the authorised touch, and can be disarmed across frames', () => {
  const x = harness(true);
  x.command({schemaVersion:2,action:'start',sessionId:ID});
  x.command({schemaVersion:2,action:'pick',sessionId:ID});
  let prevented = 0;
  const touch = {type:'pointerdown',pointerType:'touch',target:x.node,
    preventDefault() { prevented++; },
    stopImmediatePropagation() { prevented++; }};
  x.listeners.get('pointerdown')(touch);
  assert.equal(prevented, 2);
  assert.equal(x.packets.at(-1).kind, 'PICK');
  assert.ok(x.packets.at(-1).metrics.node > 0);
  assert.equal(x.packets.at(-1).metrics.node, x.packets.at(-2).metrics.node);
  assert.equal(x.packets.at(-1).metrics.frame, 'SUBFRAME');
  x.listeners.get('pointerdown')(touch);
  assert.equal(prevented, 2);
  x.command({schemaVersion:2,action:'pick',sessionId:ID});
  x.command({schemaVersion:2,action:'cancel_pick',sessionId:ID});
  x.listeners.get('pointerdown')(touch);
  assert.equal(prevented, 2);
  x.disconnect();
  assert.equal(x.listeners.size, 0);
});

test('attribute changes retain synthetic node identity and safe post-change HTML5 state', () => {
  const x = harness();
  x.command({schemaVersion:2,action:'start',sessionId:ID});
  const firstNode = x.packets.find(p => p.kind === 'MEDIA').metrics.node;
  x.node.paused = false;
  x.mutate([{type:'attributes',target:x.node,attributeName:'poster'},
    {type:'attributes',target:x.node,attributeName:'src'}]);
  const attributes = x.packets.filter(p => p.kind === 'MUTATION');
  assert.equal(attributes.length, 2);
  assert.equal(attributes[0].detail, 'ATTRIBUTE_POSTER');
  assert.equal(attributes[0].metrics.node, firstNode);
  assert.equal(attributes[1].detail, 'ATTRIBUTE_SRC');
  assert.ok(x.packets.some(p => p.kind === 'MEDIA' &&
    p.detail === 'ATTRIBUTE_STATE' && p.metrics.paused === false));
  assert.doesNotMatch(JSON.stringify(x.packets), /SUPER_SECRET|https:\/\/example\.org/);
});

test('packaged preview-assist marker is recorded as an untrusted hint only', () => {
  const f = harness();
  f.node.dataset = {niraPreviewCreated:'1'};
  f.command({schemaVersion:2,action:'start',sessionId:ID});
  const video = f.packets.find(p => p.kind === 'MEDIA' && p.target === 'VIDEO');
  assert.ok(video);
  assert.equal(video.metrics.helperMarker,true);
  assert.equal(video.metrics.poster,true);
  assert.equal(Object.keys(video.metrics).length <= 16,true);
  assert.doesNotMatch(JSON.stringify(f.packets), /SUPER_SECRET|example\.org/);
  const source = fs.readFileSync(path.join(root,
    'app/src/main/assets/extensions/nira-media-detector/preview-assist.js'),'utf8');
  assert.match(source, /video\.dataset\.niraPreviewCreated\s*=\s*"1"/);
});

test('no page rewriting, media playback or background persistence in probe source', () => {
  assert.doesNotMatch(script, /\.play\s*\(|\.pause\s*\(|\.innerHTML|\.textContent/);
  assert.doesNotMatch(script, /\bfetch\s*\(|XMLHttpRequest|localStorage|indexedDB|sendNativeMessage/);
  assert.match(script, /MAX_EVENTS = 160/);
  assert.match(script, /MAX_NODES = 40/);
  assert.match(script, /stopImmediatePropagation/);
});

test('native bridge authenticates current Gecko tab partition rather than active profile alone', () => {
  const bridge = fs.readFileSync(path.join(root,
    'app/src/main/java/com/prirai/android/nira/browser/MpvPlayPageBridgeController.kt'), 'utf8');
  const fragment = fs.readFileSync(path.join(root,
    'app/src/main/java/com/prirai/android/nira/BrowserFragment.kt'), 'utf8');
  assert.match(bridge, /ProfileTabScope\.belongsToNormalProfile\(tab, profile\)/);
  assert.match(fragment, /ProfileTabScope\s*\.belongsToNormalProfile\(current, profileId\)/);
});
